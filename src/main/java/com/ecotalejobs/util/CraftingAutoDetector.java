package com.ecotalejobs.util;

import com.ecotalejobs.config.CraftingMappingsConfig;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.protocol.BenchRequirement;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Optimized auto-detection system for crafting recipes.
 * 
 * IMPROVED ARCHITECTURE (v2):
 * - Uses Item.getCategories() for real category-based classification
 * - Uses Item.getItemLevel() as primary tier indicator  
 * - Category caps prevent plants/decorations from being over-valued
 * - Pre-caches Item lookups for O(1) access during event processing
 * - Fallback to name-based scoring only when Item metadata unavailable
 * 
 * Performance: O(1) per recipe classification after initial cache build
 * 
 * @author EcotaleJobs Team
 * @since 1.0.0
 */
public class CraftingAutoDetector {
    
    // =========================================================================
    // Category-based tier caps (prevents plants from being LEGENDARY)
    // Key: category prefix (from Item.getCategories())
    // Value: maximum allowed tier
    // =========================================================================
    
    private static final Map<String, String> CATEGORY_TIER_CAPS = new LinkedHashMap<>();
    static {
        // Plants, flowers, saplings - max BASIC regardless of materials
        CATEGORY_TIER_CAPS.put("Blocks.Plants", "BASIC");
        CATEGORY_TIER_CAPS.put("Plants", "BASIC");
        CATEGORY_TIER_CAPS.put("Seeds", "SIMPLE");
        CATEGORY_TIER_CAPS.put("Saplings", "SIMPLE");
        
        // Decorations - max STANDARD
        CATEGORY_TIER_CAPS.put("Blocks.Decorations", "STANDARD");
        CATEGORY_TIER_CAPS.put("Decorations", "STANDARD");
        CATEGORY_TIER_CAPS.put("Furniture", "STANDARD");
        
        // Building blocks - max BASIC
        CATEGORY_TIER_CAPS.put("Blocks.Building", "BASIC");
        CATEGORY_TIER_CAPS.put("Blocks.Terrain", "TRIVIAL");
        
        // Food - max STANDARD (cooking shouldn't be too profitable)
        CATEGORY_TIER_CAPS.put("Food", "STANDARD");
        CATEGORY_TIER_CAPS.put("Consumables", "STANDARD");
    }
    
    // =========================================================================
    // ItemLevel to Tier mapping (primary classification method)
    // Hytale uses ItemLevel 1-10+ to indicate item power
    // =========================================================================
    
    private static String itemLevelToTier(int itemLevel) {
        if (itemLevel <= 0) return "TRIVIAL";
        if (itemLevel <= 1) return "SIMPLE";
        if (itemLevel <= 2) return "BASIC";
        if (itemLevel <= 3) return "STANDARD";
        if (itemLevel <= 4) return "ADVANCED";
        if (itemLevel <= 5) return "EXPERT";
        if (itemLevel <= 7) return "MASTER";
        return "LEGENDARY";  // ItemLevel 8+
    }
    
    // Tier ordering for cap comparison
    private static final List<String> TIER_ORDER = Arrays.asList(
        "TRIVIAL", "SIMPLE", "BASIC", "STANDARD", "ADVANCED", "EXPERT", "MASTER", "LEGENDARY"
    );
    
    /**
     * Get the lower tier between two tiers (for capping).
     */
    private static String minTier(String tier1, String tier2) {
        int idx1 = TIER_ORDER.indexOf(tier1);
        int idx2 = TIER_ORDER.indexOf(tier2);
        if (idx1 < 0) idx1 = TIER_ORDER.size();
        if (idx2 < 0) idx2 = TIER_ORDER.size();
        return TIER_ORDER.get(Math.min(idx1, idx2));
    }
    
    // =========================================================================
    // Fallback: Material Tier Scoring (only when Item metadata unavailable)
    // =========================================================================
    
