package com.ecotalejobs.util;

import com.ecotalejobs.config.CraftingMappingsConfig;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.protocol.BenchRequirement;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Intelligent tier matcher for crafted items and recipes.
 * 
 * <p>Resolution order (highest to lowest priority):
 * <ol>
 *   <li>Recipe ID exact match</li>
 *   <li>Recipe ID pattern match</li>
 *   <li>Output Item ID exact match</li>
 *   <li>Output Item ID pattern match</li>
 *   <li>Item category match</li>
 *   <li>Bench type match</li>
 *   <li>Auto-classification by complexity</li>
 *   <li>Default tier</li>
 * </ol>
 * 
 * <p>Pattern syntax:
 * <ul>
 *   <li>* - matches any characters</li>
 *   <li>? - matches single character</li>
 *   <li>Namespace:* - matches all in namespace</li>
 * </ul>
 * 
 * @author EcotaleJobs Team
 * @since 1.0.0
 */
public class CraftingTierMatcher {
    
    // O(1) lookup cache for resolved tiers
    private final Map<String, String> recipeCache = new ConcurrentHashMap<>();
    private final Map<String, String> itemCache = new ConcurrentHashMap<>();
    
    // Compiled patterns for efficiency
    private final Map<String, Pattern> recipePatterns = new LinkedHashMap<>();
    private final Map<String, Pattern> itemPatterns = new LinkedHashMap<>();
    
    // Configuration references
    private Map<String, String> recipeMappings;
    private Map<String, String> itemMappings;
    private Map<String, String> categoryMappings;
    private Map<String, String> benchMappings;
    private Set<String> exclusions;
    private String defaultTier;
    
    // Pre-compiled exclusion patterns
    private final List<Pattern> exclusionPatterns = new ArrayList<>();
    
    /**
     * Configure the matcher with mappings from config.
     */
    public void configure(CraftingMappingsConfig config) {
        this.recipeMappings = config.getRecipeMappings();
        this.itemMappings = config.getItemMappings();
        this.categoryMappings = config.getCategoryMappings();
        this.benchMappings = config.getBenchMappings();
        this.exclusions = new HashSet<>(config.getExclusions());
        this.defaultTier = config.getDefaultTier();
        
        // Clear caches
        recipeCache.clear();
        itemCache.clear();
        recipePatterns.clear();
        itemPatterns.clear();
        exclusionPatterns.clear();
        
        // Pre-compile patterns for recipe mappings
        for (Map.Entry<String, String> entry : recipeMappings.entrySet()) {
            if (containsWildcard(entry.getKey())) {
                recipePatterns.put(entry.getKey(), compileWildcard(entry.getKey()));
            }
        }
        
        // Pre-compile patterns for item mappings
        for (Map.Entry<String, String> entry : itemMappings.entrySet()) {
            if (containsWildcard(entry.getKey())) {
                itemPatterns.put(entry.getKey(), compileWildcard(entry.getKey()));
            }
        }
        
        // Pre-compile exclusion patterns
        for (String exclusion : exclusions) {
            if (containsWildcard(exclusion)) {
                exclusionPatterns.add(compileWildcard(exclusion));
            }
        }
        
        JobsLogger.debug("[CraftingTierMatcher] Configured: %d recipe mappings, %d item mappings, %d exclusions",
            recipeMappings.size(), itemMappings.size(), exclusions.size());
    }
    
