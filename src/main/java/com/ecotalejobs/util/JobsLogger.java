package com.ecotalejobs.util;

import com.ecotalejobs.Main;

/**
 * Conditional logging utility for EcotaleJobs.
 * 
 * Only logs when DebugMode is enabled in config.
 * Warnings and errors are always logged regardless of debug mode.
 */
public final class JobsLogger {
    
    private static final String PREFIX = "[EcotaleJobs] ";
    
    private JobsLogger() {}
    
    /**
     * Check if debug mode is enabled in config.
     */
    public static boolean isDebugEnabled() {
        try {
            return Main.CONFIG != null && Main.CONFIG.get().isDebugMode();
        } catch (Exception e) {
            return false; // Default to no debug in production
        }
    }
    
    /**
     * Log a debug message (only if DebugMode is enabled in config).
     */
    public static void debug(String message) {
        if (isDebugEnabled()) {
            System.out.println(PREFIX + message);
        }
    }
    
    /**
     * Log a debug message with format (only if DebugMode is enabled in config).
     */
    public static void debug(String format, Object... args) {
        if (isDebugEnabled()) {
            System.out.println(PREFIX + String.format(format, args));
        }
    }
    
    /**
     * Log an info message (always logged).
     */
    public static void info(String message) {
        System.out.println(PREFIX + message);
    }
    
    /**
     * Log an info message with format (always logged).
     */
    public static void info(String format, Object... args) {
        System.out.println(PREFIX + String.format(format, args));
    }
    
    /**
     * Log a warning (always logged).
     */
    public static void warn(String message) {
        System.out.println(PREFIX + "WARNING: " + message);
    }
    
    /**
     * Log a warning with format (always logged).
     */
    public static void warn(String format, Object... args) {
        System.out.println(PREFIX + "WARNING: " + String.format(format, args));
    }
    
    /**
     * Log an error (always logged).
     */
    public static void error(String message) {
        System.err.println(PREFIX + "ERROR: " + message);
    }
    
    /**
     * Log an error with format (always logged).
     */
    public static void error(String format, Object... args) {
        System.err.println(PREFIX + "ERROR: " + String.format(format, args));
    }
    
    /**
     * Log an error with exception (always logged).
     */
    public static void error(String message, Throwable t) {
        System.err.println(PREFIX + "ERROR: " + message);
        t.printStackTrace();
    }
}


