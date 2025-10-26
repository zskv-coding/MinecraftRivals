package com.zskv.minecraftRivals;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Team;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Manages the MCC-style tablist display for all players
 * Shows teams grouped together with their scores and players
 * Displays offline players as grayed out
 */
public class TablistManager {

    private final MinecraftRivals plugin;
    private BukkitRunnable updateTask;
    private boolean isRunning = false;
    
    // Custom font key for pixel_uppercase
    private static final net.kyori.adventure.key.Key PIXEL_FONT = net.kyori.adventure.key.Key.key("minecraft", "pixel_uppercase");
    private static final net.kyori.adventure.key.Key DEFAULT_FONT = net.kyori.adventure.key.Key.key("minecraft", "default");

    public TablistManager(MinecraftRivals plugin) {
        this.plugin = plugin;
    }

    /**
     * Starts the automatic tablist update task
     */
    public void startUpdating() {
        if (isRunning) {
            return;
        }

        int updateInterval = plugin.getConfig().getInt("tablist.update-interval", 20); // Default: 1 second

        updateTask = new BukkitRunnable() {
            @Override
            public void run() {
                updateAllTablists();
            }
        };

        updateTask.runTaskTimer(plugin, 0L, updateInterval);
        isRunning = true;
        plugin.getLogger().info("Tablist manager started with update interval: " + updateInterval + " ticks");
    }

