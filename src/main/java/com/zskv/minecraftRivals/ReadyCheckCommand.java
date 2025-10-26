package com.zskv.minecraftRivals;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ReadyCheckCommand implements CommandExecutor {

    private final MinecraftRivals plugin;

    public ReadyCheckCommand(MinecraftRivals plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            return true;
        }

        String response = args[0].toLowerCase();
        boolean ready = response.equals("yes");

        plugin.getEventIntroManager().handleReadyResponse(player, ready);
        return true;
    }
}