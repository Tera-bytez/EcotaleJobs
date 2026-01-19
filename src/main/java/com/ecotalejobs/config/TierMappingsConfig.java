package com.ecotalejobs.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.codecs.map.MapCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

import java.util.*;

/**
 * Mob-to-tier mappings config. Separate file for auto-merge without losing customizations.
 */
public class TierMappingsConfig {
    
    // Current mappings version - increment when adding new mobs
    public static final int CURRENT_VERSION = 1;
    
    public static final BuilderCodec<TierMappingsConfig> CODEC = BuilderCodec.builder(TierMappingsConfig.class, TierMappingsConfig::new)
        .append(new KeyedCodec<>("Version", Codec.INTEGER),
            (c, v, e) -> c.version = v, (c, e) -> c.version).add()
        .append(new KeyedCodec<>("AutoMergeNewMobs", Codec.BOOLEAN),
            (c, v, e) -> c.autoMergeNewMobs = v, (c, e) -> c.autoMergeNewMobs).add()
        .append(new KeyedCodec<>("TierMappings", new MapCodec<>(Codec.STRING, HashMap::new)),
            (c, v, e) -> c.tierMappings = v, (c, e) -> c.tierMappings).add()
        .append(new KeyedCodec<>("Exclusions", Codec.STRING_ARRAY),
            (c, v, e) -> c.exclusions = new ArrayList<>(Arrays.asList(v)), 
            (c, e) -> c.exclusions.toArray(new String[0])).add()
        .append(new KeyedCodec<>("DefaultTier", Codec.STRING),
            (c, v, e) -> c.defaultTier = v, (c, e) -> c.defaultTier).add()
        .build();
    
    private int version = CURRENT_VERSION;
    private boolean autoMergeNewMobs = true;
    private Map<String, String> tierMappings = createDefaultMappings();
    private List<String> exclusions = createDefaultExclusions();
    private String defaultTier = "HOSTILE";
    
    // Getters
    public int getVersion() { return version; }
    public boolean isAutoMergeNewMobs() { return autoMergeNewMobs; }
    public Map<String, String> getTierMappings() { return tierMappings; }
    public List<String> getExclusions() { return exclusions; }
    public String getDefaultTier() { return defaultTier; }
    
    /**
     * Safely add a mapping, handling potentially immutable maps from codec deserialization.
     * If the internal map is immutable, it will be replaced with a mutable copy.
     */
    public void addMapping(String mobName, String tier) {
        try {
            tierMappings.put(mobName, tier);
        } catch (UnsupportedOperationException e) {
            // Map was deserialized as immutable, replace with mutable copy
            tierMappings = new HashMap<>(tierMappings);
            tierMappings.put(mobName, tier);
        }
    }
    
    /**
     * Merge new default mappings into this config.
     * Only adds mappings that don't already exist.
     * @return Number of new mappings added
     */
    public int mergeDefaults() {
        if (!autoMergeNewMobs) {
            return 0;
        }
        
        Map<String, String> defaults = createDefaultMappings();
        int added = 0;
        
        for (Map.Entry<String, String> entry : defaults.entrySet()) {
            if (!tierMappings.containsKey(entry.getKey())) {
                tierMappings.put(entry.getKey(), entry.getValue());
                added++;
            }
        }
        
        // Also merge new exclusions
        List<String> defaultExclusions = createDefaultExclusions();
        for (String exclusion : defaultExclusions) {
            if (!exclusions.contains(exclusion)) {
                exclusions.add(exclusion);
            }
        }
        
        // Update version if we merged anything
        if (added > 0) {
            version = CURRENT_VERSION;
        }
        
        return added;
    }
    
    /**
     * Check if this config needs an update.
     */
    public boolean needsUpdate() {
        return version < CURRENT_VERSION;
    }
    
    // ==========================================================================
    // DEFAULT MAPPINGS - Auto-generated from game assets
    // Based on Danger Score = HP + (DMG * 4) * aggression_modifier
    // ==========================================================================
    
