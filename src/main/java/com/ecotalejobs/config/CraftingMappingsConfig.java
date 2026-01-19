package com.ecotalejobs.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.codecs.map.MapCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

import java.util.*;

/**
 * Crafting item/recipe to tier mappings. Supports pattern matching.
 */
public class CraftingMappingsConfig {
    
    public static final int CURRENT_VERSION = 1;
    
    public static final BuilderCodec<CraftingMappingsConfig> CODEC = BuilderCodec.builder(CraftingMappingsConfig.class, CraftingMappingsConfig::new)
        .append(new KeyedCodec<>("Version", Codec.INTEGER),
            (c, v, e) -> c.version = v, (c, e) -> c.version).add()
        .append(new KeyedCodec<>("AutoDetectNewRecipes", Codec.BOOLEAN),
            (c, v, e) -> c.autoDetectNewRecipes = v, (c, e) -> c.autoDetectNewRecipes).add()
        .append(new KeyedCodec<>("ItemMappings", new MapCodec<>(Codec.STRING, HashMap::new)),
            (c, v, e) -> c.itemMappings = v, (c, e) -> c.itemMappings).add()
        .append(new KeyedCodec<>("RecipeMappings", new MapCodec<>(Codec.STRING, HashMap::new)),
            (c, v, e) -> c.recipeMappings = v, (c, e) -> c.recipeMappings).add()
        .append(new KeyedCodec<>("CategoryMappings", new MapCodec<>(Codec.STRING, HashMap::new)),
            (c, v, e) -> c.categoryMappings = v, (c, e) -> c.categoryMappings).add()
        .append(new KeyedCodec<>("BenchMappings", new MapCodec<>(Codec.STRING, HashMap::new)),
            (c, v, e) -> c.benchMappings = v, (c, e) -> c.benchMappings).add()
        .append(new KeyedCodec<>("Exclusions", Codec.STRING_ARRAY),
            (c, v, e) -> c.exclusions = new ArrayList<>(Arrays.asList(v)), 
            (c, e) -> c.exclusions.toArray(new String[0])).add()
        .append(new KeyedCodec<>("DefaultTier", Codec.STRING),
            (c, v, e) -> c.defaultTier = v, (c, e) -> c.defaultTier).add()
        .build();
    
    private int version = CURRENT_VERSION;
    private boolean autoDetectNewRecipes = true;
    
    // Mapping priority: RecipeMappings > ItemMappings > CategoryMappings > BenchMappings > DefaultTier
    private Map<String, String> itemMappings = createDefaultItemMappings();
    private Map<String, String> recipeMappings = createDefaultRecipeMappings();
    private Map<String, String> categoryMappings = createDefaultCategoryMappings();
    private Map<String, String> benchMappings = createDefaultBenchMappings();
    private List<String> exclusions = createDefaultExclusions();
    private String defaultTier = "SIMPLE";
    
    // Getters
    public int getVersion() { return version; }
    public boolean isAutoDetectNewRecipes() { return autoDetectNewRecipes; }
    public Map<String, String> getItemMappings() { return itemMappings; }
    public Map<String, String> getRecipeMappings() { return recipeMappings; }
    public Map<String, String> getCategoryMappings() { return categoryMappings; }
    public Map<String, String> getBenchMappings() { return benchMappings; }
    public List<String> getExclusions() { return exclusions; }
    public String getDefaultTier() { return defaultTier; }
    
    /**
     * Safely add an item mapping, handling immutable maps.
     */
    public void addItemMapping(String itemId, String tier) {
        try {
            itemMappings.put(itemId, tier);
        } catch (UnsupportedOperationException e) {
            itemMappings = new HashMap<>(itemMappings);
            itemMappings.put(itemId, tier);
        }
    }
    
    /**
     * Safely add a recipe mapping.
     */
    public void addRecipeMapping(String recipeId, String tier) {
        try {
            recipeMappings.put(recipeId, tier);
        } catch (UnsupportedOperationException e) {
            recipeMappings = new HashMap<>(recipeMappings);
            recipeMappings.put(recipeId, tier);
        }
    }
    
    // =========================================================================
    // DEFAULT ITEM MAPPINGS
    // Maps output item patterns to tiers
    // =========================================================================
    
