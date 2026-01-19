package com.ecotalejobs.config;

import com.ecotalejobs.systems.CraftingRewardSystem;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.codecs.map.MapCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.server.core.command.system.CommandSender;

import java.util.*;

/**
 * Main configuration class for EcotaleJobs plugin.
 */
public class EcotaleJobsConfig {
    
    public static final BuilderCodec<EcotaleJobsConfig> CODEC = BuilderCodec.builder(EcotaleJobsConfig.class, EcotaleJobsConfig::new)
        .append(new KeyedCodec<>("DebugMode", Codec.BOOLEAN),
            (c, v, e) -> c.debugMode = v, (c, e) -> c.debugMode).add()
        .append(new KeyedCodec<>("MobKills", MobKillsConfig.CODEC),
            (c, v, e) -> c.mobKills = v, (c, e) -> c.mobKills).add()
        .append(new KeyedCodec<>("Mining", MiningConfig.CODEC),
            (c, v, e) -> c.mining = v, (c, e) -> c.mining).add()
        .append(new KeyedCodec<>("Crafting", CraftingConfig.CODEC),
            (c, v, e) -> c.crafting = v, (c, e) -> c.crafting).add()
        .append(new KeyedCodec<>("Notifications", NotificationConfig.CODEC),
            (c, v, e) -> c.notifications = v, (c, e) -> c.notifications).add()
        .append(new KeyedCodec<>("VipMultipliers", VipConfig.CODEC),
            (c, v, e) -> c.vipMultipliers = v, (c, e) -> c.vipMultipliers).add()
        .build();
    
    private boolean debugMode = false;
    private MobKillsConfig mobKills = new MobKillsConfig();
    private MiningConfig mining = new MiningConfig();
    private CraftingConfig crafting = new CraftingConfig();
    private NotificationConfig notifications = new NotificationConfig();
    private VipConfig vipMultipliers = new VipConfig();
    
    public boolean isDebugMode() { return debugMode; }
    public MobKillsConfig getMobKills() { return mobKills; }
    public MiningConfig getMining() { return mining; }
    public CraftingConfig getCrafting() { return crafting; }
    public NotificationConfig getNotifications() { return notifications; }
    public VipConfig getVipMultipliers() { return vipMultipliers; }
    
    // =========================================================================
    // MOB KILLS CONFIG
    // =========================================================================
    
    public static class MobKillsConfig {
        public static final BuilderCodec<MobKillsConfig> CODEC = BuilderCodec.builder(MobKillsConfig.class, MobKillsConfig::new)
            .append(new KeyedCodec<>("Enabled", Codec.BOOLEAN),
                (c, v, e) -> c.enabled = v, (c, e) -> c.enabled).add()
            .append(new KeyedCodec<>("Tiers", new MapCodec<>(TierConfig.CODEC, HashMap::new)),
                (c, v, e) -> c.tiers = v, (c, e) -> c.tiers).add()
            .append(new KeyedCodec<>("Security", SecurityConfig.CODEC),
                (c, v, e) -> c.security = v, (c, e) -> c.security).add()
            .build();
        
        private boolean enabled = true;  // Mob kills enabled by default
        private Map<String, TierConfig> tiers = createDefaultTiers();
        private SecurityConfig security = new SecurityConfig();
        
        public boolean isEnabled() { return enabled; }
        public Map<String, TierConfig> getTiers() { return tiers; }
        public SecurityConfig getSecurity() { return security; }
        
        /**
         * Get a tier by name with fail-safe fallback.
         * @param defaultTier The default tier name from TierMappingsConfig
         */
        public TierConfig getTierSafe(String tierName, String defaultTier) {
            TierConfig tier = tiers.get(tierName);
            if (tier == null) {
                tier = tiers.get(defaultTier);
            }
            if (tier == null) {
                // Ultimate fallback - 1 copper, 50% chance
                tier = new TierConfig("COPPER", 0, 1, 50);
            }
            return tier;
        }
        
