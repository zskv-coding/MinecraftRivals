package com.zskv.minecraftRivals;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manages the voting phase where players feed snowballs to happy ghasts
 * to vote for their favorite minigame.
 */
public class VotingManager implements Listener {

    private static final int VOTING_DURATION_SECONDS = 30;
    private static final double FALL_Y_THRESHOLD = -47.0;
    
    private final MinecraftRivals plugin;
    
    private boolean votingActive = false;
    private BossBar votingBossBar;
    private BukkitTask votingTask;
    private BukkitTask fallCheckTask;
    
    private final Map<String, Entity> gameGhasts = new HashMap<>();
    private final Map<String, Integer> voteCount = new HashMap<>();
    private final Map<UUID, GameMode> originalGameModes = new HashMap<>();
    private final List<UUID> votingParticipants = new ArrayList<>();
    
    private Location respawnLocation;
    private ArmorStand facingTarget;
    
    // Game names and their spawn locations
    private static final Map<String, Location> GAME_LOCATIONS = new HashMap<>();
    
    public VotingManager(MinecraftRivals plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }
    
    /**
     * Starts the voting phase
     * @return true if voting started successfully, false otherwise
     */
    public synchronized boolean startVoting() {
        if (votingActive) {
            return false;
        }
        
        World lobbyWorld = Bukkit.getWorld("Lobby");
        if (lobbyWorld == null) {
            plugin.getLogger().warning("Lobby world not found! Cannot start voting.");
            return false;
        }
        
        // Initialize game locations
        GAME_LOCATIONS.clear();
        GAME_LOCATIONS.put("Capture The Ghast", new Location(lobbyWorld, -16, -36, 66));
        GAME_LOCATIONS.put("Glide", new Location(lobbyWorld, -8, -36, 74));
        GAME_LOCATIONS.put("Dish Dash", new Location(lobbyWorld, 4, -36, 74));
        GAME_LOCATIONS.put("Protect the Something", new Location(lobbyWorld, 4, -36, 46));
        GAME_LOCATIONS.put("Blueprint Blitz", new Location(lobbyWorld, -8, -36, 46));
        GAME_LOCATIONS.put("Shopping Skirmish", new Location(lobbyWorld, 13, -36, 53));
        
        respawnLocation = new Location(lobbyWorld, 25, -23, 60);
        
        // Find the armor stand that ghasts should face
        facingTarget = findVotingGhastFace(lobbyWorld);
        if (facingTarget == null) {
            plugin.getLogger().warning("Could not find armor stand with tag 'VotingGhastFace'!");
            return false;
        }
        
        // Clear previous data
        gameGhasts.clear();
        voteCount.clear();
        votingParticipants.clear();
        originalGameModes.clear();
        
        // Spawn ghasts for each game
        for (Map.Entry<String, Location> entry : GAME_LOCATIONS.entrySet()) {
            String gameName = entry.getKey();
            Location loc = entry.getValue();
            
            Entity ghast = spawnVotingGhast(loc, gameName);
            if (ghast != null) {
                gameGhasts.put(gameName, ghast);
                voteCount.put(gameName, 0);
            }
        }
        
        if (gameGhasts.isEmpty()) {
            plugin.getLogger().warning("Failed to spawn any voting ghasts!");
            return false;
        }
        
        // Store participants and their game modes
        for (Player player : Bukkit.getOnlinePlayers()) {
            votingParticipants.add(player.getUniqueId());
            originalGameModes.put(player.getUniqueId(), player.getGameMode());
            
            // Teleport to voting area
            player.teleport(respawnLocation);
            
            // Set to adventure mode for voting
            player.setGameMode(GameMode.ADVENTURE);
            
            // Give snowballs for voting
            player.getInventory().addItem(new ItemStack(Material.SNOWBALL, 1));
        }
        
        votingActive = true;
        
        // Create and show voting bossbar
        votingBossBar = Bukkit.createBossBar(
            "Voting Time: 30s",
            BarColor.YELLOW,
            BarStyle.SOLID
        );
        votingBossBar.setProgress(1.0);
        
        for (UUID uuid : votingParticipants) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                votingBossBar.addPlayer(player);
            }
        }
        
        // Start voting countdown
        startVotingCountdown();
        
        // Start fall check task
        startFallCheckTask();
        
        return true;
    }
    
    /**
     * Spawns a happy ghast at the specified location
     */
    private Entity spawnVotingGhast(Location location, String gameName) {
        World world = location.getWorld();
        if (world == null) {
            return null;
        }
        
        Entity ghast = world.spawnEntity(location, EntityType.HAPPY_GHAST);
        
        // Configure ghast (cast to LivingEntity for common methods)
        if (ghast instanceof LivingEntity) {
            LivingEntity livingGhast = (LivingEntity) ghast;
            livingGhast.setAI(false);
            livingGhast.setInvulnerable(true);
            livingGhast.setGravity(false);
            livingGhast.setSilent(true);
            livingGhast.setPersistent(true);
            livingGhast.setCustomName(gameName);
            livingGhast.setCustomNameVisible(true);
        }
        
        // Make ghast face the armor stand
        if (facingTarget != null) {
            Location ghastLoc = ghast.getLocation();
            Location targetLoc = facingTarget.getLocation();
            
            // Calculate yaw to face target
            double dx = targetLoc.getX() - ghastLoc.getX();
            double dz = targetLoc.getZ() - ghastLoc.getZ();
            float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            
            ghastLoc.setYaw(yaw);
            ghast.teleport(ghastLoc);
        }
        
        return ghast;
    }
    
    /**
     * Finds the armor stand with the VotingGhastFace tag
     */
    private ArmorStand findVotingGhastFace(World world) {
        for (Entity entity : world.getEntities()) {
            if (entity instanceof ArmorStand) {
                ArmorStand stand = (ArmorStand) entity;
                if (stand.getScoreboardTags().contains("VotingGhastFace")) {
                    return stand;
                }
            }
        }
        return null;
    }
    
    /**
     * Starts the voting countdown timer
     */
    private void startVotingCountdown() {
        votingTask = new BukkitRunnable() {
            private int secondsRemaining = VOTING_DURATION_SECONDS;
            
            @Override
            public void run() {
                if (!votingActive) {
                    cancel();
                    return;
                }
                
                // Update bossbar
                if (votingBossBar != null) {
                    votingBossBar.setTitle("Voting Time: " + secondsRemaining + "s");
                    votingBossBar.setProgress((double) secondsRemaining / VOTING_DURATION_SECONDS);
                }
                
                if (secondsRemaining <= 0) {
                    cancel();
                    endVoting();
                    return;
                }
                
                secondsRemaining--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }
    
    /**
     * Starts the task that checks if players fall below the threshold
     */
    private void startFallCheckTask() {
        fallCheckTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!votingActive) {
                    cancel();
                    return;
                }
                
                for (UUID uuid : votingParticipants) {
                    Player player = Bukkit.getPlayer(uuid);
                    if (player != null && player.isOnline()) {
                        if (player.getLocation().getY() < FALL_Y_THRESHOLD) {
                            handlePlayerFall(player);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 5L); // Check every 5 ticks (0.25 seconds)
    }
    
    /**
     * Handles when a player falls below the threshold
     */
    private void handlePlayerFall(Player player) {
        // Teleport to respawn location
        player.teleport(respawnLocation);
        
        // Show "You're out!" title
        Title outTitle = Title.title(
            Component.text("You're out!", NamedTextColor.RED, TextDecoration.BOLD),
            Component.empty(),
            Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(1000), Duration.ofMillis(400))
        );
        player.showTitle(outTitle);
    }
    
    /**
     * Handles when a player feeds a ghast (right-clicks with snowball)
     */
    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!votingActive) {
            return;
        }
        
        Player player = event.getPlayer();
        Entity entity = event.getRightClicked();
        
        // Check if player clicked a voting ghast with a snowball
        if (gameGhasts.containsValue(entity)) {
            ItemStack item = player.getInventory().getItemInMainHand();
            
            if (item.getType() == Material.SNOWBALL) {
                // Find which game this ghast represents
                String gameName = null;
                for (Map.Entry<String, Entity> entry : gameGhasts.entrySet()) {
                    if (entry.getValue().equals(entity)) {
                        gameName = entry.getKey();
                        break;
                    }
                }
                
                if (gameName != null) {
                    // Increment vote count
                    voteCount.put(gameName, voteCount.getOrDefault(gameName, 0) + 1);
                    
                    // Remove one snowball
                    if (item.getAmount() > 1) {
                        item.setAmount(item.getAmount() - 1);
                    } else {
                        player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
                    }
                    
                    // Visual feedback
                    player.sendMessage(Component.text("Voted for ", NamedTextColor.GREEN)
                        .append(Component.text(gameName, NamedTextColor.GOLD, TextDecoration.BOLD)));
                    
                    event.setCancelled(true);
                }
            }
        }
    }
    
    /**
     * Prevent ghasts from taking damage
     */
    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (!votingActive) {
            return;
        }
        
        if (gameGhasts.containsValue(event.getEntity())) {
            event.setCancelled(true);
        }
    }
    
    /**
     * Prevent players from punching ghasts
     */
    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!votingActive) {
            return;
        }
        
        if (gameGhasts.containsValue(event.getEntity())) {
            event.setCancelled(true);
        }
    }
    
    /**
     * Ends the voting phase and announces the winner
     */
    private void endVoting() {
        votingActive = false;
        
        // Stop fall check task
        if (fallCheckTask != null) {
            fallCheckTask.cancel();
            fallCheckTask = null;
        }
        
        // Hide bossbar
        if (votingBossBar != null) {
            votingBossBar.removeAll();
            votingBossBar = null;
        }
        
        // Find the winning game
        String winningGame = null;
        int maxVotes = 0;
        
        for (Map.Entry<String, Integer> entry : voteCount.entrySet()) {
            if (entry.getValue() > maxVotes) {
                maxVotes = entry.getValue();
                winningGame = entry.getKey();
            }
        }
        
        // Announce winner
        if (winningGame != null) {
            final String winner = winningGame;
            final int votes = maxVotes;
            
            Title winnerTitle = Title.title(
                Component.text(winner, NamedTextColor.GOLD, TextDecoration.BOLD),
                Component.text("wins with " + votes + " votes!", NamedTextColor.YELLOW),
                Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofMillis(500))
            );
            
            for (UUID uuid : votingParticipants) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) {
                    player.showTitle(winnerTitle);
                }
            }
            
            // Broadcast to chat
            Bukkit.broadcast(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY));
            Bukkit.broadcast(Component.text("Voting Results:", NamedTextColor.GOLD, TextDecoration.BOLD));
            Bukkit.broadcast(Component.empty());
            
            // Show all vote counts
            voteCount.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .forEach(entry -> {
                    Component line = Component.text("  " + entry.getKey() + ": ", NamedTextColor.WHITE)
                        .append(Component.text(entry.getValue() + " votes", NamedTextColor.YELLOW));
                    Bukkit.broadcast(line);
                });
            
            Bukkit.broadcast(Component.empty());
            Bukkit.broadcast(Component.text("Winner: ", NamedTextColor.GREEN)
                .append(Component.text(winner, NamedTextColor.GOLD, TextDecoration.BOLD)));
            Bukkit.broadcast(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY));
        } else {
            // No votes cast
            Title noVotesTitle = Title.title(
                Component.text("No votes cast!", NamedTextColor.RED, TextDecoration.BOLD),
                Component.empty(),
                Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(2), Duration.ofMillis(500))
            );
            
            for (UUID uuid : votingParticipants) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) {
                    player.showTitle(noVotesTitle);
                }
            }
        }
        
        // Clean up after 3 seconds
        Bukkit.getScheduler().runTaskLater(plugin, this::cleanup, 60L);
    }
    
    /**
     * Cleans up voting entities and restores player states
     */
    private void cleanup() {
        // Remove all voting ghasts
        for (Entity ghast : gameGhasts.values()) {
            if (ghast != null && ghast.isValid()) {
                ghast.remove();
            }
        }
        
        // Restore player game modes
        for (UUID uuid : votingParticipants) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                GameMode originalMode = originalGameModes.get(uuid);
                if (originalMode != null) {
                    player.setGameMode(originalMode);
                }
                
                // Remove any remaining snowballs
                player.getInventory().remove(Material.SNOWBALL);
            }
        }
        
        // Clear data
        gameGhasts.clear();
        voteCount.clear();
        votingParticipants.clear();
        originalGameModes.clear();
    }
    
    /**
     * Force stops voting (for shutdown or manual stop)
     */
    public synchronized void stopVoting() {
        if (!votingActive) {
            return;
        }
        
        votingActive = false;
        
        if (votingTask != null) {
            votingTask.cancel();
            votingTask = null;
        }
        
        if (fallCheckTask != null) {
            fallCheckTask.cancel();
            fallCheckTask = null;
        }
        
        if (votingBossBar != null) {
            votingBossBar.removeAll();
            votingBossBar = null;
        }
        
        cleanup();
    }
    
    public boolean isVotingActive() {
        return votingActive;
    }
}