    private static final Map<String, Integer> MATERIAL_SCORES = new LinkedHashMap<>();
    static {
        // Tier 1: Basic (score 1-10)
        MATERIAL_SCORES.put("wood", 5);
        MATERIAL_SCORES.put("stick", 3);
        MATERIAL_SCORES.put("plank", 5);
        MATERIAL_SCORES.put("fiber", 4);
        MATERIAL_SCORES.put("string", 5);
        MATERIAL_SCORES.put("bone", 8);
        MATERIAL_SCORES.put("leather", 15);
        MATERIAL_SCORES.put("hide", 14);
        MATERIAL_SCORES.put("fabric", 13);
        MATERIAL_SCORES.put("cloth", 12);
        
        // Tier 3: Copper/Bronze (score 20-35)
        MATERIAL_SCORES.put("copper", 25);
        MATERIAL_SCORES.put("bronze", 30);
        MATERIAL_SCORES.put("tin", 22);
        
        // Tier 4: Iron (score 35-50)
        MATERIAL_SCORES.put("iron", 40);
        MATERIAL_SCORES.put("coal", 35);
        
        // Tier 5: Steel (score 50-70)
        MATERIAL_SCORES.put("steel", 60);
        MATERIAL_SCORES.put("silversteel", 65);
        
        // Tier 6: Cobalt/Rare (score 70-100)
        MATERIAL_SCORES.put("cobalt", 80);
        MATERIAL_SCORES.put("thorium", 85);
        MATERIAL_SCORES.put("electrum", 75);
        
        // Tier 7: Legendary (score 100+)
        MATERIAL_SCORES.put("mithril", 120);
        MATERIAL_SCORES.put("adamant", 130);
        MATERIAL_SCORES.put("onyxium", 140);
        MATERIAL_SCORES.put("nexus", 150);
        MATERIAL_SCORES.put("dragon", 160);
        MATERIAL_SCORES.put("void", 145);
        
        // Special materials
        MATERIAL_SCORES.put("gem", 50);
        MATERIAL_SCORES.put("crystal", 55);
        MATERIAL_SCORES.put("essence", 70);
        MATERIAL_SCORES.put("rune", 80);
        MATERIAL_SCORES.put("enchant", 90);
    }
    
    // Bench type scores
    private static final Map<String, Integer> BENCH_SCORES = new LinkedHashMap<>();
    static {
        BENCH_SCORES.put("hand", 0);           // No bench
        BENCH_SCORES.put("fieldcraft", 5);     // Basic field crafting
        BENCH_SCORES.put("workbench", 10);     // Standard workbench
        BENCH_SCORES.put("cooking", 15);       // Cooking station
        BENCH_SCORES.put("furnace", 20);       // Smelting
        BENCH_SCORES.put("anvil", 30);         // Smithing
        BENCH_SCORES.put("weapon_bench", 35);  // Weapon crafting
        BENCH_SCORES.put("armor_bench", 35);   // Armor crafting
        BENCH_SCORES.put("alchemy", 40);       // Potions
        BENCH_SCORES.put("enchanting", 50);    // Enchanting table
        BENCH_SCORES.put("arcane", 60);        // Magic crafting
    }
    
    // Item category bonuses (positive values boost tier, negative reduce)
    private static final Map<String, Integer> CATEGORY_BONUSES = new LinkedHashMap<>();
    static {
        // NEGATIVE bonuses - items that should be lower tier
        CATEGORY_BONUSES.put("sapling", -80);     // Saplings are basic, not legendary
        CATEGORY_BONUSES.put("seeds", -60);       // Seeds are simple farming items
        CATEGORY_BONUSES.put("seed", -60);        // Also match singular
        CATEGORY_BONUSES.put("plant_", -40);      // Most plant items are basic
        CATEGORY_BONUSES.put("flower", -50);      // Flowers are decorative
        CATEGORY_BONUSES.put("moss", -30);        // Decorative natural items
        CATEGORY_BONUSES.put("mushroom", -20);    // Simple gathering
        CATEGORY_BONUSES.put("grass", -40);       // Basic blocks
        CATEGORY_BONUSES.put("soil", -50);        // Basic terrain blocks
        CATEGORY_BONUSES.put("rubble", -40);      // Basic materials
        CATEGORY_BONUSES.put("brick", -20);       // Building materials (lower)
        CATEGORY_BONUSES.put("roof", -30);        // Building materials
        CATEGORY_BONUSES.put("fence", -25);       // Simple construction
        CATEGORY_BONUSES.put("decoration", -30);  // Decorative items
        CATEGORY_BONUSES.put("deco_", -30);       // Decorative items
        CATEGORY_BONUSES.put("cloth_", -20);      // Basic textiles
        
        // Positive bonuses - valuable crafted items
        CATEGORY_BONUSES.put("weapon", 20);
        CATEGORY_BONUSES.put("sword", 25);
        CATEGORY_BONUSES.put("axe", 22);
        CATEGORY_BONUSES.put("bow", 28);
        CATEGORY_BONUSES.put("crossbow", 30);
        CATEGORY_BONUSES.put("staff", 35);
        CATEGORY_BONUSES.put("wand", 32);
        CATEGORY_BONUSES.put("armor", 25);
        CATEGORY_BONUSES.put("helmet", 20);
        CATEGORY_BONUSES.put("chestplate", 28);
        CATEGORY_BONUSES.put("leggings", 25);
        CATEGORY_BONUSES.put("boots", 18);
        CATEGORY_BONUSES.put("shield", 22);
        CATEGORY_BONUSES.put("tool", 15);
        CATEGORY_BONUSES.put("pickaxe", 18);
        CATEGORY_BONUSES.put("shovel", 12);
        CATEGORY_BONUSES.put("hoe", 10);
        CATEGORY_BONUSES.put("potion", 30);
        CATEGORY_BONUSES.put("food", 8);
        CATEGORY_BONUSES.put("furniture", 5);
        CATEGORY_BONUSES.put("decoration", 3);
        CATEGORY_BONUSES.put("trinket", 40);
        CATEGORY_BONUSES.put("ring", 45);
        CATEGORY_BONUSES.put("amulet", 50);
    }
    