        private static Map<String, TierConfig> createDefaultTiers() {
            Map<String, TierConfig> t = new LinkedHashMap<>();
            
            // ==========================================================================
            // BALANCED TIER REWARDS - Based on actual game danger scores
            // Danger Score = HP + (DMG * 4) * aggression_modifier
            // ==========================================================================
            
            // NONE - No reward (for excluded NPCs)
            t.put("NONE", new TierConfig("COPPER", 0, 0, 0));
            
            // CRITTER (Danger 0-50) - Tiny creatures, babies
            // Examples: Bunny (25 HP), Chicken (29 HP), Rat (21 HP)
            // Very easy to kill, minimal reward
            t.put("CRITTER", new TierConfig("COPPER", 0, 1, 40));
            
            // PASSIVE (Danger 50-100) - Non-aggressive or weak
            // Examples: Skeleton (92 HP), Sheep (81 HP), Feran (49-61 HP)
            // Easy kills, small consistent reward
            t.put("PASSIVE", new TierConfig("COPPER", 1, 2, 100));
            
            // HOSTILE (Danger 100-300) - Standard combat enemies
            // Examples: Zombie (49 HP, 18 DMG), Trork_Warrior (61 HP, 23 DMG)
            // Normal gameplay loop, moderate reward
            t.put("HOSTILE", new TierConfig("COPPER", 4, 10, 100));
            
            // ELITE (Danger 300-700) - Tough enemies requiring skill
            // Examples: Ghoul (193 HP, 48 DMG), Crocodile (145 HP, 48 DMG), Yeti (226 HP)
            // Challenging fights, iron coin reward (1 Iron = 10 Copper equivalent)
            t.put("ELITE", new TierConfig("IRON", 2, 5, 100));
            
            // MINIBOSS (Danger 700-1200) - Mini-boss level threats
            // Examples: Rex_Cave (400 HP, 68 DMG), Werewolf (283 HP, 66 DMG), Shadow_Knight (400 HP, 119 DMG)
            // Requires preparation and skill, cobalt reward (1 Cobalt = 100 Copper equivalent)
            t.put("MINIBOSS", new TierConfig("COBALT", 2, 4, 100));
            
            // BOSS (Reserved for future dungeon bosses)
            // Currently no standard bosses reach this tier
            // Gold reward (1 Gold = 1000 Copper equivalent)
            t.put("BOSS", new TierConfig("GOLD", 1, 3, 100));
            
            // WORLDBOSS (Danger 1000+) - Dragons and ultimate threats
            // Examples: Dragon_Fire (400 HP, BOSS type), Dragon_Frost (400 HP, BOSS type)
            // Ultimate challenge, mithril reward (1 Mithril = 10000 Copper equivalent)
            t.put("WORLDBOSS", new TierConfig("MITHRIL", 1, 2, 100));
            
            return t;
        }
    }
    
    // =========================================================================
    // SECURITY CONFIG
    // =========================================================================
    
    public static class SecurityConfig {
        public static final BuilderCodec<SecurityConfig> CODEC = BuilderCodec.builder(SecurityConfig.class, SecurityConfig::new)
            .append(new KeyedCodec<>("MaxRewardsPerMinute", Codec.INTEGER),
                (c, v, e) -> c.maxRewardsPerMinute = v, (c, e) -> c.maxRewardsPerMinute).add()
            .append(new KeyedCodec<>("MaxGlobalInjectionPerHour", Codec.LONG),
                (c, v, e) -> c.maxGlobalInjectionPerHour = v, (c, e) -> c.maxGlobalInjectionPerHour).add()
            .append(new KeyedCodec<>("EnableAntiFarm", Codec.BOOLEAN),
                (c, v, e) -> c.enableAntiFarm = v, (c, e) -> c.enableAntiFarm).add()
            .append(new KeyedCodec<>("AntiFarmThreshold", Codec.INTEGER),
                (c, v, e) -> c.antiFarmThreshold = v, (c, e) -> c.antiFarmThreshold).add()
            .append(new KeyedCodec<>("AntiFarmDecayPerKill", Codec.FLOAT),
                (c, v, e) -> c.antiFarmDecayPerKill = v, (c, e) -> c.antiFarmDecayPerKill).add()
            .build();
        
        // Per-player rate limit (kills per minute before throttling)
        private int maxRewardsPerMinute = 60;
        
