package com.zskv.minecraftRivals;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Handles player events and updates their scoreboard/tablist
 */
public class PlayerListener implements Listener {

    private final MinecraftRivals plugin;

    public PlayerListener(MinecraftRivals plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Setup the player's tablist (includes scoreboard assignment)
        plugin.getTablistManager().setupPlayerTablist(event.getPlayer());
    }
}