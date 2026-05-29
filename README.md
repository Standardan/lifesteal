# Lifesteal

A hardcore **Lifesteal SMP** plugin for Paper 1.21+ — the most-played survival genre of 2025.
Kill a player, steal a heart. Hit zero hearts, you're eliminated. Hearts are a tradable
item economy, and admins can revive the fallen.

## Download

**[Download the latest release »](https://github.com/Standardan/lifesteal/releases/latest)**

Drop the `.jar` into your server's `plugins/` folder and restart. Requires Paper 1.21+ (Java 21).

## Features

- **Heart transfer on kill** — the killer gains a heart (up to a cap), the victim loses one
- **Elimination** at zero hearts — `ban` (blocked from rejoining) or `spectator` mode, your choice
- **Heart items** — withdraw a heart into a tradable item (`/withdraw`), right-click to consume one; craftable too
- **Revives** — `/lifesteal revive <player>` restores an eliminated player, even offline
- **Persistent** per-player hearts in SQLite, applied via the `MAX_HEALTH` attribute
- Player-kill only — no farming mobs or suicides for free hearts

## Commands & permissions

| Command | Description | Permission |
|---|---|---|
| `/hearts` | Show your heart count | everyone |
| `/withdraw` | Withdraw a heart as an item | `lifesteal.withdraw` (default: all) |
| `/lifesteal reload` | Reload config | `lifesteal.admin` (op) |
| `/lifesteal sethearts <player> <n>` | Set a player's hearts | `lifesteal.admin` |
| `/lifesteal revive <player>` | Revive an eliminated player | `lifesteal.admin` |

## Configuration (`config.yml`)

```yaml
starting-hearts: 10
max-hearts: 20
min-hearts: 1            # elimination triggers below this
elimination: ban         # ban | spectator
drop-heart-on-elimination: true
```

## Design notes

- **Hearts are cached in memory** (`HeartManager`) so combat math is instant; the database is
  updated asynchronously off the main thread.
- **Items are identified by a PersistentDataContainer tag**, not their name — players can't
  forge a Heart by renaming a Nether Star.
- Elimination runs **one tick after** the death event, since the player is mid-death when the
  kill is processed.

## Building

JDK 21 + Maven. `mvn clean package` → `target/lifesteal-1.0.0.jar`.