        // Server-wide economy injection cap per hour
        // 100M = 10K gold/hour = ~200 gold/hour for 50 players
        // This is a reasonable soft cap to prevent runaway inflation
        private long maxGlobalInjectionPerHour = 100_000_000;
        
        // Anti-farm settings
        private boolean enableAntiFarm = true;
        private int antiFarmThreshold = 15; // Kills of same mob type before decay starts
        private float antiFarmDecayPerKill = 0.08f; // 8% reduction per kill over threshold
        
        public int getMaxRewardsPerMinute() { return maxRewardsPerMinute; }
        public long getMaxGlobalInjectionPerHour() { return maxGlobalInjectionPerHour; }
        public boolean isAntiFarmEnabled() { return enableAntiFarm; }
        public int getAntiFarmThreshold() { return antiFarmThreshold; }
        public float getAntiFarmDecayPerKill() { return antiFarmDecayPerKill; }
    }
    
    // =========================================================================
    // NOTIFICATION CONFIG
    // =========================================================================
    
    public static class NotificationConfig {
        public static final BuilderCodec<NotificationConfig> CODEC = BuilderCodec.builder(NotificationConfig.class, NotificationConfig::new)
            .append(new KeyedCodec<>("ShowRewards", Codec.BOOLEAN),
                (c, v, e) -> c.showRewards = v, (c, e) -> c.showRewards).add()
            .append(new KeyedCodec<>("MinRewardToShow", Codec.LONG),
                (c, v, e) -> c.minRewardToShow = v, (c, e) -> c.minRewardToShow).add()
            .build();
        
        private boolean showRewards = true;
        private long minRewardToShow = 1;
        
        public boolean isShowRewards() { return showRewards; }
        public long getMinRewardToShow() { return minRewardToShow; }
    }
    
    // =========================================================================
    // MINING CONFIG
    // =========================================================================
    
    /**
     * Configuration for the Mining Reward System.
     * Supports tier-based rewards with tool quality and depth bonuses.
     */
    public static class MiningConfig {
        public static final BuilderCodec<MiningConfig> CODEC = BuilderCodec.builder(MiningConfig.class, MiningConfig::new)
            .append(new KeyedCodec<>("Enabled", Codec.BOOLEAN),
                (c, v, e) -> c.enabled = v, (c, e) -> c.enabled).add()
            .append(new KeyedCodec<>("AllowDecoBlocks", Codec.BOOLEAN),
                (c, v, e) -> c.allowDecoBlocks = v, (c, e) -> c.allowDecoBlocks).add()
            .append(new KeyedCodec<>("Tiers", new MapCodec<>(TierConfig.CODEC, HashMap::new)),
                (c, v, e) -> c.tiers = v, (c, e) -> c.tiers).add()
            .append(new KeyedCodec<>("Security", SecurityConfig.CODEC),
                (c, v, e) -> c.security = v, (c, e) -> c.security).add()
            .append(new KeyedCodec<>("ToolQualityMultiplier", ToolQualityConfig.CODEC),
                (c, v, e) -> c.toolQuality = v, (c, e) -> c.toolQuality).add()
            .append(new KeyedCodec<>("DepthBonus", DepthBonusConfig.CODEC),
                (c, v, e) -> c.depthBonus = v, (c, e) -> c.depthBonus).add()
            .append(new KeyedCodec<>("VeinStreak", VeinStreakConfig.CODEC),
                (c, v, e) -> c.veinStreak = v, (c, e) -> c.veinStreak).add()
            .build();
        
        private boolean enabled = true;
        private boolean allowDecoBlocks = false; // If true, rewards player-placed blocks (for testing)
        private Map<String, TierConfig> tiers = createDefaultMiningTiers();
        private SecurityConfig security = new SecurityConfig();
        private ToolQualityConfig toolQuality = new ToolQualityConfig();
        private DepthBonusConfig depthBonus = new DepthBonusConfig();
        private VeinStreakConfig veinStreak = new VeinStreakConfig();
        
        public boolean isEnabled() { return enabled; }
        public boolean isAllowDecoBlocks() { return allowDecoBlocks; }
        public Map<String, TierConfig> getTiers() { return tiers; }
        public SecurityConfig getSecurity() { return security; }
        public ToolQualityConfig getToolQuality() { return toolQuality; }
        public DepthBonusConfig getDepthBonus() { return depthBonus; }
        public VeinStreakConfig getVeinStreak() { return veinStreak; }
        
