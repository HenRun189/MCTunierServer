package me.HenRun189.tunierServer.game;

import me.HenRun189.tunierServer.game.modes.*;
import me.HenRun189.tunierServer.team.*;
import me.HenRun189.tunierServer.score.ScoreManager;
import me.HenRun189.tunierServer.achievement.AchievementManager;
import me.HenRun189.tunierServer.backpack.BackpackManager;
import me.HenRun189.tunierServer.world.WorldManager;
import me.HenRun189.tunierServer.TunierServer;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.Keyed;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.Location;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.boss.BossBar;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;

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

    public boolean isPaused() {
        return paused;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    public GameManager(TeamManager teamManager,
                       ScoreManager scoreManager,
                       AchievementManager achievementManager,
                       BackpackManager backpackManager) {

        this.teamManager = teamManager;
        this.scoreManager = scoreManager;
        this.achievementManager = achievementManager;
        this.backpackManager = backpackManager;
    }



    // =========================
    // 🛑 STOP GAME
    // =========================

    public void stopGame() {

        if (currentMode == null) {
            Bukkit.broadcast(Component.text("§cKein Spiel läuft!"));
            return;
        }

        currentMode.stop();

        countdownRunning = false;
        gameActive = false;

        resetAllPlayers();
        backpackManager.clearAll();

        teleportAllToLobby();
        deleteGameWorlds();

        preparedWorlds.clear();

        scoreManager.setCurrentGame("Warten auf Spiel");

        unfreezeAll();
        currentMode = null;
        //Bukkit.broadcast(Component.text("§cSpiel gestoppt!"));
    }

    public void resetGame() {
        stopGame();

        teamManager.resetAllTeams();

        // 🔥 WICHTIG: Scoreboard neu aufbauen
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

    // =========================
    // ❄️ FREEZE
    // =========================

    public void freezePlayer(Player p) {
        frozenPlayers.add(p.getUniqueId());
    }

    public void unfreezePlayer(Player p) {
        frozenPlayers.remove(p.getUniqueId());
    }

    public boolean isFrozen(Player p) {
        return frozenPlayers.contains(p.getUniqueId());
    }

    public void freezeAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            freezePlayer(p);
        }
    }

    public void unfreezeAll() {
        frozenPlayers.clear();
    }

    public Set<UUID> getFrozenPlayers() {
        return frozenPlayers;
    }

    // =========================
    // 📍 LOBBY TP
    // =========================

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

    // =========================
    // 🌍 DELETE WORLDS
    // =========================

    private void deleteGameWorlds() {

        for (World world : Bukkit.getWorlds()) {
            if (world.getName().startsWith("game_")) {
                worldManager.deleteWorld(world);
            }
        }
    }

    // =========================
    // 🔄 PLAYER RESET
    // =========================

    private void resetAllPlayers() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            resetPlayer(p);
        }
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

        p.getActivePotionEffects().forEach(effect ->
                p.removePotionEffect(effect.getType())
        );

        p.setFireTicks(0);
        p.setFallDistance(0);

        p.setGameMode(org.bukkit.GameMode.SURVIVAL);
        p.setAllowFlight(false);
        p.setFlying(false);
        p.setInvulnerable(false);

        Bukkit.getScheduler().runTask(TunierServer.getInstance(), () -> {

            // ADVANCEMENTS RESET
            Iterator<Advancement> advIt = Bukkit.advancementIterator();

            while (advIt.hasNext()) {
                Advancement adv = advIt.next();
                AdvancementProgress progress = p.getAdvancementProgress(adv);

                for (String crit : new HashSet<>(progress.getAwardedCriteria())) {
                    progress.revokeCriteria(crit);
                }
            }

            // RECIPES RESET
            Iterator<Recipe> recipeIt = Bukkit.recipeIterator();

            while (recipeIt.hasNext()) {
                Recipe recipe = recipeIt.next();

                if (recipe instanceof Keyed keyed) {
                    p.undiscoverRecipe(keyed.getKey());
                }
            }

        });
    }

    // =========================

    public GameMode getCurrentMode() {
        return currentMode;
    }

    public boolean isGameRunning() {
        return currentMode != null;
    }

    public String getCurrentAchievement(Player player) {

        if (!(currentMode instanceof AchievementMode mode)) return null;

        TeamData team = teamManager.getTeamByPlayer(player.getUniqueId());
        if (team == null) return null;

        return mode.getCurrentAdvancement(team.getName());
    }

    public AchievementManager getAchievementManager() {
        return achievementManager;
    }


    public void startGameFlow(String mode) {

        if (isGameRunning()) {
            Bukkit.broadcast(Component.text("§cEs läuft bereits ein Spiel!"));
            return;
        }

        currentSeed = new Random().nextLong(); // jede Runde neue Map
        String lower = mode.toLowerCase(Locale.ROOT);

        // Mode setzen (dein Code bleibt gleich)
        switch (lower) {
            case "achievement" -> {
                currentMode = new AchievementMode(teamManager, scoreManager, achievementManager);
                scoreManager.setCurrentGame("Achievement Battle");
            }
            case "jumpandrun" -> {
                currentMode = new JumpAndRunMode(teamManager, scoreManager);

                scoreManager.setCurrentGame("Jump and Run");

                // ❗ WICHTIG: KEINE WORLDS LADEN
                startLobbyCountdown(lower);
                return;
            }
            case "pvp" -> {
                currentMode = new PvPMode();
                scoreManager.setCurrentGame("PvP");
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
        //teleportAllToLobby();

        // 120 Sekunden Lobby Countdown
        if (lower.equals("jumpandrun")) {

            startLobbyCountdown(lower); // nur countdown

        } else {

            prepareWorldsAsync();
            startLobbyCountdown(lower);

        }
    }

    private void startLobbyCountdown(String mode) {

        String displayName = switch (mode) {
            case "achievement" -> "Achievement Battle";
            case "pvp" -> "PvP";
            case "jumpandrun" -> "Jump and Run";
            case "itemcollector" -> "Item Collector";
            default -> mode;
        };

        new BukkitRunnable() {

            int time = devMode ? 5 : 120;
            boolean warned = false;



            @Override
            public void run() {

                // 📢 CHAT nur bestimmte Zeiten

                for (Player p : Bukkit.getOnlinePlayers()) {

                    int minutes = time / 60;
                    int seconds = time % 60;

                    String timeString = String.format("%02d:%02d", minutes, seconds);
                    String color = time <= 10 ? "§c" : time <= 30 ? "§e" : "§a";

                    p.sendActionBar(Component.text(
                            "§6" + displayName + " §8» §7Start in " + color + timeString +
                                    " §8┃ §e/gameinfo"
                    ));
                }

                if (time == 120) {
                    Bukkit.broadcast(Component.text("§7Spiel §e" + displayName  + " §7startet in §a2 Minuten!"));
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 0.8f);
                    }

                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.sendTitle("§6" + displayName , "§7Start in 2 Minuten", 10, 60, 10);
                    }

                    sendGameInfo(mode);
                }

                if (time == 60) {
                    Bukkit.broadcast(Component.text("§7Spiel §e" + displayName  + " §7startet in §a1 Minute!"));
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.0f);
                    }

                }

                if (time == 10) {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.sendTitle("§cTeleport", "§e10 Sekunden", 5, 40, 5);
                    }
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.3f);
                    }
                }

                if (time <= 5 && time > 0) {
                    Bukkit.broadcast(Component.text("§7Teleport in §e" + time + " Sekunden!"));
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.5f);
                    }
                }

                if (time <= 0) {

                    cancel();

                    // 🔥 JumpAndRun braucht keine Worlds
                    if (currentMode instanceof JumpAndRunMode) {
                        prepareWorldsAndTeleport();
                        return;
                    }

                    // 🔥 Andere Modi warten auf Worlds
                    if (preparedWorlds.size() < teamManager.getTeams().size()) {

                        if (!warned) {
                            Bukkit.broadcast(Component.text("§cWarte auf Welten..."));
                            warned = true;
                        }

                        return;
                    }

                    prepareWorldsAndTeleport();
                    return;
                }

                time--;
            }

        }.runTaskTimer(TunierServer.getInstance(), 0L, 20L);
    }

    private void prepareWorldsAndTeleport() {

        if (currentMode instanceof JumpAndRunMode) {

            World world = Bukkit.getWorld("voidworld2");

            if (world == null) {
                Bukkit.broadcast(Component.text("§cJump&Run Welt nicht gefunden!"));
                return;
            }

            for (Player p : Bukkit.getOnlinePlayers()) {
                Location spawn = new Location(world, 0.5, JR_MIN_Y, 0.5, 0f, 0f);
                p.teleport(spawn);
            }

            startGameCountdown();
            return;
        }

        for (TeamData team : teamManager.getTeams().values()) {

            World world = preparedWorlds.get(team.getName());

            if (world == null) {
                Bukkit.getLogger().warning("World nicht geladen: " + team.getName());
                continue;
            }

            Location spawn = world.getSpawnLocation();

            // WARTEN BIS CHUNK SAFE GELADEN IST
            int chunkX = spawn.getBlockX() >> 4;
            int chunkZ = spawn.getBlockZ() >> 4;

            for (int x = -5; x <= 5; x++) {
                for (int z = -5; z <= 5; z++) {
                    world.getChunkAt(chunkX + x, chunkZ + z).load(true);
                }
            }

            worldManager.teleportTeam(team, world);
            team.setSpawnLocation(spawn);
        }

        Bukkit.getScheduler().runTaskLater(
                TunierServer.getInstance(),
                this::startGameCountdown,
                30L
        );
    }

    private void startGameCountdown() {

        if (countdownRunning) return;
        countdownRunning = true;

        if (currentMode instanceof JumpAndRunMode jnrMode) {

            // Give items + enable ghost system so players can use the toggle during countdown
            final List<Player> participants = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getWorld().getName().equals("voidworld2")) participants.add(p);
            }
            jnrMode.preGame(participants);

            // Identical to the normal countdown — but NO freezeAll() so players can move in the start radius
            new BukkitRunnable() {

                int time = 15;

                @Override
                public void run() {

                    for (Player p : Bukkit.getOnlinePlayers()) {

                        p.sendTitle("§2" + time, "§8Spiel startet...", 0, 20, 0);

                        if (time <= 10 && time > 0) {
                            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                        }

                        if (time == 0) {
                            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                        }
                    }

                    if (time <= 0) {
                        gameActive = true;
                        currentMode.start();
                        countdownRunning = false;
                        cancel();
                        return;
                    }

                    time--;
                }

            }.runTaskTimer(TunierServer.getInstance(), 0L, 20L);

            return;
        }

        // 🔽 NORMALER COUNTDOWN (dein alter bleibt)
        new BukkitRunnable() {

            int time = 15;

            @Override
            public void run() {

                if (time == 15) {
                    freezeAll();
                }

                for (Player p : Bukkit.getOnlinePlayers()) {

                    p.sendTitle("§2" + time, "§8Spiel startet...", 0, 20, 0);

                    if (time <= 10 && time > 0) {
                        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                    }

                    if (time == 0) {
                        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                    }
                }

                if (time <= 0) {
                    unfreezeAll();
                    gameActive = true;
                    currentMode.start();
                    countdownRunning = false;
                    cancel();
                    return;
                }

                time--;
            }

        }.runTaskTimer(TunierServer.getInstance(), 0L, 20L);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {

        if (getCurrentMode() == null) return;

        Player p = e.getPlayer();

        TeamData team = teamManager.getTeamByPlayer(p.getUniqueId());

        if (team != null && team.getSpawnLocation() != null) {
            e.setRespawnLocation(team.getSpawnLocation());
        }
    }

    private void prepareWorldsAsync() {

        if (currentMode instanceof JumpAndRunMode) return;

        preparedWorlds.clear();

        List<TeamData> teams = new ArrayList<>(teamManager.getTeams().values());

        new BukkitRunnable() {
            int index = 0;

            @Override
            public void run() {
                if (index >= teams.size()) {
                    cancel();
                    return;
                }

                TeamData team = teams.get(index);
                index++;

                // Falls createTeamWorld Bukkit/World APIs nutzt, MUSS das synchron laufen.
                World world = worldManager.createTeamWorld(team.getName(), currentSeed);

                if (world == null) {
                    Bukkit.getLogger().warning("World konnte nicht erstellt werden: " + team.getName());
                    return;
                }

                world.setAutoSave(false);


                preparedWorlds.put(team.getName(), world);

                Location spawn = world.getSpawnLocation();
                for (int x = -5; x <= 5; x++) {
                    for (int z = -5; z <= 5; z++) {
                        world.getChunkAt(
                                (spawn.getBlockX() >> 4) + x,
                                (spawn.getBlockZ() >> 4) + z
                        ).load();
                    }
                }
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
                Bukkit.broadcast(Component.text("§7➤ Alle Teams haben die gleiche Reihenfolge"));
                Bukkit.broadcast(Component.text("§7➤ Eigene Welten pro Team, aber gleicher Seed"));
                Bukkit.broadcast(Component.text("§7➤ §c3x Skip§7 mit §e/skip"));
                Bukkit.broadcast(Component.text("§8┃ §e/help §7für Achievement Infos"));
                Bukkit.broadcast(Component.text("§8┃ §e/bp §7für Team-Backpack"));
            }

            case "pvp" -> {
                Bukkit.broadcast(Component.text("§6§lPvP"));
                Bukkit.broadcast(Component.text("§7Besiegt Gegner für Punkte."));
            }

            case "jumpandrun" -> {
                Bukkit.broadcast(Component.text("§6§lJump and Run"));
                Bukkit.broadcast(Component.text("§7Schafft Parkour Maps so schnell wie möglich."));
            }

            case "itemcollector" -> {
                Bukkit.broadcast(Component.text("§6§lItem Collector"));
                Bukkit.broadcast(Component.text("§7Sammelt Items für Punkte."));
            }
        }
    }

    public boolean isGameActive() {
        return gameActive;
    }

    private void fillInventory(Player p, Material mat) {
        ItemStack item = new ItemStack(mat);

        for (int i = 0; i < 36; i++) {
            p.getInventory().setItem(i, item);
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();

        if (isFrozen(p) && !(currentMode instanceof JumpAndRunMode)) {
            Location from = e.getFrom();
            Location to = e.getTo();
            if (to == null) return;

            e.setTo(new Location(
                    from.getWorld(),
                    from.getX(), from.getY(), from.getZ(),
                    to.getYaw(), to.getPitch()
            ));
            return;
        }

        if (!(currentMode instanceof JumpAndRunMode)) return;
        if (gameActive) return;
        if (!p.getWorld().getName().equals("voidworld2")) return;

        Location to = e.getTo();
        if (to == null) return;

        int bx = to.getBlockX();
        int by = to.getBlockY();
        int bz = to.getBlockZ();

        boolean outside = bx < JR_MIN_X || bx > JR_MAX_X
                || by < JR_MIN_Y || by > JR_MAX_Y
                || bz < JR_MIN_Z || bz > JR_MAX_Z;

        if (outside) {
            Location from = e.getFrom();
            e.setTo(new Location(
                    from.getWorld(),
                    from.getX(), from.getY(), from.getZ(),
                    to.getYaw(), to.getPitch()
            ));
        }
    }
}