package com.ecotalejobs.security;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Global economy injection cap.
 * Prevents inflation by limiting total rewards injected per hour.
 * 
 * Uses lock-free AtomicLong for high concurrency.
 */
public class EconomyCap {
    
    private final AtomicLong hourlyInjection = new AtomicLong(0);
    private volatile long hourStart = System.currentTimeMillis();
    
    private long maxHourlyInjection = 10_000_000; // 10M units = 1000 Gold/hour default
    private boolean enabled = true;
    
    /**
     * Configure the economy cap.
     */
    public void configure(long maxHourlyInjection, boolean enabled) {
        this.maxHourlyInjection = maxHourlyInjection;
        this.enabled = enabled;
    }
    
    /**
     * Try to inject value into the economy.
     * 
     * @param value The value in base units (copper)
     * @return true if allowed, false if cap reached
     */
    public boolean tryInject(long value) {
        if (!enabled) {
            return true;
        }
        
        maybeResetHour();
        
        // CAS loop to atomically check and add
        while (true) {
            long current = hourlyInjection.get();
            if (current + value > maxHourlyInjection) {
                return false; // Cap reached
            }
            
            if (hourlyInjection.compareAndSet(current, current + value)) {
                return true;
            }
            // CAS failed, retry
        }
    }
    
    /**
     * Reset counter if we're in a new hour.
     */
    private void maybeResetHour() {
        long now = System.currentTimeMillis();
        if (now - hourStart > 3600_000) { // 1 hour
            synchronized (this) {
                if (now - hourStart > 3600_000) {
                    hourlyInjection.set(0);
                    hourStart = now;
                }
            }
        }
    }
    
    /**
     * Get current hourly injection (for monitoring).
     */
    public long getCurrentHourlyInjection() {
        return hourlyInjection.get();
    }
    
    /**
     * Get remaining capacity this hour.
     */
    public long getRemainingCapacity() {
        return Math.max(0, maxHourlyInjection - hourlyInjection.get());
    }
    
    /**
     * Get max hourly injection limit.
     */
    public long getMaxHourlyInjection() {
        return maxHourlyInjection;
    }
    
    /**
     * Check if cap is enabled.
     */
    public boolean isEnabled() {
        return enabled;
    }
}