    // =========================================================================
    // Auto-Detection Entry Point
    // =========================================================================
    
    /**
     * Process loaded recipes and detect new ones that need tier assignment.
     * Called when LoadedAssetsEvent<CraftingRecipe> fires.
     * 
     * @param loadedRecipes Map of newly loaded recipes
     * @param config Current crafting mappings config
     * @return Map of new item IDs to their auto-assigned tiers
     */
    public static Map<String, String> processLoadedRecipes(
            @Nonnull Map<String, CraftingRecipe> loadedRecipes,
            @Nonnull CraftingMappingsConfig config) {
        
        Map<String, String> newMappings = new LinkedHashMap<>();
        
        Set<String> existingItemMappings = config.getItemMappings().keySet();
        Set<String> existingRecipeMappings = config.getRecipeMappings().keySet();
        Set<String> exclusions = new HashSet<>(config.getExclusions());
        
        // Pre-compile exclusion patterns
        List<Pattern> exclusionPatterns = compilePatterns(exclusions);
        List<Pattern> existingItemPatterns = compilePatterns(existingItemMappings);
        
        int totalProcessed = 0;
        int skippedExisting = 0;
        int skippedExcluded = 0;
        int detected = 0;
        
        for (Map.Entry<String, CraftingRecipe> entry : loadedRecipes.entrySet()) {
            CraftingRecipe recipe = entry.getValue();
            totalProcessed++;
            
            String recipeId = recipe.getId();
            if (recipeId == null || recipeId.isEmpty()) continue;
            
            // Get primary output item ID
            MaterialQuantity primaryOutput = recipe.getPrimaryOutput();
            String outputItemId = primaryOutput != null ? primaryOutput.getItemId() : null;
            
            if (outputItemId == null) continue;
            
            // Check if recipe is already mapped
            if (existingRecipeMappings.contains(recipeId)) {
                skippedExisting++;
                continue;
            }
            
            // Check if output item matches existing pattern
            if (matchesAnyPattern(outputItemId, existingItemPatterns) || 
                existingItemMappings.contains(outputItemId)) {
                skippedExisting++;
                continue;
            }
            
            // Check exclusions
            if (exclusions.contains(outputItemId) || 
                exclusions.contains(recipeId) ||
                matchesAnyPattern(outputItemId, exclusionPatterns) ||
                matchesAnyPattern(recipeId, exclusionPatterns)) {
                skippedExcluded++;
                continue;
            }
            
            // Auto-classify this recipe
            String tier = classifyRecipe(recipe, outputItemId);
            
            if (tier != null && !"NONE".equals(tier)) {
                newMappings.put(outputItemId, tier);
                detected++;
                JobsLogger.debug("[CraftingAutoDetector] %s -> %s (score from recipe)", 
                    outputItemId, tier);
            }
        }
        
        if (detected > 0) {
            JobsLogger.info("[CraftingAutoDetector] Processed %d recipes: %d new, %d existing, %d excluded",
                totalProcessed, detected, skippedExisting, skippedExcluded);
        }
        
        return newMappings;
    }
    
    /**
     * Full scan of all recipes in the asset map.
     * Used for initial load or manual refresh.
     */
    public static Map<String, String> scanAllRecipes(@Nonnull CraftingMappingsConfig config) {
        Map<String, String> newMappings = new LinkedHashMap<>();
        
        try {
            DefaultAssetMap<String, CraftingRecipe> recipeMap = CraftingRecipe.getAssetMap();
            if (recipeMap == null) {
                JobsLogger.warn("[CraftingAutoDetector] CraftingRecipe asset map not available");
                return newMappings;
            }
            
            Map<String, CraftingRecipe> allRecipes = recipeMap.getAssetMap();
            JobsLogger.info("[CraftingAutoDetector] Scanning %d total recipes in server...", allRecipes.size());
            
            return processLoadedRecipes(allRecipes, config);
            
        } catch (Exception e) {
            JobsLogger.warn("[CraftingAutoDetector] Failed to scan recipes: %s", e.getMessage());
        }
        
        return newMappings;
    }
    
