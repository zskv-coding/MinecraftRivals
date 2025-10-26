# Minecraft Rivals

## Features

### Implemented Features

- **Custom Team System** using Minecraft's default scoreboard teams
  - 8 visible teams: Red, Orange, Yellow, Lime, Green, Blue, Purple, Pink
  - 2 backlog teams: Cyan, Magenta (available but not shown by default)
  - Team colors with prefixes in chat and tablist
  - Team size limits (configurable, default: 5 players per team)
  - No friendly fire between team members
  - Team members can see each other when invisible

- **Unified Command System** - All commands use `/mcr <subcommand>`
  - `/mcr join <team>` - Join a team
  - `/mcr leave` - Leave your current team
  - `/mcr teams` - List all available teams with player counts
  - `/mcr help` - Show help message
  - `/mcr start` - Start the game (admin only)
  - `/mcr stop` - Stop the game (admin only)
  - `/mcr reload` - Reload configuration (admin only)

- **Smart Tab Completion**
  - Auto-completes subcommands
  - Auto-completes team names when joining
  - Only shows teams that aren't full
  - Shows admin commands only to players with permission
  - Filters suggestions based on what you're typing

- **Custom Scoreboard**
  - Shows your current team
  - Displays team standings with points
  - Updates automatically (configurable interval)
  - Customizable title and format

- **Custom Tablist**
  - Teams grouped together with their scores
  - Format: Team name → Team score (gold) → Player list
  - Online players shown in team colors
  - Offline players shown in gray
  - Uses custom `pixel_uppercase` font (requires resource pack)
  - Real-time updates every second (configurable)
  - Automatically updates when players join/leave teams or server

- **Configuration System**
  - All messages customizable
  - Team settings configurable
  - Game settings (max players, team sizes, etc.)
  - Scoreboard and tablist customization
  - Color code support with `&` character

## Configuration

The plugin uses `config.yml` for all settings. Here's what you can configure:

### Game Settings
```yaml
game:
  max-players: 40
  min-players-per-team: 1
  max-players-per-team: 5
  round-time: 300  # seconds
  lobby-time: 60   # seconds before game starts
```

### Teams
```yaml
teams:
  visible: [Red, Orange, Yellow, Lime, Green, Blue, Purple, Pink]
  backlog: [Cyan, Magenta]  # Available but not shown by default
```

### Scoreboard
```yaml
scoreboard:
  title: "§6§lMINECRAFT RIVALS"
  update-interval: 20  # ticks (1 second = 20 ticks)
```

### Tablist
```yaml
tablist:
  footer: "&7Good luck and have fun!"
  update-interval: 20  # ticks (1 second = 20 ticks)
```
The tablist displays teams grouped together with their scores.
Uses custom `pixel_uppercase` font (requires resource pack).
Note: Use `&` for color codes in config.yml (automatically converted to `§`)

### Messages
All messages are customizable with color codes:
```yaml
messages:
  prefix: "§6§l[MCR]§r "
  team-join: "§aYou joined team %team%!"
  team-leave: "§cYou left team %team%!"
  team-full: "§cThat team is full!"
  no-permission: "§cYou don't have permission to do that!"
  game-start: "§a§lGame starting!"
  game-stop: "§c§lGame stopped!"
```

## Permissions

- `mcr.admin` - Access to admin commands (start, stop, reload) - Default: OP
- `mcr.player` - Access to basic player commands - Default: true
- `mcr.join` - Join teams - Default: true
- `mcr.leave` - Leave teams - Default: true
- `mcr.teams` - View teams - Default: true

## Commands

### Player Commands
| Command | Description | Permission |
|---------|-------------|------------|
| `/mcr join <team>` | Join a team | `mcr.join` |
| `/mcr leave` | Leave your current team | `mcr.leave` |
| `/mcr teams` | List all available teams | `mcr.teams` |
| `/mcr help` | Show help message | - |

### Admin Commands
| Command | Description | Permission |
|---------|-------------|------------|
| `/mcr start` | Start the game | `mcr.admin` |
| `/mcr stop` | Stop the game | `mcr.admin` |
| `/mcr reload` | Reload configuration | `mcr.admin` |

## Team Colors

The plugin uses Minecraft's ChatColor system:
- **Red** - `ChatColor.RED`
- **Orange** - `ChatColor.GOLD`
- **Yellow** - `ChatColor.YELLOW`
- **Lime** - `ChatColor.GREEN`
- **Green** - `ChatColor.DARK_GREEN`
- **Cyan** - `ChatColor.AQUA`
- **Blue** - `ChatColor.BLUE`
- **Purple** - `ChatColor.DARK_PURPLE`
- **Magenta** - `ChatColor.LIGHT_PURPLE`
- **Pink** - `ChatColor.LIGHT_PURPLE`

## File Structure

```
MinecraftRivals/
├── src/main/java/com/zskv/minecraftRivals/
│   ├── MinecraftRivals.java         # Main plugin class
│   ├── MCRCommand.java              # Command handler with tab completion
│   ├── PlayerListener.java          # Event listener for player joins
│   ├── CustomScoreboardManager.java # Scoreboard update manager
│   └── TablistManager.java          # Tablist
├── src/main/resources/
│   ├── plugin.yml                   # Plugin metadata and commands
│   └── config.yml                   # Configuration file
├── TABLIST_FEATURES.md              # Detailed tablist documentation
└── pom.xml                          # Maven build configuration
```

## Building

This is a Maven project. To build:

```bash
mvn clean package
```

The compiled JAR will be in the `target/` directory.

## Installation

1. Build the plugin or download the JAR
2. Place the JAR in your server's `plugins/` folder
3. Start/restart your server
4. Configure `plugins/MinecraftRivals/config.yml` as needed
5. Use `/mcr reload` to reload configuration without restarting

## Technical Details

- **API Version**: 1.21
- **Uses Minecraft's default scoreboard system** - No custom implementations
- **Teams persist** across server restarts (stored in Minecraft's scoreboard)
- **Automatic scoreboard updates** via BukkitRunnable (configurable interval)
- **Real-time tablist updates** when players join/leave teams or server
- **Custom font support** - Uses `pixel_uppercase` font (requires resource pack)
- **Offline player tracking** - Shows offline team members in gray
- **Config-driven** - Most behavior can be changed without code modifications
- **Smart tab completion** with context-aware suggestions

## Resource Pack

The tablist uses the `pixel_uppercase` font for a nice look To see the custom font:
1. Create or download a resource pack with the `pixel_uppercase` font
2. Place font files in `assets/minecraft/font/pixel_uppercase.json`
3. Distribute the resource pack to players

Without the resource pack, the tablist will still work but display in default Minecraft font.

## Future Enhancements

Potential features to add:
- Game state management (lobby, active, ended)
- Multiple game modes
- Statistics tracking
- Team chat
- Spectator mode
- Automatic team balancing
- Game-specific scoreboards for different minigames

## License

Only use for the MC event "Minecraft Rivals". Any other use of this plugin will result in a copyright notice.
