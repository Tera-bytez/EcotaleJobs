package com.ecotalejobs.systems;

import com.ecotale.api.EcotaleAPI;
import com.ecotale.api.PhysicalCoinsProvider;
import com.ecotale.util.RateLimiter;
import com.ecotalejobs.config.EcotaleJobsConfig.MiningConfig;
import com.ecotalejobs.Main;
import com.ecotalejobs.config.EcotaleJobsConfig.ToolQualityConfig;
import com.ecotalejobs.config.EcotaleJobsConfig.DepthBonusConfig;
import com.ecotalejobs.config.EcotaleJobsConfig.SecurityConfig;
import com.ecotalejobs.config.EcotaleJobsConfig.VeinStreakConfig;
import com.ecotalejobs.config.TierConfig;
import com.ecotalejobs.security.AntiFarmSystem;
import com.ecotalejobs.security.EconomyCap;
import com.ecotalejobs.util.JobsLogger;
import com.ecotalejobs.util.VeinStreakTracker;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockBreakingDropType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockGathering;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemTool;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemToolSpec;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.BlockHarvestUtils;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.protocol.SoundCategory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Mining reward system - uses BreakBlockEvent for block mining rewards.
 * Classification via reflection on Hytale Family tags.
 */
public class MiningRewardSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {
    
    // Configuration - set via init()
    private MiningConfig config;
    
    // Manual overrides (optional, for edge cases)
    private Map<String, String> tierOverrides;
    private Set<String> exclusions = new HashSet<>();
    
    // Core subsystems
    private final AntiFarmSystem antiFarm = new AntiFarmSystem();
    private final EconomyCap economyCap = new EconomyCap();
    private final RateLimiter rateLimiter;
    
    // Thread-safe statistics
    private final AtomicLong totalRewardsGiven = new AtomicLong(0);
    private final AtomicLong totalValueInjected = new AtomicLong(0);
    private final AtomicLong rewardsBlocked = new AtomicLong(0);
    
    public MiningRewardSystem() {
        super(BreakBlockEvent.class);
        // RateLimiter: 60 burst capacity, 10 tokens/sec refill
        this.rateLimiter = new RateLimiter(60, 10);
    }
    
    /**
     * Initialize the reward system with configuration.
     */
    public void init(MiningConfig config) {
        this.config = config;
        
        if (config == null) {
            JobsLogger.warn("[MiningRewardSystem] Config is null - system DISABLED");
            return;
        }
        
        // Configure anti-farm subsystem
        SecurityConfig security = config.getSecurity();
        antiFarm.configure(
            security.getAntiFarmThreshold(),
            security.getAntiFarmDecayPerKill(),
            0.1f,  // Minimum multiplier (10% of reward at worst)
            30,    // TTL in minutes
            security.isAntiFarmEnabled()
        );
        
        // Configure global economy cap
        economyCap.configure(
            security.getMaxGlobalInjectionPerHour(),
            true
        );
        
        JobsLogger.info("[MiningRewardSystem] Initialized with AUTO-CLASSIFICATION by quality | %d tiers", 
            config.getTiers().size());
    }
    
    /**
     * Set manual tier overrides for edge cases.
     */
    public void setOverrides(Map<String, String> overrides, Set<String> exclusions) {
        this.tierOverrides = overrides;
        this.exclusions = exclusions != null ? exclusions : new HashSet<>();
    }
    
