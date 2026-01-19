package com.ecotalejobs.util;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * High-performance tier matcher with O(1) cache for known mob IDs.
 * 
 * <p>Pattern priority (processed in this order):
 * <ol>
 *   <li>Exact match in tier mappings</li>
 *   <li>Cached result from previous lookup</li>
 *   <li>Pattern match (wildcards, sorted by specificity)</li>
 *   <li>Auto-categorization by suffix/content</li>
 *   <li>Default tier</li>
 * </ol>
 * 
 * <p>Thread-safety: All public methods are thread-safe.
 * 
 * @author EcotaleJobs Team
 * @since 1.0.0
 */
public class TierMatcher {
    
    // =========================================================================
    // Constants
    // =========================================================================
    
    /** Tier name for excluded mobs (no reward) */
    public static final String TIER_NONE = "NONE";
    
    /** Maximum cache entries to prevent memory bloat */
    private static final int MAX_CACHE_SIZE = 2000;
    
    // =========================================================================
    // State
    // =========================================================================
    
    /** O(1) cache: mobId -> tierName (for repeat lookups) */
    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();
    
    /** Exact mob ID to tier mappings (no wildcards) */
    private volatile Map<String, String> exactMappings = new ConcurrentHashMap<>();
    
    /** Wildcard patterns, sorted by specificity (most specific first) */
    private volatile List<PatternEntry> wildcardPatterns = new ArrayList<>();
    
    /** Mobs that should never give rewards */
    private volatile Set<String> exclusions = ConcurrentHashMap.newKeySet();
    
    /** Fallback tier when no match found */
    private volatile String defaultTier = "HOSTILE";
    
    // =========================================================================
    // Initialization
    // =========================================================================
    
    public TierMatcher() {}
    
    /**
     * Configure the matcher with tier mappings.
     * Splits mappings into exact matches and wildcard patterns.
     * Call this after loading config.
     * 
     * @param mappings Mob ID patterns to tier names
     * @param exclusions Mob IDs to exclude from rewards
     * @param defaultTier Fallback tier for unmatched mobs
     */
    public void configure(
        @Nullable Map<String, String> mappings, 
        @Nullable Set<String> exclusions, 
        @Nullable String defaultTier
    ) {
        // Clear caches first
        this.cache.clear();
        
        // Set default tier
        this.defaultTier = (defaultTier != null) ? defaultTier : "HOSTILE";
        
        // Set exclusions
        this.exclusions = (exclusions != null) 
            ? new HashSet<>(exclusions) 
            : new HashSet<>();
        
        // Split mappings into exact and pattern-based
        Map<String, String> exact = new ConcurrentHashMap<>();
        List<PatternEntry> patterns = new ArrayList<>();
        
        if (mappings != null) {
            for (Map.Entry<String, String> entry : mappings.entrySet()) {
                String key = entry.getKey();
                String tier = entry.getValue();
                
                if (key.contains("*")) {
                    // Wildcard pattern
                    patterns.add(new PatternEntry(key, tier));
                } else {
                    // Exact match
                    exact.put(key, tier);
                }
            }
        }
        
        // Sort patterns by specificity (most specific first)
        // Specificity = length minus wildcards, higher = more specific
        patterns.sort((a, b) -> Integer.compare(b.specificity, a.specificity));
        
        this.exactMappings = exact;
        this.wildcardPatterns = patterns;
    }
    
    // =========================================================================
    // Core API
    // =========================================================================
    
    /**
     * Find the tier for a mob ID.
     * O(1) for cached/exact IDs, O(n) for first pattern lookup (then cached).
     * 
     * @param mobId The mob's type ID (e.g., "Trork_Warrior")
     * @return The tier name (e.g., "HOSTILE", "ELITE", "BOSS", or "NONE" for excluded)
     */
    @Nonnull
    public String findTier(@Nonnull String mobId) {
        // 1. Check exclusions first (O(1) HashSet)
        if (exclusions.contains(mobId)) {
            return TIER_NONE;
        }
        
        // 2. Check exact match (O(1) HashMap)
        String exact = exactMappings.get(mobId);
        if (exact != null) {
            return exact;
        }
        
        // 3. Check cache (O(1) - handles 99%+ of calls after warmup)
        String cached = cache.get(mobId);
        if (cached != null) {
            return cached;
        }
        
        // 4. Compute tier via patterns/inference (O(n) - only on first encounter)
        String tier = computeTier(mobId);
        
        // 5. Cache result (with size limit)
        cacheResult(mobId, tier);
        
        return tier;
    }
    
    /**
     * Check if a mob is in the exclusion list.
     * Faster than findTier() when you only need exclusion check.
     */
    public boolean isExcluded(@Nonnull String mobId) {
        return exclusions.contains(mobId);
    }
    
    // =========================================================================
    // Tier Computation (slow path)
    // =========================================================================
    
