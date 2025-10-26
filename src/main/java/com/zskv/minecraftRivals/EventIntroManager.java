package com.zskv.minecraftRivals;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Coordinates the full event intro flow:
 *  - 60 second initial countdown with fireworks and intro music
 *  - 30 second preshow period
 *  - Fade-to-black transition and camera setup
 *  - Sequential team introductions (8 seconds per team, up to 8 teams = 64 seconds)
 *  - 30 second pre-voting countdown and announcement
 */
public class EventIntroManager implements Listener {

    private static final int INITIAL_COUNTDOWN_SECONDS = 60;
    private static final int PRESHOW_SECONDS = 30;
    private static final int TEAM_INTRO_SECONDS = (int) 8;
    private static final int VOTING_COUNTDOWN_SECONDS = 30;

    private final MinecraftRivals plugin;

    private final Map<UUID, GameMode> originalGameModes = new HashMap<>();
    private final Set<UUID> cameraLockedPlayers = new HashSet<>();
    private final List<UUID> participants = new ArrayList<>();

    private final Map<UUID, Boolean> readyResponses = new HashMap<>();
    private final Map<UUID, Inventory> openReadyMenus = new HashMap<>();

    private final Map<String, TeamReadySnapshot> readyStateByTeam = new LinkedHashMap<>();

    private BossBar readyBossBar;
    private BossBar bossBar;
    private BukkitTask activeTask;
    private BukkitTask cameraLockTask;
    private BukkitTask fireworkTask;

    private Sheep cameraSheep;
    private boolean running;
    private boolean gatheringReadiness;
    private EventPhase currentPhase = EventPhase.NONE;

    private Location spectatorLocation;
    private Location teamIntroLocation;
    private Location fireworkCenter;
    
    private enum EventPhase {
        NONE,
        COUNTDOWN,
        PRESHOW,
        TEAM_INTROS,
        VOTING_COUNTDOWN
    }

