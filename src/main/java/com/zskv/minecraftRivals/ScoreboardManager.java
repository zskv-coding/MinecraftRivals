package com.zskv.minecraftRivals;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public abstract class ScoreboardManager {

    private MinecraftRivals plugin = new MinecraftRivals();
    private BukkitRunnable updateTask;

    public ScoreboardManager() {
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
        
        // Get or create the sidebar objective
        Objective objective = scoreboard.getObjective("sidebar");
        if (objective == null) {
            objective = scoreboard.registerNewObjective("sidebar", "dummy", 
                ChatColor.translateAlternateColorCodes('&', 
                    plugin.getConfig().getString("scoreboard.title", "§b§lMC RIVALS")));
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
        
        // Empty line
        setScore(objective, " ", line--);
        
        // Player's team info
        Team playerTeam = scoreboard.getEntryTeam(player.getName());
        if (playerTeam != null) {
            setScore(objective, "§7Your Team:", line--);
            setScore(objective, playerTeam.getColor() + "  " + playerTeam.getDisplayName() + " §7(" + playerTeam.getSize() + " players)", line--);
            setScore(objective, "  ", line--);
        }
        
        // Team standings
        setScore(objective, "§7Team Standings:", line--);
        
        List<String> visibleTeams = plugin.getConfig().getStringList("teams.visible");
        for (String teamName : visibleTeams) {
            Team team = scoreboard.getTeam(teamName);
            if (team != null && team.getSize() > 0) {
                Objective pointsObj = scoreboard.getObjective("points");
                int points = 0;
                if (pointsObj != null) {
                    // Get team points (sum of all team members' points)
                    for (String entry : team.getEntries()) {
                        Score score = pointsObj.getScore(entry);
                        if (score.isScoreSet()) {
                            points += score.getScore();
                        }
                    }
                }
                setScore(objective, team.getColor() + "  " + teamName + ": §f" + points, line--);
                
                if (line < 1) break; // Scoreboard line limit
            }
        }
    }

    private void setScore(Objective objective, String text, int score) {
        Score s = objective.getScore(text);
        s.setScore(score);
    }

    public abstract @NotNull Scoreboard getMainScoreboard();

    public abstract @NotNull Scoreboard getNewScoreboard();
}