        public TierConfig getTierSafe(String tierName, String defaultTier) {
            TierConfig tier = tiers.get(tierName);
            if (tier == null) {
                tier = tiers.get(defaultTier);
            }
            if (tier == null) {
                tier = new TierConfig("COPPER", 0, 1, 50);
            }
            return tier;
        }
        
        private static Map<String, TierConfig> createDefaultMiningTiers() {
            Map<String, TierConfig> t = new LinkedHashMap<>();
            
            // NONE - No reward
            t.put("NONE", new TierConfig("COPPER", 0, 0, 0));
            
            // BASIC - Basic stone, dirt, gravel (Previous: STONE)
            t.put("BASIC", new TierConfig("COPPER", 0, 1, 30));
            
            // COMMON - Coal, Copper (Previous: COAL)
            t.put("COMMON", new TierConfig("COPPER", 1, 2, 80));
            
            // UNCOMMON - Iron (Previous: IRON)
            t.put("UNCOMMON", new TierConfig("COPPER", 2, 4, 100));
            
            // RARE - Gold, Silver (Previous: GOLD)
            t.put("RARE", new TierConfig("COPPER", 4, 8, 100));
            
            // EPIC - Diamond, Cobalt (Previous: DIAMOND)
            t.put("EPIC", new TierConfig("IRON", 1, 2, 100));
            
            // LEGENDARY - Mithril, Adamantite, Thorium (Previous: RARE)
            t.put("LEGENDARY", new TierConfig("IRON", 2, 4, 100));
            
            return t;
        }
    }
    
    /**
     * Tool quality multiplier configuration.
     * Higher quality tools give bonus rewards.
     */
    public static class ToolQualityConfig {
        public static final BuilderCodec<ToolQualityConfig> CODEC = BuilderCodec.builder(ToolQualityConfig.class, ToolQualityConfig::new)
            .append(new KeyedCodec<>("Enabled", Codec.BOOLEAN),
                (c, v, e) -> c.enabled = v, (c, e) -> c.enabled).add()
            .append(new KeyedCodec<>("QualityStep", Codec.FLOAT),
                (c, v, e) -> c.qualityStep = v, (c, e) -> c.qualityStep).add()
            .append(new KeyedCodec<>("MaxBonus", Codec.FLOAT),
                (c, v, e) -> c.maxBonus = v, (c, e) -> c.maxBonus).add()
            .build();
        
        private boolean enabled = true;
        private float qualityStep = 0.02f;  // +2% per quality level
        private float maxBonus = 0.25f;     // Cap at +25%
        
        public boolean isEnabled() { return enabled; }
        public float getQualityStep() { return qualityStep; }
        public float getMaxBonus() { return maxBonus; }
        
        public float calculateMultiplier(int quality) {
            if (!enabled) return 1.0f;
            return 1.0f + Math.min(quality * qualityStep, maxBonus);
        }
    }
    
    /**
     * Depth bonus configuration.
     * Deeper mining gives bonus rewards.
     */
    public static class DepthBonusConfig {
        public static final BuilderCodec<DepthBonusConfig> CODEC = BuilderCodec.builder(DepthBonusConfig.class, DepthBonusConfig::new)
            .append(new KeyedCodec<>("Enabled", Codec.BOOLEAN),
                (c, v, e) -> c.enabled = v, (c, e) -> c.enabled).add()
            .append(new KeyedCodec<>("MinY", Codec.INTEGER),
                (c, v, e) -> c.minY = v, (c, e) -> c.minY).add()
            .append(new KeyedCodec<>("MaxY", Codec.INTEGER),
                (c, v, e) -> c.maxY = v, (c, e) -> c.maxY).add()
            .append(new KeyedCodec<>("MaxBonus", Codec.FLOAT),
                (c, v, e) -> c.maxBonus = v, (c, e) -> c.maxBonus).add()
            .build();
        
