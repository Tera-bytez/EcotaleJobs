package com.ecotalejobs.systems;

import com.ecotale.api.EcotaleAPI;
import com.ecotale.util.RateLimiter;
import com.ecotalejobs.config.CraftingMappingsConfig;
import com.ecotalejobs.config.TierConfig;
import com.ecotalejobs.security.AntiFarmSystem;
import com.ecotalejobs.security.EconomyCap;
import com.ecotalejobs.util.CraftingTierMatcher;
import com.ecotalejobs.util.JobsLogger;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.CraftRecipeEvent;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Crafting reward system - uses CraftRecipeEvent.Post.
 */
public class CraftingRewardSystem extends EntityEventSystem<EntityStore, CraftRecipeEvent.Post> {
    
    // =========================================================================
    // Configuration
    // =========================================================================
    
    private CraftingConfig config;
    private CraftingMappingsConfig mappingsConfig;
    
    // Core subsystems
    private final CraftingTierMatcher tierMatcher = new CraftingTierMatcher();
    private final AntiFarmSystem antiFarm = new AntiFarmSystem();
    private final EconomyCap economyCap = new EconomyCap();
    private final RateLimiter rateLimiter;
    
    // Thread-safe statistics
    private final AtomicLong totalRewardsGiven = new AtomicLong(0);
    private final AtomicLong totalValueInjected = new AtomicLong(0);
    private final AtomicLong rewardsBlocked = new AtomicLong(0);
    private final AtomicLong totalItemsCrafted = new AtomicLong(0);
    
    public CraftingRewardSystem() {
        super(CraftRecipeEvent.Post.class);
        // RateLimiter: 50 burst capacity, 10 tokens/sec refill
        // Crafting can be rapid-fire, allow higher burst
        this.rateLimiter = new RateLimiter(50, 10);
    }
    
    /**
     * Initialize the crafting reward system.
     * 
     * @param config The crafting configuration
     * @param mappings The crafting tier mappings
     */
    public void init(CraftingConfig config, CraftingMappingsConfig mappings) {
        this.config = config;
        this.mappingsConfig = mappings;
        
        if (config == null || mappings == null) {
            JobsLogger.warn("[CraftingRewardSystem] Config is null - system DISABLED");
            return;
        }
        
        // Initialize tier matcher
        tierMatcher.configure(mappings);
        
        // Configure anti-farm for crafting (different thresholds than mobs)
        antiFarm.configure(
            config.getAntiFarmThreshold(),  // e.g., 20 crafts of same recipe
            config.getAntiFarmDecay(),      // e.g., 5% decay per craft
            0.2f,                            // Minimum 20% reward at worst
            60,                              // 1 hour TTL for crafting tracking
            config.isAntiFarmEnabled()
        );
        
        // Configure economy cap
        economyCap.configure(
            config.getMaxInjectionPerHour(),
            true
        );
        
        JobsLogger.info("[CraftingRewardSystem] Initialized: %d item mappings, %d recipe mappings, %d category mappings | AntiFarm=%s",
            mappings.getItemMappings().size(),
            mappings.getRecipeMappings().size(),
            mappings.getCategoryMappings().size(),
            config.isAntiFarmEnabled() ? "ON" : "OFF");
    }
    