    /**
     * Stops the automatic tablist update task
     */
    public void stopUpdating() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
        isRunning = false;
        plugin.getLogger().info("Tablist manager stopped");
    }

    /**
     * Updates the tablist for all online players
     */
    public void updateAllTablists() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            updatePlayerTablist(player);
        }
    }

    /**
     * Updates the tablist for a specific player
     * @param player The player to update
     */
    public void updatePlayerTablist(Player player) {
        updatePlayerListName(player);
        updateHeaderFooter(player);
    }

    /**
     * Updates the player's display name in the tablist with team colors
     * Online players show in team color, offline players show in gray
     * Uses lowercase for better appearance with custom font
     * @param player The player to update
     */
    public void updatePlayerListName(Player player) {
        Team team = plugin.getScoreboard().getEntryTeam(player.getName());
        
        Component displayName;
        if (team != null) {
            // Online player - show in team color with font (lowercase)
            TextColor teamColor = chatColorToTextColor(team.getColor());
            displayName = Component.text(player.getName().toLowerCase())
                .color(teamColor)
                .font(PIXEL_FONT);
        } else {
            // No team - show in white with font (lowercase)
            displayName = Component.text(player.getName().toLowerCase())
                .color(NamedTextColor.WHITE)
                .font(PIXEL_FONT);
        }
        
        player.playerListName(displayName);
    }

    /**
     * Updates the header and footer for a specific player
     * Creates MCC-style layout with teams grouped together
     * @param player The player to update
     */
    public void updateHeaderFooter(Player player) {
        Component header = buildMCCHeader();
        Component footer = buildMCCFooter();

        player.sendPlayerListHeader(header);
        player.sendPlayerListFooter(footer);
    }

    /**
     * Builds the MCC-style header showing title and team listings
     * Format:
     * mc rivals
     * -----------
     * team 1: (score in gold)
     * player1, player2, etc.
     * team 2: (score in gold)
     * player1, player2, etc.
     * @return Formatted header component
     */
    private Component buildMCCHeader() {
        Component header = Component.empty();

        // Title with custom font (lowercase for better appearance)
        header = header.append(Component.text("mc rivals")
            .color(NamedTextColor.AQUA)
            .decorate(TextDecoration.BOLD)
            .font(PIXEL_FONT));
        
        header = header.append(Component.newline());
        
        // Separator
        header = header.append(Component.text("-----------")
            .color(NamedTextColor.GRAY)
            .decorate(TextDecoration.STRIKETHROUGH));
        
        header = header.append(Component.newline());

        // Get all teams sorted by score
        List<TeamScore> teamScores = getTeamScores();
        
        // Build team listings
        boolean firstTeam = true;
        for (TeamScore teamScore : teamScores) {
            Team team = teamScore.team;
            int points = teamScore.points;
            TextColor teamColor = chatColorToTextColor(team.getColor());
            
            // Add spacing between teams (except before first team)
            if (!firstTeam) {
                header = header.append(Component.newline());
            }
            firstTeam = false;
            
            // Team name with score in gold (lowercase for better appearance)
            header = header.append(Component.text(team.getDisplayName().toLowerCase() + ": ")
                .color(teamColor)
                .decorate(TextDecoration.BOLD)
                .font(PIXEL_FONT));
            
            header = header.append(Component.text(String.valueOf(points))
                .color(NamedTextColor.GOLD)
                .font(PIXEL_FONT));
            
            header = header.append(Component.newline());
            
            // Get team members (online and offline)
            Component playerList = getTeamMembersComponent(team);
            
            if (!playerList.equals(Component.empty())) {
                header = header.append(playerList);
                header = header.append(Component.newline());
            }
        }

        return header;
    }

    /**
     * Gets formatted component of team members (online in color, offline in gray)
     * Uses lowercase for better appearance with custom font
     * @param team The team to get members from
     * @return Component with formatted player names
     */
    private Component getTeamMembersComponent(Team team) {
        Set<String> onlinePlayerNames = Bukkit.getOnlinePlayers().stream()
            .map(Player::getName)
            .collect(Collectors.toSet());
        
        Component result = Component.empty();
        boolean first = true;
        TextColor teamColor = chatColorToTextColor(team.getColor());
        
        for (String entry : team.getEntries()) {
            if (!first) {
                result = result.append(Component.text(", ")
                    .color(NamedTextColor.GRAY)
                    .font(PIXEL_FONT));
            }
            first = false;
            
            if (onlinePlayerNames.contains(entry)) {
                // Online player - show in team color with font (lowercase)
                result = result.append(Component.text(entry.toLowerCase())
                    .color(teamColor)
                    .font(PIXEL_FONT));
            } else {
                // Offline player - show in gray with font (lowercase)
                result = result.append(Component.text(entry.toLowerCase())
                    .color(NamedTextColor.GRAY)
                    .font(PIXEL_FONT));
            }
        }
        
        return result;
    }

    /**
     * Builds the footer (can be used for additional info)
     * @return Formatted footer component
     */
    private Component buildMCCFooter() {
        // Custom footer from config
        String configFooter = plugin.getConfig().getString("tablist.footer", 
            "&7Good luck and have fun!");
        
        // Convert legacy color codes to Adventure component
        Component footer = Component.newline()
            .append(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand()
                .deserialize(configFooter)
                .font(PIXEL_FONT));

        return footer;
    }

    /**
     * Gets all team scores sorted by points (highest first)
     * Shows all teams even if they have no members
     * @return List of team scores
     */
    private List<TeamScore> getTeamScores() {
        List<TeamScore> scores = new ArrayList<>();
        List<String> visibleTeams = plugin.getConfig().getStringList("teams.visible");

        for (String teamName : visibleTeams) {
            Team team = plugin.getScoreboard().getTeam(teamName);
            if (team != null) {
                int points = getTeamPoints(team);
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
     * @return Total points
     */
    private int getTeamPoints(Team team) {
        int totalPoints = 0;
        org.bukkit.scoreboard.Objective pointsObj = plugin.getScoreboard().getObjective("points");

        if (pointsObj != null) {
            for (String entry : team.getEntries()) {
                org.bukkit.scoreboard.Score score = pointsObj.getScore(entry);
                if (score.isScoreSet()) {
                    totalPoints += score.getScore();
                }
            }
        }

        return totalPoints;
    }

    /**
     * Updates tablist for all players to reflect team changes
     * Call this when a player joins or leaves a team
     */
    public void refreshAllPlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            updatePlayerTablist(player);
        }
    }

    /**
     * Sets up the tablist for a player who just joined
     * @param player The player who joined
     */
    public void setupPlayerTablist(Player player) {
        // Set the player's scoreboard
        player.setScoreboard(plugin.getScoreboard());
        
        // Update their tablist
        updatePlayerTablist(player);
        
        // Update all other players' tablists to show the new player
        refreshAllPlayers();
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