# VIP Multiplier System

## Overview

The VIP Multiplier system provides bonus rewards to players with VIP permissions. It works with all EcotaleJobs reward systems (Mob Kills, Mining, Crafting).

## Features

### 1. Coin Multiplier
Increases the final coin amount received.

| Tier | Permission | Multiplier | Bonus |
|------|-----------|------------|-------|
| VIP | `ecotalejobs.multiplier.vip` | 1.20x | +20% coins |
| MVP | `ecotalejobs.multiplier.mvp` | 1.50x | +50% coins |
| MVP+ | `ecotalejobs.multiplier.mvp_plus` | 2.00x | +100% coins |

### 2. Chance Bonus
**INCREASES** the base drop probability. This makes VIP drops more consistent, especially for low-chance tiers.

| Tier | Chance Bonus | Example |
|------|-------------|---------|
| VIP | +5% | Critter (40%) → 45% |
| MVP | +10% | Critter (40%) → 50% |
| MVP+ | +15% | Critter (40%) → 55% |

> **Note:** Chance bonus is **ADDED** to the tier's base drop chance, capped at 100%.

## Required Permissions

All VIP permissions use the prefix `ecotalejobs.multiplier.`:

```
ecotalejobs.multiplier.vip      - Basic VIP (1.2x coins, +5% chance)
ecotalejobs.multiplier.mvp      - MVP tier (1.5x coins, +10% chance)  
ecotalejobs.multiplier.mvp_plus - MVP+ tier (2.0x coins, +15% chance)
```

## LuckPerms Configuration

### Grant VIP to a player:
```
lp user <username> permission set ecotalejobs.multiplier.vip true
```

### Grant VIP to a group:
```
lp group vip permission set ecotalejobs.multiplier.vip true
lp group mvp permission set ecotalejobs.multiplier.mvp true
lp group mvp_plus permission set ecotalejobs.multiplier.mvp_plus true
```

## Configuration (EcotaleJobs.json)

The VIP system is configured in the plugin's config file:

```json
{
  "VipMultipliers": {
    "Enabled": true,
    "MaxGlobalMultiplier": 5.0,
    "Multipliers": {
      "vip": 1.2,
      "mvp": 1.5,
      "mvp_plus": 2.0
    },
    "ChanceBonuses": {
      "vip": 5,
      "mvp": 10,
      "mvp_plus": 15
    }
  }
}
```

## How It Works

1. **Drop Chance**: When a mob dies or ore is mined, the tier's base drop chance is increased by the player's VIP chance bonus.
   - Example: Critter has 40% base chance. A VIP player (+5%) rolls against 45%.

2. **Coin Calculation**: After base coins are calculated, they are multiplied by the VIP multiplier.
   - Example: 2 base coins × 1.2 (VIP) = 2.4 coins

3. **Probabilistic Rounding**: Decimal coins use probabilistic rounding for fairness.
   - Example: 2.4 coins = 2 coins + 40% chance of getting 3 coins instead
   - This ensures VIP benefits are statistically fair even with small amounts

## Verification

After configuring permissions, look for these log messages when debug mode is enabled:

```
SUCCESS: Skeleton_Soldier -> 2 coins (exact=2.40, antiFarm=100%, vip=1.20x)
```

The `vip=1.20x` confirms the VIP multiplier is being applied.

---

## Related Permissions

### Bank Command (EcotaleCoins)
If you're also using EcotaleCoins, don't forget to grant the bank permission:
```
lp group default permission set ecotale.ecotalecoins.command.bank true
```