    /**
     * Compute the tier for a mob ID by checking patterns.
     * This is the slow path - only called on cache miss.
     */
    @Nonnull
    private String computeTier(@Nonnull String mobId) {
        // Priority 1: Pattern matching (sorted by specificity)
        for (PatternEntry entry : wildcardPatterns) {
            if (entry.matches(mobId)) {
                return entry.tierName;
            }
        }
        
        // Priority 2: Auto-categorization by name analysis
        String inferred = inferTierFromName(mobId);
        if (inferred != null) {
            return inferred;
        }
        
        // Priority 3: Default tier
        return defaultTier;
    }
    
    /**
     * Infer tier from mob name patterns.
     * Handles new mobs without explicit configuration.
     * 
     * <p>This provides sensible defaults for mobs added in future updates,
     * reducing the need for constant config updates.
     */
    @Nullable
    private String inferTierFromName(@Nonnull String mobId) {
        String lower = mobId.toLowerCase();
        
        // === WORLD BOSSES (highest tier) ===
        if (lower.contains("dragon") || lower.contains("titan") || 
            lower.contains("colossus") || lower.contains("ancient_")) {
            return "WORLDBOSS";
        }
        
        // === BOSSES ===
        if (lower.contains("broodmother") || lower.contains("_boss") ||
            lower.startsWith("boss_") || lower.contains("overlord")) {
            return "BOSS";
        }
        
        // === MINIBOSSES (named leaders) ===
        if (lower.endsWith("_chieftain") || lower.endsWith("_duke") ||
            lower.endsWith("_king") || lower.endsWith("_queen") ||
            lower.endsWith("_lord") || lower.endsWith("_captain") ||
            lower.endsWith("_champion")) {
            return "MINIBOSS";
        }
        
        // === ELITE (special variants) ===
        if (lower.endsWith("_elder") || lower.endsWith("_alpha") ||
            lower.endsWith("_knight") || lower.endsWith("_mage") ||
            lower.endsWith("_shaman") || lower.endsWith("_priest") ||
            lower.startsWith("golem_") || lower.contains("_elite")) {
            return "ELITE";
        }
        
        // === CRITTERS (small creatures, young) ===
        if (lower.endsWith("_cub") || lower.endsWith("_baby") ||
            lower.endsWith("_seedling") || lower.endsWith("_sapling") ||
            lower.endsWith("_hatchling") || lower.endsWith("_pup") ||
            lower.equals("bunny") || lower.equals("mouse") ||
            lower.equals("squirrel") || lower.equals("gecko")) {
            return "CRITTER";
        }
        
        // === PASSIVE (farm animals) ===
        if (lower.equals("chicken") || lower.equals("cow") || 
            lower.equals("pig") || lower.equals("sheep") ||
            lower.equals("goat") || lower.equals("horse")) {
            return "PASSIVE";
        }
        
        // No inference possible - will use default
        return null;
    }
    
    /**
     * Cache a computed result with size limiting.
     * Uses putIfAbsent to avoid overwrites in concurrent scenarios.
     */
    private void cacheResult(@Nonnull String mobId, @Nonnull String tier) {
        // Simple size check (not atomic, but close enough for cache limiting)
        if (cache.size() < MAX_CACHE_SIZE) {
            cache.putIfAbsent(mobId, tier);
        }
    }
    
    // =========================================================================
    // Maintenance
    // =========================================================================
    
    /** Clear the tier cache (for testing or config reload) */
    public void clearCache() {
        cache.clear();
    }
    
    /** Get current cache size (for monitoring) */
    public int getCacheSize() {
        return cache.size();
    }
    
    /** Get the default tier */
    @Nonnull
    public String getDefaultTier() {
        return defaultTier;
    }
    
    /** Get number of configured wildcard patterns */
    public int getPatternCount() {
        return wildcardPatterns.size();
    }
    
    /** Get number of exact mappings */
    public int getExactMappingCount() {
        return exactMappings.size();
    }
    
    // =========================================================================
    // Inner Classes
    // =========================================================================
    
    /**
     * Pre-compiled wildcard pattern entry.
     * Caches the regex and specificity for fast matching.
     */
    private static class PatternEntry {
        final String originalPattern;
        final String tierName;
        final Pattern regex;
        final int specificity; // Higher = more specific (should match first)
        
        PatternEntry(String pattern, String tierName) {
            this.originalPattern = pattern;
            this.tierName = tierName;
            this.regex = compileWildcard(pattern);
            // Specificity: pattern length minus number of wildcards
            this.specificity = pattern.length() - countChar(pattern, '*') * 2;
        }
        
        boolean matches(String mobId) {
            return regex.matcher(mobId).matches();
        }
        
        @Override
        public String toString() {
            return originalPattern + " -> " + tierName;
        }
        
        private static Pattern compileWildcard(String wildcardPattern) {
            String regex = wildcardPattern
                .replace(".", "\\.")
                .replace("*", ".*");
            return Pattern.compile("^" + regex + "$", Pattern.CASE_INSENSITIVE);
        }
        
        private static int countChar(String s, char c) {
            int count = 0;
            for (int i = 0; i < s.length(); i++) {
                if (s.charAt(i) == c) count++;
            }
            return count;
        }
    }
}
