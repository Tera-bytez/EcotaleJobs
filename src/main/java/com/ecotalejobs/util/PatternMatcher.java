package com.ecotalejobs.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Utility class for matching IDs against patterns with wildcards.
 * Supports:
 * - "*" matches everything
 * - "*_suffix" matches anything ending with "_suffix"
 * - "prefix_*" matches anything starting with "prefix_"  
 * - "*_middle_*" matches anything containing "_middle_"
 * - "exact" matches exactly "exact"
 */
public class PatternMatcher {
    
    // Cache compiled patterns for performance
    private static final ConcurrentHashMap<String, Pattern> patternCache = new ConcurrentHashMap<>();
    
    /**
     * Check if an ID matches a pattern.
     * @param pattern The pattern (may contain * wildcards)
     * @param id The ID to check
     * @return true if matches
     */
    public static boolean matches(@Nonnull String pattern, @Nonnull String id) {
        if (pattern.equals("*")) {
            return true;
        }
        
        if (!pattern.contains("*")) {
            return pattern.equals(id);
        }
        
        Pattern regex = patternCache.computeIfAbsent(pattern, p -> {
            String escaped = p.replace(".", "\\.")
                              .replace("*", ".*");
            return Pattern.compile("^" + escaped + "$", Pattern.CASE_INSENSITIVE);
        });
        
        return regex.matcher(id).matches();
    }
    
    /**
     * Find the reward for an ID by checking patterns in order.
     * Exact matches are checked first, then patterns.
     * 
     * @param rewards Map of pattern -> reward value
     * @param id The ID to look up
     * @param defaultReward Default value if no match found
     * @return The reward value
     */
    public static long findReward(@Nullable Map<String, Long> rewards, @Nonnull String id, long defaultReward) {
        if (rewards == null || rewards.isEmpty()) {
            return defaultReward;
        }
        
        // Check exact match first (fastest)
        Long exact = rewards.get(id);
        if (exact != null) {
            return exact;
        }
        
        // Check patterns (ordered by specificity would be ideal, but map order works)
        for (Map.Entry<String, Long> entry : rewards.entrySet()) {
            String pattern = entry.getKey();
            if (pattern.contains("*") && matches(pattern, id)) {
                return entry.getValue();
            }
        }
        
        return defaultReward;
    }
    
    /**
     * Clear the pattern cache (useful for config reload)
     */
    public static void clearCache() {
        patternCache.clear();
    }
}
