## ⚠️ Archived Project

This repository has been archived and is no longer receiving public updates on github.

**The code remains available under the MIT License** for use, modification, and distribution according to its terms.

Development continues in a private version. If you're interested in commercial licensing or collaboration, feel free to reach out at @michidevcl on discord

Thank you to everyone who used and contributed to this project! 

# EcotaleJobs - Reward System for Hytale

A jobs and rewards plugin for the Ecotale economy stack. Players earn currency from mining ores, killing mobs, and crafting items.

![Version](https://img.shields.io/badge/version-1.0.0-blue)
![Author](https://img.shields.io/badge/author-Tera--bytez-purple)
![Requires](https://img.shields.io/badge/requires-Ecotale-green)

## Screenshots

### Mining Rewards
![Mining Rewards](docs/screenshots/miningJob.gif)

### Mob Kill Rewards
![Mob Kill Rewards](docs/screenshots/mobsjob.gif)

## Features

### Mining Rewards
- **Tier-based rewards** - Ores classified by rarity (COMMON → LEGENDARY)
- **Auto-classification** - Uses Hytale Family tags to detect ore types
- **Vein Streak System** - Audio feedback and bonus coins for consecutive ore mining
- **Tool Required** - Must use a pickaxe (bare hands give no reward)
- **Tool Quality Bonus** *(pending - waiting for Hytale API)*
- **Depth Bonus** - Deeper mining pays more

### Mob Kill Rewards
- **Automatic NPC classification** - No manual config required for any mob
- **180+ mob patterns** - Wildcard-based classification covers all NPCs
- **Third-party mod support** - Works with ANY mod that adds custom NPCs
- **Threat Score algorithm** - Calculates tier from HP, damage, and aggression
- **Physical coin drops** - When EcotaleCoins is installed
- **Anti-Farm system** - Diminishing returns to prevent abuse

### VIP Multipliers
- **Coin multiplier** - VIPs get 1.2x-2.0x more coins
- **Chance bonus** - VIPs get +5-15% higher drop probability
- **Permission-based** - Works with LuckPerms

### Security & Economy Protection
- **Anti-Farm** - Diminishing returns for repetitive farming
- **Rate Limiting** - Per-player burst protection
- **Economy Cap** - Server-wide hourly injection limit
- **Exclusion Lists** - Block specific mobs/blocks from rewards

---

## Recommended: EcotaleCoins

![EcotaleCoins](docs/screenshots/ecotalecoins_promo.gif)

For enhanced immersion, consider installing **[EcotaleCoins](https://github.com/Tera-bytez/EcotaleCoins)** alongside EcotaleJobs.

### What it adds:
- **Physical loot drops** - Coins spawn as collectible items when mobs die
- **Tangible rewards** - Players physically pick up their earnings from the ground
- **Risk/reward factor** - Coins can be lost on death, adding strategic depth
- **Bank system** - Secure storage with deposit/withdraw via `/bank` command
- **Player trading** - Drop coins to trade with other players in-world

### Integration:
EcotaleJobs automatically detects EcotaleCoins and switches from direct deposits to physical coin drops. No configuration needed.

---

## Installation

1. Install [Ecotale](https://github.com/Tera-bytez/Ecotale) first
2. (Optional) Install [EcotaleCoins](https://github.com/Tera-bytez/EcotaleCoins) for physical coins
3. Download `EcotaleJobs-1.0.0.jar`
4. Place in your Hytale `mods/` folder
5. Restart server

## Configuration

Located in `mods/Ecotale_EcotaleJobs/EcotaleJobs.json`:

```json
{
  "DebugMode": false,
  "MobKills": {
    "Enabled": true,
    "Tiers": {
      "CRITTER": { "CoinType": "COPPER", "MinCoins": 0, "MaxCoins": 1, "DropChance": 40 },
      "PASSIVE": { "CoinType": "COPPER", "MinCoins": 1, "MaxCoins": 2, "DropChance": 100 },
      "HOSTILE": { "CoinType": "COPPER", "MinCoins": 4, "MaxCoins": 10, "DropChance": 100 },
      "ELITE": { "CoinType": "IRON", "MinCoins": 2, "MaxCoins": 5, "DropChance": 100 },
      "MINIBOSS": { "CoinType": "COBALT", "MinCoins": 2, "MaxCoins": 4, "DropChance": 100 },
      "BOSS": { "CoinType": "GOLD", "MinCoins": 1, "MaxCoins": 3, "DropChance": 100 }
    },
    "Security": {
      "MaxRewardsPerMinute": 60,
      "EnableAntiFarm": true,
      "AntiFarmThreshold": 15,
      "AntiFarmDecayPerKill": 0.08
    }
  },
  "Mining": {
    "Enabled": true,
    "Tiers": {
      "NONE": { "MinCoins": 0, "MaxCoins": 0, "DropChance": 0 },
      "COMMON": { "MinCoins": 1, "MaxCoins": 2, "DropChance": 80 },
      "UNCOMMON": { "MinCoins": 2, "MaxCoins": 4, "DropChance": 100 },
      "RARE": { "MinCoins": 4, "MaxCoins": 8, "DropChance": 100 },
      "EPIC": { "CoinType": "IRON", "MinCoins": 1, "MaxCoins": 2, "DropChance": 100 },
      "LEGENDARY": { "CoinType": "IRON", "MinCoins": 2, "MaxCoins": 4, "DropChance": 100 }
    },
    "VeinStreak": {
      "Enabled": true,
      "MaxStreak": 6,
      "TimeoutMs": 3000,
      "BonusEnabled": true,
      "AudioEnabled": true
    }
  },
  "VipMultipliers": {
    "Enabled": true,
    "Multipliers": { "vip": 1.2, "mvp": 1.5, "mvp_plus": 2.0 },
    "ChanceBonuses": { "vip": 5, "mvp": 10, "mvp_plus": 15 }
  }
}
```

## Permissions

### VIP Multipliers

VIP multipliers are permission-based and work with LuckPerms or other permission plugins.

| Permission | Coin Multiplier | Chance Bonus | Example Use |
|------------|----------------|--------------|-------------|
| `ecotalejobs.multiplier.vip` | Configurable (default 1.2x) | Configurable (default +5%) | Basic VIP rank |
| `ecotalejobs.multiplier.mvp` | Configurable (default 1.5x) | Configurable (default +10%) | Premium VIP rank |
| `ecotalejobs.multiplier.mvp_plus` | Configurable (default 2.0x) | Configurable (default +15%) | Elite VIP rank |

> **Custom Permissions:** You can add any permission name in the config's `CoinMultipliers` and `ChanceBonuses` sections. The plugin checks these at runtime.

### Important: LuckPerms Configuration

**EcotaleJobs does NOT automatically give rewards.** VIP multipliers only work if you configure them in LuckPerms:

#### Step 1: Grant VIP Permissions
```bash
# Grant VIP multiplier to a group
/lp group vip permission set ecotalejobs.multiplier.vip true

# Grant MVP multiplier to premium group
/lp group mvp permission set ecotalejobs.multiplier.mvp true

# Grant MVP+ to elite group
/lp group mvp_plus permission set ecotalejobs.multiplier.mvp_plus true
```

#### Step 2: Configure Multipliers in Config
Edit `mods/Ecotale_EcotaleJobs/EcotaleJobsConfig.json`:
```json
"vipMultipliers": {
  "CoinMultipliers": {
    "ecotalejobs.multiplier.vip": 1.2,
    "ecotalejobs.multiplier.mvp": 1.5,
    "ecotalejobs.multiplier.mvp_plus": 2.0
  },
  "ChanceBonuses": {
    "ecotalejobs.multiplier.vip": 5,
    "ecotalejobs.multiplier.mvp": 10,
    "ecotalejobs.multiplier.mvp_plus": 15
  }
}
```

#### Testing
```bash
# Check if a player has VIP multiplier
/lp user <username> permission check ecotalejobs.multiplier.vip

# View all player permissions
/lp user <username> permission info
```

## Tier Classification

### Mining Tiers (auto-detected via Family tags)
| Tier | Ores |
|------|------|
| LEGENDARY | Mithril, Adamantite, Thorium, Onyxium |
| EPIC | Cobalt, Diamond, Emerald |
| RARE | Gold, Silver |
| UNCOMMON | Iron |
| COMMON | Coal, Copper |

### Mob Tiers (wildcard-based classification)
| Tier | Examples |
|------|----------|
| WORLDBOSS | Dragon_Fire, Dragon_Frost |
| BOSS | (Reserved for dungeon bosses) |
| MINIBOSS | Rex_Cave, Werewolf, Shadow_Knight |
| ELITE | Ghoul, Crocodile, Yeti |
| HOSTILE | Zombie, Skeleton_Soldier, Trork |
| PASSIVE | Skeleton, Sheep, Feran |
| CRITTER | Bunny, Chicken, Rat |

## Mod Compatibility

EcotaleJobs is designed to work with **any Hytale mod that adds custom NPCs**.

### How it works:
1. When an unknown NPC is killed, the plugin reads its stats via Hytale's API
2. A **Threat Score** is calculated: `HP + (DMG × 4) × aggression_modifier`
3. The score is mapped to a tier automatically:

| Threat Score | Tier | Example Reward |
|--------------|------|----------------|
| 0-50 | CRITTER | 0-1 Copper |
| 50-100 | PASSIVE | 1-2 Copper |
| 100-300 | HOSTILE | 4-10 Copper |
| 300-700 | ELITE | 2-5 Iron |
| 700-1200 | MINIBOSS | 2-4 Cobalt |
| 1200+ | BOSS | 1-3 Gold |

### Override behavior:
- **Pre-mapped NPCs** (wildcard patterns) use their assigned tier
- **Unknown NPCs** use the Threat Score algorithm
- **Exclusion list** can block specific NPCs from rewards

> **Zero configuration required** - Just install your mod and rewards work automatically!

## Building from Source

**Requirements:** Place JARs in `libs/` folder:
- `hytale-server.jar` (Hytale dedicated server)
- `Ecotale-1.0.0.jar` (from Ecotale project)

```bash
./gradlew jar
```

Output: `build/libs/EcotaleJobs-1.0.0.jar`

## License

MIT License - 2026 Tera-bytez