    private static Map<String, String> createDefaultItemMappings() {
        Map<String, String> m = new LinkedHashMap<>();
        
        // =====================================
        // WEAPONS - By material tier
        // =====================================
        
        // Basic wooden tools (Tier: SIMPLE)
        m.put("Sword_Wooden", "SIMPLE");
        m.put("Axe_Wooden", "SIMPLE");
        m.put("Pickaxe_Wooden", "SIMPLE");
        m.put("Hoe_Wooden", "SIMPLE");
        m.put("Shovel_Wooden", "SIMPLE");
        m.put("*_Wooden_*", "SIMPLE");
        
        // Bone tools (Tier: SIMPLE)
        m.put("*_Bone_*", "SIMPLE");
        
        // Stone tools (Tier: BASIC)
        m.put("Sword_Stone", "BASIC");
        m.put("Axe_Stone", "BASIC");
        m.put("Pickaxe_Stone", "BASIC");
        m.put("*_Stone_*", "BASIC");
        
        // Copper tools (Tier: STANDARD)
        m.put("Sword_Copper", "STANDARD");
        m.put("*_Copper_*", "STANDARD");
        
        // Iron tools (Tier: ADVANCED)
        m.put("Sword_Iron", "ADVANCED");
        m.put("*_Iron_*", "ADVANCED");
        
        // Steel tools (Tier: EXPERT)
        m.put("Sword_Steel", "EXPERT");
        m.put("*_Steel_*", "EXPERT");
        
        // Cobalt tools (Tier: MASTER)
        m.put("Sword_Cobalt", "MASTER");
        m.put("*_Cobalt_*", "MASTER");
        
        // Mithril/Legendary (Tier: LEGENDARY)
        m.put("*_Mithril_*", "LEGENDARY");
        m.put("*_Adamant_*", "LEGENDARY");
        m.put("*_Dragon_*", "LEGENDARY");
        
        // =====================================
        // ARMOR - By material tier
        // =====================================
        
        m.put("Armor_Leather_*", "BASIC");
        m.put("Armor_Bone_*", "BASIC");
        m.put("Armor_Copper_*", "STANDARD");
        m.put("Armor_Iron_*", "ADVANCED");
        m.put("Armor_Steel_*", "EXPERT");
        m.put("Armor_Cobalt_*", "MASTER");
        m.put("Armor_Mithril_*", "LEGENDARY");
        
        // Helmet, Chestplate, Leggings, Boots patterns
        m.put("*_Helmet_Iron", "ADVANCED");
        m.put("*_Chestplate_Iron", "ADVANCED");
        m.put("*_Leggings_Iron", "ADVANCED");
        m.put("*_Boots_Iron", "ADVANCED");
        
        // =====================================
        // PROCESSED MATERIALS
        // =====================================
        
        // Ingots
        m.put("Ingot_Copper", "BASIC");
        m.put("Ingot_Iron", "STANDARD");
        m.put("Ingot_Steel", "ADVANCED");
        m.put("Ingot_Cobalt", "EXPERT");
        m.put("Ingot_Mithril", "MASTER");
        m.put("*_Ingot_*", "STANDARD");
        
        // Planks and processed wood
        m.put("Planks_*", "TRIVIAL");
        m.put("Stick_*", "TRIVIAL");
        
        // =====================================
        // FOOD
        // =====================================
        
        m.put("Food_Cooked_*", "SIMPLE");
        m.put("Food_Raw_*", "TRIVIAL");
        m.put("Bread_*", "SIMPLE");
        m.put("Stew_*", "BASIC");
        m.put("Potion_*", "STANDARD");
        
        // =====================================
        // BUILDING/DECORATIVE
        // =====================================
        
        m.put("Block_*", "TRIVIAL");
        m.put("Brick_*", "SIMPLE");
        m.put("Glass_*", "SIMPLE");
        m.put("Furniture_*", "BASIC");
        m.put("Decoration_*", "BASIC");
        
        return m;
    }
    
    // =========================================================================
    // DEFAULT RECIPE MAPPINGS
    // Maps specific recipe IDs to tiers (highest priority)
    // =========================================================================
    
    private static Map<String, String> createDefaultRecipeMappings() {
        Map<String, String> m = new LinkedHashMap<>();
        
        // Special recipes that deserve custom tiers
        m.put("Recipe_Anvil", "ADVANCED");
        m.put("Recipe_Furnace", "STANDARD");
        m.put("Recipe_Workbench", "BASIC");
        m.put("Recipe_Chest", "BASIC");
        m.put("Recipe_Enchanting_Table", "MASTER");
        
        return m;
    }
    
    // =========================================================================
    // DEFAULT CATEGORY MAPPINGS
    // Maps item categories to tiers
    // =========================================================================
    
    private static Map<String, String> createDefaultCategoryMappings() {
        Map<String, String> m = new LinkedHashMap<>();
        
        // Item categories (from Item.categories)
        m.put("Weapon", "STANDARD");
        m.put("Tool", "BASIC");
        m.put("Armor", "STANDARD");
        m.put("Food", "SIMPLE");
        m.put("Material", "TRIVIAL");
        m.put("Block", "TRIVIAL");
        m.put("Decoration", "SIMPLE");
        m.put("Consumable", "BASIC");
        m.put("Quest", "NONE"); // Quest items = no reward
        
        return m;
    }
    
    // =========================================================================
    // DEFAULT BENCH MAPPINGS
    // Maps crafting bench types to base tiers
    // =========================================================================
    
    private static Map<String, String> createDefaultBenchMappings() {
        Map<String, String> m = new LinkedHashMap<>();
        
        // Bench types (from BenchRequirement.id)
        m.put("Fieldcraft", "TRIVIAL");      // Hand crafting
        m.put("Workbench", "SIMPLE");        // Basic workbench
        m.put("Anvil", "STANDARD");          // Metal working
        m.put("Furnace", "SIMPLE");          // Smelting
        m.put("Loom", "BASIC");              // Cloth working
        m.put("Tanning_Rack", "BASIC");      // Leather working
        m.put("Alchemy_Table", "ADVANCED");  // Potion making
        m.put("Enchanting_Table", "EXPERT"); // Enchanting
        m.put("Forge", "ADVANCED");          // Advanced metal
        m.put("Smithing_Table", "EXPERT");   // Master smithing
        
        return m;
    }
    
    // =========================================================================
    // DEFAULT EXCLUSIONS
    // Items/recipes that should NEVER give rewards
    // =========================================================================
    
    private static List<String> createDefaultExclusions() {
        List<String> e = new ArrayList<>();
        
        // Quest-related items
        e.add("Quest_*");
        e.add("*_Quest_*");
        
        // Debug/Admin items
        e.add("Debug_*");
        e.add("Admin_*");
        e.add("Creative_*");
        
        // Extremely trivial conversions
        e.add("Planks_*");  // Log -> Planks is too easy
        e.add("Stick");
        e.add("Torch");
        
        // Temporary/test items
        e.add("Test_*");
        e.add("Temp_*");
        
        return e;
    }
}