        private boolean enabled = true;
        private int minY = 0;      // Deepest level (max bonus)
        private int maxY = 80;     // Surface level (no bonus)
        private float maxBonus = 0.20f;  // +20% at bedrock
        
        public boolean isEnabled() { return enabled; }
        public int getMinY() { return minY; }
        public int getMaxY() { return maxY; }
        public float getMaxBonus() { return maxBonus; }
        
        public float calculateMultiplier(int y) {
            if (!enabled || y >= maxY) return 1.0f;
            if (y <= minY) return 1.0f + maxBonus;
            // Linear interpolation: deeper = more bonus
            float ratio = 1.0f - ((float)(y - minY) / (maxY - minY));
            return 1.0f + (ratio * maxBonus);
        }
    }
    
    /**
     * Vein streak configuration - audio feedback and bonus coins for consecutive ore mining.
     */
    public static class VeinStreakConfig {
        public static final BuilderCodec<VeinStreakConfig> CODEC = BuilderCodec.builder(VeinStreakConfig.class, VeinStreakConfig::new)
            .append(new KeyedCodec<>("Enabled", Codec.BOOLEAN),
                (c, v, e) -> c.enabled = v, (c, e) -> c.enabled).add()
            // Streak mechanics
            .append(new KeyedCodec<>("MaxStreak", Codec.INTEGER),
                (c, v, e) -> c.maxStreak = v, (c, e) -> c.maxStreak).add()
            .append(new KeyedCodec<>("TimeoutMs", Codec.LONG),
                (c, v, e) -> c.timeoutMs = v, (c, e) -> c.timeoutMs).add()
            // Bonus rewards
            .append(new KeyedCodec<>("BonusEnabled", Codec.BOOLEAN),
                (c, v, e) -> c.bonusEnabled = v, (c, e) -> c.bonusEnabled).add()
            .append(new KeyedCodec<>("BonusStartStreak", Codec.INTEGER),
                (c, v, e) -> c.bonusStartStreak = v, (c, e) -> c.bonusStartStreak).add()
            .append(new KeyedCodec<>("BonusChanceStep", Codec.INTEGER),
                (c, v, e) -> c.bonusChanceStep = v, (c, e) -> c.bonusChanceStep).add()
            .append(new KeyedCodec<>("BonusMaxChance", Codec.INTEGER),
                (c, v, e) -> c.bonusMaxChance = v, (c, e) -> c.bonusMaxChance).add()
            .append(new KeyedCodec<>("BonusCoinAmount", Codec.INTEGER),
                (c, v, e) -> c.bonusCoinAmount = v, (c, e) -> c.bonusCoinAmount).add()
            // Audio
            .append(new KeyedCodec<>("AudioEnabled", Codec.BOOLEAN),
                (c, v, e) -> c.audioEnabled = v, (c, e) -> c.audioEnabled).add()
            .append(new KeyedCodec<>("SoundEventId", Codec.STRING),
                (c, v, e) -> c.soundEventId = v, (c, e) -> c.soundEventId).add()
            .append(new KeyedCodec<>("BasePitchSemitones", Codec.FLOAT),
                (c, v, e) -> c.basePitchSemitones = v, (c, e) -> c.basePitchSemitones).add()
            .append(new KeyedCodec<>("PitchStepSemitones", Codec.FLOAT),
                (c, v, e) -> c.pitchStepSemitones = v, (c, e) -> c.pitchStepSemitones).add()
            .append(new KeyedCodec<>("MaxPitchSemitones", Codec.FLOAT),
                (c, v, e) -> c.maxPitchSemitones = v, (c, e) -> c.maxPitchSemitones).add()
            .append(new KeyedCodec<>("Volume", Codec.FLOAT),
                (c, v, e) -> c.volume = v, (c, e) -> c.volume).add()
            .build();
        
        // Streak mechanics
        private boolean enabled = true;
        private int maxStreak = 6;
        private long timeoutMs = 3000;  // Full reset after 3s of not mining ores
        
        // Bonus rewards
        private boolean bonusEnabled = true;
        private int bonusStartStreak = 0;     // Bonus starts at streak 1
        private int bonusChanceStep = 10;     // +10% chance per streak above start
        private int bonusMaxChance = 40;      // Cap at 40%
        private int bonusCoinAmount = 1;      // 1 extra COPPER per bonus
        