    // =========================================================================
    // EntityEventSystem Implementation
    // =========================================================================
    
    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Player.getComponentType();
    }
    
    @Override
    public void handle(
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull BreakBlockEvent event
    ) {
        // Guard: System disabled
        if (config == null || !config.isEnabled()) {
            return;
        }
        
        BlockType blockType = event.getBlockType();
        ItemStack itemInHand = event.getItemInHand();
        Vector3i targetBlock = event.getTargetBlock();
        
        if (blockType == null) {
            return;
        }
        
        String blockId = blockType.getId();
        
        // ─────────────────────────────────────────────────────────────
        // LAYER 1: GET BREAKING DROP INFO
        // ─────────────────────────────────────────────────────────────
        BlockGathering gathering = blockType.getGathering();
        if (gathering == null) {
            return;
        }
        
        BlockBreakingDropType breaking = gathering.getBreaking();
        if (breaking == null) {
            return; // Not a mineable block
        }
        
        // ─────────────────────────────────────────────────────────────
        // LAYER 2: GATHER TYPE CHECK (Rocks = pickaxe mining in Hytale)
        // ─────────────────────────────────────────────────────────────
        String gatherType = breaking.getGatherType();
        
        // DEBUG: Log block break attempts (Quality field is always 0 in current Hytale version)
        JobsLogger.debug("[MINING-DEBUG] Block: %s | GatherType: %s", blockId, gatherType);
        
        // Allow "Rocks", "VolcanicRocks", etc.
        if (gatherType == null || !gatherType.contains("Rocks")) {
            return; // Not rock mining (could be wood, soft blocks, etc.)
        }
        
        // ─────────────────────────────────────────────────────────────
        // LAYER 3: TOOL VALIDATION - Require actual tool (not bare hands)
        // ─────────────────────────────────────────────────────────────
        Item heldItem = itemInHand != null ? itemInHand.getItem() : null;
        ItemTool tool = heldItem != null ? heldItem.getTool() : null;
        
        // IMPORTANT: Require a tool - bare-hand mining gives no rewards
        if (tool == null) {
            return; // No tool equipped - no mining reward
        }
        
        ItemToolSpec spec = BlockHarvestUtils.getSpecPowerDamageBlock(heldItem, blockType, tool);
        
        if (spec == null || spec.isIncorrect()) {
            return; // Wrong tool type for this block
        }
        
        // ─────────────────────────────────────────────────────────────
        // LAYER 4: EXCLUSION CHECK
        // ─────────────────────────────────────────────────────────────
        if (isExcluded(blockId)) {
            return;
        }
        
        // ─────────────────────────────────────────────────────────────
        // LAYER 5: AUTO-CLASSIFY BY QUALITY
        // ─────────────────────────────────────────────────────────────
        String tierName = autoClassifyBlock(blockId, breaking);
        
        if ("NONE".equals(tierName)) {
            return;
        }
        
        TierConfig tier = config.getTierSafe(tierName, "STONE");
        
        // ─────────────────────────────────────────────────────────────
        // LAYER 6: DROP CHANCE (with VIP bonus)
        // ─────────────────────────────────────────────────────────────
        // Get Player entity first (needed for VIP permission checks)
        Player player = archetypeChunk.getComponent(index, Player.getComponentType());
        
        int baseDropChance = tier.getDropChance();
        int vipChanceBonus = (player != null) 
            ? Main.CONFIG.get().getVipMultipliers().calculateChanceBonus(player) 
            : 0;
        int effectiveDropChance = Math.min(baseDropChance + vipChanceBonus, 100);
        
        if (effectiveDropChance < 100) {
            int roll = ThreadLocalRandom.current().nextInt(100);
            if (roll >= effectiveDropChance) {
                return;
            }
        }
        
        // ─────────────────────────────────────────────────────────────
        // GET PLAYER
        // ─────────────────────────────────────────────────────────────
        PlayerRef playerRef = archetypeChunk.getComponent(index, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }
        
        UUID playerUuid = playerRef.getUuid();
        
        // ─────────────────────────────────────────────────────────────
        // LAYER 7: RATE LIMIT + ANTI-FARM
        // ─────────────────────────────────────────────────────────────
        if (!rateLimiter.tryAcquire(playerUuid)) {
            rewardsBlocked.incrementAndGet();
            return;
        }
        
        float antiFarmMultiplier = antiFarm.getMultiplierAndRecord(playerUuid, blockId);
        
        // ─────────────────────────────────────────────────────────────
        // REWARD CALCULATION
        // ─────────────────────────────────────────────────────────────
        int baseCoins = tier.getMinCoins();
        int range = tier.getMaxCoins() - tier.getMinCoins();
        if (range > 0) {
            baseCoins += ThreadLocalRandom.current().nextInt(range + 1);
        }
        
        // Tool Quality Multiplier
        ToolQualityConfig toolQualityConfig = config.getToolQuality();
        float toolMultiplier = toolQualityConfig.calculateMultiplier(spec.getQuality());
        
        // Depth Bonus Multiplier
        DepthBonusConfig depthConfig = config.getDepthBonus();
        float depthMultiplier = depthConfig.calculateMultiplier(targetBlock.getY());
        
        // VIP Multiplier (player implements CommandSender which has hasPermission)
        float vipMultiplier = (player != null) 
            ? Main.CONFIG.get().getVipMultipliers().calculateMultiplier(player) 
            : 1.0f;

        // Apply all multipliers
        float totalMultiplier = antiFarmMultiplier * toolMultiplier * depthMultiplier * vipMultiplier;
        float exactCoins = baseCoins * totalMultiplier;
        int finalCoins = (int) exactCoins;
        
        // Probabilistic rounding
        if (ThreadLocalRandom.current().nextFloat() < (exactCoins - finalCoins)) {
            finalCoins++;
        }
        
        if (finalCoins < 1) {
            rewardsBlocked.incrementAndGet();
            return;
        }
        
        long totalValue = (long) finalCoins * tier.getCoinValue();
        
        // ─────────────────────────────────────────────────────────────
        // ECONOMY CAP CHECK
        // ─────────────────────────────────────────────────────────────
        if (!economyCap.tryInject(totalValue)) {
            rewardsBlocked.incrementAndGet();
            return;
        }
        
        // ─────────────────────────────────────────────────────────────
        // SUCCESS: GIVE REWARD
        // Uses physical coins if addon is available, otherwise direct balance
        // ─────────────────────────────────────────────────────────────
        Vector3d dropPosition = new Vector3d(
            targetBlock.getX() + 0.5,
            targetBlock.getY() + 0.5,
            targetBlock.getZ() + 0.5
        );
        
        if (EcotaleAPI.isPhysicalCoinsAvailable()) {
            PhysicalCoinsProvider coins = EcotaleAPI.getPhysicalCoins();
            coins.dropCoins(store, commandBuffer, dropPosition, totalValue);
        } else {
            EcotaleAPI.deposit(playerUuid, (double) totalValue, "Mining: " + blockId);
        }
        
        // Update statistics
        totalRewardsGiven.incrementAndGet();
        totalValueInjected.addAndGet(totalValue);
        
        JobsLogger.debug("SUCCESS: %s -> %d coins (exact=%.2f, tool=%.2fx, depth=%.2fx, vip=%.2fx)", 
            blockId, finalCoins, exactCoins, toolMultiplier, depthMultiplier, vipMultiplier);
        
        // ─────────────────────────────────────────────────────────────
        // VEIN STREAK: Audio + Bonus (only for non-BASIC tiers)
        // ─────────────────────────────────────────────────────────────
        if (!tierName.equals("BASIC") && !tierName.equals("NONE")) {
            VeinStreakConfig streakConfig = config.getVeinStreak();
            if (streakConfig.isEnabled()) {
                int streak = VeinStreakTracker.getInstance().recordOreAndGetStreak(playerUuid);
                
                // Audio feedback
                if (streakConfig.isAudioEnabled() && streak > 0) {
                    String soundId = streakConfig.getSoundEventId();
                    int soundIndex = SoundEvent.getAssetMap().getIndex(soundId);
                    if (soundIndex != Integer.MIN_VALUE) {
                        float pitch = streakConfig.calculatePitch(streak);
                        float volume = streakConfig.getVolume();
                        SoundUtil.playSoundEvent2dToPlayer(playerRef, soundIndex, 
                            SoundCategory.SFX, volume, pitch);
                    }
                }
                
                // Bonus coins (probability-based)
                int bonusChance = streakConfig.calculateBonusChance(streak);
                if (bonusChance > 0) {
                    int roll = ThreadLocalRandom.current().nextInt(100);
                    if (roll < bonusChance) {
                        int bonusAmount = streakConfig.getBonusCoinAmount();
                        long bonusValue = (long) bonusAmount; // Copper value = 1
                        if (economyCap.tryInject(bonusValue)) {
                            if (EcotaleAPI.isPhysicalCoinsAvailable()) {
                                PhysicalCoinsProvider coins = EcotaleAPI.getPhysicalCoins();
                                coins.dropCoins(store, commandBuffer, dropPosition, bonusValue);
                            } else {
                                EcotaleAPI.deposit(playerUuid, (double) bonusValue, "VeinStreak bonus");
                            }
                            totalValueInjected.addAndGet(bonusValue);
                            JobsLogger.debug("[VEIN STREAK] Streak %d -> Bonus +%d", streak, bonusAmount);
                        }
                    }
                }
            }
        }
        
        JobsLogger.debug("[MINING] %s (q=%d) -> %s -> %d coins", 
            blockId, breaking.getQuality(), tierName, finalCoins);
    }
    
    /**
     * Uses native Hytale tags (via reflection) to classify blocks.
     * This is 100% robust as it uses the game's internal categorization.
     */
    private String autoClassifyBlock(String blockId, BlockBreakingDropType breaking) {
        // 1. Check manual override first
        if (tierOverrides != null && tierOverrides.containsKey(blockId)) {
            return tierOverrides.get(blockId);
        }

        // ══════════════════════════════════════════════════════════════════════════════
        // HYTALE ASSET BUGS - These ores have incorrect Family tags in Hytale assets:
        // - Adamantite: Tagged as "Gold" family, should be high-tier ore
        // - Mithril: Tagged as "Mithril" but reflection sometimes fails
        // - Silver: Tagged as "Gold" family (works by coincidence since both are RARE)
        // TODO: Review after Hytale updates to see if these are fixed
        // ══════════════════════════════════════════════════════════════════════════════
        if (blockId.contains("Adamantite")) return "LEGENDARY";
        if (blockId.contains("Mithril")) return "LEGENDARY";

        // 2. Try to get tags from the Item asset using reflection
        try {
            com.hypixel.hytale.server.core.asset.type.item.config.Item item = 
                com.hypixel.hytale.server.core.asset.type.item.config.Item.getAssetMap().getAsset(blockId);
            
            if (item != null) {
                // Access the protected 'data' field
                java.lang.reflect.Field dataField = com.hypixel.hytale.server.core.asset.type.item.config.Item.class.getDeclaredField("data");
                dataField.setAccessible(true);
                Object dataObj = dataField.get(item);
                
                if (dataObj instanceof com.hypixel.hytale.assetstore.AssetExtraInfo.Data) {
                    com.hypixel.hytale.assetstore.AssetExtraInfo.Data data = (com.hypixel.hytale.assetstore.AssetExtraInfo.Data) dataObj;
                    java.util.Map<String, String[]> tags = data.getRawTags();
                    
                    if (tags.containsKey("Family")) {
                        String[] families = tags.get("Family");
                        for (String family : families) {
                            String tier = mapFamilyToTier(family);
                            if (tier != null) {
                                JobsLogger.debug("[MINING-DEBUG] Found tag family: %s -> Tier: %s", family, tier);
                                return tier;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            JobsLogger.error("Failed to read tags for block " + blockId + ": " + e.getMessage());
        }

        // 3. Fallback: Name pattern matching (if tags failed)
        // Only ores with explicit names get rewards, plain rocks return NONE
        if (blockId.contains("Thorium") || blockId.contains("Onyxium")) return "LEGENDARY";
        if (blockId.contains("Cobalt") || blockId.contains("Diamond") || blockId.contains("Emerald")) return "EPIC";
        if (blockId.contains("Gold") || blockId.contains("Silver")) return "RARE";
        if (blockId.contains("Iron")) return "UNCOMMON";
        if (blockId.contains("Coal") || blockId.contains("Copper")) return "COMMON";
        
        // Plain rocks (Rock_Stone, Rock_Basalt, Rock_Marble, etc.) get no reward
        return "NONE";
    }

    private String mapFamilyToTier(String family) {
        switch (family) {
            case "Mithril":
            case "Adamantite":
            case "Thorium":
            case "Onyxium":
                return "LEGENDARY";
            case "Cobalt":
            case "Diamond":
            case "Emerald":
                return "EPIC";
            case "Gold":
            case "Silver":
                return "RARE";
            case "Iron":
                return "UNCOMMON";
            case "Coal":
            case "Copper":
                return "COMMON";
            default:
                return null;
        }
    }
    
    /**
     * Check if a block ID matches any exclusion pattern.
     */
    private boolean isExcluded(String blockId) {
        if (exclusions.contains(blockId)) {
            return true;
        }
        
        for (String pattern : exclusions) {
            if (matchesPattern(blockId, pattern)) {
                return true;
            }
        }
        
        return false;
    }
    
    private boolean matchesPattern(String text, String pattern) {
        if (pattern.startsWith("*") && pattern.endsWith("*")) {
            return text.contains(pattern.substring(1, pattern.length() - 1));
        } else if (pattern.startsWith("*")) {
            return text.endsWith(pattern.substring(1));
        } else if (pattern.endsWith("*")) {
            return text.startsWith(pattern.substring(0, pattern.length() - 1));
        }
        return text.equals(pattern);
    }
    
    // =========================================================================
    // Maintenance
    // =========================================================================
    
    public void performCleanup() {
        int antiFarmCleaned = antiFarm.cleanup();
        rateLimiter.cleanup();
        
        if (antiFarmCleaned > 0) {
            JobsLogger.debug("Cleanup: removed %d anti-farm trackers", antiFarmCleaned);
        }
    }
    
    // =========================================================================
    // Monitoring API
    // =========================================================================
    
    public long getTotalRewardsGiven() { return totalRewardsGiven.get(); }
    public long getTotalValueInjected() { return totalValueInjected.get(); }
    public long getRewardsBlocked() { return rewardsBlocked.get(); }
    public int getActiveAntiFarmTrackers() { return antiFarm.getActiveTrackerCount(); }
    public long getRemainingEconomyCap() { return economyCap.getRemainingCapacity(); }
    
    @Nullable
    public MiningConfig getConfig() { return config; }
}
