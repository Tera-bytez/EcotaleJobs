package com.ecotalejobs.config;

import com.ecotalejobs.util.CoinValues;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Single reward tier config: CoinType, MinCoins, MaxCoins, DropChance.
 */
public class TierConfig {
    
    /** Hytale codec for JSON serialization */
    public static final BuilderCodec<TierConfig> CODEC = BuilderCodec.builder(TierConfig.class, TierConfig::new)
        .append(new KeyedCodec<>("CoinType", Codec.STRING),
            (c, v, e) -> c.setCoinTypeName(v), (c, e) -> c.coinTypeName).add()
        .append(new KeyedCodec<>("MinCoins", Codec.INTEGER),
            (c, v, e) -> c.minCoins = v, (c, e) -> c.minCoins).add()
        .append(new KeyedCodec<>("MaxCoins", Codec.INTEGER),
            (c, v, e) -> c.maxCoins = v, (c, e) -> c.maxCoins).add()
        .append(new KeyedCodec<>("DropChance", Codec.INTEGER),
            (c, v, e) -> c.dropChance = v, (c, e) -> c.dropChance).add()
        .build();
    
    // =========================================================================
    // Fields
    // =========================================================================
    
    private String coinTypeName = "COPPER";
    private int minCoins = 1;
    private int maxCoins = 1;
    private int dropChance = 100; // 0-100 percent
    
    // =========================================================================
    // Constructors
    // =========================================================================
    
    /** Default constructor for codec deserialization */
    public TierConfig() {}
    
    /**
     * Create a tier configuration.
     * 
     * @param coinType Coin denomination name (e.g., "COPPER", "GOLD")
     * @param min Minimum coins to drop (0+ for chance, 1+ for guaranteed)
     * @param max Maximum coins to drop (must be >= min)
     * @param chance Drop chance percentage (0-100)
     */
    public TierConfig(@Nonnull String coinType, int min, int max, int chance) {
        this.coinTypeName = coinType;
        this.minCoins = Math.max(0, min);
        this.maxCoins = Math.max(min, max);
        this.dropChance = Math.max(0, Math.min(100, chance));
    }
    
    // =========================================================================
    // Getters
    // =========================================================================
    
    /** Raw coin type name as stored in config */
    @Nonnull
    public String getCoinTypeName() {
        return coinTypeName;
    }
    
    /**
     * Get the value of one coin of this tier's type.
     * Uses local CoinValues lookup.
     * 
     * @return Value in base units (copper = 1)
     */
    public long getCoinValue() {
        return CoinValues.getValue(coinTypeName);
    }
    
    public int getMinCoins() {
        return minCoins;
    }
    
    public int getMaxCoins() {
        return maxCoins;
    }
    
    public int getDropChance() {
        return dropChance;
    }
    
    // =========================================================================
    // Setters (invalidate cache)
    // =========================================================================
    
    /** Set coin type name */
    public void setCoinTypeName(@Nonnull String name) {
        this.coinTypeName = name;
    }
    
    // =========================================================================
    // Computed Properties
    // =========================================================================
    
    /**
     * Calculate the maximum possible value this tier can drop.
     * Useful for economy impact analysis.
     * 
     * @return Max value in base currency units
     */
    public long getMaxValue() {
        return (long) maxCoins * getCoinValue();
    }
    
    /**
     * Calculate the expected average value per drop.
     * Assumes uniform distribution of coins and considers drop chance.
     * 
     * @return Expected value per mob kill
     */
    public double getExpectedValue() {
        double avgCoins = (minCoins + maxCoins) / 2.0;
        double chanceMultiplier = dropChance / 100.0;
        return avgCoins * getCoinValue() * chanceMultiplier;
    }
    
    // =========================================================================
    // Validation
    // =========================================================================
    
    /**
     * Validate this tier configuration.
     * 
     * @return Error message if invalid, null if valid
     */
    @Nullable
    public String validate() {
        if (coinTypeName == null || coinTypeName.isEmpty()) {
            return "CoinType cannot be empty";
        }
        if (minCoins < 0) {
            return "MinCoins cannot be negative";
        }
        if (maxCoins < minCoins) {
            return "MaxCoins (" + maxCoins + ") cannot be less than MinCoins (" + minCoins + ")";
        }
        if (maxCoins > 1000) {
            return "MaxCoins exceeds safety limit (1000)";
        }
        if (dropChance < 0 || dropChance > 100) {
            return "DropChance must be 0-100, got: " + dropChance;
        }
        
        // Economy safety check
        long maxValue = getMaxValue();
        if (maxValue > 10_000_000) { // 1000 gold (extreme, likely config error)
            return "Tier max value (" + maxValue + ") exceeds economy safety limit";
        }
        
        return null; // Valid
    }
    
    /**
     * Check if this tier is valid.
     * @return true if validation passes
     */
    public boolean isValid() {
        return validate() == null;
    }
    
    // =========================================================================
    // Object Methods
    // =========================================================================
    
    @Override
    public String toString() {
        return String.format("TierConfig{%s x%d-%d @ %d%% (avg=%.1f)}", 
            coinTypeName, minCoins, maxCoins, dropChance, getExpectedValue());
    }
}