        // Audio (pitch in semitones, will be converted to linear)
        private boolean audioEnabled = true;
        private String soundEventId = "SFX_Crystal_Break";
        private float basePitchSemitones = -2f;   // Starting pitch at streak 1
        private float pitchStepSemitones = 2f;    // +2 semitones per streak
        private float maxPitchSemitones = 6f;     // Cap at +6 semitones
        private float volume = 0.8f;
        
        // Getters
        public boolean isEnabled() { return enabled; }
        public int getMaxStreak() { return maxStreak; }
        public long getTimeoutMs() { return timeoutMs; }
        public boolean isBonusEnabled() { return bonusEnabled; }
        public int getBonusStartStreak() { return bonusStartStreak; }
        public int getBonusChanceStep() { return bonusChanceStep; }
        public int getBonusMaxChance() { return bonusMaxChance; }
        public int getBonusCoinAmount() { return bonusCoinAmount; }
        public boolean isAudioEnabled() { return audioEnabled; }
        public String getSoundEventId() { return soundEventId; }
        public float getBasePitchSemitones() { return basePitchSemitones; }
        public float getPitchStepSemitones() { return pitchStepSemitones; }
        public float getMaxPitchSemitones() { return maxPitchSemitones; }
        public float getVolume() { return volume; }
        
        // Calculate linear pitch based on streak (semitones converted to linear)
        public float calculatePitch(int streak) {
            float semitones = basePitchSemitones + ((streak - 1) * pitchStepSemitones);
            semitones = Math.min(semitones, maxPitchSemitones);
            // Convert semitones to linear pitch: 2^(semitones/12)
            return (float) Math.pow(2.0, semitones / 12.0);
        }
        
        // Calculate bonus chance based on streak
        public int calculateBonusChance(int streak) {
            if (!bonusEnabled || streak < bonusStartStreak) return 0;
            int chance = (streak - bonusStartStreak + 1) * bonusChanceStep;
            return Math.min(chance, bonusMaxChance);
        }
    }
    
    // =========================================================================
    // CRAFTING CONFIG
    // =========================================================================
    
    /**
     * Configuration for the Crafting Reward System.
     * Supports tier-based rewards with anti-farm and economy protection.
     */
    public static class CraftingConfig implements CraftingRewardSystem.CraftingConfig {
        public static final BuilderCodec<CraftingConfig> CODEC = BuilderCodec.builder(CraftingConfig.class, CraftingConfig::new)
            .append(new KeyedCodec<>("Enabled", Codec.BOOLEAN),
                (c, v, e) -> c.enabled = v, (c, e) -> c.enabled).add()
            .append(new KeyedCodec<>("Tiers", new MapCodec<>(TierConfig.CODEC, HashMap::new)),
                (c, v, e) -> c.tiers = v, (c, e) -> c.tiers).add()
            .append(new KeyedCodec<>("AntiFarmEnabled", Codec.BOOLEAN),
                (c, v, e) -> c.antiFarmEnabled = v, (c, e) -> c.antiFarmEnabled).add()
            .append(new KeyedCodec<>("AntiFarmThreshold", Codec.INTEGER),
                (c, v, e) -> c.antiFarmThreshold = v, (c, e) -> c.antiFarmThreshold).add()
            .append(new KeyedCodec<>("AntiFarmDecay", Codec.FLOAT),
                (c, v, e) -> c.antiFarmDecay = v, (c, e) -> c.antiFarmDecay).add()
            .append(new KeyedCodec<>("MaxInjectionPerHour", Codec.LONG),
                (c, v, e) -> c.maxInjectionPerHour = v, (c, e) -> c.maxInjectionPerHour).add()
            .build();
        
        // EXPERIMENTAL: Crafting rewards currently support inventory crafting only.
        private boolean enabled = false;
        private Map<String, TierConfig> tiers = createDefaultCraftingTiers();
        private boolean antiFarmEnabled = true;
        private int antiFarmThreshold = 20;         // Crafts of same recipe before decay
        private float antiFarmDecay = 0.05f;        // 5% decay per craft over threshold
        private long maxInjectionPerHour = 50_000_000; // 50M/hour for crafting
        
