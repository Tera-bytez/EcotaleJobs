package com.ecotalejobs.systems;

import com.ecotale.api.EcotaleAPI;
import com.ecotale.api.PhysicalCoinsProvider;
import com.ecotale.util.RateLimiter;
import com.ecotalejobs.config.EcotaleJobsConfig.MobKillsConfig;
import com.ecotalejobs.config.EcotaleJobsConfig.SecurityConfig;
import com.ecotalejobs.config.TierConfig;
import com.ecotalejobs.config.TierMappingsConfig;
import com.ecotalejobs.Main;
import com.ecotalejobs.security.AntiFarmSystem;
import com.ecotalejobs.security.EconomyCap;
import com.ecotalejobs.util.TierMatcher;
import com.ecotalejobs.util.JobsLogger;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.RefChangeSystem;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Mob reward system - uses DeathComponent to detect kills.
 */
public class MobRewardSystem extends RefChangeSystem<EntityStore, DeathComponent> {
    
    // Configuration - set via init()
    private MobKillsConfig config;
    private TierMappingsConfig mappingsConfig;
    
    // Core subsystems
    private final TierMatcher tierMatcher = new TierMatcher();
    private final AntiFarmSystem antiFarm = new AntiFarmSystem();
    private final EconomyCap economyCap = new EconomyCap();
    private final RateLimiter rateLimiter;
    
    // Cached exclusions for O(1) lookup - populated on init()
    private volatile Set<String> exclusionSet = new HashSet<>();
    
    // Thread-safe statistics for monitoring
    private final AtomicLong totalRewardsGiven = new AtomicLong(0);
    private final AtomicLong totalValueInjected = new AtomicLong(0);
    private final AtomicLong rewardsBlocked = new AtomicLong(0);
    
    public MobRewardSystem() {
        // RateLimiter: 30 burst capacity, 5 tokens/sec refill
        // This allows 30 rapid kills, then ~5 kills/sec sustained
        this.rateLimiter = new RateLimiter(30, 5);
    }
    
    /**
     * Initialize the reward system with configuration.
     * Must be called after plugin load, before any events fire.
     * 
     * @param config The mob kills configuration (nullable - disables system if null)
     * @param mappings The tier mappings configuration
     */
    public void init(MobKillsConfig config, TierMappingsConfig mappings) {
        this.config = config;
        this.mappingsConfig = mappings;
        
        if (config == null || mappings == null) {
            JobsLogger.warn("[MobRewardSystem] Config is null - system DISABLED");
            return;
        }
        
        // Initialize tier matcher with pattern mappings from TierMappingsConfig
        tierMatcher.configure(
            mappings.getTierMappings(),
            new HashSet<>(mappings.getExclusions()),
            mappings.getDefaultTier()
        );
        
        // Cache exclusions for O(1) lookup (volatile for thread-safety)
        this.exclusionSet = new HashSet<>(mappings.getExclusions());
        
        // Configure anti-farm subsystem
        SecurityConfig security = config.getSecurity();
        antiFarm.configure(
            security.getAntiFarmThreshold(),
            security.getAntiFarmDecayPerKill(),
            0.1f,  // Minimum multiplier (10% of reward at worst)
            30,    // TTL in minutes for player tracking
            security.isAntiFarmEnabled()
        );
        
        // Configure global economy cap
        economyCap.configure(
            security.getMaxGlobalInjectionPerHour(),
            true   // Enabled
        );
        
        JobsLogger.info("[MobRewardSystem] Initialized: %d tiers, %d mappings, %d exclusions | AntiFarm=%s",
            config.getTiers().size(), 
            mappings.getTierMappings().size(),
            mappings.getExclusions().size(),
            security.isAntiFarmEnabled() ? "ON" : "OFF");
    }
    
    // =========================================================================
    // RefChangeSystem Implementation - DeathComponent
    // =========================================================================
    