    // =========================================================================
    // Recipe Classification (IMPROVED v2)
    // =========================================================================
    
    /**
     * Classify a recipe using Item metadata (preferred) or fallback scoring.
     * 
     * PRIORITY ORDER:
     * 1. Get output Item from asset map
     * 2. Use Item.getItemLevel() as primary indicator
     * 3. Apply category caps from Item.getCategories()
     * 4. Fallback to name-based scoring if Item unavailable
     * 
     * This is O(1) per call when Item is cached in asset map.
     */
    @Nullable
    private static String classifyRecipe(CraftingRecipe recipe, String outputItemId) {
        // Try to get the actual Item asset for metadata-based classification
        Item outputItem = null;
        try {
            DefaultAssetMap<String, Item> itemMap = Item.getAssetMap();
            if (itemMap != null) {
                outputItem = itemMap.getAsset(outputItemId);
            }
        } catch (Exception e) {
            // Item map not available, will use fallback
        }
        
        String tier;
        
        if (outputItem != null) {
            // === PRIMARY: Use Item metadata ===
            tier = classifyByItemMetadata(outputItem, recipe);
        } else {
            // === FALLBACK: Use name-based scoring ===
            tier = classifyByScoring(recipe, outputItemId);
        }
        
        return tier;
    }
    
    /**
     * Classify using actual Item metadata - fast and accurate.
     * O(1) complexity - just reads fields.
     */
    private static String classifyByItemMetadata(Item item, CraftingRecipe recipe) {
        // Get tier from ItemLevel (Hytale's own item power rating)
        int itemLevel = item.getItemLevel();
        String tier = itemLevelToTier(itemLevel);
        
        // Apply category caps
        String[] categories = item.getCategories();
        if (categories != null) {
            for (String category : categories) {
                // Check each category against our caps
                for (Map.Entry<String, String> cap : CATEGORY_TIER_CAPS.entrySet()) {
                    if (category.startsWith(cap.getKey()) || category.contains(cap.getKey())) {
                        tier = minTier(tier, cap.getValue());
                    }
                }
            }
        }
        
        // Also check item ID for safety (catches misclassified items)
        String itemId = item.getId();
        if (itemId != null) {
            String lower = itemId.toLowerCase();
            if (lower.contains("sapling") || lower.contains("seed")) {
                tier = minTier(tier, "SIMPLE");
            } else if (lower.contains("plant_") || lower.contains("flower")) {
                tier = minTier(tier, "BASIC");
            } else if (lower.startsWith("deco_") || lower.contains("decoration")) {
                tier = minTier(tier, "STANDARD");
            }
        }
        
        return tier;
    }
    
    /**
     * Fallback: Score-based classification when Item metadata unavailable.
     * Used for modded items that might not have proper Item assets.
     */
    private static String classifyByScoring(CraftingRecipe recipe, String outputItemId) {
        int score = 0;
        
        // Factor 1: Material scores from inputs
        MaterialQuantity[] inputs = recipe.getInput();
        if (inputs != null && inputs.length > 0) {
            int inputScore = 0;
            int totalQuantity = 0;
            
            for (MaterialQuantity input : inputs) {
                String itemId = input.getItemId();
                int qty = input.getQuantity();
                int materialScore = getMaterialScore(itemId);
                
                inputScore += materialScore * Math.min(qty, 10);
                totalQuantity += qty;
            }
            
            score += inputScore / Math.max(1, inputs.length);
            score += inputs.length * 5;
            score += Math.min(totalQuantity, 20) * 2;
        }
        
        // Factor 2: Output item category bonus (name-based fallback)
        score += getCategoryBonus(outputItemId);
        
        // Factor 3: Bench requirements
        BenchRequirement[] benchReqs = recipe.getBenchRequirement();
        if (benchReqs != null && benchReqs.length > 0) {
            int maxBenchScore = 0;
            for (BenchRequirement bench : benchReqs) {
                int benchScore = getBenchScore(bench);
                maxBenchScore = Math.max(maxBenchScore, benchScore);
            }
            score += maxBenchScore;
        }
        
        // Factor 4: Crafting time bonus
        float craftTime = recipe.getTimeSeconds();
        if (craftTime > 0) {
            score += (int)(craftTime * 3);
        }
        
        // Factor 5: Knowledge requirement bonus
        if (recipe.isKnowledgeRequired()) {
            score += 20;
        }
        
        return scoreToTier(score);
    }
    