    /**
     * Find the appropriate tier for a crafted recipe.
     * 
     * @param recipe The crafting recipe
     * @return The tier name, or "NONE" if excluded
     */
    @Nonnull
    public String findTier(@Nonnull CraftingRecipe recipe) {
        String recipeId = recipe.getId();
        if (recipeId == null) {
            recipeId = "unknown_recipe";
        }
        
        // Check cache first
        String cached = recipeCache.get(recipeId);
        if (cached != null) {
            return cached;
        }
        
        // Check exclusions
        if (isExcluded(recipeId)) {
            recipeCache.put(recipeId, "NONE");
            return "NONE";
        }
        
        // Get output item info
        MaterialQuantity primaryOutput = recipe.getPrimaryOutput();
        String outputItemId = primaryOutput != null ? primaryOutput.getItemId() : null;
        
        // Check output item exclusion
        if (outputItemId != null && isExcluded(outputItemId)) {
            recipeCache.put(recipeId, "NONE");
            return "NONE";
        }
        
        String tier = null;
        
        // Priority 1: Exact recipe match
        tier = recipeMappings.get(recipeId);
        if (tier != null) {
            recipeCache.put(recipeId, tier);
            return tier;
        }
        
        // Priority 2: Recipe pattern match
        tier = matchPattern(recipeId, recipePatterns, recipeMappings);
        if (tier != null) {
            recipeCache.put(recipeId, tier);
            return tier;
        }
        
        // Priority 3: Exact item match
        if (outputItemId != null) {
            tier = itemMappings.get(outputItemId);
            if (tier != null) {
                recipeCache.put(recipeId, tier);
                return tier;
            }
            
            // Priority 4: Item pattern match
            tier = matchPattern(outputItemId, itemPatterns, itemMappings);
            if (tier != null) {
                recipeCache.put(recipeId, tier);
                return tier;
            }
            
            // Priority 5: Item category match
            tier = matchItemCategory(outputItemId);
            if (tier != null) {
                recipeCache.put(recipeId, tier);
                return tier;
            }
        }
        
        // Priority 6: Bench type match
        tier = matchBenchType(recipe);
        if (tier != null) {
            recipeCache.put(recipeId, tier);
            return tier;
        }
        
        // Priority 7: Auto-classification by complexity
        tier = autoClassify(recipe);
        if (tier != null) {
            recipeCache.put(recipeId, tier);
            return tier;
        }
        
        // Priority 8: Default tier
        recipeCache.put(recipeId, defaultTier);
        return defaultTier;
    }
    
