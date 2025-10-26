package com.zskv.minecraftRivals;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Manages the sidebar scoreboard display for all players
 * Shows team information and standings
 */
public class CustomScoreboardManager {

    private final MinecraftRivals plugin;
    private BukkitRunnable updateTask;
    
    // Custom font key for pixel_uppercase
    private static final net.kyori.adventure.key.Key PIXEL_FONT = net.kyori.adventure.key.Key.key("minecraft", "pixel_uppercase");

    public CustomScoreboardManager(MinecraftRivals plugin) {
        this.plugin = plugin;
    }

    public void startUpdating() {
        int updateInterval = plugin.getConfig().getInt("scoreboard.update-interval", 20);
        
        updateTask = new BukkitRunnable() {
            @Override
            public void run() {
                updateAllScoreboards();
            }
        };
        
        updateTask.runTaskTimer(plugin, 0L, updateInterval);
        plugin.getLogger().info("Scoreboard manager started with update interval: " + updateInterval + " ticks");
    }

    public void stopUpdating() {
        if (updateTask != null) {
            updateTask.cancel();
        }
    }

    private void updateAllScoreboards() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            updatePlayerScoreboard(player);
        }
    }

    public void updatePlayerScoreboard(Player player) {
        Scoreboard scoreboard = plugin.getScoreboard();
        
        // Get or create the sidebar objective with Adventure API
        Objective objective = scoreboard.getObjective("sidebar");
        if (objective == null) {
            Component title = Component.text("mc rivals", NamedTextColor.AQUA)
                .font(PIXEL_FONT);
            
            objective = scoreboard.registerNewObjective("sidebar", "dummy", title);
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        }

        // Clear existing scores
        for (String entry : scoreboard.getEntries()) {
            if (objective.getScore(entry).isScoreSet()) {
                scoreboard.resetScores(entry);
            }
        }

        // Build scoreboard content
        int line = 15;
        int invisibleIndex = 0;
        
        // Get current game number from config
        int currentGame = plugin.getConfig().getInt("game.current-game", 1);
        int totalGames = plugin.getConfig().getInt("game.total-games", 6);
        
        // Game number with custom font
        Component gameComponent = Component.text("game ", NamedTextColor.GRAY)
            .append(Component.text(currentGame, NamedTextColor.WHITE))
            .append(Component.text("/", NamedTextColor.GRAY))
            .append(Component.text(totalGames, NamedTextColor.WHITE))
            .font(PIXEL_FONT);
        setScore(objective, gameComponent, line--);
        
        // Empty line
        setScore(objective, Component.empty(), line--);
        
        // Leaderboard header with custom font
        Component leaderboardHeader = Component.text("leaderboard:", NamedTextColor.GRAY)
            .font(PIXEL_FONT);
        setScore(objective, leaderboardHeader, line--);
        
        // Get all teams with their scores and sort by points (top 8)
        List<TeamScore> teamScores = getTeamScores(scoreboard);
        int position = 1;
        for (TeamScore teamScore : teamScores) {
            if (position > 8) break; // Only show top 8
            Team team = teamScore.team;
            int points = teamScore.points;
            TextColor teamColor = chatColorToTextColor(team.getColor());
            
            // Team entry with custom font
            Component teamEntry = Component.text(position + ". ", NamedTextColor.WHITE)
                .append(Component.text(team.getDisplayName().toLowerCase(), teamColor))
                .append(Component.text(" - ", NamedTextColor.GRAY))
                .append(Component.text(points, NamedTextColor.WHITE))
                .font(PIXEL_FONT);
            setScore(objective, teamEntry, line--);
            position++;
            
            if (line < 1) break; // Scoreboard line limit
        }
        
        // Empty line
        setScore(objective, Component.empty(), line--);
        
        // Teammates section with custom font
        Team playerTeam = scoreboard.getEntryTeam(player.getName());
        if (playerTeam != null) {
            Component teammatesHeader = Component.text("teammates:", NamedTextColor.GRAY)
                .font(PIXEL_FONT);
            setScore(objective, teammatesHeader, line--);
            
            // Get teammates (excluding the player themselves)
            List<String> teammates = new ArrayList<>();
            for (String entry : playerTeam.getEntries()) {
                if (!entry.equals(player.getName())) {
                    teammates.add(entry);
                }
            }
            
            if (teammates.isEmpty()) {
                Component noneComponent = Component.text("  none", NamedTextColor.GRAY)
                    .font(PIXEL_FONT);
                setScore(objective, noneComponent, line--);
            } else {
                // Show all teammates with custom font (emoji in default font, name in custom font)
                TextColor teamColor = chatColorToTextColor(playerTeam.getColor());
                for (String teammate : teammates) {
                    Component teammateComponent = Component.text("  👤 ")
                        .append(Component.text(teammate.toLowerCase(), teamColor).font(PIXEL_FONT));
                    setScore(objective, teammateComponent, line--);
                    if (line < 1) break;
                }
            }
        } else {
            Component noTeamComponent = Component.text("teammates: ", NamedTextColor.GRAY)
                .append(Component.text("none", NamedTextColor.RED))
                .font(PIXEL_FONT);
            setScore(objective, noTeamComponent, line--);
        }
        
        // Empty line
        setScore(objective, Component.empty(), line--);
        
        // Individual points with custom font
        Component pointsComponent = Component.text("your points: ", NamedTextColor.GRAY)
            .append(Component.text("n/a", NamedTextColor.WHITE))
            .font(PIXEL_FONT);
        setScore(objective, pointsComponent, line--);
    }
    
    /**
     * Generates a unique invisible string for scoreboard entries
     * Uses color codes to create unique but invisible entries
     * @param index The index to generate a unique string for
     * @return A unique invisible string
     */
    private String getInvisibleString(int index) {
        // Use color reset codes to create unique invisible strings
        // Each index gets a different number of color codes
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i <= index; i++) {
            sb.append("§r");
        }
        return sb.toString();
    }
    
    /**
     * Gets all team scores sorted by points (highest first)
     * Shows all teams even if they have no members
     * @param scoreboard The scoreboard to get teams from
     * @return List of team scores
     */
    private List<TeamScore> getTeamScores(Scoreboard scoreboard) {
        List<TeamScore> scores = new ArrayList<>();
        List<String> visibleTeams = plugin.getConfig().getStringList("teams.visible");
        
        for (String teamName : visibleTeams) {
            Team team = scoreboard.getTeam(teamName);
            if (team != null) {
                int points = getTeamPoints(team, scoreboard);
                scores.add(new TeamScore(team, points));
            }
        }
        
        // Sort by points (descending)
        scores.sort((a, b) -> Integer.compare(b.points, a.points));
        
        return scores;
    }
    
    /**
     * Calculates total points for a team
     * @param team The team to calculate points for
     * @param scoreboard The scoreboard to get points from
     * @return Total points
     */
    private int getTeamPoints(Team team, Scoreboard scoreboard) {
        int totalPoints = 0;
        Objective pointsObj = scoreboard.getObjective("points");
        
        if (pointsObj != null) {
            for (String entry : team.getEntries()) {
                Score score = pointsObj.getScore(entry);
                if (score.isScoreSet()) {
                    totalPoints += score.getScore();
                }
            }
        }
        
        return totalPoints;
    }

    private void setScore(Objective objective, String text, int score) {
        Score s = objective.getScore(text);
        s.setScore(score);
    }
    
    /**
     * Sets a score with a custom Component (supports custom fonts and colors)
     * @param objective The objective to set the score on
     * @param component The component to display
     * @param score The score value (line number)
     */
    private void setScore(Objective objective, Component component, int score) {
        // Use a unique invisible string as the score entry
        String entry = getInvisibleString(score);
        Score s = objective.getScore(entry);
        s.customName(component);
        s.setScore(score);
    }
    
    /**
     * Converts Bukkit ChatColor to Adventure TextColor
     * @param chatColor The ChatColor to convert
     * @return Corresponding Adventure TextColor
     */
    private TextColor chatColorToTextColor(ChatColor chatColor) {
        if (chatColor == null) {
            return NamedTextColor.WHITE;
        }
        
        return switch (chatColor) {
            case BLACK -> NamedTextColor.BLACK;
            case DARK_BLUE -> NamedTextColor.DARK_BLUE;
            case DARK_GREEN -> NamedTextColor.DARK_GREEN;
            case DARK_AQUA -> NamedTextColor.DARK_AQUA;
            case DARK_RED -> NamedTextColor.DARK_RED;
            case DARK_PURPLE -> NamedTextColor.DARK_PURPLE;
            case GOLD -> NamedTextColor.GOLD;
            case GRAY -> NamedTextColor.GRAY;
            case DARK_GRAY -> NamedTextColor.DARK_GRAY;
            case BLUE -> NamedTextColor.BLUE;
            case GREEN -> NamedTextColor.GREEN;
            case AQUA -> NamedTextColor.AQUA;
            case RED -> NamedTextColor.RED;
            case LIGHT_PURPLE -> NamedTextColor.LIGHT_PURPLE;
            case YELLOW -> NamedTextColor.YELLOW;
            case WHITE -> NamedTextColor.WHITE;
            default -> NamedTextColor.WHITE;
        };
    }
    
    /**
     * Helper class to store team scores
     */
    private static class TeamScore {
        final Team team;
        final int points;
        
        TeamScore(Team team, int points) {
            this.team = team;
            this.points = points;
        }
    }
}