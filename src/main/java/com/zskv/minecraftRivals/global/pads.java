package com.zskv.minecraftRivals.global;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

public class pads implements Listener {

    // Configuration values
    private static final double DASH_POWER = 2.0;
    private static final double JUMP_POWER = 1.5;
    private static final int SPEED_DURATION = 60; // 3 seconds (20 ticks = 1 second)
    private static final int SPEED_AMPLIFIER = 2; // Speed III

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        Material blockBelow = p.getLocation().subtract(0, 1, 0).getBlock().getType();

        // Dash Pad - Dead Fire Coral Block
        if (blockBelow == Material.DEAD_FIRE_CORAL_BLOCK) {
            handleDashPad(p);
        }

        // Jump Pad - Dead Bubble Coral Block
        else if (blockBelow == Material.DEAD_BUBBLE_CORAL_BLOCK) {
            handleJumpPad(p);
        }

        // Speed Pad - Dead Tube Coral Block
        else if (blockBelow == Material.DEAD_TUBE_CORAL_BLOCK) {
            handleSpeedPad(p);
        }
    }

    private void handleDashPad(Player p) {
        // Get the direction the player is looking (horizontal only)
        Vector direction = p.getLocation().getDirection().normalize();
        direction.setY(0); // Remove vertical component
        direction.normalize();

        // Apply horizontal boost
        Vector velocity = direction.multiply(DASH_POWER);
        velocity.setY(0.3); // Small upward boost to prevent getting stuck

        p.setVelocity(velocity);
    }

    private void handleJumpPad(Player p) {
        // Boost player upward
        Vector velocity = p.getVelocity();
        velocity.setY(JUMP_POWER);

        p.setVelocity(velocity);
    }

    private void handleSpeedPad(Player p) {
        // Give speed effect
        p.addPotionEffect(new PotionEffect(
                PotionEffectType.SPEED,
                SPEED_DURATION,
                SPEED_AMPLIFIER,
                false, // ambient
                false  // show particles
        ));
    }
}