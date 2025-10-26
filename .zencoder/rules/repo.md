# Repository Guidelines

## Project Overview
- **Name**: Minecraft Rivals
- **Purpose**: Bukkit/Spigot plugin that delivers MCC-style event features including team management, scoreboards, tablist presentation, and a cinematic event intro flow.
- **Primary Language**: Java (Maven project targeting API version 1.21).

## Key Components
1. **`MinecraftRivals`**: Main plugin entry; wires commands, listeners, and managers.
2. **`MCRCommand`**: Handles `/mcr` commands and tab completion.
3. **`PlayerListener`**: Maintains team/player state on join/leave.
4. **`CustomScoreboardManager`**: Updates scoreboard objectives and display.
5. **`TablistManager`**: Produces MCC-style tab list using Adventure components/custom fonts.
6. **`EventIntroManager`**: Coordinates intro sequence (boss bar, camera sheep, team showcases, voting countdown).

## Build & Run
1. Ensure Maven and a compatible Java JDK (17+) are installed.
2. Execute `mvn clean package` at the repository root.
3. Deploy the resulting JAR from `target/` into a Spigot/Paper server's `plugins/` directory.
4. Restart or reload the server.

## Coding Conventions
- Follow standard Java style with descriptive names and explicit visibility modifiers.
- Use Adventure `Component` APIs for rich text; convert to legacy strings when interacting with Bukkit classes that expect `String`.
- Prefer immutable collections or defensive copies when sharing state between tasks.
- Leverage Bukkit scheduler utilities (`runTaskTimer`, `runTaskLater`) for timing-sensitive sequences.
- Keep plugin state synchronized where cross-thread access might occur (e.g., `synchronized` on critical flows).

## Testing & Validation
- After changes, rebuild with Maven and test on a local Paper server.
- Verify that event intro flows (titles, boss bars, camera lock) perform as expected.
- Check console logs for warnings/errors during plugin enable/disable.

## Contribution Tips
- Add new configuration toggles to `config.yml` with sensible defaults.
- Document new commands or features in `README.md`.
- Avoid unnecessary API version bumps unless required by new features.