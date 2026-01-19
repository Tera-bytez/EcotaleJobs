package com.ecotalejobs.util;

import com.ecotale.systems.BalanceHudSystem;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Utility for sending reward notifications to players.
 * The BalanceHudSystem automatically shows balance changes with animations,
 * so we just need to trigger updates after deposits.
 */
public class RewardNotifier {
    
    private static long minRewardToShow = 1;
    private static boolean enabled = true;
    
    /**
     * Configure the notifier settings
     */
    public static void configure(boolean showRewards, double minReward, String notifyFormat) {
        enabled = showRewards;
        minRewardToShow = (long) minReward;
        // Format not currently used - BalanceHudSystem handles display
    }
    
    /**
     * Show a reward notification to a player.
     * The HUD system automatically animates balance changes.
     * 
     * @param playerUuid The player's UUID
     * @param newBalance The player's new balance after reward
     * @param amount The reward amount (for threshold checking)
     * @param source Optional source description (for logging)
     */
    public static void notify(@Nonnull UUID playerUuid, long newBalance, long amount, @Nonnull String source) {
        if (!enabled || amount < minRewardToShow) {
            return;
        }
        
        // Update HUD with new balance - it will animate the change
        BalanceHudSystem.updatePlayerHud(playerUuid, newBalance);
    }
    
    /**
     * Show a notification for dropped coins (not yet picked up).
     * This doesn't update HUD balance since coins aren't in inventory yet.
     * 
     * @param playerUuid The player's UUID
     * @param amount The coin amount dropped
     * @param source Description of the drop source
     */
    public static void notifyDrop(@Nonnull UUID playerUuid, long amount, @Nonnull String source) {
        if (!enabled || amount < minRewardToShow) {
            return;
        }
        
        // For now, just log - coins will update HUD when picked up
        // TODO: Could show a small "Coins dropped!" message via chat or HUD
    }
    
    /**
     * Check if notifications are enabled and amount meets threshold
     */
    public static boolean shouldNotify(long amount) {
        return enabled && amount >= minRewardToShow;
    }
    
    public static long getMinRewardToShow() {
        return minRewardToShow;
    }
}
