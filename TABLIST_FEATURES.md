# MCC-Style Tablist Features

## Overview
The Minecraft Rivals plugin now includes a comprehensive MCC-style tablist system that displays teams grouped together with their scores, just like in Minecraft Championship events. The tablist shows all teams with their members, with online players in team colors and offline players grayed out.

## Features

### 1. **MCC-Style Team Grouping**
The tablist displays teams in a grouped format:
- **Title**: "MC RIVALS" in gold with custom font
- **Separator**: Visual divider line
- **Team Listings**: Each team shows:
  - Team name in team color (bold)
  - Team score in gold
  - All team members on the next line (comma-separated)
  - Online players in team color
  - Offline players in gray

Example:
```
MC RIVALS
-----------
Red: 150
Steve, Alex, Notch
Blue: 120
Jeb, Dinnerbone
Yellow: 95
Grumm, Herobrine
```

### 2. **Online/Offline Player Display**
- **Online Players**: Displayed in their team's color
- **Offline Players**: Displayed in gray (ChatColor.GRAY)
- Players are shown in the tablist even when offline
- Real-time updates when players join/leave

### 3. **Custom Font Support**
- Uses `pixel_uppercase` font for authentic MCC look
- Font tags: `<font:minecraft:pixel_uppercase>` and `<font:minecraft:default>`
- Requires a resource pack with the custom font
- Applies to title, team names, and player names

### 4. **Real-Time Updates**
- Automatically updates every second (configurable)
- Updates when players join/leave teams
- Updates when players join/leave the server
- Synchronized across all online players
- Team scores recalculated dynamically

### 5. **Team Standings**
- Calculates team points from the scoreboard objective
- Sorts teams by total points (highest first)
- Only shows teams with active players
- Displays team scores in gold next to team names

## Configuration

### Config.yml Settings

```yaml
tablist:
  footer: "&7Good luck and have fun!"
  update-interval: 20  # ticks (1 second = 20 ticks)
```

### Customization Options

1. **Footer**: Add custom messages or server info
2. **Update Interval**: Adjust how often the tablist refreshes (20 ticks = 1 second)
3. **Font**: The `pixel_uppercase` font is hardcoded but can be changed in TablistManager.java

## Technical Implementation

### TablistManager.java
The core class that handles all tablist functionality:

#### Key Methods:
- `startUpdating()` - Starts the automatic update task
- `stopUpdating()` - Stops the update task
- `updateAllTablists()` - Updates all players' tablists
- `updatePlayerTablist(Player)` - Updates a specific player's tablist
- `setupPlayerTablist(Player)` - Initial setup for joining players
- `refreshAllPlayers()` - Force refresh for all players (used when teams change)
- `buildMCCHeader()` - Builds the MCC-style header with team groupings
- `getTeamMembers(Team)` - Gets formatted list of team members (online/offline)

#### Update Cycle:
1. **Header Building** (MCC-Style):
   - Adds title "MC RIVALS" with custom font
   - Adds separator line
   - Iterates through all teams sorted by score
   - For each team:
     - Shows team name (colored, bold) and score (gold)
     - Lists all team members (online in color, offline in gray)

2. **Player Name Formatting**:
   - Gets player's team from scoreboard
   - Applies team color with custom font
   - Sets player list name
   - No team = white color

3. **Footer Building**:
   - Adds custom footer message from config
   - Applies custom font

### Integration Points

#### MinecraftRivals.java
```java
private TablistManager tablistManager;

@Override
public void onEnable() {
    // Initialize and start tablist manager
    tablistManager = new TablistManager(this);
    tablistManager.startUpdating();
}

@Override
public void onDisable() {
    // Stop tablist updates
    if (tablistManager != null) {
        tablistManager.stopUpdating();
    }
}
```

#### PlayerListener.java
```java
@EventHandler
public void onPlayerJoin(PlayerJoinEvent event) {
    // Setup the player's tablist
    plugin.getTablistManager().setupPlayerTablist(event.getPlayer());
}
```

#### MCRCommand.java
```java
// When player joins a team
team.addEntry(player.getName());
plugin.getTablistManager().refreshAllPlayers();

// When player leaves a team
currentTeam.removeEntry(player.getName());
plugin.getTablistManager().refreshAllPlayers();
```

## Color Codes

The tablist uses Minecraft color codes with `&` in config:
- `&6` - Gold
- `&7` - Gray
- `&8` - Dark Gray
- `&a` - Green
- `&c` - Red
- `&e` - Yellow
- `&l` - Bold
- `&m` - Strikethrough
- `&r` - Reset

## Performance Considerations

1. **Update Interval**: Default is 20 ticks (1 second)
   - Lower values = more frequent updates but higher CPU usage
   - Higher values = less frequent updates but lower CPU usage

2. **Efficient Updates**: 
   - Only recalculates when needed
   - Uses Bukkit's async scheduler
   - Minimal impact on server performance

3. **Scalability**:
   - Tested with up to 40 players
   - Handles multiple teams efficiently
   - Optimized score calculations

## Usage Examples

### For Players
- Join the server → Tablist automatically appears
- Join a team → Name becomes colored with team prefix
- View top teams → Check footer for current standings

### For Admins
- Customize header/footer in config.yml
- Adjust update interval for performance
- Toggle team prefixes on/off
- Use `/mcr reload` to apply config changes

## Troubleshooting

### Tablist not updating?
- Check that `update-interval` is set in config.yml
- Verify the plugin is enabled
- Check console for errors

### Team colors not showing?
- Verify teams are properly created in the scoreboard
- Check that players are actually in teams
- Ensure the resource pack with `pixel_uppercase` font is loaded

### Custom font not displaying?
- Make sure players have the resource pack installed
- The font must be named `pixel_uppercase` in the resource pack
- Font tags use format: `<font:minecraft:pixel_uppercase>`
- Without the resource pack, text will display in default font

### Offline players not showing?
- Offline players only show if they were previously in a team
- They must have joined the server at least once
- Check that team entries are persisted

### Performance issues?
- Increase `update-interval` (e.g., 40 ticks = 2 seconds)
- Reduce number of visible teams
- Check server TPS

## Resource Pack Requirements

To use the `pixel_uppercase` font, you need a resource pack with:
1. Font file in `assets/minecraft/font/pixel_uppercase.json`
2. Font texture files (PNG)
3. Proper font configuration

The plugin will work without the resource pack, but text will display in default Minecraft font.

## Future Enhancements

Potential improvements:
- Configurable font selection
- Ping display for players
- Game state indicators (Lobby/Active/Ended)
- Custom sorting options (alphabetical, by team, by score)
- Per-player tablist customization
- Animation support for header/footer
- Integration with other plugins (Vault, PlaceholderAPI)
- Automatic resource pack distribution

## Credits

Created for MCC-style events on Minecraft servers.
Inspired by Minecraft Championship (MCC) tablist design.