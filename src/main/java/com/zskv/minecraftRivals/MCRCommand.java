package com.zskv.minecraftRivals;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MCRCommand implements CommandExecutor, TabCompleter {

    private final MinecraftRivals plugin;

    public MCRCommand(MinecraftRivals plugin) {
        this.plugin = plugin;
    }
    
    private String getMessage(String key) {
        String message = ChatColor.translateAlternateColorCodes('&', 
            plugin.getConfig().getString("messages." + key, key));
        return message;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            return handleHelp(sender);
        }

        String subcommand = args[0].toLowerCase();

        switch (subcommand) {
            case "event":
                return handleEvent(sender, args);
            case "voting":
                return handleVoting(sender, args);
            case "start":
                return handleStart(sender);
            case "stop":
                return handleStop(sender);
            case "join":
                return handleJoin(sender, args);
            case "leave":
                return handleLeave(sender);
            case "teams":
                return handleTeams(sender);
            case "setgame":
                return handleSetGame(sender, args);
            case "reload":
                return handleReload(sender);
            case "help":
                return handleHelp(sender);
            default:
                sender.sendMessage("§cUnknown subcommand. Use §e/mcr help§c for help.");
                return true;
        }
    }

    private boolean handleEvent(CommandSender sender, String[] args) {
        if (!sender.hasPermission("mcr.admin")) {
            sender.sendMessage(getMessage("no-permission"));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage("§cUsage: /mcr event <start|cancel>");
            return true;
        }

        String action = args[1].toLowerCase();
        switch (action) {
            case "start":
                boolean started = plugin.getEventIntroManager().startIntro(sender);
                if (started) {
                    sender.sendMessage("§aEvent intro sequence initiated.");
                }
                return true;
            case "cancel":
                plugin.getEventIntroManager().shutdown();
                sender.sendMessage("§cEvent intro sequence cancelled.");
                return true;
            default:
                sender.sendMessage("§cUnknown event action: " + action);
                return true;
        }
    }
    
    private boolean handleVoting(CommandSender sender, String[] args) {
        if (!sender.hasPermission("mcr.admin")) {
            sender.sendMessage(getMessage("no-permission"));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage("§cUsage: /mcr voting <start|stop>");
            return true;
        }

        String action = args[1].toLowerCase();
        switch (action) {
            case "start":
                boolean started = plugin.getVotingManager().startVoting();
                if (started) {
                    sender.sendMessage("§aVoting started!");
                } else {
                    sender.sendMessage("§cVoting is already active or failed to start.");
                }
                return true;
            case "stop":
                plugin.getVotingManager().stopVoting();
                sender.sendMessage("§cVoting stopped.");
                return true;
            default:
                sender.sendMessage("§cUnknown voting action: " + action);
                return true;
        }
    }

    private boolean handleStart(CommandSender sender) {
        if (!sender.hasPermission("mcr.admin")) {
            sender.sendMessage(getMessage("no-permission"));
            return true;
        }
        // Logic to start the game
        sender.sendMessage(getMessage("game-start"));
        return true;
    }

    private boolean handleStop(CommandSender sender) {
        if (!sender.hasPermission("mcr.admin")) {
            sender.sendMessage(getMessage("no-permission"));
            return true;
        }
        // Logic to stop the game
        sender.sendMessage(getMessage("game-stop"));
        return true;
    }

    private boolean handleJoin(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can join teams.");
            return true;
        }
        Player player = (Player) sender;
        if (args.length < 2) {
            sender.sendMessage("Usage: /mcr join <team>");
            return true;
        }
        String teamName = args[1];
        
        // Check if team exists
        Team team = plugin.getScoreboard().getTeam(teamName);
        if (team == null) {
            sender.sendMessage("§cTeam not found: " + teamName);
            return true;
        }
        
        // Check team size limit
        int maxTeamSize = plugin.getConfig().getInt("game.max-players-per-team", 5);
        if (team.getSize() >= maxTeamSize) {
            sender.sendMessage(getMessage("team-full"));
            return true;
        }
        
        // Remove from current team
        plugin.getScoreboard().getTeams().forEach(t -> t.removeEntry(player.getName()));
        
        // Add to new team
        team.addEntry(player.getName());
        
        // Update tablist for all players
        plugin.getTablistManager().refreshAllPlayers();
        
        String message = getMessage("team-join").replace("%team%", team.getColor() + teamName + ChatColor.RESET);
        sender.sendMessage(message);
        return true;
    }

    private boolean handleLeave(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can leave teams.");
            return true;
        }
        Player player = (Player) sender;
        
        // Remove from current team
        Team currentTeam = plugin.getScoreboard().getEntryTeam(player.getName());
        if (currentTeam == null) {
            sender.sendMessage("§cYou are not in a team!");
            return true;
        }
        
        String teamName = currentTeam.getDisplayName();
        ChatColor teamColor = currentTeam.getColor();
        
        currentTeam.removeEntry(player.getName());
        
        // Update tablist for all players
        plugin.getTablistManager().refreshAllPlayers();
        
        String message = getMessage("team-leave").replace("%team%", teamColor + teamName + ChatColor.RESET);
        sender.sendMessage(message);
        return true;
    }

    private boolean handleTeams(CommandSender sender) {
        // List visible teams with player counts
        List<String> visibleTeams = plugin.getConfig().getStringList("teams.visible");
        
        sender.sendMessage("§6Available Teams:");
        for (String teamName : visibleTeams) {
            Team team = plugin.getScoreboard().getTeam(teamName);
            if (team != null) {
                int size = team.getSize();
                int maxSize = plugin.getConfig().getInt("game.max-players-per-team", 5);
                String status = size >= maxSize ? "§c[FULL]" : "§a[" + size + "/" + maxSize + "]";
                sender.sendMessage("  " + team.getColor() + "● " + teamName + " " + status);
            }
        }
        return true;
    }
    
    private boolean handleSetGame(CommandSender sender, String[] args) {
        if (!sender.hasPermission("mcr.admin")) {
            sender.sendMessage(getMessage("no-permission"));
            return true;
        }
        
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /mcr setgame <number>");
            return true;
        }
        
        try {
            int gameNumber = Integer.parseInt(args[1]);
            int totalGames = plugin.getConfig().getInt("game.total-games", 6);
            
            if (gameNumber < 1 || gameNumber > totalGames) {
                sender.sendMessage("§cGame number must be between 1 and " + totalGames);
                return true;
            }
            
            plugin.getConfig().set("game.current-game", gameNumber);
            plugin.saveConfig();
            
            sender.sendMessage("§aGame number set to §f" + gameNumber + "§a/§f" + totalGames);
            return true;
        } catch (NumberFormatException e) {
            sender.sendMessage("§cInvalid number: " + args[1]);
            return true;
        }
    }
    
    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("mcr.admin")) {
            sender.sendMessage(getMessage("no-permission"));
            return true;
        }
        
        plugin.reloadConfig();
        sender.sendMessage("§aConfiguration reloaded!");
        return true;
    }
    
    private boolean handleHelp(CommandSender sender) {
        sender.sendMessage("§6§l§m                    §r");
        sender.sendMessage("§6§lMinecraft Rivals Commands");
        sender.sendMessage("§6§l§m                    §r");
        sender.sendMessage("§e/mcr join <team> §7- Join a team");
        sender.sendMessage("§e/mcr leave §7- Leave your current team");
        sender.sendMessage("§e/mcr teams §7- List all available teams");
        sender.sendMessage("§e/mcr help §7- Show this help message");
        
        if (sender.hasPermission("mcr.admin")) {
            sender.sendMessage("§c§lAdmin Commands:");
            sender.sendMessage("§e/mcr event <start|cancel> §7- Control event intro");
            sender.sendMessage("§e/mcr voting <start|stop> §7- Control voting phase");
            sender.sendMessage("§e/mcr start §7- Start the game");
            sender.sendMessage("§e/mcr stop §7- Stop the game");
            sender.sendMessage("§e/mcr setgame <number> §7- Set current game number");
            sender.sendMessage("§e/mcr reload §7- Reload configuration");
        }
        sender.sendMessage("§6§l§m                    §r");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            // Main subcommands
            List<String> subcommands = new ArrayList<>(Arrays.asList("join", "leave", "teams", "help"));
            
            // Add admin commands if sender has permission
            if (sender.hasPermission("mcr.admin")) {
                subcommands.add("event");
                subcommands.add("voting");
                subcommands.add("start");
                subcommands.add("stop");
                subcommands.add("setgame");
                subcommands.add("reload");
            }
            
            String input = args[0].toLowerCase();
            for (String sub : subcommands) {
                if (sub.toLowerCase().startsWith(input)) {
                    completions.add(sub);
                }
            }
            return completions;
        } else if (args.length == 2 && args[0].equalsIgnoreCase("event")) {
            completions.addAll(Arrays.asList("start", "cancel"));
            return filterByPrefix(completions, args[1]);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("voting")) {
            completions.addAll(Arrays.asList("start", "stop"));
            return filterByPrefix(completions, args[1]);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("join")) {
            // Suggest visible teams for join command
            List<String> visibleTeams = plugin.getConfig().getStringList("teams.visible");
            String input = args[1].toLowerCase();
            
            for (String teamName : visibleTeams) {
                Team team = plugin.getScoreboard().getTeam(teamName);
                if (team != null && teamName.toLowerCase().startsWith(input)) {
                    // Only suggest teams that aren't full
                    int maxSize = plugin.getConfig().getInt("game.max-players-per-team", 5);
                    if (team.getSize() < maxSize) {
                        completions.add(teamName);
                    }
                }
            }
            return completions;
        } else if (args.length == 2 && args[0].equalsIgnoreCase("setgame")) {
            // Suggest game numbers 1-6
            int totalGames = plugin.getConfig().getInt("game.total-games", 6);
            for (int i = 1; i <= totalGames; i++) {
                completions.add(String.valueOf(i));
            }
            return completions;
        }
        return completions;
    }

    private List<String> filterByPrefix(List<String> source, String input) {
        String lower = input.toLowerCase();
        List<String> matches = new ArrayList<>();
        for (String value : source) {
            if (value.toLowerCase().startsWith(lower)) {
                matches.add(value);
            }
        }
        return matches;
    }
}