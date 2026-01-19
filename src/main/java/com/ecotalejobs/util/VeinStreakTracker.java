package com.ecotalejobs.util;

import com.ecotalejobs.Main;
import com.ecotalejobs.config.EcotaleJobsConfig.VeinStreakConfig;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks consecutive ore mining streaks per player.
 * Streak resets completely after timeout (no gradual decay).
 */
public class VeinStreakTracker {
    
    private static final VeinStreakTracker INSTANCE = new VeinStreakTracker();
    
    public static VeinStreakTracker getInstance() {
        return INSTANCE;
    }
    
    private final ConcurrentHashMap<UUID, PlayerStreak> streaks = new ConcurrentHashMap<>();
    private static final int MAX_TRACKERS = 1000;
    
    private VeinStreakTracker() {}
    
    /**
     * Record an ore mine and get current streak level.
     * Resets streak if timeout has passed.
     */
    public int recordOreAndGetStreak(UUID playerUuid) {
        VeinStreakConfig config = getConfig();
        if (config == null || !config.isEnabled()) {
            return 0;
        }
        
        PlayerStreak streak = getOrCreateStreak(playerUuid);
        return streak.recordAndGet(config);
    }
    
    /**
     * Peek at current streak without recording.
     */
    public int peekStreak(UUID playerUuid) {
        PlayerStreak streak = streaks.get(playerUuid);
        if (streak == null) return 0;
        
        VeinStreakConfig config = getConfig();
        if (config == null) return 0;
        
        return streak.peek(config);
    }
    
    private PlayerStreak getOrCreateStreak(UUID playerUuid) {
        if (streaks.size() >= MAX_TRACKERS && !streaks.containsKey(playerUuid)) {
            cleanup();
        }
        return streaks.computeIfAbsent(playerUuid, k -> new PlayerStreak());
    }
    
    /**
     * Cleanup expired trackers (5 minutes of inactivity).
     */
    public int cleanup() {
        long now = System.currentTimeMillis();
        long ttl = 5 * 60 * 1000; // 5 minutes
        int before = streaks.size();
        streaks.entrySet().removeIf(e -> now - e.getValue().lastActivity > ttl);
        return before - streaks.size();
    }
    
    /**
     * Reset a player's streak.
     */
    public void resetPlayer(UUID playerUuid) {
        streaks.remove(playerUuid);
    }
    
    private VeinStreakConfig getConfig() {
        try {
            return Main.CONFIG.get().getMining().getVeinStreak();
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Per-player streak data with timeout reset.
     */
    private static class PlayerStreak {
        private int currentStreak = 0;
        private long lastOreTime = 0;
        private long lastActivity = System.currentTimeMillis();
        
        /**
         * Record an ore mine and return the new streak level.
         * Resets if timeout has passed since last ore.
         */
        synchronized int recordAndGet(VeinStreakConfig config) {
            long now = System.currentTimeMillis();
            lastActivity = now;
            
            // Check for timeout - full reset
            if (lastOreTime > 0 && (now - lastOreTime) > config.getTimeoutMs()) {
                currentStreak = 0;
            }
            
            // Increment streak
            currentStreak = Math.min(currentStreak + 1, config.getMaxStreak());
            lastOreTime = now;
            
            return currentStreak;
        }
        
        /**
         * Peek at streak (returns 0 if timed out).
         */
        synchronized int peek(VeinStreakConfig config) {
            long now = System.currentTimeMillis();
            if (lastOreTime > 0 && (now - lastOreTime) > config.getTimeoutMs()) {
                return 0;
            }
            return currentStreak;
        }
    }
}
