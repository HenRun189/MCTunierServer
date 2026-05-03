package me.HenRun189.tunierServer.game;

import me.HenRun189.tunierServer.game.modes.*;
import me.HenRun189.tunierServer.team.*;
import me.HenRun189.tunierServer.score.ScoreManager;
import me.HenRun189.tunierServer.achievement.AchievementManager;
import me.HenRun189.tunierServer.backpack.BackpackManager;
import me.HenRun189.tunierServer.world.WorldManager;
import me.HenRun189.tunierServer.TunierServer;

import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.Keyed;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.World;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.Location;
import org.bukkit.event.player.PlayerMoveEvent;

import net.kyori.adventure.text.Component;

import java.util.*;

public class GameManager implements Listener {
    private GameMode currentMode;

    private final TeamManager teamManager;
    private final ScoreManager scoreManager;
    private final AchievementManager achievementManager;
    private final BackpackManager backpackManager;
    private final WorldManager worldManager = new WorldManager();
    private final Map<String, World> preparedWorlds = new HashMap<>();
    private long currentSeed;
    private boolean gameActive = false;
    private boolean countdownRunning = false;
    private boolean devMode = true;

    private static final int JR_MIN_X = -2;
    private static final int JR_MAX_X = 3;
    private static final int JR_MIN_Y = 64;
    private static final int JR_MAX_Y = 66;
    private static final int JR_MIN_Z = -1;
    private static final int JR_MAX_Z = 2;

    private final Set<UUID> frozenPlayers = new HashSet<>();
    private boolean paused = false;

    public boolean isPaused()             { return paused; }
    public void setPaused(boolean paused) { this.paused = paused; }

    public GameManager(TeamManager teamManager,
                       ScoreManager scoreManager,
                       AchievementManager achievementManager,
                       BackpackManager backpackManager) {
        this.teamManager        = teamManager;
        this.scoreManager       = scoreManager;
        this.achievementManager = achievementManager;
        this.backpackManager    = backpackManager;
    }

    // ══════════════════════════════════════════════════════════════
    //  STOP GAME  — smooth mit Delay, Items clearen, Player resetten
    // ══════════════════════════════════════════════════════════════

