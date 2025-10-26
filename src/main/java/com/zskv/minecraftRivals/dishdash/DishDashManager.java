package com.zskv.minecraftRivals.dishdash;

import com.zskv.minecraftRivals.MinecraftRivals;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Endermite;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Team;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Main manager for the Dish Dash minigame
 */
public class DishDashManager implements Listener {
    
    private static final int GAME_DURATION_MINUTES = 10;
    private static final int FINAL_COUNTDOWN_SECONDS = 30;
    private static final int CUSTOMERS_UNTIL_WARNING = 4;
    private static final int MAX_CUSTOMERS_PER_TEAM = 5; // 4 front + 1 drive-thru
    
    private final MinecraftRivals plugin;
    private boolean gameActive;
    private World gameWorld;
    
    private final Map<String, DishDashTeamData> teamDataMap;
    private BossBar gameBossBar;
    private BukkitTask gameTask;
    private BukkitTask customerTask;
    private BukkitTask ratTask;
    private BukkitTask fridgeTask;
    private BukkitTask cauldronTask;
    private BukkitTask cropGrowthTask;
    private BukkitTask musicTask;
    
    private Objective pointsObjective;
    
    private int gameTicksRemaining;
    private boolean finalCountdownStarted;
    private String firstFinishedTeam;
    
    // Cooking stations tracking
    private final Map<Location, CookingStation> activeStations;
    private final Map<UUID, Long> playerShopCooldowns;
    private final Map<UUID, Long> playerRatMessageCooldowns;
    private final Map<String, Long> teamWarningCooldowns;
    private final Map<String, Integer> teamSeedPurchases;
    private final Map<String, Integer> teamCarrotPurchases;
    private final Map<String, Integer> teamPotatoPurchases;
    private final Map<String, Integer> teamBucketPurchases;
    private final Map<UUID, Long> playerChickenEggCooldowns;
    
    // Station data class
    private static class CookingStation {
        Material input;
        long startTime;
        int cookTime; // in ticks
        
        CookingStation(Material input, int cookTime) {
            this.input = input;
            this.startTime = System.currentTimeMillis();
            this.cookTime = cookTime;
        }
    }
    
    public DishDashManager(MinecraftRivals plugin) {
        this.plugin = plugin;
        this.teamDataMap = new HashMap<>();
        this.gameActive = false;
        this.activeStations = new HashMap<>();
        this.playerShopCooldowns = new HashMap<>();
        this.playerRatMessageCooldowns = new HashMap<>();
        this.teamWarningCooldowns = new HashMap<>();
        this.teamSeedPurchases = new HashMap<>();
        this.teamCarrotPurchases = new HashMap<>();
        this.teamPotatoPurchases = new HashMap<>();
        this.teamBucketPurchases = new HashMap<>();
        this.playerChickenEggCooldowns = new HashMap<>();
        
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }
    
    /**
     * Starts the Dish Dash game
     */
    public synchronized boolean startGame(CommandSender sender) {
        if (gameActive) {
            sender.sendMessage(Component.text("Dish Dash is already running!", NamedTextColor.RED));
            return false;
        }
        
        // Get the DishDash world
        gameWorld = Bukkit.getWorld("DishDash");
        if (gameWorld == null) {
            sender.sendMessage(Component.text("DishDash world not found!", NamedTextColor.RED));
            return false;
        }
        
        // Initialize team data
        initializeTeamData();
        
        // Teleport players to their team spawns
        teleportPlayersToSpawns();
        
        // Start the game
        gameActive = true;
        gameTicksRemaining = GAME_DURATION_MINUTES * 60 * 20; // Convert to ticks
        finalCountdownStarted = false;
        firstFinishedTeam = null;
        
        // Create boss bar
        gameBossBar = Bukkit.createBossBar(
            "Dish Dash",
            BarColor.YELLOW,
            BarStyle.SOLID
        );
        
        // Add all players to boss bar
        Bukkit.getOnlinePlayers().forEach(gameBossBar::addPlayer);
        
        // Setup scoreboard
        setupScoreboard();
        
        // Play music
        playMusic();
        
        // Start game loop
        startGameLoop();
        
        // Start customer spawning
        startCustomerSpawning();
        
        // Start rat spawning
        startRatSpawning();
        
        // Start fridge monitoring
        startFridgeMonitoring();
        
        // Start cauldron/composter filling
        startCauldronFilling();
        
        // Start crop growth acceleration
        startCropGrowth();
        
        // Give players starting items
        giveStartingItems();
        
        // Announce game start
        broadcastToAll(Component.text("🍽 Dish Dash has started! 🍽", NamedTextColor.GOLD, TextDecoration.BOLD));
        broadcastToAll(Component.text("Serve customers to earn points!", NamedTextColor.YELLOW));
        
        return true;
    }
    
    /**
     * Stops the Dish Dash game
     */
    public synchronized boolean stopGame(CommandSender sender) {
        if (!gameActive) {
            return false;
        }
        
        gameActive = false;
        
        // Cancel tasks
        if (gameTask != null) {
            gameTask.cancel();
            gameTask = null;
        }
        if (customerTask != null) {
            customerTask.cancel();
            customerTask = null;
        }
        if (ratTask != null) {
            ratTask.cancel();
            ratTask = null;
        }
        if (fridgeTask != null) {
            fridgeTask.cancel();
            fridgeTask = null;
        }
        if (cauldronTask != null) {
            cauldronTask.cancel();
            cauldronTask = null;
        }
        if (cropGrowthTask != null) {
            cropGrowthTask.cancel();
            cropGrowthTask = null;
        }
        if (musicTask != null) {
            musicTask.cancel();
            musicTask = null;
        }
        
        // Remove boss bar
        if (gameBossBar != null) {
            gameBossBar.removeAll();
            gameBossBar = null;
        }
        
        // Clear scoreboard
        if (pointsObjective != null) {
            pointsObjective.unregister();
            pointsObjective = null;
        }
        
        // Stop music for all players
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.stopSound("custom:dish_dash", org.bukkit.SoundCategory.VOICE);
            player.stopSound("custom:dish_dash_fast", org.bukkit.SoundCategory.VOICE);
        }
        
        // Clear all customers
        teamDataMap.values().forEach(DishDashTeamData::clearCustomers);
        
        // Clear rats
        clearRats();
        
        // Clear active cooking stations
        activeStations.clear();
        
        // Clear purchase tracking
        teamSeedPurchases.clear();
        teamCarrotPurchases.clear();
        teamPotatoPurchases.clear();
        teamBucketPurchases.clear();
        
        // Announce results
        announceResults();
        
        // Clear team data
        teamDataMap.clear();
        