        @Override public boolean isEnabled() { return enabled; }
        @Override public Map<String, TierConfig> getTiers() { return tiers; }
        @Override public boolean isAntiFarmEnabled() { return antiFarmEnabled; }
        @Override public int getAntiFarmThreshold() { return antiFarmThreshold; }
        @Override public float getAntiFarmDecay() { return antiFarmDecay; }
        @Override public long getMaxInjectionPerHour() { return maxInjectionPerHour; }
        
        @Override
        public TierConfig getTierSafe(String tierName) {
            TierConfig tier = tiers.get(tierName);
            if (tier == null) {
                tier = tiers.get("SIMPLE");
            }
            if (tier == null) {
                tier = new TierConfig("COPPER", 1, 2, 80);
            }
            return tier;
        }
        
        /**
         * Create default crafting tiers.
         * NERFED: All values < 100 coins. Crafting is supplemental income, not main.
         * Focus on mobs for real money.
         */
        private static Map<String, TierConfig> createDefaultCraftingTiers() {
            Map<String, TierConfig> t = new LinkedHashMap<>();
            
            // NONE - No reward (excluded items)
            t.put("NONE", new TierConfig("COPPER", 0, 0, 0));
            
            // TRIVIAL - Basic conversions (planks, sticks, building blocks)
            // Value: 0-1 copper = 0-1 coins @ 20%
            t.put("TRIVIAL", new TierConfig("COPPER", 0, 1, 20));
            
            // SIMPLE - Basic hand crafting (torches, ladders, simple items)
            // Value: 1-2 copper = 1-2 coins @ 60%
            t.put("SIMPLE", new TierConfig("COPPER", 1, 2, 60));
            
            // BASIC - Workbench items (wooden tools, basic furniture)
            // Value: 2-4 copper = 2-4 coins @ 80%
            t.put("BASIC", new TierConfig("COPPER", 2, 4, 80));
            
            // STANDARD - Standard crafting (stone/copper tools, food)
            // Value: 4-8 copper = 4-8 coins @ 90%
            t.put("STANDARD", new TierConfig("COPPER", 4, 8, 90));
            
            // ADVANCED - Advanced crafting (iron items, potions)
            // Value: 8-15 copper = 8-15 coins @ 100%
            t.put("ADVANCED", new TierConfig("COPPER", 8, 15, 100));
            
            // EXPERT - Expert crafting (steel items, complex tools)
            // Value: 15-30 copper = 15-30 coins @ 100%
            t.put("EXPERT", new TierConfig("COPPER", 15, 30, 100));
            
            // MASTER - Master crafting (cobalt, thorium items)
            // Value: 30-50 copper = 30-50 coins @ 100%
            t.put("MASTER", new TierConfig("COPPER", 30, 50, 100));
            
            // LEGENDARY - Legendary crafting (mithril, adamantite, artifacts)
            // Value: 50-90 copper = 50-90 coins @ 100%
            t.put("LEGENDARY", new TierConfig("COPPER", 50, 90, 100));
            
            return t;
        }
    }
    
    // =========================================================================
    // VIP CONFIG
    // =========================================================================
    
    /**
     * Configuration for VIP multipliers.
     * Allows giving bonus rewards to players with specific permissions.
     * 
     * <p>Features:
     * <ul>
     *   <li>Coin multiplier: Increases final coin amount (e.g., 1.2x = +20%)</li>
     *   <li>Chance bonus: Increases base drop probability (e.g., +10% to tier chance)</li>
     * </ul>
     * 
     * <p>Optimized for 500+ concurrent players:
     * <ul>
     *   <li>No object allocations in hot path</li>
     *   <li>Direct permission checks via CommandSender interface</li>
     *   <li>Early exit when VIP disabled</li>
     * </ul>
     */
    public static class VipConfig {
        public static final BuilderCodec<VipConfig> CODEC = BuilderCodec.builder(VipConfig.class, VipConfig::new)
            .append(new KeyedCodec<>("Enabled", Codec.BOOLEAN),
                (c, v, e) -> c.enabled = v, (c, e) -> c.enabled).add()
            .append(new KeyedCodec<>("MaxGlobalMultiplier", Codec.FLOAT),
                (c, v, e) -> c.maxGlobalMultiplier = v, (c, e) -> c.maxGlobalMultiplier).add()
            .append(new KeyedCodec<>("Multipliers", new MapCodec<>(Codec.FLOAT, HashMap::new)),
                (c, v, e) -> c.multipliers = v, (c, e) -> c.multipliers).add()
            .append(new KeyedCodec<>("ChanceBonuses", new MapCodec<>(Codec.INTEGER, HashMap::new)),
                (c, v, e) -> c.chanceBonuses = v, (c, e) -> c.chanceBonuses).add()
            .build();
        
