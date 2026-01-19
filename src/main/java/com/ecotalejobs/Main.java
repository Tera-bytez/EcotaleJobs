package com.ecotalejobs;

import com.ecotalejobs.systems.MobRewardSystem;
import com.ecotalejobs.systems.CraftingRewardSystem;
import com.ecotalejobs.systems.MiningRewardSystem;
// import com.ecotalejobs.systems.HarvestRewardSystem;
import com.ecotalejobs.config.EcotaleJobsConfig;
import com.ecotalejobs.config.TierMappingsConfig;
import com.ecotalejobs.config.CraftingMappingsConfig;
import com.ecotalejobs.util.NPCAutoDetector;
import com.ecotalejobs.util.CraftingAutoDetector;
import com.ecotalejobs.util.RewardNotifier;
import com.hypixel.hytale.assetstore.event.LoadedAssetsEvent;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.util.Config;
import com.hypixel.hytale.server.npc.AllNPCsLoadedEvent;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import java.util.Map;
import java.util.logging.Level;

/**
 * EcotaleJobs - Job rewards plugin. Requires Ecotale.
 */
public class Main extends JavaPlugin {
    
    private static Main instance;
    public static Config<EcotaleJobsConfig> CONFIG;
    public static Config<TierMappingsConfig> TIER_MAPPINGS;
    public static Config<CraftingMappingsConfig> CRAFTING_MAPPINGS;
    
    // Keep references to reward systems for initialization/monitoring
    private MobRewardSystem mobRewardSystem;
    private MiningRewardSystem miningRewardSystem;
    private CraftingRewardSystem craftingRewardSystem;
    
    public Main(@NonNullDecl JavaPluginInit init) {
        super(init);
        CONFIG = this.withConfig("EcotaleJobs", EcotaleJobsConfig.CODEC);
        TIER_MAPPINGS = this.withConfig("TierMappings", TierMappingsConfig.CODEC);
        CRAFTING_MAPPINGS = this.withConfig("CraftingMappings", CraftingMappingsConfig.CODEC);
    }
    
    @Override
    protected void setup() {
        super.setup();
        instance = this;
        
        // Save main config
        CONFIG.save();
        
        // Load tier mappings
        TierMappingsConfig mappings = TIER_MAPPINGS.get();
        
        // Phase 1: Merge hardcoded defaults (for known mobs)
        int fromDefaults = mappings.mergeDefaults();
        
        // Phase 2: Register listener for AllNPCsLoadedEvent
        // This event fires AFTER all NPC assets are loaded by the server
        // Much more reliable than trying to detect during setup()
        this.getEventRegistry().register(AllNPCsLoadedEvent.class, this::onNPCsLoaded);
        
        // Save defaults that were merged
        if (fromDefaults > 0) {
            TIER_MAPPINGS.save();
            this.getLogger().at(Level.INFO).log(
                "[EcotaleJobs] Merged %d mobs from defaults", fromDefaults
            );
        }
        
        EcotaleJobsConfig config = CONFIG.get();
        boolean craftingEnabled = config.getCrafting().isEnabled();
        
        // Phase 3: Register listener for CraftingRecipe assets (only if crafting is enabled)
        if (craftingEnabled) {
            // This fires when recipes are loaded, allowing us to auto-detect new ones
            this.getEventRegistry().register(LoadedAssetsEvent.class, CraftingRecipe.class, this::onRecipesLoaded);
        }
        
        // Configure reward notifier
        RewardNotifier.configure(
            config.getNotifications().isShowRewards(),
            config.getNotifications().getMinRewardToShow(),
            null
        );
        
        // Create and initialize MobRewardSystem with both configs
        mobRewardSystem = new MobRewardSystem();
        mobRewardSystem.init(config.getMobKills(), mappings);
        
        // Load crafting mappings and create CraftingRewardSystem (only if enabled)
        CraftingMappingsConfig craftingMappings = null;
        if (craftingEnabled) {
            craftingMappings = CRAFTING_MAPPINGS.get();
            craftingRewardSystem = new CraftingRewardSystem();
            craftingRewardSystem.init(config.getCrafting(), craftingMappings);
        } else {
            craftingRewardSystem = null;
        }
        
        // Register ECS systems
        this.getEntityStoreRegistry().registerSystem(mobRewardSystem);
        if (craftingEnabled && craftingRewardSystem != null) {
            this.getEntityStoreRegistry().registerSystem(craftingRewardSystem);
        }
        
        // Mining Reward System (auto-classification by quality)
        boolean miningEnabled = config.getMining().isEnabled();
        if (miningEnabled) {
            miningRewardSystem = new MiningRewardSystem();
            miningRewardSystem.init(config.getMining());
            
            // Register as EntityEventSystem
            this.getEntityStoreRegistry().registerSystem(miningRewardSystem);
            
            this.getLogger().at(Level.INFO).log(
                "[EcotaleJobs] Mining system enabled with AUTO-CLASSIFICATION: %d tiers",
                config.getMining().getTiers().size()
            );
        } else {
            miningRewardSystem = null;
        }
        
        // Harvesting system (future)
        // this.getEntityStoreRegistry().registerSystem(new HarvestRewardSystem());
        
        // Save crafting mappings (may have defaults)
        if (craftingEnabled) {
            CRAFTING_MAPPINGS.save();
        }
        
        if (craftingEnabled && craftingMappings != null) {
            this.getLogger().at(Level.INFO).log(
                "[EcotaleJobs] Loaded - %d tiers, %d mob mappings (v%d), %d crafting mappings",
                config.getMobKills().getTiers().size(),
                mappings.getTierMappings().size(),
                mappings.getVersion(),
                craftingMappings.getItemMappings().size()
            );
        } else {
            this.getLogger().at(Level.INFO).log(
                "[EcotaleJobs] Loaded - %d tiers, %d mob mappings (v%d), Crafting=DISABLED",
                config.getMobKills().getTiers().size(),
                mappings.getTierMappings().size(),
                mappings.getVersion()
            );
        }
        
        // Register debug commands
        this.getCommandRegistry().registerCommand(new com.ecotalejobs.commands.TestOresCommand());
    }
    
