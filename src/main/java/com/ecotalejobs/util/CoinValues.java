package com.ecotalejobs.util;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;

/**
 * Coin values for reward calculations.
 * 
 * EcotaleJobs uses these values to calculate total reward amounts
 * independently of whether EcotaleCoins addon is installed.
 * 
 * When EcotaleCoins IS installed: Coins are dropped as physical items
 * When NOT installed: Value is deposited directly to player balance
 * 
 * @author Ecotale
 * @since 1.1.0
 */
public final class CoinValues {
    
    private CoinValues() {}
    
    /** Coin type values in base units */
    private static final Map<String, Long> VALUES = new HashMap<>();
    
    static {
        VALUES.put("COPPER", 1L);
        VALUES.put("IRON", 10L);
        VALUES.put("COBALT", 100L);
        VALUES.put("GOLD", 1_000L);
        VALUES.put("MITHRIL", 10_000L);
        VALUES.put("ADAMANTITE", 100_000L);
    }
    
    /**
     * Get the value of a coin type in base units.
     * 
     * @param coinTypeName Coin type name (e.g., "COPPER", "GOLD")
     * @return Value in base units, defaults to 1 if unknown
     */
    public static long getValue(@Nonnull String coinTypeName) {
        Long value = VALUES.get(coinTypeName.toUpperCase());
        return value != null ? value : 1L;
    }
    
    /**
     * Check if a coin type name is valid.
     * 
     * @param coinTypeName Coin type name
     * @return true if known coin type
     */
    public static boolean isValidCoinType(@Nonnull String coinTypeName) {
        return VALUES.containsKey(coinTypeName.toUpperCase());
    }
    
    /**
     * Get all valid coin type names.
     * 
     * @return Set of coin type names
     */
    public static java.util.Set<String> getCoinTypes() {
        return VALUES.keySet();
    }
}
