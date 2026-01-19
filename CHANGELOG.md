# Changelog

All notable changes to EcotaleJobs will be documented in this file.

## [1.0.0] - 2026-01-18

### First Release

Complete reward system for Hytale economy.

### Features

#### Mining System
- **Tier-based ore classification** using Hytale Family tags
- **Auto-detection** of ore types (Copper, Iron, Gold, Diamond, etc.)
- **Vein Streak** system with audio feedback and bonus coins
- **Tool Quality** multiplier (better tools = more rewards)
- **Depth Bonus** multiplier (deeper mining = more rewards)
- Plain rocks excluded (only ores give rewards)

#### Mob Kill System
- **897 NPCs pre-mapped** to reward tiers
- **7 reward tiers**: CRITTER, PASSIVE, HOSTILE, ELITE, MINIBOSS, BOSS, WORLDBOSS
- **DeathComponent detection** - correct Hytale ECS pattern
- **Killer validation** - only players get rewards

#### VIP Multipliers
- **Coin multipliers**: VIP 1.2x, MVP 1.5x, MVP+ 2.0x
- **Chance bonuses**: VIP +5%, MVP +10%, MVP+ +15%
- **Permission-based** with LuckPerms support
- **Probabilistic rounding** for fair decimal handling

#### Security
- **Anti-Farm system** - diminishing returns for repetitive farming
- **Rate Limiting** - token bucket per player
- **Economy Cap** - server-wide injection limit per hour
- **Exclusion lists** - block specific mobs/blocks

#### Integration
- Full **Ecotale API** integration
- **EcotaleCoins** support for physical coin drops
- **LuckPerms** compatible permissions

### Technical
- Thread-safe with AtomicLong counters
- ThreadLocalRandom for performance
- Minimal allocations in hot paths
- Optimized for 500+ concurrent players