    public void stopGame() {
        if (currentMode == null) {
            Bukkit.broadcast(Component.text("§cKein Spiel läuft!"));
            return;
        }

        currentMode.stop();   // End-Screen + Sound wird in stop() gemacht
        countdownRunning = false;
        gameActive       = false;

        // Fix 3: Inventar sofort clearen damit niemand Items in die Lobby schleppt
        //        + alle gedroppten Items in der Spielwelt löschen
        clearAllInventories();
        clearDroppedItemsInGameWorlds();

        // Fix 2: Smooth-TP mit 3 Sek Delay nach dem End-Screen
        Bukkit.getScheduler().runTaskLater(TunierServer.getInstance(), () -> {

            // Countdown-Sound: 3-2-1
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 0.8f);
                p.sendTitle("§6Zurück zur Lobby", "§7Teleport in 3...", 5, 40, 5);
            }

            Bukkit.getScheduler().runTaskLater(TunierServer.getInstance(), () -> {
                for (Player p : Bukkit.getOnlinePlayers())
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.0f);

                Bukkit.getScheduler().runTaskLater(TunierServer.getInstance(), () -> {
                    for (Player p : Bukkit.getOnlinePlayers())
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.3f);

                    Bukkit.getScheduler().runTaskLater(TunierServer.getInstance(), () -> {
                        // Jetzt teleportieren + vollen Reset
                        resetAllPlayers();
                        backpackManager.clearAll();
                        teleportAllToLobby();
                        deleteGameWorlds();

                        preparedWorlds.clear();
                        scoreManager.setCurrentGame("Warten auf Spiel");
                        unfreezeAll();

                        teamManager.reapplyAllTeams();
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            teamManager.applyTeamToPlayer(p);
                            // Fix 4: Lobby-Sound
                            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                        }

                        currentMode = null;
                    }, 20L); // +1 Sek = GO

                }, 20L); // 1 Sek
            }, 20L); // 1 Sek

        }, 60L); // 3 Sek Delay nach End-Screen
    }

    public void resetGame() {
        stopGame();
        teamManager.resetAllTeams();
        scoreManager.setupScoreboard();
        scoreManager.setCurrentGame("Warten auf Spiel");
        scoreManager.updateScoreboard();
        Bukkit.broadcast(Component.text("§cAlles wurde zurückgesetzt!"));
    }

    public void softReset() {
        if (currentMode == null) {
            Bukkit.broadcast(Component.text("§cKein Spiel läuft!"));
            return;
        }
        currentMode.stop();
        scoreManager.resetGame();
        Bukkit.broadcast(Component.text("§eGame resetet!"));
    }

    // ══════════════════════════════════════════════════════════════
    //  FIX 3: Inventar + gedroppte Items clearen
    // ══════════════════════════════════════════════════════════════

    private void clearAllInventories() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.getInventory().clear();
            p.getInventory().setArmorContents(new ItemStack[4]);
        }
    }

    private void clearDroppedItemsInGameWorlds() {
        for (World world : Bukkit.getWorlds()) {
            if (world.getName().startsWith("game_") || world.getName().equals("pvp_map")) {
                for (Entity e : world.getEntities()) {
                    if (e instanceof Item) e.remove();
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  FREEZE
    // ══════════════════════════════════════════════════════════════

    public void freezePlayer(Player p)   { frozenPlayers.add(p.getUniqueId()); }
    public void unfreezePlayer(Player p) { frozenPlayers.remove(p.getUniqueId()); }
    public boolean isFrozen(Player p)    { return frozenPlayers.contains(p.getUniqueId()); }
    public void freezeAll()              { Bukkit.getOnlinePlayers().forEach(this::freezePlayer); }
    public void unfreezeAll()            { frozenPlayers.clear(); }
    public Set<UUID> getFrozenPlayers()  { return frozenPlayers; }

    // ══════════════════════════════════════════════════════════════
    //  LOBBY TP
    // ══════════════════════════════════════════════════════════════

    private void teleportAllToLobby() {
        World lobby = Bukkit.getWorld("lobby");
        if (lobby == null) {
            Bukkit.broadcast(Component.text("§cLobby Welt nicht gefunden!"));
            return;
        }
        Location spawn = lobby.getSpawnLocation();
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.teleport(spawn);
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  DELETE WORLDS
    // ══════════════════════════════════════════════════════════════

    private void deleteGameWorlds() {
        for (World world : Bukkit.getWorlds()) {
            if (world.getName().startsWith("game_")) worldManager.deleteWorld(world);
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  PLAYER RESET
    // ══════════════════════════════════════════════════════════════

    private void resetAllPlayers() {
        for (Player p : Bukkit.getOnlinePlayers()) resetPlayer(p);
    }

    private void resetPlayer(Player p) {
        p.getInventory().clear();
        p.getInventory().setArmorContents(new ItemStack[4]);
        p.getEnderChest().clear();
        p.setLevel(0);
        p.setExp(0);
        p.setHealth(20.0);
        p.setFoodLevel(20);
        p.setSaturation(20);
        p.getActivePotionEffects().forEach(effect -> p.removePotionEffect(effect.getType()));
        p.setFireTicks(0);
        p.setFallDistance(0);
        p.setGameMode(org.bukkit.GameMode.SURVIVAL);
        p.setAllowFlight(false);
        p.setFlying(false);
        p.setInvulnerable(false);
        p.playerListName(null);

        Bukkit.getScheduler().runTask(TunierServer.getInstance(), () -> {
            Iterator<Advancement> advIt = Bukkit.advancementIterator();
            while (advIt.hasNext()) {
                Advancement adv = advIt.next();
                AdvancementProgress progress = p.getAdvancementProgress(adv);
                for (String crit : new HashSet<>(progress.getAwardedCriteria()))
                    progress.revokeCriteria(crit);
            }
            Iterator<Recipe> recipeIt = Bukkit.recipeIterator();
            while (recipeIt.hasNext()) {
                Recipe recipe = recipeIt.next();
                if (recipe instanceof Keyed keyed) p.undiscoverRecipe(keyed.getKey());
            }
        });
    }

    // ══════════════════════════════════════════════════════════════

    public GameMode getCurrentMode()  { return currentMode; }
    public boolean isGameRunning()    { return currentMode != null; }

    public String getCurrentAchievement(Player player) {
        if (!(currentMode instanceof AchievementMode mode)) return null;
        TeamData team = teamManager.getTeamByPlayer(player.getUniqueId());
        if (team == null) return null;
        return mode.getCurrentAdvancement(team.getName());
    }

    public AchievementManager getAchievementManager() { return achievementManager; }

    // ══════════════════════════════════════════════════════════════
    //  START GAME FLOW
    // ══════════════════════════════════════════════════════════════

    public void startGameFlow(String mode) {
        if (isGameRunning()) {
            Bukkit.broadcast(Component.text("§cEs läuft bereits ein Spiel!"));
            return;
        }

        currentSeed = new Random().nextLong();
        String lower = mode.toLowerCase(Locale.ROOT);

        switch (lower) {
            case "achievement" -> {
                currentMode = new AchievementMode(teamManager, scoreManager, achievementManager);
                scoreManager.setCurrentGame("Achievement Battle");
            }
            case "jumpandrun" -> {
                currentMode = new JumpAndRunMode(teamManager, scoreManager, this);
                scoreManager.setCurrentGame("Jump and Run");
                startLobbyCountdown(lower);
                return;
            }
            case "spleefwindcharge" -> {
                currentMode = new SpleefWindChargeMode(teamManager, scoreManager);
                scoreManager.setCurrentGame("Spleef Windcharge");
                startLobbyCountdown(lower);
                return;
            }
            case "spleeffallingblocks" -> {
                currentMode = new SpleefFallingBlocks(teamManager, scoreManager);
                scoreManager.setCurrentGame("Spleef Falling Blocks");
                startLobbyCountdown(lower);
                return;
            }
            case "pvp", "pvptest" -> {
                PvPMode pvp = new PvPMode(teamManager, scoreManager);
                if (lower.equals("pvptest")) pvp.setSoloTestMode(true);
                currentMode = pvp;
                scoreManager.setCurrentGame("Hunger Games");
                backpackManager.clearAll();
                scoreManager.resetGame();
                scoreManager.setupScoreboard();
                resetAllPlayers();
                startLobbyCountdown(lower);
                for (Player p : Bukkit.getOnlinePlayers()) {
                    scoreManager.applyToPlayer(p);
                    teamManager.applyTeamToPlayer(p);
                }
                return;
            }
            case "itemcollector" -> {
                currentMode = new ItemCollectorMode(teamManager, scoreManager);
                scoreManager.setCurrentGame("Item Collector");
            }
            default -> {
                Bukkit.broadcast(Component.text("§cUnbekannter Modus!"));
                return;
            }
        }

        backpackManager.clearAll();
        scoreManager.resetGame();
        scoreManager.setupScoreboard();
        resetAllPlayers();
        prepareWorldsAsync();
        startLobbyCountdown(lower);

        for (Player p : Bukkit.getOnlinePlayers()) {
            scoreManager.applyToPlayer(p);
            teamManager.applyTeamToPlayer(p);
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  LOBBY COUNTDOWN (2 min oder devMode 5 sek)
    // ══════════════════════════════════════════════════════════════

    private void startLobbyCountdown(String mode) {
        String displayName = switch (mode) {
            case "achievement"         -> "Achievement Battle";
            case "pvp", "pvptest"      -> "Hunger Games";
            case "jumpandrun"          -> "Jump and Run";
            case "spleefwindcharge"    -> "Spleef Windcharge";
            case "spleeffallingblocks" -> "Spleef Falling Blocks";
            case "itemcollector"       -> "Item Collector";
            default                    -> mode;
        };

        new BukkitRunnable() {
            int time    = devMode ? 5 : 120;
            boolean warned = false;

            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    int minutes   = time / 60;
                    int seconds   = time % 60;
                    String timeStr = String.format("%02d:%02d", minutes, seconds);
                    String color  = time <= 10 ? "§c" : time <= 30 ? "§e" : "§a";
                    p.sendActionBar(Component.text(
                            "§6" + displayName + " §8» §7TP in " + color + timeStr + " §8┃ §e/gameinfo"
                    ));
                }

                if (time == 120) {
                    Bukkit.broadcast(Component.text("§7Spiel §e" + displayName + " §7startet in §a2 Minuten!"));
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 0.8f);
                        p.sendTitle("§6" + displayName, "§7Start in 2 Minuten", 10, 60, 10);
                    }
                    sendGameInfo(mode);
                }
                if (time == 60) {
                    Bukkit.broadcast(Component.text("§7Spiel §e" + displayName + " §7startet in §a1 Minute!"));
                    for (Player p : Bukkit.getOnlinePlayers())
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.0f);
                }
                if (time == 10) {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.sendTitle("§cTeleport", "§e10 Sekunden", 5, 40, 5);
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.3f);
                    }
                }
                if (time <= 5 && time > 0) {
                    Bukkit.broadcast(Component.text("§7Teleport in §e" + time + " Sekunden!"));
                    for (Player p : Bukkit.getOnlinePlayers())
                        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.5f);
                }

                if (time <= 0) {
                    cancel();

                    if (currentMode instanceof JumpAndRunMode
                            || currentMode instanceof SpleefWindChargeMode
                            || currentMode instanceof SpleefFallingBlocks) {
                        prepareWorldsAndTeleport();
                        return;
                    }

                    // Fix 1: PvP → erst in pvp_map TP (Wartepunkt), DANN 15sek Countdown
                    if (currentMode instanceof PvPMode) {
                        teleportAllToPvpWaitSpot();
                        return;
                    }

                    if (preparedWorlds.size() < teamManager.getTeams().size()) {
                        if (!warned) {
                            Bukkit.broadcast(Component.text("§cWarte auf Welten..."));
                            warned = true;
                        }
                        return;
                    }
                    prepareWorldsAndTeleport();
                }
                time--;
            }
        }.runTaskTimer(TunierServer.getInstance(), 0L, 20L);
    }

    // ══════════════════════════════════════════════════════════════
    //  FIX 1: PvP Warte-Spot vor dem 15sek Countdown
    //  Alle landen an einem zentralen Punkt in pvp_map,
    //  dann läuft der 15sek Countdown, DANN verteilt onGameStart()
    //  sie auf die individuellen Spawn-Coords.
    // ══════════════════════════════════════════════════════════════

    private void teleportAllToPvpWaitSpot() {
        World pvpWorld = Bukkit.getWorld("pvp_map");
        if (pvpWorld == null) {
            Bukkit.broadcast(Component.text("§cPvP Welt nicht gefunden!"));
            return;
        }

        // Spawn-Liste mischen (gleiche Logik wie in PvPMode)
        int[][] SPAWN_COORDS = {
                {38,67,32},{32,67,42},{22,67,48},{10,67,48},{0,67,42},{-5,67,32},
                {-5,67,20},{0,67,10},{10,67,4},{22,67,4},{32,67,10},{38,67,20}
        };
        List<int[]> shuffled = new ArrayList<>(Arrays.asList(SPAWN_COORDS));
        Collections.shuffle(shuffled);

        List<Player> participants = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (teamManager.getTeamByPlayer(p.getUniqueId()) != null) participants.add(p);
        }

        for (int i = 0; i < participants.size(); i++) {
            Player p = participants.get(i);
            int[] c = shuffled.get(i % shuffled.size());
            p.teleport(new Location(pvpWorld, c[0] + 0.5, c[1], c[2] + 0.5));
            p.setGameMode(org.bukkit.GameMode.ADVENTURE);
            p.setInvulnerable(true);
            p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
        }

        Bukkit.broadcast(Component.text("§6§lHunger Games §8| §7Alle auf Startposition – Countdown läuft!"));
        Bukkit.getScheduler().runTaskLater(TunierServer.getInstance(), this::startGameCountdown, 20L);
    }

    // ══════════════════════════════════════════════════════════════
    //  WORLD PREPARE & TELEPORT
    // ══════════════════════════════════════════════════════════════

    private void prepareWorldsAndTeleport() {
        if (currentMode instanceof JumpAndRunMode) {
            World world = Bukkit.getWorld("voidworld2");
            if (world == null) { Bukkit.broadcast(Component.text("§cJump&Run Welt nicht gefunden!")); return; }
            for (Player p : Bukkit.getOnlinePlayers()) p.teleport(new Location(world, 0.5, 65, 0.5, 0f, 0f));
            startGameCountdown();
            return;
        }

        if (currentMode instanceof SpleefWindChargeMode) {
            World world = Bukkit.getWorld("windchargeworld");
            if (world == null) { Bukkit.broadcast(Component.text("§cWindcharge Welt nicht gefunden!")); return; }
            for (Player p : Bukkit.getOnlinePlayers()) p.teleport(new Location(world, -13.7, 117, -15.7, 0f, 0f));
            startGameCountdown();
            return;
        }

        if (currentMode instanceof SpleefFallingBlocks) {
            World world = Bukkit.getWorld("windchargeworld");
            if (world == null) { Bukkit.broadcast(Component.text("§cWindcharge Welt nicht gefunden!")); return; }
            for (Player p : Bukkit.getOnlinePlayers()) p.teleport(new Location(world, 0, 128, -39, 0f, 0f));
            startGameCountdown();
            return;
        }

        for (TeamData team : teamManager.getTeams().values()) {
            World world = preparedWorlds.get(team.getName());
            if (world == null) { Bukkit.getLogger().warning("World nicht geladen: " + team.getName()); continue; }
            Location spawn = world.getSpawnLocation();
            int chunkX = spawn.getBlockX() >> 4;
            int chunkZ = spawn.getBlockZ() >> 4;
            for (int x = -5; x <= 5; x++)
                for (int z = -5; z <= 5; z++)
                    world.getChunkAt(chunkX + x, chunkZ + z).load(true);
            worldManager.teleportTeam(team, world);
            team.setSpawnLocation(spawn);
        }

        Bukkit.getScheduler().runTaskLater(TunierServer.getInstance(), this::startGameCountdown, 30L);
    }

    // ══════════════════════════════════════════════════════════════
    //  15 SEK GAME COUNTDOWN
    // ══════════════════════════════════════════════════════════════

    private void startGameCountdown() {
        if (countdownRunning) return;
        countdownRunning = true;

        if (currentMode instanceof JumpAndRunMode jnrMode) {
            final List<Player> participants = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers())
                if (p.getWorld().getName().equals("voidworld2")) participants.add(p);
            jnrMode.preGame(participants);

            new BukkitRunnable() {
                int time = 15;
                @Override public void run() {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.sendTitle("§2" + time, "§8Spiel startet...", 0, 20, 0);
                        if (time <= 10 && time > 0) p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                        if (time == 0)              p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                    }
                    if (time <= 0) { gameActive = true; currentMode.start(); countdownRunning = false; cancel(); return; }
                    time--;
                }
            }.runTaskTimer(TunierServer.getInstance(), 0L, 20L);
            return;
        }

        if (currentMode instanceof SpleefWindChargeMode jnrMode) {
            final List<Player> participants = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers())
                if (p.getWorld().getName().equals("windchargeworld")) participants.add(p);
            jnrMode.preGame(participants);
            Bukkit.getPluginManager().registerEvents(jnrMode, TunierServer.getInstance());

            new BukkitRunnable() {
                int time = 15;
                @Override public void run() {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.sendTitle("§2" + time, "§8Spiel startet...", 0, 20, 0);
                        if (time <= 10 && time > 0) p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                        if (time == 0)              p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                    }
                    if (time <= 0) { gameActive = true; currentMode.start(); countdownRunning = false; cancel(); return; }
                    time--;
                }
            }.runTaskTimer(TunierServer.getInstance(), 0L, 20L);
            return;
        }

        // Normaler Countdown — PvP + Achievement + ItemCollector
        new BukkitRunnable() {
            int time = 15;
            @Override public void run() {
                if (time == 15) freezeAll();

                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.sendTitle("§2" + time, "§8Spiel startet...", 0, 20, 0);
                    // Fix 4: Tick-Sound jeden Sek im Countdown
                    if (time <= 10 && time > 0)
                        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f + (0.05f * (10 - time)));
                    if (time == 0) {
                        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                        p.setInvulnerable(false); // Fix 1: Invulnerable vom Warte-Spot aufheben
                    }
                }

                if (time <= 0) {
                    unfreezeAll();
                    gameActive = true;
                    currentMode.start(); // → onGameStart() verteilt PvP-Spieler auf Spawns
                    countdownRunning = false;
                    cancel();
                    return;
                }
                time--;
            }
        }.runTaskTimer(TunierServer.getInstance(), 0L, 20L);
    }

    // ══════════════════════════════════════════════════════════════
    //  EVENTS
    // ══════════════════════════════════════════════════════════════

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        if (getCurrentMode() == null) return;
        Player p = e.getPlayer();
        TeamData team = teamManager.getTeamByPlayer(p.getUniqueId());
        if (team != null && team.getSpawnLocation() != null)
            e.setRespawnLocation(team.getSpawnLocation());
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();

        if (isFrozen(p) && !(currentMode instanceof JumpAndRunMode)) {
            Location from = e.getFrom();
            Location to   = e.getTo();
            if (to == null) return;
            e.setTo(new Location(from.getWorld(), from.getX(), from.getY(), from.getZ(),
                    to.getYaw(), to.getPitch()));
            // ← diese Zeile hinzufügen:
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.5f);
            return;
        }

        if (!(currentMode instanceof JumpAndRunMode)) return;
        if (gameActive) return;
        if (!p.getWorld().getName().equals("voidworld2")) return;

        Location to = e.getTo();
        if (to == null) return;

        int bx = to.getBlockX(), by = to.getBlockY(), bz = to.getBlockZ();
        boolean outside = bx < JR_MIN_X || bx > JR_MAX_X
                || by < JR_MIN_Y || by > JR_MAX_Y
                || bz < JR_MIN_Z || bz > JR_MAX_Z;

        if (outside) {
            Location from = e.getFrom();
            e.setTo(new Location(from.getWorld(), from.getX(), from.getY(), from.getZ(), to.getYaw(), to.getPitch()));
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  WORLD PREP
    // ══════════════════════════════════════════════════════════════

    private void prepareWorldsAsync() {
        if (currentMode instanceof JumpAndRunMode) return;
        if (currentMode instanceof PvPMode) return;
        preparedWorlds.clear();

        List<TeamData> teams = new ArrayList<>(teamManager.getTeams().values());

        new BukkitRunnable() {
            int index = 0;
            @Override public void run() {
                if (index >= teams.size()) { cancel(); return; }
                TeamData team = teams.get(index++);
                World world = worldManager.createTeamWorld(team.getName(), currentSeed);
                if (world == null) { Bukkit.getLogger().warning("World konnte nicht erstellt werden: " + team.getName()); return; }
                world.setAutoSave(false);
                preparedWorlds.put(team.getName(), world);
                Location spawn = world.getSpawnLocation();
                for (int x = -5; x <= 5; x++)
                    for (int z = -5; z <= 5; z++)
                        world.getChunkAt((spawn.getBlockX() >> 4) + x, (spawn.getBlockZ() >> 4) + z).load();
            }
        }.runTaskTimer(TunierServer.getInstance(), 0L, 30L);
    }

    private void sendGameInfo(String mode) {
        switch (mode.toLowerCase()) {
            case "achievement" -> {
                Bukkit.broadcast(Component.text(" "));
                Bukkit.broadcast(Component.text("§8§m-----------------------------"));
                Bukkit.broadcast(Component.text("§6§lAchievement Battle"));
                Bukkit.broadcast(Component.text("§7➤ Erfülle das vorgegebene Achievement"));
                Bukkit.broadcast(Component.text("§7➤ Jedes Achievement gibt §a+10 Punkte"));
                Bukkit.broadcast(Component.text("§7➤ §c3x Skip§7 mit §e/skip"));
                Bukkit.broadcast(Component.text("§8┃ §e/help §7für Achievement Infos"));
                Bukkit.broadcast(Component.text("§8┃ §e/bp §7für Team-Backpack"));
                Bukkit.broadcast(Component.text("§8§m-----------------------------"));
            }
            case "pvp", "pvptest" -> {
                Bukkit.broadcast(Component.text(" "));
                Bukkit.broadcast(Component.text("§8§m-----------------------------"));
                Bukkit.broadcast(Component.text("§6§lHunger Game"));
                Bukkit.broadcast(Component.text("§7➤ Pro Kill §a+20 Punkte"));
                Bukkit.broadcast(Component.text("§7➤ Überleben bringt alle 15s §a+1 Punkt"));
                Bukkit.broadcast(Component.text("§7➤ Worldborder verkleinert sich"));
                Bukkit.broadcast(Component.text("§8§m-----------------------------"));
            }
            case "jumpandrun" -> {
                Bukkit.broadcast(Component.text(" "));
                Bukkit.broadcast(Component.text("§8§m-----------------------------"));
                Bukkit.broadcast(Component.text("§6§lJump and Run"));
                Bukkit.broadcast(Component.text("§7➤ Erreiche das Ziel so schnell wie möglich"));
                Bukkit.broadcast(Component.text("§7➤ §6Goldblöcke §7= Checkpoints (§a+5 Punkte§7)"));
                Bukkit.broadcast(Component.text("§7➤ Die ersten §e3 Spieler §7erhalten Bonuspunkte"));
                Bukkit.broadcast(Component.text("§8§m-----------------------------"));
            }
            case "itemcollector" -> {
                Bukkit.broadcast(Component.text(" "));
                Bukkit.broadcast(Component.text("§8§m-----------------------------"));
                Bukkit.broadcast(Component.text("§6§lItem Sammler"));
                Bukkit.broadcast(Component.text("§7➤ Sammle so viele verschiedene Items wie möglich"));
                Bukkit.broadcast(Component.text("§7➤ Jedes neue Item §a+1 Punkt"));
                Bukkit.broadcast(Component.text("§8┃ §e/bp §7für Team-Backpack"));
                Bukkit.broadcast(Component.text("§8§m-----------------------------"));
            }
            case "spleefwindcharge" -> {
                Bukkit.broadcast(Component.text(" "));
                Bukkit.broadcast(Component.text("§8§m-----------------------------"));
                Bukkit.broadcast(Component.text("§b§lSpleef Windcharge"));
                Bukkit.broadcast(Component.text("§7➤ Bleib auf den Plattformen für Punkte"));
                Bukkit.broadcast(Component.text("§7➤ Nutze §fWindcharges §7um Gegner runterzuschießen"));
                Bukkit.broadcast(Component.text("§8§m-----------------------------"));
            }
        }
    }

    public boolean isGameActive() { return gameActive; }
}