    @Nonnull
    @Override
    public ComponentType<EntityStore, DeathComponent> componentType() {
        return DeathComponent.getComponentType();
    }
    
    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        // Only process entities that also have NPCEntity component
        return NPCEntity.getComponentType();
    }

    /**
     * Called when a DeathComponent is ADDED to an entity.
     * This is the death event - the entity just died.
     */
    @Override
    public void onComponentAdded(
        @Nonnull Ref<EntityStore> ref, 
        @Nonnull DeathComponent deathComponent, 
        @Nonnull Store<EntityStore> store, 
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        // Get the NPCEntity component (guaranteed by our query)
        NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
        
        if (npc == null) {
            // Not an NPC death - ignore (shouldn't happen due to query)
            return;
        }
        
        String mobId = npc.getNPCTypeId();
        
        // Debug logging for all NPC deaths
        JobsLogger.debug("=== NPC DEATH: %s ===", mobId != null ? mobId : "NULL_ID");
        
        // Guard: System disabled
        if (config == null || !config.isEnabled()) {
            JobsLogger.debug("SKIP: Config null or disabled");
            return;
        }
        
        // Get the killer from DeathComponent's damage info
        Damage deathInfo = deathComponent.getDeathInfo();
        if (deathInfo == null) {
            JobsLogger.debug("SKIP: No death info (natural death?)");
            return;
        }
        
        // Check if the damage source was an entity
        Damage.Source source = deathInfo.getSource();
        if (!(source instanceof Damage.EntitySource entitySource)) {
            JobsLogger.debug("SKIP: Damage source is not an entity (environmental?)");
            return;
        }
        
        Ref<EntityStore> killerRef = entitySource.getRef();
        if (killerRef == null || !killerRef.isValid()) {
            JobsLogger.debug("SKIP: Killer ref is null or invalid");
            return;
        }
        
        // Verify killer is a player (not another NPC)
        Player killer = store.getComponent(killerRef, Player.getComponentType());
        PlayerRef killerPlayerRef = store.getComponent(killerRef, PlayerRef.getComponentType());
        
        if (killer == null || killerPlayerRef == null) {
            JobsLogger.debug("SKIP: Killer is not a player");
            return;
        }
        
        // Process the reward through security layers
        processKill(killer, killerPlayerRef, npc, ref, store, commandBuffer);
    }

    @Override
    public void onComponentSet(
        @Nonnull Ref<EntityStore> ref, 
        @Nullable DeathComponent oldComponent, 
        @Nonnull DeathComponent newComponent, 
        @Nonnull Store<EntityStore> store, 
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        // DeathComponent is typically only added, not set
        // But handle it just in case
    }

    @Override
    public void onComponentRemoved(
        @Nonnull Ref<EntityStore> ref, 
        @Nonnull DeathComponent component, 
        @Nonnull Store<EntityStore> store, 
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        // DeathComponent removal = respawn, not relevant for rewards
    }
    
    // =========================================================================
    // Reward Processing Pipeline
    // =========================================================================
    
    /**
     * Process a mob kill through all security layers.
     * 
     * <p>This method is optimized for minimal allocations:
     * <ul>
     *   <li>No String concatenation in hot path</li>
     *   <li>ThreadLocalRandom (no contention)</li>
     *   <li>Primitive operations where possible</li>
     * </ul>
     */
    private void processKill(
        Player killer,
        PlayerRef killerPlayerRef, 
        NPCEntity npc,
        Ref<EntityStore> mobRef, 
        Store<EntityStore> store, 
        CommandBuffer<EntityStore> commandBuffer
    ) {
        String mobId = npc.getNPCTypeId();
        if (mobId == null) {
            mobId = "unknown";
        }
        
        UUID playerUuid = killerPlayerRef.getUuid();
        
        // ─────────────────────────────────────────────────────────────
        // LAYER 1: EXCLUSION CHECK
        // O(1) HashSet lookup for NPCs that should never give rewards
        // ─────────────────────────────────────────────────────────────
        if (exclusionSet.contains(mobId)) {
            JobsLogger.debug("BLOCKED [Exclusion]: %s", mobId);
            return;
        }
        
        // ─────────────────────────────────────────────────────────────
        // LAYER 2: TIER LOOKUP
        // Pattern matching with O(1) cache for known mobs
        // ─────────────────────────────────────────────────────────────
        String tierName = tierMatcher.findTier(mobId);
        
        if ("NONE".equals(tierName)) {
            JobsLogger.debug("BLOCKED [Tier=NONE]: %s", mobId);
            return;
        }
        
        TierConfig tier = config.getTierSafe(tierName, mappingsConfig.getDefaultTier());
        
        // ─────────────────────────────────────────────────────────────
        // LAYER 3: DROP CHANCE
        // Random roll - skip if tier has <100% chance
        // VIP players get bonus chance added to base drop chance
        // ─────────────────────────────────────────────────────────────
        int baseDropChance = tier.getDropChance();
        int vipChanceBonus = Main.CONFIG.get().getVipMultipliers().calculateChanceBonus(killer);
        int effectiveDropChance = Math.min(baseDropChance + vipChanceBonus, 100);
        
        if (effectiveDropChance < 100) {
            int roll = ThreadLocalRandom.current().nextInt(100);
            if (roll >= effectiveDropChance) {
                JobsLogger.debug("BLOCKED [Chance %d >= %d%% (base=%d, vip=+%d)]: %s", 
                    roll, effectiveDropChance, baseDropChance, vipChanceBonus, mobId);
                return;
            }
        }
        
        // ─────────────────────────────────────────────────────────────
        // LAYER 4: RATE LIMITING
        // Per-player burst protection using token bucket
        // ─────────────────────────────────────────────────────────────
        if (!rateLimiter.tryAcquire(playerUuid)) {
            rewardsBlocked.incrementAndGet();
            JobsLogger.debug("BLOCKED [RateLimit]: Player %s", playerUuid);
            return;
        }
        
        // ─────────────────────────────────────────────────────────────
        // LAYER 5: ANTI-FARM
        // Diminishing returns for killing same mob type repeatedly
        // ─────────────────────────────────────────────────────────────
        float antiFarmMultiplier = antiFarm.getMultiplierAndRecord(playerUuid, mobId);
        
        // ─────────────────────────────────────────────────────────────
        // LAYER 6: REWARD CALCULATION
        // Random amount within tier range, adjusted by anti-farm
        // ─────────────────────────────────────────────────────────────
        int baseCoins = tier.getMinCoins();
        int range = tier.getMaxCoins() - tier.getMinCoins();
        if (range > 0) {
            baseCoins += ThreadLocalRandom.current().nextInt(range + 1);
        }
        
        // VIP Multiplier (killer implements CommandSender which has hasPermission)
        float vipMultiplier = Main.CONFIG.get().getVipMultipliers().calculateMultiplier(killer);

        // Apply anti-farm penalty & VIP
        float exactCoins = baseCoins * antiFarmMultiplier * vipMultiplier;
        int finalCoins = (int) exactCoins;
        
        // Probabilistic rounding: 1.2 coins = 1 coin + 20% chance of extra coin
        // This ensures even small drops benefit from multipliers over time
        if (ThreadLocalRandom.current().nextFloat() < (exactCoins - finalCoins)) {
            finalCoins++;
        }
        
        if (finalCoins < 1) {
            rewardsBlocked.incrementAndGet();
            JobsLogger.debug("BLOCKED [AntiFarm=%.0f%%]: %s -> 0 coins", 
                antiFarmMultiplier * 100, mobId);
            return;
        }
        
        long totalValue = (long) finalCoins * tier.getCoinValue();
        
        // ─────────────────────────────────────────────────────────────
        // LAYER 7: GLOBAL ECONOMY CAP
        // Server-wide limit on currency injection per hour
        // ─────────────────────────────────────────────────────────────
        if (!economyCap.tryInject(totalValue)) {
            rewardsBlocked.incrementAndGet();
            JobsLogger.debug("BLOCKED [EconomyCap]: Tried to inject %d, cap full", totalValue);
            return;
        }
        
        // ─────────────────────────────────────────────────────────────
        // SUCCESS: GIVE REWARD
        // Uses physical coins if addon is available, otherwise direct balance
        // ─────────────────────────────────────────────────────────────
        if (EcotaleAPI.isPhysicalCoinsAvailable()) {
            // Physical coins addon installed - drop coins in world
            PhysicalCoinsProvider coins = EcotaleAPI.getPhysicalCoins();
            coins.dropCoinsAtEntity(mobRef, store, commandBuffer, totalValue);
        } else {
            // No coins addon - deposit directly to player's balance
            EcotaleAPI.deposit(playerUuid, (double) totalValue, "Mob kill: " + mobId);
        }
        
        // Update statistics (atomic for thread-safety)
        totalRewardsGiven.incrementAndGet();
        totalValueInjected.addAndGet(totalValue);
        
        JobsLogger.debug("SUCCESS: %s -> %d coins (exact=%.2f, antiFarm=%.0f%%, vip=%.2fx, mode=%s)", 
            mobId, finalCoins, exactCoins, antiFarmMultiplier * 100, vipMultiplier,
            EcotaleAPI.isPhysicalCoinsAvailable() ? "COINS" : "BALANCE");
    }
    
    // =========================================================================
    // Maintenance
    // =========================================================================
    
    /**
     * Periodic cleanup of expired tracking data.
     * Should be called every 5-10 minutes via a scheduled task.
     */
    public void performCleanup() {
        int antiFarmCleaned = antiFarm.cleanup();
        rateLimiter.cleanup(); // RateLimiter.cleanup() returns void
        
        if (antiFarmCleaned > 0) {
            JobsLogger.debug("Cleanup: removed %d anti-farm trackers", antiFarmCleaned);
        }
    }
    
    // =========================================================================
    // Monitoring API
    // =========================================================================
    
    /** Total rewards successfully given since server start */
    public long getTotalRewardsGiven() {
        return totalRewardsGiven.get();
    }
    
    /** Total currency value injected into the economy (in base units) */
    public long getTotalValueInjected() {
        return totalValueInjected.get();
    }
    
    /** Number of rewards blocked by security layers */
    public long getRewardsBlocked() {
        return rewardsBlocked.get();
    }
    
    /** Current size of the tier matching cache */
    public int getTierCacheSize() {
        return tierMatcher.getCacheSize();
    }
    
    /** Number of active player anti-farm trackers */
    public int getActiveAntiFarmTrackers() {
        return antiFarm.getActiveTrackerCount();
    }
    
    /** Remaining economy cap capacity for this hour */
    public long getRemainingEconomyCap() {
        return economyCap.getRemainingCapacity();
    }
    
    /** Get current configuration (for admin inspection) */
    @Nullable
    public MobKillsConfig getConfig() {
        return config;
    }
}