    public EventIntroManager(MinecraftRivals plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Begins the full event intro sequence.
     *
     * @param sender Command initiator for feedback.
     * @return {@code true} if the sequence started successfully, otherwise {@code false}.
     */
    public synchronized boolean startIntro(CommandSender sender) {
        if (running) {
            sender.sendMessage(Component.text("An event intro is already running.", NamedTextColor.RED));
            return false;
        }

        if (gatheringReadiness) {
            sender.sendMessage(Component.text("A ready check is already in progress.", NamedTextColor.RED));
            return false;
        }

        World lobbyWorld = Bukkit.getWorld("Lobby");
        if (lobbyWorld == null) {
            sender.sendMessage(Component.text("World 'Lobby' is not loaded. Unable to start intro.", NamedTextColor.RED));
            return false;
        }

        spectatorLocation = new Location(lobbyWorld, 0.5, -24, 20.5, 180f, 0f);
        teamIntroLocation = new Location(lobbyWorld, -2.5, -22, 29.5, -180f, 0f);
        fireworkCenter = new Location(lobbyWorld, 51, -21, 60);

        cameraSheep = locateCameraSheep();
        if (cameraSheep == null) {
            sender.sendMessage(Component.text("Could not find a sheep with the 'CameraTeamIntros' tag.", NamedTextColor.RED));
            return false;
        }

        participants.clear();
        readyResponses.clear();
        openReadyMenus.clear();
        readyStateByTeam.clear();

        Bukkit.getOnlinePlayers().forEach(player -> {
            participants.add(player.getUniqueId());
            originalGameModes.put(player.getUniqueId(), player.getGameMode());
        });

        if (participants.isEmpty()) {
            sender.sendMessage(Component.text("No online players to run the intro for.", NamedTextColor.RED));
            cameraSheep = null;
            spectatorLocation = null;
            teamIntroLocation = null;
            return false;
        }

        gatheringReadiness = true;
        
        initializeReadyState();
        
        // Notify admins
        Bukkit.getOnlinePlayers().stream()
            .filter(p -> p.hasPermission("minecraftrivals.admin"))
            .forEach(admin -> admin.sendMessage(Component.text("A ready check has been run", NamedTextColor.GRAY)));
        
        // Send ready check prompt to all players
        sendReadyCheckPrompt();
        
        // Start reminder task
        readyReminderTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!gatheringReadiness) {
                    cancel();
                    return;
                }
                checkAllTeamsReady();
            }
        }.runTaskTimer(plugin, 20L, 20L);
        
        return true;
    }

    private void beginIntroSequence() {
        gatheringReadiness = false;
        running = true;
        beginInitialCountdown();
    }

    /**
     * Cancels any running sequences and restores player states.
     */
    public synchronized void shutdown() {
        if (!running && !gatheringReadiness) {
            return;
        }

        running = false;
        gatheringReadiness = false;
        currentPhase = EventPhase.NONE;

        cancelActiveTask();
        stopCameraLockTask();
        stopFireworkTask();
        hideBossBar();
        restoreParticipants();
        closeAllReadyMenus();

        cameraLockedPlayers.clear();
        readyResponses.clear();
        openReadyMenus.clear();
        readyStateByTeam.clear();

        cancelReadyReminder();
        if (readyBossBar != null) {
            readyBossBar.removeAll();
            readyBossBar = null;
        }

        cameraSheep = null;
        spectatorLocation = null;
        teamIntroLocation = null;
        fireworkCenter = null;
        participants.clear();
    }

    public boolean isRunning() {
        return running;
    }

    private void beginInitialCountdown() {
        currentPhase = EventPhase.COUNTDOWN;
        showBossBar(Component.text("Event intro begins soon", NamedTextColor.GOLD));
        
        // Start fireworks display immediately
        startFireworkDisplay();
        
        // Start intro music immediately
        playIntroMusic();
        
        // Broadcast music start message
        broadcast(Component.text("♪ ", NamedTextColor.LIGHT_PURPLE)
            .append(Component.text("Intro music has started!", NamedTextColor.GOLD)));

        cancelActiveTask();
        activeTask = new BukkitRunnable() {
            private int secondsRemaining = INITIAL_COUNTDOWN_SECONDS;

            @Override
            public void run() {
                if (!running) {
                    cancel();
                    return;
                }

                updateBossBar(
                    Component.text("Event starting in " + secondsRemaining + "s", NamedTextColor.GOLD),
                    (double) secondsRemaining / INITIAL_COUNTDOWN_SECONDS
                );

                if (secondsRemaining <= 0) {
                    cancel();
                    stopFireworkTask();
                    hideBossBar();
                    beginPreshow();
                    return;
                }

                secondsRemaining--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }
    
    private void beginPreshow() {
        currentPhase = EventPhase.PRESHOW;
        showBossBar(Component.text("Pre-show starting!", NamedTextColor.AQUA));
        
        // Continue fireworks during preshow
        startFireworkDisplay();
        
        cancelActiveTask();
        activeTask = new BukkitRunnable() {
            private int secondsRemaining = PRESHOW_SECONDS;

            @Override
            public void run() {
                if (!running) {
                    cancel();
                    return;
                }

                updateBossBar(
                    Component.text("Pre-show: " + secondsRemaining + "s", NamedTextColor.AQUA),
                    (double) secondsRemaining / PRESHOW_SECONDS
                );

                if (secondsRemaining <= 0) {
                    cancel();
                    stopFireworkTask();
                    hideBossBar();
                    triggerFadeToBlack();
                    return;
                }

                secondsRemaining--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void triggerFadeToBlack() {
        Title fadeTitle = Title.title(
            Component.text("\uE01F", NamedTextColor.WHITE),
            Component.empty(),
            Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(600), Duration.ofMillis(200))
        );

        participants.stream()
            .map(Bukkit::getPlayer)
            .filter(Objects::nonNull)
            .forEach(player -> {
                player.teleport(spectatorLocation);
                player.setGameMode(GameMode.SPECTATOR);
                player.showTitle(fadeTitle);
                player.setSpectatorTarget(cameraSheep);
                cameraLockedPlayers.add(player.getUniqueId());
            });

        // Ensure spectating locks in even if teleport/title timing desyncs slightly
        Bukkit.getScheduler().runTask(plugin, () -> participants.stream()
            .map(Bukkit::getPlayer)
            .filter(Objects::nonNull)
            .forEach(player -> {
                if (cameraSheep != null && cameraSheep.isValid()) {
                    player.setSpectatorTarget(cameraSheep);
                }
            }));

        // Wait roughly two seconds before starting intros (players are already on camera)
        Bukkit.getScheduler().runTaskLater(plugin, this::prepareCameraStage, 40L);
    }

    private void prepareCameraStage() {
        if (!running) {
            return;
        }

        // Ensure the camera still exists
        if (cameraSheep == null || !cameraSheep.isValid()) {
            broadcast(Component.text("Camera entity is missing. Intro cancelled.", NamedTextColor.RED));
            shutdown();
            return;
        }

        startCameraLockTask();
        beginTeamIntroductions();
    }

    private void beginTeamIntroductions() {
        currentPhase = EventPhase.TEAM_INTROS;
        Scoreboard scoreboard = plugin.getScoreboard();
        List<String> visibleTeams = plugin.getConfig().getStringList("teams.visible");

        List<Team> teams = visibleTeams.stream()
            .map(scoreboard::getTeam)
            .filter(Objects::nonNull)
            .limit(8)
            .collect(Collectors.toList());

        if (teams.isEmpty()) {
            broadcast(Component.text("No teams available for introductions.", NamedTextColor.RED));
            startVotingCountdown();
            return;
        }

        presentTeamSequentially(teams, 0);
    }

    private void presentTeamSequentially(List<Team> teams, int index) {
        if (!running) {
            return;
        }

        if (index >= teams.size()) {
            startVotingCountdown();
            return;
        }

        Team team = teams.get(index);
        List<Player> onlineMembers = team.getEntries().stream()
            .map(Bukkit::getPlayerExact)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

        List<String> offlineMembers = team.getEntries().stream()
            .filter(name -> Bukkit.getPlayerExact(name) == null)
            .collect(Collectors.toList());

        // Allow featured players to leave the camera for the intro
        for (Player member : onlineMembers) {
            cameraLockedPlayers.remove(member.getUniqueId());
            member.setSpectatorTarget(null);
            member.setGameMode(GameMode.ADVENTURE);
            member.teleport(teamIntroLocation);
        }

        Component title = Component.text(team.getDisplayName(), toTextColor(team.getColor()))
            .decorate(net.kyori.adventure.text.format.TextDecoration.BOLD);
        Component subtitle = buildTeamSubtitle(onlineMembers, offlineMembers);
        Title.Times times = Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(2), Duration.ofMillis(200));

        participants.stream()
            .map(Bukkit::getPlayer)
            .filter(Objects::nonNull)
            .forEach(player -> player.showTitle(Title.title(title, subtitle, times)));

        showBossBar(Component.text("Introducing " + team.getDisplayName(), toTextColor(team.getColor())));
        
        // Start team-colored fireworks in N pattern
        startTeamFireworks(team);
        
        cancelActiveTask();
        activeTask = new BukkitRunnable() {
            private int secondsRemaining = TEAM_INTRO_SECONDS;

            @Override
            public void run() {
                if (!running) {
                    cancel();
                    stopFireworkTask();
                    return;
                }

                updateBossBar(
                    Component.text(
                        team.getDisplayName() + " spotlight: " + secondsRemaining + "s",
                        toTextColor(team.getColor())
                    ),
                    (double) secondsRemaining / TEAM_INTRO_SECONDS
                );

                if (secondsRemaining <= 0) {
                    cancel();
                    stopFireworkTask();
                    hideBossBar();
                    wrapTeamIntro(team, onlineMembers, offlineMembers);
                    presentTeamSequentially(teams, index + 1);
                }

                secondsRemaining--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void wrapTeamIntro(Team team, List<Player> members, List<String> offlineMembers) {
        for (Player member : members) {
            if (cameraSheep != null && cameraSheep.isValid()) {
                member.teleport(spectatorLocation);
                member.setGameMode(GameMode.SPECTATOR);
                member.setSpectatorTarget(cameraSheep);
                cameraLockedPlayers.add(member.getUniqueId());
            }
        }

        for (String offline : offlineMembers) {
            UUID uuid = Bukkit.getOfflinePlayer(offline).getUniqueId();
            cameraLockedPlayers.remove(uuid);
        }

        broadcast(Component.text(team.getDisplayName() + " intro complete!", toTextColor(team.getColor())));
    }

    private void startVotingCountdown() {
        currentPhase = EventPhase.VOTING_COUNTDOWN;
        showBossBar(Component.text("Voting begins shortly", NamedTextColor.GREEN));

        cancelActiveTask();
        activeTask = new BukkitRunnable() {
            private int secondsRemaining = VOTING_COUNTDOWN_SECONDS;

            @Override
            public void run() {
                if (!running) {
                    cancel();
                    return;
                }

                if (secondsRemaining == VOTING_COUNTDOWN_SECONDS) {
                    // Teleport players to spawn location
                    World lobbyWorld = Bukkit.getWorld("Lobby");
                    if (lobbyWorld != null) {
                        Location spawnLocation = new Location(lobbyWorld, 73.5, -28, 60.5, 90f, 0f);
                        participants.stream()
                            .map(Bukkit::getPlayer)
                            .filter(Objects::nonNull)
                            .forEach(player -> {
                                player.teleport(spawnLocation);
                                player.setGameMode(GameMode.ADVENTURE);
                            });
                    }
                    
                    Title startTitle = Title.title(
                        Component.text("Starting in " + VOTING_COUNTDOWN_SECONDS + " seconds", NamedTextColor.GOLD),
                        Component.empty(),
                        Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(1000), Duration.ofMillis(200))
                    );
                    participants.stream()
                        .map(Bukkit::getPlayer)
                        .filter(Objects::nonNull)
                        .forEach(player -> player.showTitle(startTitle));
                }
                
                // Show voting intro 20 seconds before voting starts
                if (secondsRemaining == 20) {
                    showVotingIntro();
                }

                updateBossBar(
                    Component.text("Voting in " + secondsRemaining + "s", NamedTextColor.GREEN),
                    (double) secondsRemaining / VOTING_COUNTDOWN_SECONDS
                );

                if (secondsRemaining <= 0) {
                    cancel();
                    hideBossBar();
                    announceVotingStart();
                    finalizeParticipants();
                    shutdown();
                }

                secondsRemaining--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }
    
    private void showVotingIntro() {
        World lobbyWorld = Bukkit.getWorld("Lobby");
        if (lobbyWorld == null) {
            return;
        }
        
        // Teleport players to voting area
        participants.stream()
            .map(Bukkit::getPlayer)
            .filter(Objects::nonNull);

        // Create the voting announcement message with custom formatting
        Component votingTitle = Component.text("Voting!", NamedTextColor.GOLD)
            .decorate(TextDecoration.BOLD);
        
        Component separator = Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY);
        
        Component instructions = Component.text("To vote for your favourite game you have to feed the happy ghast corresponding to it! ", NamedTextColor.BLUE)
            .append(Component.text("But watch out the team with the lowest amount of points last game have knockback sticks and can knock you OUT!", NamedTextColor.BLUE));
        
        // Send messages to all participants
        participants.stream()
            .map(Bukkit::getPlayer)
            .filter(Objects::nonNull)
            .forEach(player -> {
                player.sendMessage(Component.empty());
                player.sendMessage(separator);
                player.sendMessage(votingTitle);
                player.sendMessage(separator);
                player.sendMessage(instructions);
                player.sendMessage(separator);
                player.sendMessage(Component.empty());
            });
    }

    private void announceVotingStart() {
        Title title = Title.title(
            Component.text("Voting time!", NamedTextColor.GOLD),
            Component.empty(),
            Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(2), Duration.ofMillis(400))
        );

        participants.stream()
            .map(Bukkit::getPlayer)
            .filter(Objects::nonNull)
            .forEach(player -> player.showTitle(title));
        
        // Start voting after title is shown (2.6 seconds total for title animation)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            VotingManager votingManager = plugin.getVotingManager();
            if (votingManager != null) {
                votingManager.startVoting();
            }
        }, 52L); // 2.6 seconds = 52 ticks
    }

    private Component buildTeamSubtitle(List<Player> onlineMembers, List<String> offlineMembers) {
        List<Component> segments = new ArrayList<>();

        if (!onlineMembers.isEmpty()) {
            List<@NotNull TextComponent> names = onlineMembers.stream()
                .map(player -> Component.text(player.getName(), NamedTextColor.WHITE))
                .toList();
            segments.add(Component.text("Online: ", NamedTextColor.GREEN)
                .append(Component.join(JoinConfiguration.separator(Component.text(", ", NamedTextColor.DARK_GRAY)), names)));
        }

        if (!offlineMembers.isEmpty()) {
            List<@NotNull TextComponent> names = offlineMembers.stream()
                .map(name -> Component.text(name, NamedTextColor.GRAY))
                .toList();
            segments.add(Component.text("Offline: ", NamedTextColor.RED)
                .append(Component.join(JoinConfiguration.separator(Component.text(", ", NamedTextColor.DARK_GRAY)), names)));
        }

        if (segments.isEmpty()) {
            return Component.text("No players registered", NamedTextColor.GRAY);
        }

        return Component.join(JoinConfiguration.separator(Component.text("  ", NamedTextColor.DARK_GRAY)), segments);
    }

    private void finalizeParticipants() {
        World lobbyWorld = spectatorLocation != null ? spectatorLocation.getWorld() : Bukkit.getWorld("Lobby");
        if (lobbyWorld == null) {
            return;
        }

        Location finalLocation = new Location(lobbyWorld, 73.5, -28, 60.5, 90f, 0f);

        for (UUID uuid : new ArrayList<>(participants)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) {
                continue;
            }

            cameraLockedPlayers.remove(uuid);
            player.setSpectatorTarget(null);
            player.teleport(finalLocation);
            player.setGameMode(GameMode.ADVENTURE);
        }
    }

    private void showBossBar(Component title) {
        if (bossBar == null) {
            bossBar = Bukkit.createBossBar("", BarColor.PURPLE, BarStyle.SOLID);
        }
        bossBar.setVisible(true);
        bossBar.setProgress(1.0);
        bossBar.setTitle(renderBossBarTitle(title));

        for (UUID uuid : participants) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                bossBar.addPlayer(player);
            }
        }
    }

    private void updateBossBar(Component title, double progress) {
        if (bossBar == null) {
            return;
        }
        bossBar.setTitle(renderBossBarTitle(title));
        bossBar.setProgress(Math.max(0.0, Math.min(1.0, progress)));
    }

    private String renderBossBarTitle(Component component) {
        return LegacyComponentSerializer.legacySection().serialize(component);
    }

    private void hideBossBar() {
        if (bossBar == null) {
            return;
        }
        bossBar.setVisible(false);
        for (UUID uuid : participants) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                bossBar.removePlayer(player);
            }
        }
    }

    private void startCameraLockTask() {
        stopCameraLockTask();
        cameraLockTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!running) {
                    cancel();
                    return;
                }

                if (cameraSheep == null || !cameraSheep.isValid()) {
                    broadcast(Component.text("Camera entity is missing. Intro cancelled.", NamedTextColor.RED));
                    shutdown();
                    cancel();
                    return;
                }

                Iterator<UUID> iterator = cameraLockedPlayers.iterator();
                while (iterator.hasNext()) {
                    UUID uuid = iterator.next();
                    Player player = Bukkit.getPlayer(uuid);
                    if (player == null || player.getGameMode() != GameMode.SPECTATOR) {
                        iterator.remove();
                        continue;
                    }

                    Entity currentTarget = player.getSpectatorTarget();
                    if (currentTarget == null || !currentTarget.equals(cameraSheep)) {
                        player.setSpectatorTarget(cameraSheep);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 10L);
    }

    private void stopCameraLockTask() {
        if (cameraLockTask != null) {
            cameraLockTask.cancel();
            cameraLockTask = null;
        }
    }
    
    private void stopFireworkTask() {
        if (fireworkTask != null) {
            fireworkTask.cancel();
            fireworkTask = null;
        }
    }
    
    private void playIntroMusic() {
        // Play custom music for all participants
        participants.stream()
            .map(Bukkit::getPlayer)
            .filter(Objects::nonNull)
            .forEach(player -> {
                try {
                    player.playSound(player.getLocation(), "custom:mcr_intro_test", SoundCategory.VOICE, 1.0f, 1.0f);
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to play intro music for " + player.getName() + ": " + e.getMessage());
                }
            });
    }
    
    private void startFireworkDisplay() {
        if (fireworkCenter == null) {
            return;
        }
        
        stopFireworkTask();
        fireworkTask = new BukkitRunnable() {
            private int tickCount = 0;
            
            @Override
            public void run() {
                if (!running) {
                    cancel();
                    return;
                }
                
                // Spawn fireworks every 10 ticks (0.5 seconds)
                // Spawn multiple fireworks to cover the whole lobby
                if (tickCount % 10 == 0) {
                    for (int i = 0; i < 5; i++) {
                        spawnRandomFirework();
                    }
                }
                
                tickCount++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
    
    private void spawnRandomFirework() {
        if (fireworkCenter == null || fireworkCenter.getWorld() == null) {
            return;
        }
        
        World world = fireworkCenter.getWorld();
        
        // Spread fireworks throughout the entire lobby world
        double offsetX = (Math.random() - 0.5) * 200; // ±100 blocks
        double offsetY = Math.random() * 40 - 10; // -10 to 30 blocks from center y
        double offsetZ = (Math.random() - 0.5) * 200; // ±100 blocks
        
        Location spawnLoc = fireworkCenter.clone().add(offsetX, offsetY, offsetZ);
        
        // Spawn firework
        Firework firework = (Firework) world.spawnEntity(spawnLoc, EntityType.FIREWORK_ROCKET);
        FireworkMeta meta = firework.getFireworkMeta();
        
        // Random firework effect
        FireworkEffect.Builder effectBuilder = FireworkEffect.builder();
        
        // Random type
        FireworkEffect.Type[] types = FireworkEffect.Type.values();
        effectBuilder.with(types[(int) (Math.random() * types.length)]);
        
        // Random colors
        Color[] colors = {
            Color.RED, Color.BLUE, Color.GREEN, Color.YELLOW, 
            Color.ORANGE, Color.PURPLE, Color.WHITE, Color.AQUA,
            Color.FUCHSIA, Color.LIME
        };
        
        int colorCount = 1 + (int) (Math.random() * 3); // 1-3 colors
        for (int i = 0; i < colorCount; i++) {
            effectBuilder.withColor(colors[(int) (Math.random() * colors.length)]);
        }
        
        // Random fade colors
        if (Math.random() > 0.5) {
            effectBuilder.withFade(colors[(int) (Math.random() * colors.length)]);
        }
        
        // Random effects
        if (Math.random() > 0.7) {
            effectBuilder.withTrail();
        }
        if (Math.random() > 0.7) {
            effectBuilder.withFlicker();
        }
        
        meta.addEffect(effectBuilder.build());
        meta.setPower(0); // Power 0 = instant explode
        
        firework.setFireworkMeta(meta);
        
        // Detonate instantly
        Bukkit.getScheduler().runTaskLater(plugin, firework::detonate, 1L);
    }
    
    private void startTeamFireworks(Team team) {
        World lobbyWorld = Bukkit.getWorld("Lobby");
        if (lobbyWorld == null) {
            return;
        }
        
        Color teamColor = chatColorToBukkitColor(team.getColor());
        
        // Define the upside-down U-shaped pattern positions (∩ shape)
        // Pattern visualization:
        //       [][][][][]         <- top curve
        //     []          []
        //   []              []     <- curve section
        // []                  []
        // []                  []
        // []                  []
        // []                  []  <- bottom of left and right verticals
        
        List<Location> uPattern = new ArrayList<>();
        
        // Top curve (left to right): y=-5, z=29, x varies
        uPattern.add(new Location(lobbyWorld, -4, -5, 29));   // top of curve left
        uPattern.add(new Location(lobbyWorld, -3, -5, 29));
        uPattern.add(new Location(lobbyWorld, -2, -5, 29));   // center top
        uPattern.add(new Location(lobbyWorld, -1, -5, 29));
        uPattern.add(new Location(lobbyWorld, 0, -5, 29));    // top of curve right
        uPattern.add(new Location(lobbyWorld, -5, -6, 29));   // curve start left
        uPattern.add(new Location(lobbyWorld, 1, -6, 29));    // curve start right
        uPattern.add(new Location(lobbyWorld, -6, -7, 29));   // curve transition left
        uPattern.add(new Location(lobbyWorld, 2, -7, 29));    // curve transition right
        
        // Left vertical line (top to bottom): x=-7, z=29, y varies
        uPattern.add(new Location(lobbyWorld, -7, -8, 29));   // start left vertical
        uPattern.add(new Location(lobbyWorld, -7, -9, 29));
        uPattern.add(new Location(lobbyWorld, -7, -10, 29));
        uPattern.add(new Location(lobbyWorld, -7, -11, 29));
        uPattern.add(new Location(lobbyWorld, -7, -12, 29));
        uPattern.add(new Location(lobbyWorld, -7, -13, 29));
        uPattern.add(new Location(lobbyWorld, -7, -14, 29));
        uPattern.add(new Location(lobbyWorld, -7, -15, 29));
        uPattern.add(new Location(lobbyWorld, -7, -16, 29));
        uPattern.add(new Location(lobbyWorld, -7, -17, 29));  // bottom left
        
        // Right vertical line (top to bottom): x=3, z=29, y varies
        uPattern.add(new Location(lobbyWorld, 3, -8, 29));    // start right vertical
        uPattern.add(new Location(lobbyWorld, 3, -9, 29));
        uPattern.add(new Location(lobbyWorld, 3, -10, 29));
        uPattern.add(new Location(lobbyWorld, 3, -11, 29));
        uPattern.add(new Location(lobbyWorld, 3, -12, 29));
        uPattern.add(new Location(lobbyWorld, 3, -13, 29));
        uPattern.add(new Location(lobbyWorld, 3, -14, 29));
        uPattern.add(new Location(lobbyWorld, 3, -15, 29));
        uPattern.add(new Location(lobbyWorld, 3, -16, 29));
        uPattern.add(new Location(lobbyWorld, 3, -17, 29));   // bottom right
        
        stopFireworkTask();
        fireworkTask = new BukkitRunnable() {
            private int index = 0;
            
            @Override
            public void run() {
                if (!running) {
                    cancel();
                    return;
                }
                
                // Spawn 1 firework at a random position from the pattern
                Location loc = uPattern.get((int) (Math.random() * uPattern.size()));
                spawnTeamFirework(loc, teamColor);
                
                index++;
            }
        }.runTaskTimer(plugin, 0L, 20L); // Every 20 ticks (1 second)
    }
    
    private void spawnTeamFirework(Location location, Color teamColor) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        
        Firework firework = (Firework) location.getWorld().spawnEntity(location, EntityType.FIREWORK_ROCKET);
        FireworkMeta meta = firework.getFireworkMeta();
        
        FireworkEffect.Builder effectBuilder = FireworkEffect.builder();
        
        // Random type
        FireworkEffect.Type[] types = {
            FireworkEffect.Type.BALL,
            FireworkEffect.Type.BALL_LARGE,
            FireworkEffect.Type.BURST,
            FireworkEffect.Type.STAR
        };
        effectBuilder.with(types[(int) (Math.random() * types.length)]);
        
        // Use team color
        effectBuilder.withColor(teamColor);
        
        // Add some white for contrast
        if (Math.random() > 0.5) {
            effectBuilder.withColor(Color.WHITE);
        }
        
        // Fade to team color or darker shade
        if (Math.random() > 0.3) {
            effectBuilder.withFade(teamColor);
        }
        
        // Random effects
        if (Math.random() > 0.6) {
            effectBuilder.withTrail();
        }
        if (Math.random() > 0.7) {
            effectBuilder.withFlicker();
        }
        
        meta.addEffect(effectBuilder.build());
        meta.setPower(0); // Power 0 = instant explode
        
        firework.setFireworkMeta(meta);
        
        // Detonate instantly
        Bukkit.getScheduler().runTaskLater(plugin, firework::detonate, 1L);
    }
    
    private Color chatColorToBukkitColor(ChatColor chatColor) {
        return switch (chatColor) {
            case DARK_RED -> Color.fromRGB(170, 0, 0);
            case RED -> Color.fromRGB(255, 85, 85);
            case GOLD -> Color.fromRGB(255, 170, 0);
            case YELLOW -> Color.fromRGB(255, 255, 85);
            case DARK_GREEN -> Color.fromRGB(0, 170, 0);
            case GREEN -> Color.fromRGB(85, 255, 85);
            case AQUA -> Color.fromRGB(85, 255, 255);
            case DARK_AQUA -> Color.fromRGB(0, 170, 170);
            case DARK_BLUE -> Color.fromRGB(0, 0, 170);
            case BLUE -> Color.fromRGB(85, 85, 255);
            case LIGHT_PURPLE -> Color.fromRGB(255, 85, 255);
            case DARK_PURPLE -> Color.fromRGB(170, 0, 170);
            case WHITE -> Color.WHITE;
            case GRAY -> Color.GRAY;
            case DARK_GRAY -> Color.fromRGB(85, 85, 85);
            case BLACK -> Color.BLACK;
            default -> Color.WHITE;
        };
    }

    private void cancelActiveTask() {
        if (activeTask != null) {
            activeTask.cancel();
            activeTask = null;
        }
    }

    private void restoreParticipants() {
        for (UUID uuid : new ArrayList<>(participants)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) {
                continue;
            }

            player.setSpectatorTarget(null);
            GameMode originalMode = originalGameModes.get(uuid);
            if (originalMode != null) {
                player.setGameMode(originalMode);
            }
        }

        originalGameModes.clear();
    }

    private void broadcast(Component message) {
        Bukkit.getServer().sendMessage(message);
    }

    private Sheep locateCameraSheep() {
        for (World world : Bukkit.getWorlds()) {
            for (Sheep sheep : world.getEntitiesByClass(Sheep.class)) {
                if (sheep.getScoreboardTags().contains("CameraTeamIntros")) {
                    return sheep;
                }
            }
        }
        return null;
    }

    private TextColor toTextColor(org.bukkit.ChatColor chatColor) {
        return switch (chatColor) {
            case BLACK -> NamedTextColor.BLACK;
            case DARK_BLUE -> NamedTextColor.DARK_BLUE;
            case DARK_GREEN -> NamedTextColor.DARK_GREEN;
            case DARK_AQUA -> NamedTextColor.DARK_AQUA;
            case DARK_RED -> NamedTextColor.DARK_RED;
            case DARK_PURPLE -> NamedTextColor.DARK_PURPLE;
            case GOLD -> NamedTextColor.GOLD;
            case GRAY -> NamedTextColor.GRAY;
            case DARK_GRAY -> NamedTextColor.DARK_GRAY;
            case BLUE -> NamedTextColor.BLUE;
            case GREEN -> NamedTextColor.GREEN;
            case AQUA -> NamedTextColor.AQUA;
            case RED -> NamedTextColor.RED;
            case LIGHT_PURPLE -> NamedTextColor.LIGHT_PURPLE;
            case YELLOW -> NamedTextColor.YELLOW;
            case WHITE -> NamedTextColor.WHITE;
            default -> NamedTextColor.WHITE;
        };
    }

    // ========== Ready Check System ==========

    private BukkitTask readyReminderTask;

    private void initializeReadyState() {
        Scoreboard scoreboard = plugin.getScoreboard();
        List<String> visibleTeams = plugin.getConfig().getStringList("teams.visible");

        for (String teamName : visibleTeams) {
            Team team = scoreboard.getTeam(teamName);
            if (team == null) {
                continue;
            }

            TeamReadySnapshot snapshot = new TeamReadySnapshot(teamName);
            for (String entry : team.getEntries()) {
                Player player = Bukkit.getPlayerExact(entry);
                if (player != null) {
                    snapshot.addMember(player.getUniqueId());
                }
            }

            if (snapshot.getTotalCount() > 0) {
                readyStateByTeam.put(teamName, snapshot);
            }
        }
    }

    private void sendReadyCheckPrompt() {
        for (UUID uuid : participants) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.sendMessage(Component.empty());
                player.sendMessage(Component.text("Is your team ready?", NamedTextColor.GOLD, TextDecoration.BOLD));
                
                // Create clickable Yes/No buttons
                Component yesButton = Component.text("[", NamedTextColor.DARK_GRAY)
                    .append(Component.text("YES", NamedTextColor.GREEN, TextDecoration.BOLD)
                        .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/readycheck yes")))
                    .append(Component.text("]", NamedTextColor.DARK_GRAY));
                
                Component noButton = Component.text("[", NamedTextColor.DARK_GRAY)
                    .append(Component.text("NO", NamedTextColor.RED, TextDecoration.BOLD)
                        .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/readycheck no")))
                    .append(Component.text("]", NamedTextColor.DARK_GRAY));
                
                player.sendMessage(yesButton.append(Component.text("  ")).append(noButton));
                player.sendMessage(Component.empty());
            }
        }
    }

    private void broadcastTeamReadyStatus() {
        // Get ready and not ready teams
        List<String> readyTeams = new ArrayList<>();
        List<String> notReadyTeams = new ArrayList<>();
        
        Scoreboard scoreboard = plugin.getScoreboard();
        
        for (Map.Entry<String, TeamReadySnapshot> entry : readyStateByTeam.entrySet()) {
            String teamName = entry.getKey();
            TeamReadySnapshot snapshot = entry.getValue();
            Team team = scoreboard.getTeam(teamName);
            
            if (team != null) {
                String displayName = team.getDisplayName();
                if (snapshot.isTeamReady()) {
                    readyTeams.add(displayName);
                } else {
                    notReadyTeams.add(displayName);
                }
            }
        }
        
        // Broadcast ready teams
        if (!readyTeams.isEmpty()) {
            Component readyMessage = Component.text("Ready: ", NamedTextColor.GREEN);
            for (int i = 0; i < readyTeams.size(); i++) {
                if (i > 0) {
                    readyMessage = readyMessage.append(Component.text(", ", NamedTextColor.GRAY));
                }
                readyMessage = readyMessage.append(Component.text(readyTeams.get(i), NamedTextColor.WHITE));
            }
            broadcast(readyMessage);
        }
        
        // Broadcast not ready teams
        if (!notReadyTeams.isEmpty()) {
            Component notReadyMessage = Component.text("Not Ready: ", NamedTextColor.RED);
            for (int i = 0; i < notReadyTeams.size(); i++) {
                if (i > 0) {
                    notReadyMessage = notReadyMessage.append(Component.text(", ", NamedTextColor.GRAY));
                }
                notReadyMessage = notReadyMessage.append(Component.text(notReadyTeams.get(i), NamedTextColor.WHITE));
            }
            broadcast(notReadyMessage);
        }
    }

    private void checkAllTeamsReady() {
        boolean allReady = !readyStateByTeam.isEmpty() &&
            readyStateByTeam.values().stream().allMatch(TeamReadySnapshot::isTeamReady);

        if (allReady) {
            cancelReadyReminder();
            closeAllReadyMenus();
            broadcast(Component.text("All teams are ready! Starting intro...", NamedTextColor.GREEN));
            beginIntroSequence();
        }
    }

    private void closeAllReadyMenus() {
        // No longer needed for chat-based system
        openReadyMenus.clear();
    }

    private void cancelReadyReminder() {
        if (readyReminderTask != null) {
            readyReminderTask.cancel();
            readyReminderTask = null;
        }
    }

    public void handleReadyResponse(Player player, boolean ready) {
        if (!gatheringReadiness) {
            return;
        }
        
        UUID uuid = player.getUniqueId();
        if (!participants.contains(uuid)) {
            return;
        }
        
        // Only mark as ready if they clicked Yes
        if (ready) {
            readyResponses.put(uuid, true);
        } else {
            readyResponses.remove(uuid);
        }

        // Update team snapshot
        TeamReadySnapshot playerTeam = null;
        for (TeamReadySnapshot snapshot : readyStateByTeam.values()) {
            if (snapshot.getMembers().contains(uuid)) {
                snapshot.setReady(uuid, ready);
                playerTeam = snapshot;
                break;
            }
        }
        
        // If team just became ready, broadcast status
        if (ready && playerTeam != null && playerTeam.isTeamReady()) {
            broadcastTeamReadyStatus();
        }
        
        checkAllTeamsReady();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        
        // If event is running, re-add the player to participants
        if (running) {
            if (!participants.contains(uuid)) {
                participants.add(uuid);
            }
            
            // Store their original game mode if not already stored
            if (!originalGameModes.containsKey(uuid)) {
                originalGameModes.put(uuid, player.getGameMode());
            }
            
            // Add them to the bossbar if it exists
            if (bossBar != null && bossBar.isVisible()) {
                bossBar.addPlayer(player);
            }
            
            // Handle different phases
            switch (currentPhase) {
                case COUNTDOWN:
                case PRESHOW:
                    // During countdown/preshow, players should be in their normal state
                    // They'll be teleported and set to spectator during fade to black
                    break;
                    
                case TEAM_INTROS:
                case VOTING_COUNTDOWN:
                    // During team intros or voting countdown, put them in spectator mode
                    // and lock them to the camera
                    player.setGameMode(GameMode.SPECTATOR);
                    player.teleport(spectatorLocation);
                    
                    if (cameraSheep != null && cameraSheep.isValid()) {
                        player.setSpectatorTarget(cameraSheep);
                        cameraLockedPlayers.add(uuid);
                    }
                    break;
                    
                case NONE:
                default:
                    // No special handling needed
                    break;
            }
        }
        
        // Handle ready check phase
        if (gatheringReadiness) {
            if (!participants.contains(uuid)) {
                participants.add(uuid);
            }
            
            // Add to ready bossbar if it exists
            if (readyBossBar != null) {
                readyBossBar.addPlayer(player);
            }
            
            // Send them the ready check prompt
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (gatheringReadiness && player.isOnline()) {
                    player.sendMessage(Component.empty());
                    player.sendMessage(Component.text("Is your team ready?", NamedTextColor.GOLD, TextDecoration.BOLD));
                    
                    // Create clickable Yes/No buttons
                    Component yesButton = Component.text("[", NamedTextColor.DARK_GRAY)
                        .append(Component.text("YES", NamedTextColor.GREEN, TextDecoration.BOLD)
                            .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/readycheck yes")))
                        .append(Component.text("]", NamedTextColor.DARK_GRAY));
                    
                    Component noButton = Component.text("[", NamedTextColor.DARK_GRAY)
                        .append(Component.text("NO", NamedTextColor.RED, TextDecoration.BOLD)
                            .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/readycheck no")))
                        .append(Component.text("]", NamedTextColor.DARK_GRAY));
                    
                    player.sendMessage(yesButton.append(Component.text("  ")).append(noButton));
                    player.sendMessage(Component.empty());
                }
            }, 20L); // Delay by 1 second to let them fully load in
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();

        if (gatheringReadiness && participants.contains(uuid)) {
            readyResponses.put(uuid, false);

            for (TeamReadySnapshot snapshot : readyStateByTeam.values()) {
                if (snapshot.getMembers().contains(uuid)) {
                    snapshot.setReady(uuid, false);
                    break;
                }
            }
        }

        if (participants.contains(uuid)) {
            participants.remove(uuid);
            cameraLockedPlayers.remove(uuid);
            originalGameModes.remove(uuid);
        }
    }
}