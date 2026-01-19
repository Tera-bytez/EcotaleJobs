package com.ecotalejobs.security;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Anti-farm system with diminishing returns.
 */
public class AntiFarmSystem {
    
    // Track per-player kill patterns
    private final ConcurrentHashMap<UUID, PlayerKillTracker> trackers = new ConcurrentHashMap<>();
    
    // Config
    private int sameTypeThreshold = 10;      // Kills before decay starts
    private float decayPerKill = 0.1f;       // 10% reduction per kill over threshold
    private float minimumMultiplier = 0.1f;  // Never go below 10%
    private long trackerTtlMs = 30 * 60 * 1000; // 30 minutes TTL
    private int maxTrackers = 1000;          // Memory bound
    
    private boolean enabled = true;
    
    /**
     * Configure the anti-farm system.
     */
    public void configure(int sameTypeThreshold, float decayPerKill, 
                         float minimumMultiplier, long trackerTtlMinutes, boolean enabled) {
        this.sameTypeThreshold = sameTypeThreshold;
        this.decayPerKill = decayPerKill;
        this.minimumMultiplier = minimumMultiplier;
        this.trackerTtlMs = trackerTtlMinutes * 60 * 1000;
        this.enabled = enabled;
    }
    
    /**
     * Get the reward multiplier for a kill.
     * Records the kill and returns a multiplier based on farming patterns.
     * 
     * @param playerUuid The player's UUID
     * @param mobType The mob type being killed
     * @return Multiplier between minimumMultiplier and 1.0
     */
    public float getMultiplierAndRecord(UUID playerUuid, String mobType) {
        if (!enabled) {
            return 1.0f;
        }
        
        PlayerKillTracker tracker = getOrCreateTracker(playerUuid);
        return tracker.recordKillAndGetMultiplier(mobType);
    }
    
    /**
     * Get current multiplier without recording a kill.
     */
    public float peekMultiplier(UUID playerUuid, String mobType) {
        if (!enabled) {
            return 1.0f;
        }
        
        PlayerKillTracker tracker = trackers.get(playerUuid);
        if (tracker == null) {
            return 1.0f;
        }
        return tracker.peekMultiplier(mobType);
    }
    
    /**
     * Get or create a tracker for a player.
     * Enforces memory bounds.
     */
    private PlayerKillTracker getOrCreateTracker(UUID playerUuid) {
        // Check if already exists
        PlayerKillTracker existing = trackers.get(playerUuid);
        if (existing != null) {
            existing.touch();
            return existing;
        }
        
        // Enforce memory bound
        if (trackers.size() >= maxTrackers) {
            cleanup();
            if (trackers.size() >= maxTrackers) {
                // Still full, evict oldest
                evictOldest();
            }
        }
        
        return trackers.computeIfAbsent(playerUuid, 
            k -> new PlayerKillTracker(sameTypeThreshold, decayPerKill, minimumMultiplier));
    }
    
    /**
     * Cleanup expired trackers.
     * Call periodically (e.g., every 5 minutes).
     * 
     * @return Number of trackers removed
     */
    public int cleanup() {
        long now = System.currentTimeMillis();
        int beforeSize = trackers.size();
        trackers.entrySet().removeIf(entry -> 
            now - entry.getValue().getLastActivity() > trackerTtlMs);
        return beforeSize - trackers.size();
    }
    
    /**
     * Evict the oldest tracker when at capacity.
     */
    private void evictOldest() {
        long oldestTime = Long.MAX_VALUE;
        UUID oldestKey = null;
        
        for (var entry : trackers.entrySet()) {
            if (entry.getValue().getLastActivity() < oldestTime) {
                oldestTime = entry.getValue().getLastActivity();
                oldestKey = entry.getKey();
            }
        }
        
        if (oldestKey != null) {
            trackers.remove(oldestKey);
        }
    }
    
    /**
     * Get number of active trackers (for monitoring).
     */
    public int getActiveTrackerCount() {
        return trackers.size();
    }
    
    /**
     * Reset a player's tracker (admin command).
     */
    public void resetPlayer(UUID playerUuid) {
        trackers.remove(playerUuid);
    }
    
    /**
     * Per-player kill tracker.
     * Tracks recent kills of each mob type.
     */
    private static class PlayerKillTracker {
        private final ConcurrentHashMap<String, AtomicInteger> killCounts = new ConcurrentHashMap<>();
        private volatile long lastActivity;
        
        private final int threshold;
        private final float decayPerKill;
        private final float minimumMultiplier;
        
        // Decay window - counts reset after this time
        private static final long DECAY_WINDOW_MS = 5 * 60 * 1000; // 5 minutes
        private volatile long windowStart;
        
        PlayerKillTracker(int threshold, float decayPerKill, float minimumMultiplier) {
            this.threshold = threshold;
            this.decayPerKill = decayPerKill;
            this.minimumMultiplier = minimumMultiplier;
            this.lastActivity = System.currentTimeMillis();
            this.windowStart = this.lastActivity;
        }
        
        /**
         * Record a kill and get the multiplier.
         */
        float recordKillAndGetMultiplier(String mobType) {
            touch();
            maybeResetWindow();
            
            AtomicInteger count = killCounts.computeIfAbsent(mobType, k -> new AtomicInteger(0));
            int kills = count.incrementAndGet();
            
            return calculateMultiplier(kills);
        }
        
        /**
         * Peek at multiplier without recording.
         */
        float peekMultiplier(String mobType) {
            maybeResetWindow();
            
            AtomicInteger count = killCounts.get(mobType);
            int kills = count != null ? count.get() : 0;
            
            return calculateMultiplier(kills);
        }
        
        /**
         * Calculate multiplier based on kill count.
         */
        private float calculateMultiplier(int kills) {
            if (kills <= threshold) {
                return 1.0f;
            }
            
            int excessKills = kills - threshold;
            float reduction = excessKills * decayPerKill;
            float multiplier = 1.0f - reduction;
            
            return Math.max(minimumMultiplier, multiplier);
        }
        
        /**
         * Reset counts if window expired.
         */
        private void maybeResetWindow() {
            long now = System.currentTimeMillis();
            if (now - windowStart > DECAY_WINDOW_MS) {
                killCounts.clear();
                windowStart = now;
            }
        }
        
        void touch() {
            lastActivity = System.currentTimeMillis();
        }
        
        long getLastActivity() {
            return lastActivity;
        }
    }
}
