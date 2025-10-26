package com.zskv.minecraftRivals;

import com.zskv.minecraftRivals.dishdash.DishDashCommand;
import com.zskv.minecraftRivals.dishdash.DishDashManager;
import com.zskv.minecraftRivals.global.pads;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

public final class MinecraftRivals extends JavaPlugin {

    private Scoreboard scoreboard;
    private CustomScoreboardManager scoreboardManager;
    private TablistManager tablistManager;
    private EventIntroManager eventIntroManager;
    private VotingManager votingManager;
    private DishDashManager dishDashManager;

    public Scoreboard getScoreboard() {
        return scoreboard;
    }
    
    public CustomScoreboardManager getScoreboardManager() {
        return scoreboardManager;
    }
    
    public TablistManager getTablistManager() {
        return tablistManager;
    }
    
    public EventIntroManager getEventIntroManager() {
        return eventIntroManager;
    }
    
    public VotingManager getVotingManager() {
        return votingManager;
    }
    
    public DishDashManager getDishDashManager() {
        return dishDashManager;
    }

    @Override
    public void onEnable() {
        // Plugin startup logic
        saveDefaultConfig();

        // Initialize scoreboard
        initializeScoreboard();

        // Create teams
        createTeams();

        // Register commands
        getCommand("mcr").setExecutor(new MCRCommand(this));
        getCommand("mcr").setTabCompleter(new MCRCommand(this));
        getCommand("readycheck").setExecutor(new ReadyCheckCommand(this));

        // Register events
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new pads(), this);
        // Initialize and start scoreboard manager
        scoreboardManager = new CustomScoreboardManager(this);
        scoreboardManager.startUpdating();
        
        // Initialize and start tablist manager
        tablistManager = new TablistManager(this);
        tablistManager.startUpdating();
        
        // Initialize event intro manager
        eventIntroManager = new EventIntroManager(this);
        
        // Initialize voting manager
        votingManager = new VotingManager(this);
        
        // Initialize Dish Dash manager
        dishDashManager = new DishDashManager(this);
        getCommand("dishdash").setExecutor(new DishDashCommand(this, dishDashManager));
        getCommand("dishdash").setTabCompleter(new DishDashCommand(this, dishDashManager));
        
        getLogger().info("Minecraft Rivals has been enabled!");
    }

    @Override
    public void onDisable() {
        // Stop scoreboard updates
        if (scoreboardManager != null) {
            scoreboardManager.stopUpdating();
        }
        
        // Stop tablist updates
        if (tablistManager != null) {
            tablistManager.stopUpdating();
        }
        
        if (eventIntroManager != null) {
            eventIntroManager.shutdown();
        }
        
        if (votingManager != null) {
            votingManager.stopVoting();
        }
        
        if (dishDashManager != null && dishDashManager.isGameActive()) {
            dishDashManager.stopGame(Bukkit.getConsoleSender());
        }
        
        getLogger().info("Minecraft Rivals has been disabled!");
    }

    private void initializeScoreboard() {
        org.bukkit.scoreboard.ScoreboardManager manager = Bukkit.getScoreboardManager();
        scoreboard = manager.getMainScoreboard();

        // Create custom objectives from config
        java.util.List<java.util.Map<?, ?>> objectives = getConfig().getMapList("objectives");
        for (java.util.Map<?, ?> obj : objectives) {
            String name = (String) obj.get("name");
            String type = (String) obj.get("type");
            String display = (String) obj.get("display");
            if (scoreboard.getObjective(name) == null) {
                org.bukkit.scoreboard.Objective objective = scoreboard.registerNewObjective(name, type, display);
                // Don't display on sidebar by default - CustomScoreboardManager handles this
            }
        }
    }

    private void createTeams() {
        // Create visible teams
        java.util.List<String> visibleTeams = getConfig().getStringList("teams.visible");
        java.util.List<String> backlogTeams = getConfig().getStringList("teams.backlog");

        java.util.Map<String, ChatColor> colorMap = java.util.Map.of(
            "Red", ChatColor.RED,
            "Orange", ChatColor.GOLD,
            "Yellow", ChatColor.YELLOW,
            "Lime", ChatColor.GREEN,
            "Green", ChatColor.DARK_GREEN,
            "Cyan", ChatColor.AQUA,
            "Blue", ChatColor.BLUE,
            "Purple", ChatColor.DARK_PURPLE,
            "Magenta", ChatColor.LIGHT_PURPLE,
            "Pink", ChatColor.LIGHT_PURPLE
        );

        // Create visible teams
        for (String name : visibleTeams) {
            Team team = scoreboard.getTeam(name);
            if (team == null) {
                team = scoreboard.registerNewTeam(name);
            }
            team.setColor(colorMap.getOrDefault(name, ChatColor.WHITE));
            team.setDisplayName(name);
            team.setPrefix(colorMap.getOrDefault(name, ChatColor.WHITE) + "[" + name + "] ");
            team.setSuffix(ChatColor.RESET + "");
            team.setAllowFriendlyFire(false);
            team.setCanSeeFriendlyInvisibles(true);
            team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.ALWAYS);
            team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.FOR_OWN_TEAM);
        }

        // Create backlog teams (Cyan and Magenta) - available but not shown in main lists
        for (String name : backlogTeams) {
            Team team = scoreboard.getTeam(name);
            if (team == null) {
                team = scoreboard.registerNewTeam(name);
            }
            team.setColor(colorMap.getOrDefault(name, ChatColor.WHITE));
            team.setDisplayName(name);
            team.setPrefix(colorMap.getOrDefault(name, ChatColor.WHITE) + "[" + name + "] ");
            team.setSuffix(ChatColor.RESET + "");
            team.setAllowFriendlyFire(false);
            team.setCanSeeFriendlyInvisibles(true);
            team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.ALWAYS);
            team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.FOR_OWN_TEAM);
        }
        
        getLogger().info("Created " + (visibleTeams.size() + backlogTeams.size()) + " teams");
    }
}
