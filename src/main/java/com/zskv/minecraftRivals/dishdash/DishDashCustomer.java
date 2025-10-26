package com.zskv.minecraftRivals.dishdash;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;

import java.util.Random;

/**
 * Represents a customer waiting for food
 */
public class DishDashCustomer {
    
    private static final Random RANDOM = new Random();
    private static final int PATIENCE_SECONDS = 60;
    
    private final Entity entity;
    private final Location spawnLocation;
    private final DishDashRecipe requestedRecipe;
    private final boolean isSpecial; // Mooshroom = special customer
    private final int pointMultiplier;
    private int ticksRemaining;
    private boolean angry;
    
    public DishDashCustomer(Location location, DishDashRecipe recipe, boolean isSpecial) {
        this.spawnLocation = location.clone();
        this.requestedRecipe = recipe;
        this.isSpecial = isSpecial;
        this.pointMultiplier = isSpecial ? 2 : 1;
        this.ticksRemaining = PATIENCE_SECONDS * 20; // Convert to ticks
        this.angry = false;
        
        // Spawn the appropriate entity
        if (isSpecial) {
            this.entity = location.getWorld().spawnEntity(location, EntityType.MOOSHROOM);
        } else {
            this.entity = location.getWorld().spawnEntity(location, EntityType.COW);
        }
        
        // Configure entity
        if (entity instanceof LivingEntity living) {
            living.setAI(false);
            living.setInvulnerable(true);
            living.setGravity(false);
            living.setCollidable(false);
            living.setSilent(true);
        }
        
        updateCustomName();
    }
    
    /**
     * Updates the customer's display name with their order and patience
     */
    private void updateCustomName() {
        int secondsLeft = ticksRemaining / 20;
        NamedTextColor color;
        
        if (secondsLeft > 40) {
            color = NamedTextColor.GREEN;
        } else if (secondsLeft > 20) {
            color = NamedTextColor.YELLOW;
        } else {
            color = NamedTextColor.RED;
            angry = true;
        }
        
        Component name = Component.text(requestedRecipe.getDisplayName(), color, TextDecoration.BOLD)
            .append(Component.text(" (" + secondsLeft + "s)", color));
        
        if (isSpecial) {
            name = Component.text("⭐ ", NamedTextColor.GOLD)
                .append(name)
                .append(Component.text(" ⭐", NamedTextColor.GOLD));
        }
        
        entity.customName(name);
        entity.setCustomNameVisible(true);
    }
    
    /**
     * Ticks the customer's patience timer
     * @return true if customer is still waiting, false if they left
     */
    public boolean tick() {
        ticksRemaining--;
        
        // Update name every second
        if (ticksRemaining % 20 == 0) {
            updateCustomName();
        }
        
        // Customer leaves if patience runs out
        if (ticksRemaining <= 0) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Serves the customer with the correct food
     * @return points earned
     */
    public int serve() {
        return requestedRecipe.getPoints() * pointMultiplier;
    }
    
    /**
     * Removes the customer entity
     */
    public void remove() {
        if (entity != null && entity.isValid()) {
            entity.remove();
        }
    }
    
    public Entity getEntity() {
        return entity;
    }
    
    public Location getSpawnLocation() {
        return spawnLocation;
    }
    
    public DishDashRecipe getRequestedRecipe() {
        return requestedRecipe;
    }
    
    public boolean isSpecial() {
        return isSpecial;
    }
    
    public boolean isAngry() {
        return angry;
    }
    
    /**
     * Creates a random customer with weighted recipe selection
     * Simple recipes are more common than complex ones
     */
    public static DishDashCustomer createRandom(Location location) {
        DishDashRecipe[] recipes = DishDashRecipe.values();
        
        // Weight selection towards simpler recipes
        int roll = RANDOM.nextInt(100);
        DishDashRecipe selected;
        
        if (roll < 40) {
            // 40% chance for simple recipes
            DishDashRecipe[] simple = {
                DishDashRecipe.COOKED_BEEF,
                DishDashRecipe.COOKED_PORKCHOP,
                DishDashRecipe.BAKED_POTATO,
                DishDashRecipe.COOKED_CHICKEN,
                DishDashRecipe.WATER_BOTTLE
            };
            selected = simple[RANDOM.nextInt(simple.length)];
        } else if (roll < 70) {
            // 30% chance for medium recipes
            selected = DishDashRecipe.GOLDEN_CARROT;
        } else if (roll < 95) {
            // 25% chance for pork stew
            selected = DishDashRecipe.PORK_STEW;
        } else {
            // 5% chance for cake (reduced from 15%)
            selected = DishDashRecipe.CAKE;
        }
        
        // 10% chance for special customer (Happy Ghast)
        boolean isSpecial = RANDOM.nextInt(100) < 10;
        
        return new DishDashCustomer(location, selected, isSpecial);
    }
    
    /**
     * Creates a random customer with specified special status
     * Used to control where Happy Ghasts spawn (drive-thru only)
     */
    public static DishDashCustomer createRandomSpecial(Location location, boolean forceSpecial) {
        DishDashRecipe[] recipes = DishDashRecipe.values();
        
        // Weight selection towards simpler recipes
        int roll = RANDOM.nextInt(100);
        DishDashRecipe selected;
        
        if (roll < 40) {
            // 40% chance for simple recipes
            DishDashRecipe[] simple = {
                DishDashRecipe.COOKED_BEEF,
                DishDashRecipe.COOKED_PORKCHOP,
                DishDashRecipe.BAKED_POTATO,
                DishDashRecipe.COOKED_CHICKEN,
                DishDashRecipe.WATER_BOTTLE
            };
            selected = simple[RANDOM.nextInt(simple.length)];
        } else if (roll < 70) {
            // 30% chance for medium recipes
            selected = DishDashRecipe.GOLDEN_CARROT;
        } else if (roll < 95) {
            // 25% chance for pork stew
            selected = DishDashRecipe.PORK_STEW;
        } else {
            // 5% chance for cake (reduced from 15%)
            selected = DishDashRecipe.CAKE;
        }
        
        return new DishDashCustomer(location, selected, forceSpecial);
    }
}