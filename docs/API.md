# EcotaleJobs API Documentation

## Overview

EcotaleJobs provides configurable rewards for mining and mob killing activities.

## Events

### BlockRewardEvent

Fired when a player receives a mining reward.

```java
import com.ecotalejobs.events.BlockRewardEvent;

EventManager.register(BlockRewardEvent.class, event -> {
    Player player = event.getPlayer();
    String blockId = event.getBlockId();
    double reward = event.getReward();
    
    // Modify reward
    event.setReward(reward * 2.0);
    
    // Cancel reward
    event.setCancelled(true);
});
```

### MobRewardEvent

Fired when a player receives a mob kill reward.

```java
import com.ecotalejobs.events.MobRewardEvent;

EventManager.register(MobRewardEvent.class, event -> {
    Player player = event.getPlayer();
    String mobId = event.getMobId();
    MobTier tier = event.getTier();
    double reward = event.getReward();
    
    // Modify reward based on tier
    if (tier == MobTier.BOSS) {
        event.setReward(reward * 1.5);
    }
});
```

## Configuration Access

```java
import com.ecotalejobs.config.JobsConfig;

JobsConfig config = JobsPlugin.getInstance().getConfig();

// Check if features enabled
boolean miningEnabled = config.isEnableMiningRewards();
boolean mobsEnabled = config.isEnableMobRewards();

// Get vein streak settings
boolean streakEnabled = config.isVeinStreakEnabled();
double streakMultiplier = config.getVeinStreakMultiplier();
double streakMaxBonus = config.getVeinStreakMaxBonus();
```

## MobTier Enum

```java
public enum MobTier {
    PASSIVE,
    COMMON,
    UNCOMMON,
    RARE,
    BOSS;
    
    public double getBaseReward();
}
```