    // Handles AllNPCsLoadedEvent - auto-detect new NPCs
    private void onNPCsLoaded(AllNPCsLoadedEvent event) {
        TierMappingsConfig mappings = TIER_MAPPINGS.get();
        
        if (!mappings.isAutoMergeNewMobs()) {
            this.getLogger().at(Level.INFO).log(
                "[EcotaleJobs] Auto-merge disabled. %d NPCs available, %d mapped.",
                event.getAllNPCs().size(), mappings.getTierMappings().size()
            );
            return;
        }
        
        // Now we can safely detect NPCs - they're ALL loaded!
        Map<String, String> detectedNPCs = NPCAutoDetector.detectNewNPCs(mappings);
        int fromAutoDetect = 0;
        
        // Get current mappings - make a mutable copy if needed
        Map<String, String> currentMappings = mappings.getTierMappings();
        
        for (Map.Entry<String, String> entry : detectedNPCs.entrySet()) {
            if (!currentMappings.containsKey(entry.getKey())) {
                // Use mergeMapping method instead of direct put to handle immutable maps
                mappings.addMapping(entry.getKey(), entry.getValue());
                fromAutoDetect++;
            }
        }
        
        if (fromAutoDetect > 0) {
            TIER_MAPPINGS.save();
            this.getLogger().at(Level.INFO).log(
                "[EcotaleJobs] Auto-detected %d new NPCs from %d total server NPCs",
                fromAutoDetect, event.getAllNPCs().size()
            );
        } else {
            this.getLogger().at(Level.INFO).log(
                "[EcotaleJobs] All %d server NPCs already mapped (%d mappings)",
                event.getAllNPCs().size(), mappings.getTierMappings().size()
            );
        }
    }
    
    /**
     * Called when CraftingRecipe assets are loaded.
     * This is the optimal time to auto-detect new recipes because:
     * - All vanilla Hytale recipes are loaded
     * - All recipes from mods are loaded
     * - The CraftingRecipe.getAssetMap() returns complete data
     */
    private void onRecipesLoaded(LoadedAssetsEvent<String, CraftingRecipe, DefaultAssetMap<String, CraftingRecipe>> event) {
        CraftingMappingsConfig craftingMappings = CRAFTING_MAPPINGS.get();
        
        if (!craftingMappings.isAutoDetectNewRecipes()) {
            this.getLogger().at(Level.INFO).log(
                "[EcotaleJobs] Recipe auto-detect disabled. %d recipes loaded.",
                event.getLoadedAssets().size()
            );
            return;
        }
        
        // Process newly loaded recipes
        Map<String, CraftingRecipe> loadedRecipes = event.getLoadedAssets();
        Map<String, String> detectedRecipes = CraftingAutoDetector.processLoadedRecipes(loadedRecipes, craftingMappings);
        
        int fromAutoDetect = 0;
        for (Map.Entry<String, String> entry : detectedRecipes.entrySet()) {
            craftingMappings.addItemMapping(entry.getKey(), entry.getValue());
            fromAutoDetect++;
        }
        
        if (fromAutoDetect > 0) {
            CRAFTING_MAPPINGS.save();
            
            // Re-initialize tier matcher with new mappings
            if (craftingRewardSystem != null) {
                craftingRewardSystem.refreshMappings(craftingMappings);
            }
            
            this.getLogger().at(Level.INFO).log(
                "[EcotaleJobs] Auto-detected %d new recipes from %d loaded (total: %d mappings)",
                fromAutoDetect, loadedRecipes.size(), craftingMappings.getItemMappings().size()
            );
        }
    }
    
    protected void onDisable() {
        this.getLogger().at(Level.INFO).log("EcotaleJobs disabled!");
    }
    
    public static Main getInstance() {
        return instance;
    }
    
    /**
     * Get the mob reward system for monitoring/admin.
     */
    public MobRewardSystem getMobRewardSystem() {
        return mobRewardSystem;
    }
    
    /**
     * Get the crafting reward system for monitoring/admin.
     */
    public CraftingRewardSystem getCraftingRewardSystem() {
        return craftingRewardSystem;
    }
    
    /**
     * Get the mining reward system for monitoring/admin.
     */
    public MiningRewardSystem getMiningRewardSystem() {
        return miningRewardSystem;
    }
}