    private static Map<String, String> createDefaultMappings() {
        Map<String, String> m = new LinkedHashMap<>();
        
        // ============ WORLDBOSS (Danger 1000+) ============
        // Dragons are the ultimate bosses
        m.put("Dragon_*", "WORLDBOSS");
        m.put("*_Titan", "WORLDBOSS");
        
        // ============ MINIBOSS (Danger 700-1200) ============
        // These are the hardest non-dragon enemies
        m.put("Shadow_Knight", "MINIBOSS");      // 400 HP, 119 DMG -> 1139
        m.put("Zombie_Aberrant", "MINIBOSS");    // 400 HP, 119 DMG -> 1139  
        m.put("Zombie_Aberrant_Big", "MINIBOSS"); // 341 HP, 86 DMG -> 890
        m.put("Rex_Cave", "MINIBOSS");           // 400 HP, 68 DMG -> 874
        m.put("Werewolf", "MINIBOSS");           // 283 HP, 66 DMG -> 711
        
        // ============ ELITE (Danger 300-700) ============
        // Strong enemies that require skill to defeat
        m.put("Emberwulf", "ELITE");             // 193 HP, 64 DMG -> 584
        m.put("Ghoul", "ELITE");                 // 193 HP, 48 DMG -> 500
        m.put("Crocodile", "ELITE");             // 145 HP, 48 DMG -> 438
        m.put("Tiger_Sabertooth", "ELITE");      // 124 HP, 46 DMG -> 400
        m.put("Whale_Humpback", "ELITE");        // 400 HP, peaceful giant
        m.put("Spawn_Void", "ELITE");            // 193 HP, 48 DMG -> 385
        m.put("Golem_Crystal_Sand", "ELITE");    // 193 HP, 47 DMG -> 381
        m.put("Wraith", "ELITE");                // 193 HP, 40 DMG -> 353
        m.put("Cow_Undead", "ELITE");            // 124 HP, 35 DMG -> 343
        m.put("Toad_Rhino*", "ELITE");           // 124 HP, 35 DMG -> 343
        m.put("Leopard_Snow", "ELITE");          // 103 HP, 36 DMG -> 321
        m.put("Goblin_Duke*", "ELITE");          // Boss phases
        m.put("Hound_Bleached", "ELITE");        // 126 HP, 30 DMG -> 320
        m.put("Zombie_Aberrant_Small", "ELITE"); // 126 HP, 30 DMG -> 320
        m.put("Yeti", "ELITE");                  // 226 HP, tough mythic
        m.put("Golem_*", "ELITE");               // All golems are elite
        m.put("*_Void", "ELITE");                // Void creatures
        
        // ============ HOSTILE (Danger 100-300) ============
        // Standard combat enemies
        m.put("Raptor_Cave", "HOSTILE");         // 103 HP, 27 DMG -> 211
        m.put("Trork_Chieftain", "HOSTILE");     // 124 HP, 35 DMG -> 264
        m.put("Outlander_Brute", "HOSTILE");     // 124 HP, 35 DMG
        m.put("Outlander_Berserker", "HOSTILE"); // 103 HP, 27 DMG
        m.put("Outlander_*", "HOSTILE");         // All outlanders
        m.put("Trork_*", "HOSTILE");             // All trorks  
        m.put("Zombie*", "HOSTILE");             // 49-126 HP, 18-30 DMG
        m.put("Scarak_Broodmother*", "HOSTILE"); // 145 HP, no damage
        m.put("Scarak_Defender*", "HOSTILE");    // 103 HP
        m.put("Skeleton_Burnt_*", "HOSTILE");    
        m.put("Skeleton_Incandescent_*", "HOSTILE");
        m.put("Skeleton_Pirate_*", "HOSTILE");
        m.put("Molerat", "HOSTILE");             // 61 HP, 23 DMG
        m.put("Fen_Stalker", "HOSTILE");         // 74 HP, 29 DMG
        m.put("Bear_*", "HOSTILE");              // 103-124 HP
        m.put("Wolf_Black", "HOSTILE");
        m.put("Wolf_White", "HOSTILE");
        m.put("Hyena", "HOSTILE");
        m.put("Shark_*", "HOSTILE");
        m.put("Snake_Cobra", "HOSTILE");
        m.put("Scorpion", "HOSTILE");
        m.put("Spider*", "HOSTILE");
        m.put("Bison", "HOSTILE");               // 126 HP
        m.put("Camel", "HOSTILE");               // 126 HP
        m.put("Horse", "HOSTILE");               // 124 HP, 12 DMG
        m.put("Ram", "HOSTILE");                 // 124 HP
        m.put("Cow", "HOSTILE");                 // 103 HP, 9 DMG
        m.put("Boar", "HOSTILE");                // 81 HP
        m.put("Warthog", "HOSTILE");
        m.put("Antelope", "HOSTILE");
        m.put("Moose_*", "HOSTILE");
        m.put("Mosshorn*", "HOSTILE");
        m.put("Deer_Stag", "HOSTILE");
        m.put("Kweebec_Razorleaf*", "HOSTILE");  // 105 HP
        m.put("Hedera", "HOSTILE");              // 226 HP
        m.put("Trillodon", "HOSTILE");           // 145 HP
        m.put("Snapdragon", "HOSTILE");          // 103 HP
        m.put("Spirit_Thunder", "HOSTILE");      // 249 HP
        m.put("Spirit_Ember", "HOSTILE");        // 126 HP
        m.put("Lizard_Sand", "HOSTILE");
        m.put("Tortoise", "HOSTILE");
        m.put("Armadillo", "HOSTILE");
        m.put("Slug_Magma", "HOSTILE");
        
        // ============ PASSIVE (Danger 50-100) ============
        // Non-aggressive or weak enemies
        m.put("Skeleton", "PASSIVE");            // 92 HP
        m.put("Skeleton_Archer", "PASSIVE");
        m.put("Skeleton_*", "PASSIVE");          
        m.put("Scarak_Fighter*", "PASSIVE");     // 81 HP
        m.put("Scarak_Seeker*", "PASSIVE");      // 61 HP
        m.put("Dungeon_Scarak_*", "PASSIVE");    
        m.put("Feran_*", "PASSIVE");             
        m.put("Kweebec_Sapling*", "PASSIVE");
        m.put("Kweebec_Rootling", "PASSIVE");
        m.put("Goblin_Scavenger*", "PASSIVE");   // 54 HP
        m.put("Goblin_Ogre", "PASSIVE");         // 124 HP but slow
        m.put("Klops_*", "PASSIVE");             
        m.put("Sheep", "PASSIVE");
        m.put("Mouflon", "PASSIVE");
        m.put("Goat", "PASSIVE");
        m.put("Deer_Doe", "PASSIVE");
        m.put("Pig_Wild", "PASSIVE");
        m.put("Cow_Calf", "PASSIVE");
        m.put("Camel_Calf", "PASSIVE");
        m.put("Bison_Calf", "PASSIVE");
        m.put("Ram_Lamb", "PASSIVE");
        m.put("Warthog_Piglet", "PASSIVE");
        m.put("Spirit_Frost", "PASSIVE");
        m.put("Spirit_Root", "PASSIVE");
        m.put("Cactee", "PASSIVE");
        m.put("Spark_Living", "PASSIVE");
        m.put("Snail_*", "PASSIVE");
        m.put("Snake_*", "PASSIVE");
        m.put("Eel_*", "PASSIVE");
        m.put("Trilobite*", "PASSIVE");
        m.put("Lobster", "PASSIVE");
        m.put("Jellyfish_Man_Of_War", "PASSIVE");
        m.put("Frostgill", "PASSIVE");
        m.put("Snapjaw", "PASSIVE");
        m.put("Archaeopteryx", "PASSIVE");
        m.put("Vulture", "PASSIVE");
        m.put("Pterodactyl", "PASSIVE");
        m.put("Wraith_Lantern", "PASSIVE");
        m.put("Crawler_Void", "PASSIVE");
        m.put("Eye_Void", "PASSIVE");
        m.put("Larva_Silk", "PASSIVE");
        
        // ============ CRITTER (Danger 0-50) ============
        // Tiny creatures, babies, passive wildlife
        m.put("*_Chick", "CRITTER");
        m.put("*_Cub", "CRITTER");
        m.put("*_Baby", "CRITTER");
        m.put("*_Piglet", "CRITTER");
        m.put("*_Lamb", "CRITTER");
        m.put("*_Foal", "CRITTER");
        m.put("*_Kid", "CRITTER");
        m.put("*_Seedling", "CRITTER");
        m.put("*_Sproutling", "CRITTER");
        m.put("Chicken", "CRITTER");
        m.put("Chicken_Desert", "CRITTER");
        m.put("Pig", "CRITTER");
        m.put("Bunny", "CRITTER");
        m.put("Mouse", "CRITTER");
        m.put("Squirrel", "CRITTER");
        m.put("Meerkat", "CRITTER");
        m.put("Gecko", "CRITTER");
        m.put("Rat", "CRITTER");
        m.put("Fox", "CRITTER");
        m.put("Rabbit", "CRITTER");
        m.put("Hatworm", "CRITTER");
        m.put("Frog_*", "CRITTER");
        m.put("Bat*", "CRITTER");
        m.put("Skrill*", "CRITTER");
        m.put("Turkey*", "CRITTER");
        m.put("Penguin", "CRITTER");
        m.put("Parrot", "CRITTER");
        m.put("Owl_*", "CRITTER");
        m.put("Crow", "CRITTER");
        m.put("Raven", "CRITTER");
        m.put("Bluebird", "CRITTER");
        m.put("Finch_*", "CRITTER");
        m.put("Sparrow", "CRITTER");
        m.put("Woodpecker", "CRITTER");
        m.put("Pigeon", "CRITTER");
        m.put("Duck", "CRITTER");
        m.put("Flamingo", "CRITTER");
        m.put("Hawk", "CRITTER");
        m.put("Tetrabird", "CRITTER");
        m.put("Crab", "CRITTER");
        m.put("Jellyfish_*", "CRITTER");
        m.put("Pufferfish", "CRITTER");
        m.put("Clownfish", "CRITTER");
        m.put("Minnow", "CRITTER");
        m.put("Tang_*", "CRITTER");
        m.put("Pike", "CRITTER");
        m.put("Piranha*", "CRITTER");
        m.put("Salmon", "CRITTER");
        m.put("Bluegill", "CRITTER");
        m.put("Catfish", "CRITTER");
        m.put("Trout_*", "CRITTER");
        m.put("Shellfish_*", "CRITTER");
        m.put("Scarak_Louse", "CRITTER");
        m.put("Larva_Void", "CRITTER");
        m.put("Goblin_Hermit", "CRITTER");
        m.put("Goblin_Miner*", "CRITTER");
        m.put("Goblin_Scrapper*", "CRITTER");
        m.put("Goblin_Thief*", "CRITTER");
        m.put("Goblin_Lobber*", "CRITTER");
        m.put("Temple_*", "CRITTER");
        m.put("Snake_Marsh", "CRITTER");
        
        return m;
    }
    
    private static List<String> createDefaultExclusions() {
        List<String> ex = new ArrayList<>();
        // Quest NPCs and friendly characters
        ex.add("Quest_Master");
        ex.add("Tuluk_Fisherman");
        ex.add("Klops_Gentleman");
        ex.add("Klops_Miner");
        ex.add("Kweebec_Prisoner");
        ex.add("Kweebec_Elder");
        
        // Test entities (development)
        ex.add("Test_*");
        ex.add("Edible_*");
        
        // Wolf companions (tamed)
        ex.add("Wolf_Trork_*");
        ex.add("Wolf_Outlander_*");
        
        return ex;
    }
}