    /**
     * Get material score from item ID by checking for known material keywords.
     */
    private static int getMaterialScore(String itemId) {
        if (itemId == null) return 5;
        
        String lower = itemId.toLowerCase();
        int maxScore = 5; // Default base score
        
        for (Map.Entry<String, Integer> entry : MATERIAL_SCORES.entrySet()) {
            if (lower.contains(entry.getKey())) {
                maxScore = Math.max(maxScore, entry.getValue());
            }
        }
        
        return maxScore;
    }
    
    /**
     * Get category bonus from output item ID.
     * Supports negative bonuses to reduce tier for basic items.
     */
    private static int getCategoryBonus(String outputItemId) {
        if (outputItemId == null) return 0;
        
        String lower = outputItemId.toLowerCase();
        int maxPositive = 0;
        int minNegative = 0;
        
        for (Map.Entry<String, Integer> entry : CATEGORY_BONUSES.entrySet()) {
            if (lower.contains(entry.getKey())) {
                int value = entry.getValue();
                if (value < 0) {
                    // Track most negative penalty
                    minNegative = Math.min(minNegative, value);
                } else {
                    // Track best positive bonus
                    maxPositive = Math.max(maxPositive, value);
                }
            }
        }
        
        // Apply negative penalties even if there's a positive
        // Saplings/seeds will be heavily penalized
        return maxPositive + minNegative;
    }
    
    /**
     * Get bench score from bench requirement.
     * BenchRequirement uses PUBLIC FIELDS, not getters.
     */
    private static int getBenchScore(BenchRequirement bench) {
        if (bench == null) return 0;
        
        // BenchRequirement has public fields: type, id, categories, requiredTierLevel
        String benchId = bench.id;
        if (benchId == null) {
            benchId = bench.type != null ? bench.type.name() : "";
        }
        
        String lower = benchId.toLowerCase();
        int maxScore = 0;
        
        for (Map.Entry<String, Integer> entry : BENCH_SCORES.entrySet()) {
            if (lower.contains(entry.getKey())) {
                maxScore = Math.max(maxScore, entry.getValue());
            }
        }
        
        // Tier level bonus (public field)
        int tierLevel = bench.requiredTierLevel;
        maxScore += tierLevel * 15;
        
        return maxScore;
    }
    
    /**
     * Convert numerical score to tier name.
     * 
     * Score ranges (tuned for balance):
     * - TRIVIAL:   0-20    (basic conversions)
     * - SIMPLE:    21-45   (hand crafting)
     * - BASIC:     46-80   (workbench items)
     * - STANDARD:  81-130  (standard tools)
     * - ADVANCED:  131-200 (iron tier)
     * - EXPERT:    201-300 (steel tier)
     * - MASTER:    301-450 (cobalt tier)
     * - LEGENDARY: 451+    (mithril+)
     */
    private static String scoreToTier(int score) {
        if (score <= 20) return "TRIVIAL";
        if (score <= 45) return "SIMPLE";
        if (score <= 80) return "BASIC";
        if (score <= 130) return "STANDARD";
        if (score <= 200) return "ADVANCED";
        if (score <= 300) return "EXPERT";
        if (score <= 450) return "MASTER";
        return "LEGENDARY";
    }
    
    // =========================================================================
    // Pattern Matching Utilities
    // =========================================================================
    
    /**
     * Compile a set of patterns (may include wildcards) into regex patterns.
     */
    private static List<Pattern> compilePatterns(Set<String> patterns) {
        List<Pattern> compiled = new ArrayList<>();
        for (String pattern : patterns) {
            if (pattern.contains("*") || pattern.contains("?")) {
                compiled.add(compileWildcard(pattern));
            }
        }
        return compiled;
    }
    
    /**
     * Convert wildcard pattern to regex.
     */
    private static Pattern compileWildcard(String wildcard) {
        String regex = "^" + wildcard
            .replace(".", "\\.")
            .replace("*", ".*")
            .replace("?", ".")
            + "$";
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
    }
    
    /**
     * Check if a string matches any of the compiled patterns.
     */
    private static boolean matchesAnyPattern(String value, List<Pattern> patterns) {
        for (Pattern pattern : patterns) {
            if (pattern.matcher(value).matches()) {
                return true;
            }
        }
        return false;
    }
}