    // =========================================================================
    // EntityEventSystem Implementation
    // =========================================================================
    
    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        // Process events for entities that have Player component
        return Player.getComponentType();
    }
    
    @Override
    public void handle(
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull CraftRecipeEvent.Post event
    ) {
        // Get the crafting recipe
        CraftingRecipe recipe = event.getCraftedRecipe();
        int quantity = event.getQuantity();
        
        if (recipe == null) {
            JobsLogger.debug("[CraftingReward] SKIP: Null recipe");
            return;
        }
        
        String recipeId = recipe.getId();
        MaterialQuantity primaryOutput = recipe.getPrimaryOutput();
        String outputItemId = primaryOutput != null ? primaryOutput.getItemId() : "unknown";
        
        // HIGH VISIBILITY LOG - ECS event received
        JobsLogger.info("[CRAFT-ECS] EVENT RECEIVED: %s (qty=%d, output=%s)", 
            recipeId != null ? recipeId : "NULL", quantity, outputItemId);
        
        // Guard: System disabled
        if (config == null || !config.isEnabled()) {
            JobsLogger.debug("SKIP: Config null or disabled");
            return;
        }
        
        // Get player ref from archetype chunk (guaranteed by query)
        PlayerRef playerRef = archetypeChunk.getComponent(index, PlayerRef.getComponentType());
        
        if (playerRef == null) {
            JobsLogger.debug("SKIP: Not a player craft (no PlayerRef)");
            return;
        }
        
        // Process reward using shared logic
        processCraftReward(playerRef.getUuid(), recipe, quantity);
    }
    
    // =========================================================================
    // Core Reward Processing (shared logic)
    // =========================================================================
    
    /**
     * Core crafting reward logic.
     * This is the single entry point for all crafting rewards.
     * 
     * @param playerUuid The player's UUID
     * @param recipe The crafting recipe
     * @param quantity Number of items crafted
     */
    private void processCraftReward(UUID playerUuid, CraftingRecipe recipe, int quantity) {
        String recipeId = recipe.getId() != null ? recipe.getId() : "unknown";
        MaterialQuantity output = recipe.getPrimaryOutput();
        String outputId = output != null ? output.getItemId() : recipeId;
        
        // Track total items crafted
        totalItemsCrafted.addAndGet(quantity);
        
        // ─────────────────────────────────────────────────────────────
        // LAYER 1: TIER LOOKUP
        // ─────────────────────────────────────────────────────────────
        String tierName = tierMatcher.findTier(recipe);
        
        if ("NONE".equals(tierName)) {
            JobsLogger.debug("BLOCKED [Tier=NONE]: %s", outputId);
            return;
        }
        
        TierConfig tier = config.getTierSafe(tierName);
        if (tier == null) {
            JobsLogger.debug("BLOCKED [TierConfig null]: %s -> %s", outputId, tierName);
            return;
        }
        
        // ─────────────────────────────────────────────────────────────
        // LAYER 2: DROP CHANCE
        // ─────────────────────────────────────────────────────────────
        int dropChance = tier.getDropChance();
        if (dropChance < 100) {
            int roll = ThreadLocalRandom.current().nextInt(100);
            if (roll >= dropChance) {
                JobsLogger.debug("BLOCKED [Chance %d < %d%%]: %s", roll, dropChance, outputId);
                return;
            }
        }
        
        // ─────────────────────────────────────────────────────────────
        // LAYER 3: RATE LIMITING
        // ─────────────────────────────────────────────────────────────
        if (!rateLimiter.tryAcquire(playerUuid)) {
            rewardsBlocked.incrementAndGet();
            JobsLogger.debug("BLOCKED [RateLimit]: Player %s", playerUuid);
            return;
        }
        
        // ─────────────────────────────────────────────────────────────
        // LAYER 4: ANTI-FARM
        // ─────────────────────────────────────────────────────────────
        float antiFarmMultiplier = antiFarm.getMultiplierAndRecord(playerUuid, outputId);
        
        // ─────────────────────────────────────────────────────────────
        // LAYER 5: REWARD CALCULATION
        // ─────────────────────────────────────────────────────────────
        int baseCoins = tier.getMinCoins();
        int range = tier.getMaxCoins() - tier.getMinCoins();
        if (range > 0) {
            baseCoins += ThreadLocalRandom.current().nextInt(range + 1);
        }
        
        // Scale by quantity crafted (with diminishing returns)
        float quantityMultiplier = (float) Math.sqrt(quantity);
        int scaledCoins = Math.round(baseCoins * quantityMultiplier);
        
        // Apply anti-farm penalty
        int finalCoins = Math.round(scaledCoins * antiFarmMultiplier);
        
        if (finalCoins < 1) {
            rewardsBlocked.incrementAndGet();
            JobsLogger.debug("BLOCKED [AntiFarm=%.0f%%]: %s -> 0 coins",
                antiFarmMultiplier * 100, outputId);
            return;
        }
        
        // Calculate total value
        long totalValue = (long) finalCoins * tier.getCoinValue();
        
        // ─────────────────────────────────────────────────────────────
        // LAYER 6: ECONOMY CAP
        // ─────────────────────────────────────────────────────────────
        if (!economyCap.tryInject(totalValue)) {
            rewardsBlocked.incrementAndGet();
            JobsLogger.debug("BLOCKED [EconomyCap]: Tried to inject %d, cap full", totalValue);
            return;
        }
        
        // ─────────────────────────────────────────────────────────────
        // SUCCESS: ADD TO PLAYER BALANCE
        // ─────────────────────────────────────────────────────────────
        try {
            EcotaleAPI.deposit(playerUuid, (double) totalValue, "Crafting:" + outputId);
        } catch (Exception e) {
            JobsLogger.warn("Failed to deposit crafting reward: %s", e.getMessage());
            rewardsBlocked.incrementAndGet();
            return;
        }
        
        // Update statistics
        totalRewardsGiven.incrementAndGet();
        totalValueInjected.addAndGet(totalValue);
        
        JobsLogger.info("[CRAFT-REWARD] SUCCESS: %s (x%d) -> %d coins (value=%d)",
            outputId, quantity, finalCoins, totalValue);
    }
    
    // =========================================================================
    // Maintenance
    // =========================================================================
    
    public void performCleanup() {
        int antiFarmCleaned = antiFarm.cleanup();
        rateLimiter.cleanup();
        
        if (antiFarmCleaned > 0) {
            JobsLogger.debug("Cleanup: removed %d crafting anti-farm trackers", antiFarmCleaned);
        }
    }
    
    /**
     * Refresh the tier mappings after auto-detection adds new recipes.
     * Called from Main when LoadedAssetsEvent fires.
     */
    public void refreshMappings(CraftingMappingsConfig newMappings) {
        if (newMappings != null) {
            this.mappingsConfig = newMappings;
            tierMatcher.configure(newMappings);
            JobsLogger.debug("[CraftingRewardSystem] Refreshed mappings: %d items, %d recipes",
                newMappings.getItemMappings().size(),
                newMappings.getRecipeMappings().size());
        }
    }
    
    // =========================================================================
    // Monitoring API
    // =========================================================================
    
    public long getTotalRewardsGiven() { return totalRewardsGiven.get(); }
    public long getTotalValueInjected() { return totalValueInjected.get(); }
    public long getRewardsBlocked() { return rewardsBlocked.get(); }
    public long getTotalItemsCrafted() { return totalItemsCrafted.get(); }
    public int getTierCacheSize() { return tierMatcher.getRecipeCacheSize(); }
    public int getActiveAntiFarmTrackers() { return antiFarm.getActiveTrackerCount(); }
    public long getRemainingEconomyCap() { return economyCap.getRemainingCapacity(); }
    
    @Nullable
    public CraftingConfig getConfig() { return config; }
    
    // =========================================================================
    // CRAFTING CONFIG INTERFACE
    // =========================================================================
    
    /**
     * Interface for crafting configuration.
     * Implemented by EcotaleJobsConfig.CraftingConfig for serialization.
     */
    public interface CraftingConfig {
        boolean isEnabled();
        Map<String, TierConfig> getTiers();
        int getAntiFarmThreshold();
        float getAntiFarmDecay();
        boolean isAntiFarmEnabled();
        long getMaxInjectionPerHour();
        TierConfig getTierSafe(String tierName);
    }
}