        return true;
    }
    
    /**
     * Initialize team data with coordinates
     */
    private void initializeTeamData() {
        teamDataMap.clear();
        
        // Red Team
        teamDataMap.put("Red", new DishDashTeamData(
            "Red",
            new Location(gameWorld, -79, -59, 25),
            new Location(gameWorld, -70, -59, 28),
            new Location(gameWorld, -70, -59, 25),
            Arrays.asList(
                new Location(gameWorld, -82, -59, 44),
                new Location(gameWorld, -80, -59, 44),
                new Location(gameWorld, -78, -59, 44),
                new Location(gameWorld, -76, -59, 44)
            ),
            new Location(gameWorld, -87, -59, 36),
            new Location(gameWorld, -81, -59, 27),  // Cauldron
            new Location(gameWorld, -82, -59, 19),  // Composter
            new Location(gameWorld, -82, -60, 21)   // Seed Barrel
        ));
        
        // Orange Team
        teamDataMap.put("Orange", new DishDashTeamData(
            "Orange",
            new Location(gameWorld, 0, -59, 25),
            new Location(gameWorld, 9, -59, 28),
            new Location(gameWorld, 9, -59, 25),
            Arrays.asList(
                new Location(gameWorld, 3, -59, 44),
                new Location(gameWorld, 1, -59, 44),
                new Location(gameWorld, -1, -59, 44),
                new Location(gameWorld, -3, -59, 44)
            ),
            new Location(gameWorld, -8, -59, 36),
            new Location(gameWorld, -2, -59, 27),  // Cauldron
            new Location(gameWorld, -3, -59, 19),  // Composter
            new Location(gameWorld, -3, -60, 21)   // Seed Barrel
        ));
        
        // Yellow Team
        teamDataMap.put("Yellow", new DishDashTeamData(
            "Yellow",
            new Location(gameWorld, 79, -59, 25),
            new Location(gameWorld, 88, -59, 28),
            new Location(gameWorld, 88, -59, 25),
            Arrays.asList(
                new Location(gameWorld, 76, -59, 44),
                new Location(gameWorld, 78, -59, 44),
                new Location(gameWorld, 80, -59, 44),
                new Location(gameWorld, 82, -59, 44)
            ),
            new Location(gameWorld, 71, -59, 36),
            new Location(gameWorld, 77, -59, 27),  // Cauldron
            new Location(gameWorld, 76, -59, 19),  // Composter
            new Location(gameWorld, 76, -60, 21)   // Seed Barrel
        ));
        
        // Lime Team
        teamDataMap.put("Lime", new DishDashTeamData(
            "Lime",
            new Location(gameWorld, 79, -59, 106),
            new Location(gameWorld, 88, -59, 109),
            new Location(gameWorld, 88, -59, 106),
            Arrays.asList(
                new Location(gameWorld, 76, -59, 125),
                new Location(gameWorld, 78, -59, 125),
                new Location(gameWorld, 80, -59, 125),
                new Location(gameWorld, 82, -59, 125)
            ),
            new Location(gameWorld, 71, -59, 117),
            new Location(gameWorld, 77, -59, 108),  // Cauldron
            new Location(gameWorld, 76, -59, 100),  // Composter
            new Location(gameWorld, 76, -60, 102)   // Seed Barrel
        ));
        
        // Green Team
        teamDataMap.put("Green", new DishDashTeamData(
            "Green",
            new Location(gameWorld, 0, -59, 106),
            new Location(gameWorld, 9, -59, 109),
            new Location(gameWorld, 9, -59, 106),
            Arrays.asList(
                new Location(gameWorld, 3, -59, 125),
                new Location(gameWorld, 1, -59, 125),
                new Location(gameWorld, -1, -59, 125),
                new Location(gameWorld, -3, -59, 125)
            ),
            new Location(gameWorld, -8, -59, 117),
            new Location(gameWorld, -2, -59, 108),  // Cauldron
            new Location(gameWorld, -3, -59, 100),  // Composter
            new Location(gameWorld, -3, -60, 102)   // Seed Barrel
        ));
        
        // Blue Team
        teamDataMap.put("Blue", new DishDashTeamData(
            "Blue",
            new Location(gameWorld, -79, -59, 106),
            new Location(gameWorld, -70, -59, 109),
            new Location(gameWorld, -70, -59, 106),
            Arrays.asList(
                new Location(gameWorld, -76, -59, 125),
                new Location(gameWorld, -78, -59, 125),
                new Location(gameWorld, -80, -59, 125),
                new Location(gameWorld, -82, -59, 125)
            ),
            new Location(gameWorld, -87, -59, 117),
            new Location(gameWorld, -81, -59, 108),  // Cauldron
            new Location(gameWorld, -82, -59, 100),  // Composter
            new Location(gameWorld, -82, -60, 102)   // Seed Barrel
        ));
        
        // Purple Team
        teamDataMap.put("Purple", new DishDashTeamData(
            "Purple",
            new Location(gameWorld, 0, -59, 187),
            new Location(gameWorld, 9, -59, 190),
            new Location(gameWorld, 9, -59, 187),
            Arrays.asList(
                new Location(gameWorld, 3, -59, 206),
                new Location(gameWorld, 1, -59, 206),
                new Location(gameWorld, -1, -59, 206),
                new Location(gameWorld, -3, -59, 206)
            ),
            new Location(gameWorld, -8, -59, 197),
            new Location(gameWorld, -2, -59, 189),  // Cauldron
            new Location(gameWorld, -3, -59, 181),  // Composter
            new Location(gameWorld, -3, -60, 183)   // Seed Barrel
        ));
        
        // Pink Team
        teamDataMap.put("Pink", new DishDashTeamData(
            "Pink",
            new Location(gameWorld, -79, -59, 187),
            new Location(gameWorld, -70, -59, 190),
            new Location(gameWorld, -70, -59, 187),
            Arrays.asList(
                new Location(gameWorld, -82, -59, 206),
                new Location(gameWorld, -80, -59, 206),
                new Location(gameWorld, -78, -59, 206),
                new Location(gameWorld, -76, -59, 206)
            ),
            new Location(gameWorld, -87, -59, 197),
            new Location(gameWorld, -81, -59, 189),  // Cauldron
            new Location(gameWorld, -82, -59, 181),  // Composter
            new Location(gameWorld, -82, -60, 183)   // Seed Barrel
        ));
    }
    
    /**
     * Teleport players to their team spawns
     */
    private void teleportPlayersToSpawns() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Team team = plugin.getScoreboard().getPlayerTeam(player);
            if (team != null) {
                DishDashTeamData teamData = teamDataMap.get(team.getName());
                if (teamData != null) {
                    player.teleport(teamData.getSpawnLocation());
                    player.setGameMode(GameMode.SURVIVAL);
                }
            }
        }
    }
    
    /**
     * Main game loop
     */
    private void startGameLoop() {
        gameTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!gameActive) {
                    cancel();
                    return;
                }
                
                gameTicksRemaining--;
                
                // Update boss bar
                updateBossBar();
                
                // Tick all customers
                tickCustomers();
                
                // Check win conditions
                checkWinConditions();
                
                // Game ends at night (10 minutes)
                if (gameTicksRemaining <= 0) {
                    endGame();
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
    
    /**
     * Update the boss bar display
     */
    private void updateBossBar() {
        if (gameBossBar == null) return;
        
        int secondsRemaining = gameTicksRemaining / 20;
        int minutes = secondsRemaining / 60;
        int seconds = secondsRemaining % 60;
        
        String timeStr = String.format("%d:%02d", minutes, seconds);
        
        if (finalCountdownStarted) {
            gameBossBar.setTitle("⚠ FINAL COUNTDOWN: " + timeStr + " ⚠");
            gameBossBar.setColor(BarColor.RED);
        } else {
            gameBossBar.setTitle("Dish Dash - Time: " + timeStr);
        }
        
        double progress = (double) gameTicksRemaining / (GAME_DURATION_MINUTES * 60 * 20);
        gameBossBar.setProgress(Math.max(0.0, Math.min(1.0, progress)));
    }
    
    /**
     * Tick all active customers
     */
    private void tickCustomers() {
        for (DishDashTeamData teamData : teamDataMap.values()) {
            List<DishDashCustomer> toRemove = new ArrayList<>();
            
            for (DishDashCustomer customer : teamData.getActiveCustomers()) {
                if (!customer.tick()) {
                    // Customer left angry
                    toRemove.add(customer);
                    Location leftLoc = customer.getSpawnLocation();
                    customer.remove();
                    plugin.getLogger().info("Customer left angry from: " + leftLoc.getBlockX() + ", " + leftLoc.getBlockY() + ", " + leftLoc.getBlockZ());
                }
            }
            
            if (!toRemove.isEmpty()) {
                teamData.getActiveCustomers().removeAll(toRemove);
                plugin.getLogger().info("Team " + teamData.getTeamName() + " now has " + teamData.getActiveCustomers().size() + " active customers after timeout");
            }
        }
    }
    
    /**
     * Spawn customers for teams
     */
    private void startCustomerSpawning() {
        customerTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!gameActive) {
                    cancel();
                    return;
                }
                
                spawnCustomersForAllTeams();
            }
        }.runTaskTimer(plugin, 20L, 300L); // Spawn every 15 seconds
    }
    
    /**
     * Spawn customers for all teams
     */
    private void spawnCustomersForAllTeams() {
        for (DishDashTeamData teamData : teamDataMap.values()) {
            if (teamData.isFinished()) continue;
            
            // Clean up invalid customers first
            List<DishDashCustomer> toRemove = new ArrayList<>();
            for (DishDashCustomer customer : teamData.getActiveCustomers()) {
                if (customer.getEntity() == null || !customer.getEntity().isValid()) {
                    toRemove.add(customer);
                }
            }
            teamData.getActiveCustomers().removeAll(toRemove);
            
            // Don't spawn if at max capacity
            if (teamData.getActiveCustomers().size() >= MAX_CUSTOMERS_PER_TEAM) {
                continue;
            }
            
            // Get available front locations
            List<Location> availableFrontLocations = new ArrayList<>(teamData.getCustomerLocations());
            Location driveThruLoc = teamData.getDriveThruLocation();
            
            // Debug: Log current customers and their spawn locations
            plugin.getLogger().info("Team " + teamData.getTeamName() + " has " + teamData.getActiveCustomers().size() + " active customers");
            
            // Remove occupied locations (check exact spawn locations to prevent overlap)
            for (DishDashCustomer customer : teamData.getActiveCustomers()) {
                Location spawnLoc = customer.getSpawnLocation();
                
                plugin.getLogger().info("  Customer at: " + spawnLoc.getBlockX() + ", " + spawnLoc.getBlockY() + ", " + spawnLoc.getBlockZ());
                
                // Remove locations that match this spawn point
                availableFrontLocations.removeIf(loc -> 
                    loc.getBlockX() == spawnLoc.getBlockX() && 
                    loc.getBlockY() == spawnLoc.getBlockY() && 
                    loc.getBlockZ() == spawnLoc.getBlockZ()
                );
                
                // Check if drive-thru is occupied (exact location match)
                if (driveThruLoc != null && 
                    driveThruLoc.getBlockX() == spawnLoc.getBlockX() &&
                    driveThruLoc.getBlockY() == spawnLoc.getBlockY() &&
                    driveThruLoc.getBlockZ() == spawnLoc.getBlockZ()) {
                    driveThruLoc = null; // Mark as occupied
                    plugin.getLogger().info("  Drive-thru occupied");
                }
            }
            
            plugin.getLogger().info("  Available front locations: " + availableFrontLocations.size());
            
            // 20% chance to spawn at drive-thru (Mooshroom only)
            if (driveThruLoc != null && new Random().nextInt(100) < 20) {
                DishDashCustomer customer = DishDashCustomer.createRandomSpecial(driveThruLoc, true);
                teamData.addCustomer(customer);
                plugin.getLogger().info("  Spawned customer at drive-thru: " + driveThruLoc.getBlockX() + ", " + driveThruLoc.getBlockY() + ", " + driveThruLoc.getBlockZ());
            }
            // Otherwise spawn regular customer at front
            else if (!availableFrontLocations.isEmpty()) {
                Location spawnLoc = availableFrontLocations.get(new Random().nextInt(availableFrontLocations.size()));
                DishDashCustomer customer = DishDashCustomer.createRandomSpecial(spawnLoc, false);
                teamData.addCustomer(customer);
                plugin.getLogger().info("  Spawned customer at front: " + spawnLoc.getBlockX() + ", " + spawnLoc.getBlockY() + ", " + spawnLoc.getBlockZ());
            }
        }
    }
    
    /**
     * Start spawning rats
     */
    private void startRatSpawning() {
        ratTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!gameActive) {
                    cancel();
                    return;
                }
                
                spawnRats();
            }
        }.runTaskTimer(plugin, 200L, 400L); // Spawn every 20 seconds
    }
    
    /**
     * Spawn rats near players
     */
    private void spawnRats() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().equals(gameWorld)) {
                // 30% chance to spawn a rat near this player
                if (new Random().nextInt(100) < 30) {
                    Location loc = player.getLocation().add(
                        (new Random().nextDouble() - 0.5) * 5,
                        0,
                        (new Random().nextDouble() - 0.5) * 5
                    );
                    
                    Endermite rat = (Endermite) gameWorld.spawnEntity(loc, org.bukkit.entity.EntityType.ENDERMITE);
                    rat.customName(Component.text("🐀 Rat", NamedTextColor.GRAY));
                    rat.setCustomNameVisible(true);
                }
            }
        }
    }
    
    /**
     * Clear all rats from the game
     */
    private void clearRats() {
        if (gameWorld == null) return;
        
        gameWorld.getEntitiesByClass(Endermite.class).forEach(Entity::remove);
    }
    
    /**
     * Check win conditions
     */
    private void checkWinConditions() {
        for (DishDashTeamData teamData : teamDataMap.values()) {
            if (teamData.isFinished()) continue;
            
            // Check if team is close to finishing (warning)
            int customersLeft = MAX_CUSTOMERS_PER_TEAM - teamData.getCustomersServed();
            if (customersLeft == CUSTOMERS_UNTIL_WARNING) {
                announceTeamCloseToFinishing(teamData.getTeamName());
            }
            
            // Check if team finished all customers
            if (teamData.getCustomersServed() >= 20) { // Arbitrary number for "all customers"
                teamData.setFinished(true);
                
                if (firstFinishedTeam == null) {
                    firstFinishedTeam = teamData.getTeamName();
                    startFinalCountdown();
                }
            }
        }
    }
    
    /**
     * Start the final countdown when first team finishes
     */
    private void startFinalCountdown() {
        if (finalCountdownStarted) return;
        
        finalCountdownStarted = true;
        gameTicksRemaining = Math.min(gameTicksRemaining, FINAL_COUNTDOWN_SECONDS * 20);
        
        broadcastToAll(Component.text("⚠ " + firstFinishedTeam + " team finished! ⚠", NamedTextColor.RED, TextDecoration.BOLD));
        broadcastToAll(Component.text("Final countdown started! Hurry up!", NamedTextColor.YELLOW));
        
        // Play sound
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f);
        }
    }
    
    /**
     * Announce that a team is close to finishing (with cooldown to prevent spam)
     */
    private void announceTeamCloseToFinishing(String teamName) {
        long currentTime = System.currentTimeMillis();
        
        // Check cooldown (30 seconds)
        if (teamWarningCooldowns.containsKey(teamName)) {
            long lastWarning = teamWarningCooldowns.get(teamName);
            if (currentTime - lastWarning < 30000) {
                return; // Still on cooldown
            }
        }
        
        teamWarningCooldowns.put(teamName, currentTime);
        broadcastToAll(Component.text(teamName + " team has only " + CUSTOMERS_UNTIL_WARNING + " customers left!", NamedTextColor.YELLOW));
    }
    
    /**
     * End the game
     */
    private void endGame() {
        stopGame(Bukkit.getConsoleSender());
    }
    
    /**
     * Announce game results
     */
    private void announceResults() {
        broadcastToAll(Component.text("═══════════════════════════", NamedTextColor.GOLD, TextDecoration.BOLD));
        broadcastToAll(Component.text("🍽 DISH DASH RESULTS 🍽", NamedTextColor.GOLD, TextDecoration.BOLD));
        broadcastToAll(Component.text("═══════════════════════════", NamedTextColor.GOLD, TextDecoration.BOLD));
        
        // Sort teams by points
        List<DishDashTeamData> sortedTeams = teamDataMap.values().stream()
            .sorted(Comparator.comparingInt(DishDashTeamData::getPoints).reversed())
            .collect(Collectors.toList());
        
        int rank = 1;
        for (DishDashTeamData teamData : sortedTeams) {
            Component rankComponent = Component.text(rank + ". ", NamedTextColor.YELLOW)
                .append(Component.text(teamData.getTeamName(), NamedTextColor.WHITE))
                .append(Component.text(" - ", NamedTextColor.GRAY))
                .append(Component.text(teamData.getPoints() + " points", NamedTextColor.GREEN))
                .append(Component.text(" (" + teamData.getCustomersServed() + " served)", NamedTextColor.GRAY));
            
            broadcastToAll(rankComponent);
            rank++;
        }
        
        broadcastToAll(Component.text("═══════════════════════════", NamedTextColor.GOLD, TextDecoration.BOLD));
    }
    
    /**
     * Broadcast message to all players
     */
    private void broadcastToAll(Component message) {
        Bukkit.getOnlinePlayers().forEach(player -> player.sendMessage(message));
    }
    
    /**
     * Handle shop GUI interaction
     */
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!gameActive) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_AIR) return;
        
        Player player = event.getPlayer();
        Team team = plugin.getScoreboard().getPlayerTeam(player);
        if (team == null) return;
        
        DishDashTeamData teamData = teamDataMap.get(team.getName());
        if (teamData == null) return;
        
        // Check if player is at shop GUI location
        Location shopLoc = teamData.getShopGuiLocation();
        if (player.getLocation().distance(shopLoc) < 2.0) {
            // Check cooldown to prevent spam
            UUID playerId = player.getUniqueId();
            if (playerShopCooldowns.containsKey(playerId)) {
                long lastUse = playerShopCooldowns.get(playerId);
                if (System.currentTimeMillis() - lastUse < 500) { // 0.5 second cooldown
                    event.setCancelled(true);
                    return;
                }
            }
            
            playerShopCooldowns.put(playerId, System.currentTimeMillis());
            openShopGUI(player, teamData);
            event.setCancelled(true);
        }
    }
    
    /**
     * Open the shop GUI for a player
     */
    private void openShopGUI(Player player, DishDashTeamData teamData) {
        Inventory shop = Bukkit.createInventory(null, 54, Component.text("Supply Shop", NamedTextColor.GOLD));
        
        // Row 2: Basic Supplies (centered)
        shop.setItem(11, createShopItem(Material.BUCKET, "Bucket", 1));
        shop.setItem(13, createShopItem(Material.GLASS_BOTTLE, "Glass Bottle", 1));
        shop.setItem(15, createShopItem(Material.BOWL, "Bowl", 1));
        
        // Row 3: Seeds & Crops (centered)
        shop.setItem(20, createShopItem(Material.WHEAT_SEEDS, "Wheat Seeds", 3));
        shop.setItem(22, createShopItem(Material.CARROT, "Carrot", 2));
        shop.setItem(24, createShopItem(Material.POTATO, "Potato", 2));
        
        // Row 4: Special Ingredients (centered)
        shop.setItem(30, createShopItem(Material.GOLD_INGOT, "Gold Ingot", 1));
        shop.setItem(32, createShopItem(Material.SUGAR, "Sugar", 2));
        
        player.openInventory(shop);
    }
    
    /**
     * Create a shop item
     */
    private ItemStack createShopItem(Material material, String name, int amount) {
        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.YELLOW));
        item.setItemMeta(meta);
        return item;
    }
    
    /**
     * Handle shop GUI clicks
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!gameActive) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        
        String title = event.getView().title().toString();
        if (!title.contains("Supply Shop")) return;
        
        event.setCancelled(true);
        
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        
        Team team = plugin.getScoreboard().getPlayerTeam(player);
        if (team == null) return;
        
        DishDashTeamData teamData = teamDataMap.get(team.getName());
        if (teamData == null) return;
        
        String teamName = team.getName();
        Material itemType = clicked.getType();
        
        // Check purchase limits for buckets (max 2 per team)
        if (itemType == Material.BUCKET) {
            int purchased = teamBucketPurchases.getOrDefault(teamName, 0);
            if (purchased >= 2) {
                player.sendMessage(Component.text("Your team has already purchased 2 buckets!", NamedTextColor.RED));
                player.closeInventory();
                return;
            }
            teamBucketPurchases.put(teamName, purchased + 1);
        }
        // Check purchase limits for seeds, carrots, and potatoes
        else if (itemType == Material.WHEAT_SEEDS) {
            int purchased = teamSeedPurchases.getOrDefault(teamName, 0);
            if (purchased >= 1) {
                player.sendMessage(Component.text("Your team has already purchased seeds!", NamedTextColor.RED));
                player.closeInventory();
                return;
            }
            teamSeedPurchases.put(teamName, purchased + 1);
        } else if (itemType == Material.CARROT) {
            int purchased = teamCarrotPurchases.getOrDefault(teamName, 0);
            if (purchased >= 1) {
                player.sendMessage(Component.text("Your team has already purchased carrots!", NamedTextColor.RED));
                player.closeInventory();
                return;
            }
            teamCarrotPurchases.put(teamName, purchased + 1);
        } else if (itemType == Material.POTATO) {
            int purchased = teamPotatoPurchases.getOrDefault(teamName, 0);
            if (purchased >= 1) {
                player.sendMessage(Component.text("Your team has already purchased potatoes!", NamedTextColor.RED));
                player.closeInventory();
                return;
            }
            teamPotatoPurchases.put(teamName, purchased + 1);
        }
        
        // Deliver item to barrel with animation
        deliverItemToBarrel(player, teamData, itemType);
        player.closeInventory();
    }
    
    /**
     * Deliver purchased item to team barrel
     */
    private void deliverItemToBarrel(Player player, DishDashTeamData teamData, Material item) {
        Location barrelLoc = teamData.getBarrelLocation();
        
        // Spawn item directly above barrel (reduced height for better accuracy)
        Location spawnLoc = barrelLoc.clone().add(0.5, 3, 0.5);
        org.bukkit.entity.Item droppedItem = gameWorld.dropItem(spawnLoc, new ItemStack(item));
        droppedItem.setVelocity(droppedItem.getVelocity().multiply(0)); // Remove horizontal velocity
        
        // Play sound
        barrelLoc.getWorld().playSound(barrelLoc, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0f, 1.0f);
        
        // Schedule boom sound when it lands
        new BukkitRunnable() {
            @Override
            public void run() {
                barrelLoc.getWorld().playSound(barrelLoc, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.5f);
            }
        }.runTaskLater(plugin, 15L);
        
        player.sendActionBar(Component.text("Item ordered! Check the barrel.", NamedTextColor.GREEN));
    }
    
    /**
     * Handle player interacting with customers to serve food
     */
    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractAtEntityEvent event) {
        if (!gameActive) return;
        
        Player player = event.getPlayer();
        Entity entity = event.getRightClicked();
        ItemStack handItem = player.getInventory().getItemInMainHand();
        
        Team team = plugin.getScoreboard().getPlayerTeam(player);
        if (team == null) return;
        
        DishDashTeamData teamData = teamDataMap.get(team.getName());
        if (teamData == null) return;
        
        // Check if entity is a customer (check by UUID for more reliable matching)
        DishDashCustomer customer = null;
        for (DishDashCustomer c : teamData.getActiveCustomers()) {
            if (c.getEntity() != null && c.getEntity().isValid() && 
                c.getEntity().getUniqueId().equals(entity.getUniqueId())) {
                customer = c;
                break;
            }
        }
        
        if (customer != null) {
            // Interacting with customer - try to serve food
            Material requestedFood = customer.getRequestedRecipe().getResult();
            Material handFood = handItem.getType();
            
            // Debug logging
            plugin.getLogger().info("Player " + player.getName() + " interacting with customer");
            plugin.getLogger().info("Requested: " + requestedFood + ", Holding: " + handFood);
            
            if (handFood == requestedFood) {
                // Correct food! Serve the customer
                int pointsEarned = customer.serve();
                teamData.addPoints(pointsEarned);
                teamData.incrementCustomersServed();
                
                plugin.getLogger().info("Customer served! Points earned: " + pointsEarned + ", Total: " + teamData.getPoints());
                
                // Update scoreboard
                updateScoreboard();
                
                // Remove one item from hand
                handItem.setAmount(handItem.getAmount() - 1);
                
                // Remove customer
                Location removedLoc = customer.getSpawnLocation();
                teamData.removeCustomer(customer);
                customer.remove();
                
                plugin.getLogger().info("Removed customer from: " + removedLoc.getBlockX() + ", " + removedLoc.getBlockY() + ", " + removedLoc.getBlockZ());
                plugin.getLogger().info("Team " + team.getName() + " now has " + teamData.getActiveCustomers().size() + " active customers");
                
                // Feedback
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
                player.sendActionBar(Component.text("✓ Customer served! +" + pointsEarned + " points", NamedTextColor.GREEN));
                
                // Announce to team
                announceToTeam(team, Component.text(player.getName() + " served a customer! (+" + pointsEarned + " points)", NamedTextColor.GREEN));
                
                event.setCancelled(true);
            } else if (handFood != Material.AIR) {
                // Wrong food (only show if holding something)
                player.sendActionBar(Component.text("✗ That's not what they ordered! They want: " + customer.getRequestedRecipe().getDisplayName(), NamedTextColor.RED));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            }
            return;
        }
        
        // Check if entity is a barn animal
        if (entity instanceof org.bukkit.entity.Chicken chicken) {
            // Collect egg from chicken (with 3 second cooldown)
            if (handItem.getType() == Material.AIR || handItem.getType() == Material.EGG) {
                long currentTime = System.currentTimeMillis();
                long lastCollect = playerChickenEggCooldowns.getOrDefault(player.getUniqueId(), 0L);
                
                if (currentTime - lastCollect >= 3000) { // 3 seconds = 3000ms
                    player.getInventory().addItem(new ItemStack(Material.EGG));
                    player.playSound(player.getLocation(), Sound.ENTITY_CHICKEN_EGG, 1.0f, 1.0f);
                    playerChickenEggCooldowns.put(player.getUniqueId(), currentTime);
                } else {
                    // Still on cooldown
                    long remainingMs = 3000 - (currentTime - lastCollect);
                    player.sendActionBar(Component.text("Wait " + (remainingMs / 1000.0) + "s before collecting another egg", NamedTextColor.RED));
                }
                event.setCancelled(true);
            }
        } else if (entity instanceof org.bukkit.entity.Cow cow) {
            // Milk cow with bucket
            if (handItem.getType() == Material.BUCKET) {
                handItem.setAmount(handItem.getAmount() - 1);
                player.getInventory().addItem(new ItemStack(Material.MILK_BUCKET));
                player.playSound(player.getLocation(), Sound.ENTITY_COW_MILK, 1.0f, 1.0f);
                event.setCancelled(true);
            }
        } else if (entity instanceof org.bukkit.entity.Pig pig) {
            // Kill pig for porkchop (with trident)
            if (handItem.getType() == Material.TRIDENT) {
                pig.damage(100.0);
                pig.getWorld().dropItemNaturally(pig.getLocation(), new ItemStack(Material.PORKCHOP, 2));
                player.playSound(player.getLocation(), Sound.ENTITY_PIG_DEATH, 1.0f, 1.0f);
                event.setCancelled(true);
            }
        }
    }
    
    /**
     * Handle kitchen station interactions (stove, oven, combiner)
     */
    @EventHandler
    public void onBlockInteract(PlayerInteractEvent event) {
        if (!gameActive) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;
        
        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        Material blockType = block.getType();
        ItemStack handItem = player.getInventory().getItemInMainHand();
        
        // Stove (Campfire) - cooks beef, porkchop, chicken, and potato
        if (blockType == Material.CAMPFIRE || blockType == Material.SOUL_CAMPFIRE) {
            // Always cancel campfire interaction to prevent vanilla behavior
            event.setCancelled(true);
            
            if (handItem.getType() == Material.BEEF) {
                cookItem(player, handItem, Material.COOKED_BEEF, 100, "Cooking beef...", block); // 5 seconds
            } else if (handItem.getType() == Material.PORKCHOP) {
                cookItem(player, handItem, Material.COOKED_PORKCHOP, 100, "Cooking porkchop...", block); // 5 seconds
            } else if (handItem.getType() == Material.CHICKEN) {
                cookItem(player, handItem, Material.COOKED_CHICKEN, 100, "Cooking chicken...", block); // 5 seconds
            } else if (handItem.getType() == Material.POTATO) {
                cookItem(player, handItem, Material.BAKED_POTATO, 100, "Baking potato...", block); // 5 seconds
            }
        }
        
        // Oven/Grill (Smoker) - cooks potato and chicken
        else if (blockType == Material.SMOKER) {
            // Always cancel smoker interaction to prevent vanilla GUI
            event.setCancelled(true);
            
            if (handItem.getType() == Material.POTATO) {
                cookItem(player, handItem, Material.BAKED_POTATO, 100, "Baking potato...", block); // 5 seconds
            } else if (handItem.getType() == Material.CHICKEN) {
                cookItem(player, handItem, Material.COOKED_CHICKEN, 100, "Cooking chicken...", block); // 5 seconds
            }
        }
        
        // Crafting Table - allow normal crafting GUI to open
        // (No custom handling needed - let Minecraft handle it naturally)
        
        // Sink (Cauldron) - fill water bottles
        else if (blockType == Material.WATER_CAULDRON) {
            if (handItem.getType() == Material.GLASS_BOTTLE) {
                // Fill bottle with water
                handItem.setAmount(handItem.getAmount() - 1);
                player.getInventory().addItem(new ItemStack(Material.POTION)); // Water bottle
                player.playSound(player.getLocation(), Sound.ITEM_BOTTLE_FILL, 1.0f, 1.0f);
                player.sendActionBar(Component.text("Filled water bottle!", NamedTextColor.AQUA));
                
                // Reduce cauldron level (handled by Bukkit naturally)
                event.setCancelled(false); // Let Bukkit handle it
            }
        }
        
        // Composter - convert items to bonemeal
        else if (blockType == Material.COMPOSTER) {
            // Let Bukkit handle composting naturally
            // When full, it produces bonemeal
        }
        
        // Farm - use bonemeal on crops
        else if (blockType == Material.WHEAT || blockType == Material.CARROTS || blockType == Material.POTATOES) {
            if (handItem.getType() == Material.BONE_MEAL) {
                // Let Bukkit handle bonemeal growth naturally
                player.sendActionBar(Component.text("Growing crops...", NamedTextColor.GREEN));
            }
        }
        
        // Walk-in fridge entrance - apply slowness when entering
        else if (blockType == Material.IRON_DOOR || blockType == Material.IRON_TRAPDOOR) {
            // Check if player is entering a cold area (you can customize this based on location)
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (isInFridge(player.getLocation())) {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 1, false, false));
                    }
                }
            }.runTaskLater(plugin, 5L);
        }
        
        // Target block in fridge - hit with trident to get meat
        else if (blockType == Material.TARGET) {
            // This will be handled by projectile hit event
        }
    }
    
    /**
     * Check if a location is inside a walk-in fridge (cold area)
     * This is a simple check - you can customize based on your map
     */
    private boolean isInFridge(Location loc) {
        // Check if player is in a cold biome or specific area
        // For now, just check if they're near ice blocks
        for (int x = -3; x <= 3; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -3; z <= 3; z++) {
                    Block block = loc.clone().add(x, y, z).getBlock();
                    if (block.getType() == Material.ICE || block.getType() == Material.PACKED_ICE || 
                        block.getType() == Material.BLUE_ICE) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
    
    /**
     * Handle trident hitting target blocks in fridge
     */
    @EventHandler
    public void onProjectileHit(org.bukkit.event.entity.ProjectileHitEvent event) {
        if (!gameActive) return;
        if (!(event.getEntity() instanceof org.bukkit.entity.Trident trident)) return;
        if (!(trident.getShooter() instanceof Player player)) return;
        
        Block hitBlock = event.getHitBlock();
        if (hitBlock != null && hitBlock.getType() == Material.TARGET) {
            // Give specific meat based on Y-coordinate (only 1 item)
            // Y=-59: Raw Chicken | Y=-58: Raw Porkchop | Y=-57: Raw Beef
            int yLevel = hitBlock.getY();
            Material meat;
            
            if (yLevel == -59) {
                meat = Material.CHICKEN;
            } else if (yLevel == -58) {
                meat = Material.PORKCHOP;
            } else if (yLevel == -57) {
                meat = Material.BEEF;
            } else {
                // If Y-level doesn't match expected values, don't give anything
                player.sendActionBar(Component.text("Target block at wrong Y-level: " + yLevel, NamedTextColor.RED));
                return;
            }
            
            player.getInventory().addItem(new ItemStack(meat, 1));
            player.playSound(player.getLocation(), Sound.ENTITY_ARROW_HIT_PLAYER, 1.0f, 1.0f);
            player.sendActionBar(Component.text("Got " + meat.name().toLowerCase().replace("_", " ") + " from the fridge!", NamedTextColor.GREEN));
        }
    }
    
    /**
     * Cook an item with a delay
     */
    private void cookItem(Player player, ItemStack input, Material output, int ticks, String message, Block station) {
        Location stationLoc = station.getLocation();
        
        // Check if station is already in use
        if (activeStations.containsKey(stationLoc)) {
            player.sendActionBar(Component.text("This station is already in use!", NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }
        
        // Mark station as in use
        activeStations.put(stationLoc, new CookingStation(input.getType(), ticks));
        
        player.sendActionBar(Component.text(message, NamedTextColor.YELLOW));
        player.playSound(player.getLocation(), Sound.BLOCK_FIRE_AMBIENT, 0.5f, 1.0f);
        
        // Remove input item
        input.setAmount(input.getAmount() - 1);
        
        // Give output after delay
        new BukkitRunnable() {
            @Override
            public void run() {
                player.getInventory().addItem(new ItemStack(output));
                player.playSound(player.getLocation(), Sound.BLOCK_LAVA_POP, 1.0f, 1.5f);
                player.sendActionBar(Component.text("Cooking complete!", NamedTextColor.GREEN));
                
                // Remove station from active tracking
                activeStations.remove(stationLoc);
            }
        }.runTaskLater(plugin, ticks);
    }
    
    /**
     * Handle rat killing with thrown tridents
     */
    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!gameActive) return;
        if (!(event.getEntity() instanceof Endermite)) return;
        
        Endermite rat = (Endermite) event.getEntity();
        if (rat.customName() != null && rat.customName().toString().contains("Rat")) {
            // Check if damage is from a thrown trident (projectile)
            if (event.getDamager() instanceof org.bukkit.entity.Trident) {
                // Thrown trident - allow kill
                org.bukkit.entity.Trident trident = (org.bukkit.entity.Trident) event.getDamager();
                rat.remove();
                
                if (trident.getShooter() instanceof Player player) {
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 1.0f);
                    player.sendActionBar(Component.text("Rat eliminated!", NamedTextColor.GREEN));
                }
            } else {
                // Not a thrown trident (e.g., melee attack) - cancel damage
                event.setCancelled(true);
                
                // Show message to player if they tried to kill it another way
                if (event.getDamager() instanceof Player player) {
                    UUID playerId = player.getUniqueId();
                    long currentTime = System.currentTimeMillis();
                    
                    // Only show message if cooldown has passed (3 seconds)
                    if (!playerRatMessageCooldowns.containsKey(playerId) || 
                        currentTime - playerRatMessageCooldowns.get(playerId) >= 3000) {
                        player.sendActionBar(Component.text("You need to throw a trident to kill rats!", NamedTextColor.RED));
                        playerRatMessageCooldowns.put(playerId, currentTime);
                    }
                }
            }
        }
    }
    
    /**
     * Announce message to all players on a team
     */
    private void announceToTeam(Team team, Component message) {
        for (String entry : team.getEntries()) {
            Player player = Bukkit.getPlayer(entry);
            if (player != null && player.isOnline()) {
                player.sendMessage(message);
            }
        }
    }
    
    /**
     * Start monitoring players in fridges
     */
    private void startFridgeMonitoring() {
        fridgeTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!gameActive) {
                    cancel();
                    return;
                }
                
                // Apply slowness to players in fridge areas
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getWorld().equals(gameWorld) && isInFridge(player.getLocation())) {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1, false, false, false));
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 40L); // Check every 2 seconds
    }
    
    /**
     * Give players starting items
     */
    private void giveStartingItems() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.getInventory().clear();
            
            // Give trident with Loyalty III
            ItemStack trident = new ItemStack(Material.TRIDENT, 1);
            trident.addEnchantment(Enchantment.LOYALTY, 3);
            player.getInventory().addItem(trident);
            
            // Set game mode
            player.setGameMode(GameMode.SURVIVAL);
            player.setHealth(20.0);
            player.setFoodLevel(20);
            player.setSaturation(20.0f);
        }
    }
    
    /**
     * Handle player death - respawn at team spawn
     */
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!gameActive) return;
        
        Player player = event.getPlayer();
        Team team = plugin.getScoreboard().getPlayerTeam(player);
        if (team == null) return;
        
        DishDashTeamData teamData = teamDataMap.get(team.getName());
        if (teamData == null) return;
        
        // Keep inventory
        event.setKeepInventory(true);
        event.getDrops().clear();
        
        // Respawn at team spawn after 1 second
        new BukkitRunnable() {
            @Override
            public void run() {
                player.spigot().respawn();
                player.teleport(teamData.getSpawnLocation());
                player.setHealth(20.0);
                player.setFoodLevel(20);
            }
        }.runTaskLater(plugin, 20L);
    }
    
    /**
     * Handle planting crops
     */
    @EventHandler
    public void onBlockPlace(org.bukkit.event.block.BlockPlaceEvent event) {
        if (!gameActive) return;
        
        Block block = event.getBlock();
        Material type = block.getType();
        
        // Allow planting crops
        if (type == Material.WHEAT || type == Material.CARROTS || 
            type == Material.POTATOES || type == Material.BEETROOTS) {
            // Check if placed on farmland
            Block below = block.getRelative(BlockFace.DOWN);
            if (below.getType() != Material.FARMLAND) {
                event.setCancelled(true);
                event.getPlayer().sendActionBar(Component.text("Crops must be planted on farmland!", NamedTextColor.RED));
            }
        }
    }
    
    /**
     * Disable inventory crafting for porkchop stew (only allow crafting table)
     */
    @EventHandler
    public void onCraft(org.bukkit.event.inventory.CraftItemEvent event) {
        if (!gameActive) return;
        
        ItemStack result = event.getRecipe().getResult();
        
        // Check if crafting porkchop stew (rabbit stew)
        if (result.getType() == Material.RABBIT_STEW) {
            // Only allow crafting in crafting table, not in player inventory
            if (event.getInventory().getType() != org.bukkit.event.inventory.InventoryType.WORKBENCH) {
                event.setCancelled(true);
                if (event.getWhoClicked() instanceof Player player) {
                    player.sendActionBar(Component.text("Porkchop Stew must be crafted at a crafting table!", NamedTextColor.RED));
                }
            }
        }
    }
    
    /**
     * Setup scoreboard for points display
     */
    private void setupScoreboard() {
        Scoreboard scoreboard = plugin.getScoreboard();
        
        // Remove existing objective if it exists
        Objective existingObjective = scoreboard.getObjective("dishdash_points");
        if (existingObjective != null) {
            existingObjective.unregister();
        }
        
        // Create objective for tab list
        pointsObjective = scoreboard.registerNewObjective("dishdash_points", "dummy", 
            Component.text("Points", NamedTextColor.GOLD));
        pointsObjective.setDisplaySlot(DisplaySlot.PLAYER_LIST);
        
        // Initialize all team scores to 0
        for (String teamName : teamDataMap.keySet()) {
            pointsObjective.getScore(teamName).setScore(0);
        }
        
        // Ensure all players have the scoreboard assigned
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.setScoreboard(scoreboard);
        }
    }
    
    /**
     * Update scoreboard with current points
     */
    private void updateScoreboard() {
        if (pointsObjective == null) {
            plugin.getLogger().warning("Cannot update scoreboard - pointsObjective is null!");
            return;
        }
        
        plugin.getLogger().info("Updating scoreboard...");
        for (Map.Entry<String, DishDashTeamData> entry : teamDataMap.entrySet()) {
            int points = entry.getValue().getPoints();
            pointsObjective.getScore(entry.getKey()).setScore(points);
            plugin.getLogger().info("Team " + entry.getKey() + ": " + points + " points");
        }
    }
    
    /**
     * Play background music with looping
     * Plays custom:dish_dash on loop until last 2:28, then switches to custom:dish_dash_fast
     */
    private void playMusic() {
        final int SONG_LENGTH_TICKS = 154 * 20; // 2 minutes 34 seconds in ticks
        final int SWITCH_TIME_TICKS = (2 * 60 + 28) * 20; // Last 2:28 in ticks
        
        musicTask = new BukkitRunnable() {
            private boolean switchedToFast = false;
            
            @Override
            public void run() {
                if (!gameActive) {
                    cancel();
                    return;
                }
                
                // Check if we should switch to fast version
                if (!switchedToFast && gameTicksRemaining <= SWITCH_TIME_TICKS) {
                    // Stop normal music and play fast version
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        player.stopSound("custom:dish_dash", org.bukkit.SoundCategory.VOICE);
                        player.playSound(player.getLocation(), "custom:dish_dash_fast", org.bukkit.SoundCategory.VOICE, 1.0f, 1.0f);
                    }
                    switchedToFast = true;
                } else if (!switchedToFast) {
                    // Play normal music on loop
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        player.playSound(player.getLocation(), "custom:dish_dash", org.bukkit.SoundCategory.VOICE, 1.0f, 1.0f);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, SONG_LENGTH_TICKS); // Run immediately, then every 2:34
    }
    
    /**
     * Gradually fill cauldrons and composters throughout the game
     */
    private void startCauldronFilling() {
        cauldronTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!gameActive) {
                    cancel();
                    return;
                }
                
                // Fill cauldrons and composters for each team slowly
                for (DishDashTeamData teamData : teamDataMap.values()) {
                    // Fill cauldron
                    Location cauldronLoc = teamData.getCauldronLocation();
                    Block cauldronBlock = gameWorld.getBlockAt(cauldronLoc);
                    
                    if (cauldronBlock.getType() == Material.CAULDRON) {
                        cauldronBlock.setType(Material.WATER_CAULDRON);
                        BlockData data = cauldronBlock.getBlockData();
                        if (data instanceof Levelled levelled) {
                            levelled.setLevel(1);
                            cauldronBlock.setBlockData(levelled);
                        }
                    } else if (cauldronBlock.getType() == Material.WATER_CAULDRON) {
                        BlockData data = cauldronBlock.getBlockData();
                        if (data instanceof Levelled levelled) {
                            if (levelled.getLevel() < 3) {
                                levelled.setLevel(levelled.getLevel() + 1);
                                cauldronBlock.setBlockData(levelled);
                            }
                        }
                    }
                    
                    // Fill composter
                    Location composterLoc = teamData.getComposterLocation();
                    Block composterBlock = gameWorld.getBlockAt(composterLoc);
                    
                    if (composterBlock.getType() == Material.COMPOSTER) {
                        BlockData data = composterBlock.getBlockData();
                        if (data instanceof Levelled levelled) {
                            if (levelled.getLevel() < 8) {
                                levelled.setLevel(levelled.getLevel() + 1);
                                composterBlock.setBlockData(levelled);
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 200L, 600L); // Every 30 seconds
    }
    
    /**
     * Accelerate crop growth throughout the game
     */
    private void startCropGrowth() {
        cropGrowthTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!gameActive) {
                    cancel();
                    return;
                }
                
                // Grow crops in loaded chunks
                for (org.bukkit.Chunk chunk : gameWorld.getLoadedChunks()) {
                    int chunkX = chunk.getX() << 4;
                    int chunkZ = chunk.getZ() << 4;
                    
                    // Scan blocks in chunk (only check farm areas - y levels -60 to -50)
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            for (int y = -60; y < -50; y++) {
                                Block block = gameWorld.getBlockAt(chunkX + x, y, chunkZ + z);
                                Material type = block.getType();
                                
                                // Check if it's a crop
                                if (type == Material.WHEAT || type == Material.CARROTS || 
                                    type == Material.POTATOES || type == Material.BEETROOTS) {
                                    
                                    BlockData data = block.getBlockData();
                                    if (data instanceof Ageable ageable) {
                                        // Grow the crop if not fully grown
                                        if (ageable.getAge() < ageable.getMaximumAge()) {
                                            // 25% chance to grow each tick (faster growth)
                                            if (new Random().nextInt(100) < 25) {
                                                ageable.setAge(ageable.getAge() + 1);
                                                block.setBlockData(ageable);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 40L, 40L); // Every 2 seconds
    }
    
    public boolean isGameActive() {
        return gameActive;
    }
}