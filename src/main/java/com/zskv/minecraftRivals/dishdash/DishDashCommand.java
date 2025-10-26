package com.zskv.minecraftRivals.dishdash;

import com.zskv.minecraftRivals.MinecraftRivals;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Command handler for Dish Dash minigame
 * Temporary commands: /dishdash start and /dishdash stop
 */
public class DishDashCommand implements CommandExecutor, TabCompleter {
    
    private final MinecraftRivals plugin;
    private final DishDashManager manager;
    
    public DishDashCommand(MinecraftRivals plugin, DishDashManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }
    
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Component.text("Usage: /dishdash <start|stop>", NamedTextColor.RED));
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "start":
                if (manager.startGame(sender)) {
                    sender.sendMessage(Component.text("Dish Dash game started!", NamedTextColor.GREEN));
                } else {
                    sender.sendMessage(Component.text("Failed to start Dish Dash. Check console for details.", NamedTextColor.RED));
                }
                return true;
                
            case "stop":
                if (manager.stopGame(sender)) {
                    sender.sendMessage(Component.text("Dish Dash game stopped!", NamedTextColor.GREEN));
                } else {
                    sender.sendMessage(Component.text("No Dish Dash game is currently running.", NamedTextColor.RED));
                }
                return true;
                
            default:
                sender.sendMessage(Component.text("Unknown subcommand. Use: /dishdash <start|stop>", NamedTextColor.RED));
                return true;
        }
    }
    
    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            completions.add("start");
            completions.add("stop");
        }
        
        return completions;
    }
}