        private boolean enabled = true;
        private float maxGlobalMultiplier = 5.0f; // Hard cap for safety
        private Map<String, Float> multipliers = new HashMap<>();
        private Map<String, Integer> chanceBonuses = new HashMap<>();  // e.g., "vip" -> 10 means +10% drop chance
        
        // Default configuration with MORE NOTICEABLE bonuses
        public VipConfig() {
            // Coin multipliers (applied to final coin count)
            multipliers.put("vip", 1.20f);      // +20% coins (was 1.1)
            multipliers.put("mvp", 1.50f);      // +50% coins (was 1.25)
            multipliers.put("mvp_plus", 2.0f);  // +100% coins (was 1.5)
            
            // Chance bonuses (added to tier drop chance, capped at 100%)
            // Makes VIP drops MORE CONSISTENT even for low-chance tiers
            chanceBonuses.put("vip", 5);        // +5% drop chance
            chanceBonuses.put("mvp", 10);       // +10% drop chance
            chanceBonuses.put("mvp_plus", 15);  // +15% drop chance
        }
        
        public boolean isEnabled() { return enabled; }
        public float getMaxGlobalMultiplier() { return maxGlobalMultiplier; }
        public Map<String, Float> getMultipliers() { return multipliers; }
        public Map<String, Integer> getChanceBonuses() { return chanceBonuses; }
        
        /**
         * Calculate the highest multiplier for a player based on their permissions.
         * Checks permissions: ecotalejobs.multiplier.<key>
         * 
         * <p><b>IMPORTANT:</b> Pass Player entity, NOT PlayerRef!
         * Only Player implements CommandSender which has hasPermission().
         * 
         * @param player The Player entity (implements CommandSender)
         * @return Multiplier between 1.0 and maxGlobalMultiplier
         */
        public float calculateMultiplier(CommandSender player) {
            if (!enabled || player == null || multipliers.isEmpty()) return 1.0f;
            
            float max = 1.0f;
            
            for (Map.Entry<String, Float> entry : multipliers.entrySet()) {
                String perm = "ecotalejobs.multiplier." + entry.getKey();
                if (player.hasPermission(perm)) {
                    max = Math.max(max, entry.getValue());
                }
            }
            
            return Math.min(max, maxGlobalMultiplier);
        }
        
        /**
         * Calculate the highest chance bonus for a player.
         * This is ADDED to the tier's base drop chance.
         * 
         * <p>Example: Tier has 40% chance, VIP bonus is +10%
         * → Final chance = min(50%, 100%)
         * 
         * @param player The Player entity (implements CommandSender)
         * @return Bonus percentage points to add (0-100)
         */
        public int calculateChanceBonus(CommandSender player) {
            if (!enabled || player == null || chanceBonuses.isEmpty()) return 0;
            
            int max = 0;
            
            for (Map.Entry<String, Integer> entry : chanceBonuses.entrySet()) {
                String perm = "ecotalejobs.multiplier." + entry.getKey();
                if (player.hasPermission(perm)) {
                    max = Math.max(max, entry.getValue());
                }
            }
            
            return max;
        }
        
        /**
         * Check if player has ANY VIP permission (for quick early-exit).
         * Used to skip VIP calculations entirely for non-VIP players.
         */
        public boolean hasAnyVip(CommandSender player) {
            if (!enabled || player == null) return false;
            
            for (String key : multipliers.keySet()) {
                if (player.hasPermission("ecotalejobs.multiplier." + key)) {
                    return true;
                }
            }
            return false;
        }
    }
}