    /**
     * Check if an ID matches any exclusion pattern.
     */
    private boolean isExcluded(String id) {
        // Exact match
        if (exclusions.contains(id)) {
            return true;
        }
        
        // Pattern match
        for (Pattern pattern : exclusionPatterns) {
            if (pattern.matcher(id).matches()) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Match against pre-compiled patterns.
     */
    @Nullable
    private String matchPattern(String id, Map<String, Pattern> patterns, Map<String, String> mappings) {
        for (Map.Entry<String, Pattern> entry : patterns.entrySet()) {
            if (entry.getValue().matcher(id).matches()) {
                return mappings.get(entry.getKey());
            }
        }
        return null;
    }
    
    /**
     * Match item by its categories.
     */
    @Nullable
    private String matchItemCategory(String itemId) {
        try {
            DefaultAssetMap<String, Item> itemMap = Item.getAssetMap();
            if (itemMap == null) return null;
            
            Item item = itemMap.getAsset(itemId);
            if (item == null) return null;
            
            String[] categories = item.getCategories();
            if (categories == null) return null;
            
            // Check each category against mappings
            for (String category : categories) {
                String tier = categoryMappings.get(category);
                if (tier != null) {
                    return tier;
                }
            }
        } catch (Exception e) {
            JobsLogger.debug("[CraftingTierMatcher] Error getting item categories: %s", e.getMessage());
        }
        return null;
    }
    
    /**
     * Match by crafting bench type.
     */
    @Nullable
    private String matchBenchType(CraftingRecipe recipe) {
        BenchRequirement[] requirements = recipe.getBenchRequirement();
        if (requirements == null || requirements.length == 0) {
            // No bench requirement = Fieldcraft
            return benchMappings.get("Fieldcraft");
        }
        
        // Use the highest-tier bench requirement
        String highestTier = null;
        int highestPriority = -1;
        
        for (BenchRequirement req : requirements) {
            String benchTier = benchMappings.get(req.id);
            if (benchTier != null) {
                int priority = getTierPriority(benchTier);
                if (priority > highestPriority) {
                    highestPriority = priority;
                    highestTier = benchTier;
                }
            }
        }
        
        return highestTier;
    }
    
    /**
     * Auto-classify recipe by complexity metrics.
     * 
     * <p>Factors:
     * <ul>
     *   <li>Number of input types</li>
     *   <li>Total input quantity</li>
     *   <li>Crafting time</li>
     *   <li>Required tier level</li>
     * </ul>
     */
    @Nullable
    private String autoClassify(CraftingRecipe recipe) {
        int score = 0;
        
        // Input complexity
        MaterialQuantity[] inputs = recipe.getInput();
        if (inputs != null) {
            score += inputs.length * 10; // +10 per unique input type
            
            int totalQuantity = 0;
            for (MaterialQuantity input : inputs) {
                totalQuantity += input.getQuantity();
            }
            score += totalQuantity; // +1 per total item needed
        }
        
        // Crafting time bonus
        float time = recipe.getTimeSeconds();
        if (time > 0) {
            score += (int)(time * 5); // +5 per second
        }
        
        // Knowledge requirement
        if (recipe.isKnowledgeRequired()) {
            score += 50;
        }
        
        // Memories level requirement
        int memoriesLevel = recipe.getRequiredMemoriesLevel();
        if (memoriesLevel > 1) {
            score += (memoriesLevel - 1) * 25;
        }
        
        // Map score to tier
        if (score < 10) return "TRIVIAL";
        if (score < 30) return "SIMPLE";
        if (score < 60) return "BASIC";
        if (score < 100) return "STANDARD";
        if (score < 150) return "ADVANCED";
        if (score < 250) return "EXPERT";
        if (score < 400) return "MASTER";
        return "LEGENDARY";
    }
    
    /**
     * Get numeric priority for tier (for comparison).
     */
    private int getTierPriority(String tier) {
        return switch (tier) {
            case "NONE" -> 0;
            case "TRIVIAL" -> 1;
            case "SIMPLE" -> 2;
            case "BASIC" -> 3;
            case "STANDARD" -> 4;
            case "ADVANCED" -> 5;
            case "EXPERT" -> 6;
            case "MASTER" -> 7;
            case "LEGENDARY" -> 8;
            default -> 3;
        };
    }
    
    // =========================================================================
    // Pattern Utilities
    // =========================================================================
    
    private static boolean containsWildcard(String pattern) {
        return pattern.contains("*") || pattern.contains("?");
    }
    
    /**
     * Convert wildcard pattern to regex.
     * * -> .*
     * ? -> .
     */
    private static Pattern compileWildcard(String wildcard) {
        StringBuilder regex = new StringBuilder("^");
        for (char c : wildcard.toCharArray()) {
            switch (c) {
                case '*' -> regex.append(".*");
                case '?' -> regex.append(".");
                case '.' -> regex.append("\\.");
                case '+' -> regex.append("\\+");
                case '[' -> regex.append("\\[");
                case ']' -> regex.append("\\]");
                case '(' -> regex.append("\\(");
                case ')' -> regex.append("\\)");
                case '{' -> regex.append("\\{");
                case '}' -> regex.append("\\}");
                case '^' -> regex.append("\\^");
                case '$' -> regex.append("\\$");
                case '|' -> regex.append("\\|");
                case '\\' -> regex.append("\\\\");
                default -> regex.append(c);
            }
        }
        regex.append("$");
        return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE);
    }
    
    // =========================================================================
    // Cache Management
    // =========================================================================
    
    public int getRecipeCacheSize() {
        return recipeCache.size();
    }
    
    public int getItemCacheSize() {
        return itemCache.size();
    }
    
    public void clearCache() {
        recipeCache.clear();
        itemCache.clear();
    }
}
