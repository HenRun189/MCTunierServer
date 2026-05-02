package me.HenRun189.tunierServer.game.modes;

import me.HenRun189.tunierServer.jumpandrun.JnRVisibilityManager;
import me.HenRun189.tunierServer.jumpandrun.PlayerData;
import me.HenRun189.tunierServer.listeners.VisibilityToggleItem;
import me.HenRun189.tunierServer.score.ScoreManager;
import me.HenRun189.tunierServer.team.*;
import me.HenRun189.tunierServer.TunierServer;
import me.HenRun189.tunierServer.game.GameManager;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerJoinEvent; // FIX: Rejoin

import net.kyori.adventure.text.Component;

import org.bukkit.boss.BossBar;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;

import java.util.*;

import static java.lang.Math.abs;

public class JumpAndRunMode extends AbstractGameMode implements Listener {

    private static final int TOTAL_CHECKPOINTS = 40;

    private final TeamManager teamManager;
    private final ScoreManager scoreManager;
    private final JnRVisibilityManager visibilityManager;

    private final Map<UUID, PlayerData> data = new HashMap<>();

    private final String WORLD_NAME = "voidworld2";

    private final double MIN_X = -2;
    private final double MAX_X = 3;
    private final double MIN_Z = -1;
    private final double MAX_Z = 2;
    private final double MIN_Y = 63;
    private final double MAX_Y = 68;

    private boolean gameStarted = false;

    private final GameManager gameManager;

    private final Map<UUID, Long> lastUse = new HashMap<>();
    private final Map<UUID, BukkitRunnable> cooldownTasks = new HashMap<>();
    private final HashMap<UUID, Integer> rankings = new HashMap<>();
    private final Map<UUID, Long> finishTimes = new HashMap<>();


    private int activePlayerAmount = 0;
    private boolean registered = false;
    private boolean timeExpired = false;
    private BossBar timerBar;
    private long gameStartTime = 0;
    private boolean stopping = false;

    // Zum Testen 2 Min, normal: 20 * 60 * 1000L
    private static final long GAME_DURATION_MS = 2 * 60 * 1000L;


    // ─────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────

    private String formatTime(long ms) {
        long seconds = ms / 1000;
        long millis  = (ms % 1000) / 10;
        return seconds + "." + String.format("%02d", millis) + "s";
    }

    /** Gibt Spieler das Blaze-Rod + Visibility-Toggle ins Inventar. */
    private void giveGameItems(Player p) {
        p.getInventory().clear();

        ItemStack blazeRod = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = blazeRod.getItemMeta();
        meta.setDisplayName("§cZum Checkpoint teleportieren");
        blazeRod.setItemMeta(meta);
        p.getInventory().setItem(0, blazeRod);

        p.getInventory().setItem(
                JnRVisibilityManager.TOGGLE_SLOT,
                VisibilityToggleItem.create(JnRVisibilityManager.Mode.GHOST)
        );
    }

    // ─────────────────────────────────────────────
    // Constructor
    // ─────────────────────────────────────────────

    public JumpAndRunMode(TeamManager teamManager, ScoreManager scoreManager, GameManager gameManager) {
        super(1200, teamManager);
        this.teamManager       = teamManager;
        this.scoreManager      = scoreManager;
        this.visibilityManager = new JnRVisibilityManager(teamManager);
        this.gameManager       = gameManager;
    }

    // ─────────────────────────────────────────────
    // PRE-GAME
    // ─────────────────────────────────────────────

    public void preGame(Collection<Player> players) {
        for (Player p : players) {
            data.put(p.getUniqueId(), new PlayerData(p.getUniqueId()));
            giveGameItems(p);
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
        }

        visibilityManager.enable(players);

        if (!registered) {
            Bukkit.getPluginManager().registerEvents(this, TunierServer.getInstance());
            registered = true;
        }
    }

    // ─────────────────────────────────────────────
    // START
    // ─────────────────────────────────────────────

    @Override
    public void start() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }

        data.clear();
        gameStarted = false;
        lastUse.clear();
        cooldownTasks.clear();
        activePlayerAmount = 0;

        for (TeamData team : teamManager.getTeams().values()) {
            for (UUID uuid : team.getPlayers()) {
                Player p = Bukkit.getPlayer(uuid);
                if (p == null) continue;
                data.put(p.getUniqueId(), new PlayerData(p.getUniqueId()));
                activePlayerAmount++;
                giveGameItems(p);
            }
        }

        if (!registered) {
            Bukkit.getPluginManager().registerEvents(this, TunierServer.getInstance());
            registered = true;
        }

        super.start();
    }

    private Location startLocation;

    @Override
    protected void updateActionbar() {
        // absichtlich leer – JnR hat eigene ActionBar im onGameTick()
    }

    @Override
    protected void onGameStart() {
        World world = Bukkit.getWorld(WORLD_NAME);
        if (world == null) return;

        startLocation = new Location(world, 0.5, 65, 0.5, 0f, 0f);
        world.getChunkAt(startLocation).load(true);

        timerBar = Bukkit.createBossBar("§6Jump & Run §8| §e20:00", BarColor.GREEN, BarStyle.SOLID);

        Bukkit.getScheduler().runTaskLater(TunierServer.getInstance(), () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!p.getWorld().equals(world)) continue;
                PlayerData pd = data.get(p.getUniqueId());
                if (pd != null) pd.startTimer();
                timerBar.addPlayer(p);
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
            }
            gameStarted = true;
            gameStartTime = System.currentTimeMillis();
        }, 1L);
    }

    // ─────────────────────────────────────────────
    // GAME TICK
    // ─────────────────────────────────────────────

    @Override
    protected void onGameTick() {

        // ── Ende-Bedingung (nur einmal auslösen) ────────────────────────────
        // Bedingungen:
        //   • alle aktiven Spieler haben die Runde beendet  ODER
        //   • Zeit abgelaufen UND mindestens 1 Spieler im Ziel
        boolean allFinished = activePlayerAmount > 0 && rankings.size() >= activePlayerAmount;
        boolean timeAndOne  = timeExpired && rankings.size() >= 1;
// timeExpired wird erst true wenn 20min um sind → passt bereits

        if (!stopping && (allFinished || timeAndOne)) {
            stopping = true;
            showEndRanking();
            Bukkit.getScheduler().runTaskLater(TunierServer.getInstance(),
                    () -> gameManager.stopGame(), 100L);
            return;
        }

        // ── BossBar-Timer ────────────────────────────────────────────────────
        long elapsed   = gameStartTime > 0 ? System.currentTimeMillis() - gameStartTime : 0;
        long totalMs = GAME_DURATION_MS;
        long remaining = Math.max(0, totalMs - elapsed);

        // Zeit-abgelaufen Check
        if (gameStartTime > 0 && !timeExpired && elapsed >= GAME_DURATION_MS) {
            timeExpired = true;
            Bukkit.broadcastMessage("§cZeit abgelaufen! Erster der ins Ziel kommt, beendet das Spiel!");
        }

        if (timerBar != null) {
            if (timeExpired && rankings.isEmpty()) {
                timerBar.setTitle("§cZeit um §8| §eWarte auf ersten im Ziel...");
                timerBar.setProgress(0.0);
                timerBar.setColor(BarColor.RED);
            } else {
                long secs = remaining / 1000;
                long mins = secs / 60;
                long s    = secs % 60;
                String color = remaining < 60_000 ? "§c" : remaining < 5 * 60_000 ? "§e" : "§a";
                timerBar.setTitle("§6Jump & Run §8| " + color + String.format("%02d:%02d", mins, s));
                timerBar.setProgress(Math.max(0.0, Math.min(1.0, (double) remaining / totalMs)));
                if      (remaining < 60_000)     timerBar.setColor(BarColor.RED);
                else if (remaining < 5 * 60_000) timerBar.setColor(BarColor.YELLOW);
                else                             timerBar.setColor(BarColor.GREEN);
            }
        }

        // ── Spieler-Loop ─────────────────────────────────────────────────────
        for (Player p : Bukkit.getOnlinePlayers()) {
            World world = Bukkit.getWorld(WORLD_NAME);
            if (!p.getWorld().equals(world)) continue;

            // Kein Team = Caster → nur einmal als Spectator registrieren
            if (teamManager.getTeamByPlayer(p.getUniqueId()) == null) {
                if (visibilityManager.isActive() && !visibilityManager.isKnownSpectator(p)) {
                    visibilityManager.registerExternalSpectator(p);
                }
                continue; // kein PlayerData für Caster
            }

            PlayerData pd = data.get(p.getUniqueId());
            if (pd == null) continue;

            // Void-Reset
            if (p.getLocation().getY() < 55 && p.getGameMode() != GameMode.SPECTATOR) {
                resetPlayer(p, pd);
                p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
            }

            // ActionBar: NUR Platz, Checkpoint und Falls – KEINE Zeit
            int place = getPlayerPlace(p);
            p.sendActionBar(Component.text(
                    "§7Platz §e#" + place + "§7/§e" + activePlayerAmount +
                            " §8| §7CP §e" + pd.getCheckpoint() + "§7/§e" + TOTAL_CHECKPOINTS +
                            " §8| §cFalls: " + pd.getFalls()
            ));

            p.setFoodLevel(20);
            p.setSaturation(20);
        }
    }

    // ─────────────────────────────────────────────
    // RESET PLAYER
    // ─────────────────────────────────────────────

    private void resetPlayer(Player p, PlayerData pd) {
        p.setInvulnerable(true);
        Bukkit.getScheduler().runTaskLater(TunierServer.getInstance(),
                () -> p.setInvulnerable(false), 10L);

        World world = Bukkit.getWorld(WORLD_NAME);
        if (world == null) return;

        Location target = pd.getLastCheckpoint() != null
                ? pd.getLastCheckpoint()
                : (startLocation != null ? startLocation.clone() : new Location(world, 0.5, 65, 0.5));

        p.setVelocity(new Vector(0, 0, 0));
        p.teleport(target);
        pd.addFall();
    }

    // ─────────────────────────────────────────────
    // EVENTS
    // ─────────────────────────────────────────────

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (e.getTo() == null) return;
        if (!e.hasChangedBlock()) return;

        Player p = e.getPlayer();
        if (!p.getWorld().getName().equals(WORLD_NAME)) return;

        PlayerData pd = data.get(p.getUniqueId());
        if (pd == null) return;

        // ── Pre-Game Wand ────────────────────────────────────────────────────
        if (!gameStarted) {
            Location to = e.getTo();
            if (to.getX() < MIN_X || to.getX() > MAX_X
                    || to.getZ() < MIN_Z || to.getZ() > MAX_Z
                    || to.getY() < MIN_Y || to.getY() > MAX_Y) {
                e.setTo(e.getFrom());
            }
            return;
        }

        if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) return;

        Location to   = e.getTo();
        Location from = e.getFrom();

        Block fromBelow = from.clone().subtract(0, 1, 0).getBlock();
        Block toBelow   = to.clone().subtract(0, 1, 0).getBlock();

        // ── Ziel ─────────────────────────────────────────────────────────────
        if (toBelow.getType() == Material.DIAMOND_BLOCK && !rankings.containsKey(p.getUniqueId())) {
            finished(p);
            return;
        }

        // ── Checkpoint ───────────────────────────────────────────────────────
        if (toBelow.getType() != Material.GOLD_BLOCK) return;
        if (fromBelow.getType() == Material.GOLD_BLOCK) return;

        Location cpLoc    = toBelow.getLocation();
        Location checkLoc = cpLoc.clone().add(0.5, 1, 0.5);

        // Anti-Farm: schon in der Liste?
        for (Location past : pd.getCheckpoints()) {
            if (past.getWorld() != null && checkLoc.getWorld() != null
                    && past.getWorld().equals(checkLoc.getWorld())
                    && past.distanceSquared(checkLoc) < 100) {
                return;
            }
        }

        pd.getCheckpoints().add(cpLoc);

        int next = pd.getCheckpoint() + 1;
        Location cpTeleport = cpLoc.clone().add(0.5, 1, 0.5);

        pd.setCheckpoint(next);
        pd.setLastCheckpoint(cpTeleport);

        TeamData team = teamManager.getTeamByPlayer(p.getUniqueId());
        if (team != null) scoreManager.addPoints(team.getName(), 5);

        p.spawnParticle(Particle.HAPPY_VILLAGER, cpTeleport, 30, 0.3, 0.3, 0.3, 0);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
        p.sendActionBar(Component.text("§aCheckpoint §e#" + next));
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Action action = e.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        Player p = e.getPlayer();

        ItemStack item = e.getItem();
        if (item == null || item.getType() != Material.BLAZE_ROD || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !"§cZum Checkpoint teleportieren".equals(meta.getDisplayName())) return;

        if (!gameStarted) {
            p.sendActionBar(Component.text("§cSpiel startet gleich..."));
            return;
        }

        long now = System.currentTimeMillis();
        if (lastUse.containsKey(p.getUniqueId()) && now - lastUse.get(p.getUniqueId()) < 200) return;
        lastUse.put(p.getUniqueId(), now);

        PlayerData pd = data.get(p.getUniqueId());
        if (pd == null) return;

        World world = Bukkit.getWorld(WORLD_NAME);
        if (world == null) return;

        Location target = pd.getLastCheckpoint() != null
                ? pd.getLastCheckpoint()
                : new Location(world, 0.5, 65, 0.5);

        p.teleport(target);
        pd.addFall();
        p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
        startCooldownBar(p);
    }

    // ─────────────────────────────────────────────
    // FIX: REJOIN
    // ─────────────────────────────────────────────

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();

        World world = Bukkit.getWorld(WORLD_NAME);
        if (world == null) return;

        TeamData team = teamManager.getTeamByPlayer(p.getUniqueId());
        if (team == null) return;

        if (rankings.containsKey(p.getUniqueId())) {
            Bukkit.getScheduler().runTaskLater(TunierServer.getInstance(), () -> {
                p.teleport(new Location(world, 0.5, 65, 0.5));
                p.setGameMode(GameMode.SPECTATOR);
            }, 10L);
            return;
        }

        boolean isActive = gameStarted || data.containsKey(p.getUniqueId());
        if (!isActive) return;

        if (!data.containsKey(p.getUniqueId())) {
            PlayerData fresh = new PlayerData(p.getUniqueId());
            if (gameStarted) fresh.startTimer();
            data.put(p.getUniqueId(), fresh);
            activePlayerAmount++;
        } else if (data.get(p.getUniqueId()).isDisconnected()) {
            data.get(p.getUniqueId()).setDisconnected(false);
            activePlayerAmount++;
        }

        giveGameItems(p);
        if (timerBar != null) timerBar.addPlayer(p);

        PlayerData current = data.get(p.getUniqueId());
        Location target = current.getLastCheckpoint() != null
                ? current.getLastCheckpoint()
                : (startLocation != null ? startLocation.clone() : new Location(world, 0.5, 65, 0.5));

        Bukkit.getScheduler().runTaskLater(TunierServer.getInstance(), () -> {
            p.teleport(target);
            p.setGameMode(gameStarted ? GameMode.SURVIVAL : GameMode.ADVENTURE);
            visibilityManager.onPlayerRejoin(p);
        }, 40L);
    }

    // ─────────────────────────────────────────────
    // FINISHED
    // ─────────────────────────────────────────────

    protected void finished(Player p) {
        int place = rankings.size() + 1;
        rankings.put(p.getUniqueId(), place);

        PlayerData pd = data.get(p.getUniqueId());
        if (pd != null) pd.finishTimer();
        long time = pd != null ? pd.getTime() : 0;
        finishTimes.put(p.getUniqueId(), time);

        data.remove(p.getUniqueId());

        p.setGameMode(GameMode.SPECTATOR);
        visibilityManager.setSpectatorMode(p);

        for (Player other : Bukkit.getOnlinePlayers()) {
            p.showPlayer(TunierServer.getInstance(), other);
        }

        String timeStr = formatTime(time);

        p.sendTitle(
                "§a✔ Ziel erreicht",
                "§7Platz §e#" + place + " §8| §a" + timeStr,
                10, 50, 10
        );
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);

        TeamData team = teamManager.getTeamByPlayer(p.getUniqueId());
        if (team != null) {
            switch (place) {
                case 1 -> {
                    scoreManager.addPoints(team.getName(), 40);
                    // FIX: Zeit im Chat anzeigen
                    Bukkit.broadcastMessage("§6🏆 §e" + p.getName() + " §7hat das Ziel als §c§l1. §7erreicht! §8| §a" + timeStr);
                }
                case 2 -> {
                    scoreManager.addPoints(team.getName(), 25);
                    Bukkit.broadcastMessage("§e" + p.getName() + " §7hat das Ziel als §c§l2. §7erreicht! §8| §a" + timeStr);
                }
                case 3 -> {
                    scoreManager.addPoints(team.getName(), 15);
                    Bukkit.broadcastMessage("§e" + p.getName() + " §7hat das Ziel als §c§l3. §7erreicht! §8| §a" + timeStr);
                }
                default -> {
                    scoreManager.addPoints(team.getName(), 10);
                    Bukkit.broadcastMessage("§b" + p.getName() + " §7hat das Ziel erreicht! §8| §ePlatz: §c#" + place + " §8| §a" + timeStr);
                }
            }
        }
    }

    // ─────────────────────────────────────────────
    // HELPER: Platz berechnen
    // ─────────────────────────────────────────────

    private int getPlayerPlace(Player player) {
        List<PlayerData> list = new ArrayList<>(data.values());
        list.sort((a, b) -> {
            int cmp = Integer.compare(b.getCheckpoint(), a.getCheckpoint());
            if (cmp != 0) return cmp;
            return Integer.compare(a.getFalls(), b.getFalls());
        });

        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getUuid().equals(player.getUniqueId())) return i + 1;
        }
        return list.size() + 1;
    }

    // ─────────────────────────────────────────────
    // ABSTRACT: Ranking / Points
    // ─────────────────────────────────────────────

    @Override
    protected List<TeamData> getRanking() {
        List<TeamData> ranking = new ArrayList<>(teamManager.getTeams().values());
        ranking.sort((a, b) -> Integer.compare(
                scoreManager.getPoints(b.getName()),
                scoreManager.getPoints(a.getName())
        ));
        return ranking;
    }

    @Override
    protected int getPoints(String teamName) {
        TeamData team = teamManager.getTeam(teamName);
        if (team == null) return 0;
        int sum = 0;
        for (UUID uuid : team.getPlayers()) {
            PlayerData pd = data.get(uuid);
            if (pd != null) sum += pd.getCheckpoint() * 5;
        }
        return sum;
    }

    @Override
    public void handleEvent(Event event) {}

    // ─────────────────────────────────────────────
    // STOP
    // ─────────────────────────────────────────────

    @Override
    public void stop() {
        stopping = false;
        finishTimes.clear();

        // Task canceln OHNE super.stop() – kein doppeltes Ranking
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }

        if (timerBar != null) {
            timerBar.removeAll();
            timerBar = null;
        }

        gameStartTime = 0;

        for (BukkitRunnable task : cooldownTasks.values()) {
            if (task != null) task.cancel();
        }

        timeExpired = false;
        cooldownTasks.clear();
        lastUse.clear();

        for (UUID uuid : data.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.setExp(0f);
                player.setLevel(0);
            }
        }

        visibilityManager.disable();
        // super.stop() NICHT aufrufen!

        HandlerList.unregisterAll(this);
        registered = false;

        data.clear();
        rankings.clear();
        activePlayerAmount = 0;
        gameStarted = false;
    }

    // ─────────────────────────────────────────────
    // INVENTORY PROTECTION
    // ─────────────────────────────────────────────

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (data.containsKey(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p && data.containsKey(p.getUniqueId()))
            e.setCancelled(true);
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (data.containsKey(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }

    // ─────────────────────────────────────────────
    // ADVANCEMENTS suppression
    // ─────────────────────────────────────────────

    @EventHandler
    public void onAdvancement(org.bukkit.event.player.PlayerAdvancementDoneEvent e) {
        if (!data.containsKey(e.getPlayer().getUniqueId())) return;
        e.message(Component.empty());
        var adv      = e.getAdvancement();
        var progress = e.getPlayer().getAdvancementProgress(adv);
        for (String crit : new HashSet<>(progress.getAwardedCriteria())) {
            progress.revokeCriteria(crit);
        }
    }

    // ─────────────────────────────────────────────
    // BLOCK PROTECTION
    // ─────────────────────────────────────────────

    @EventHandler
    public void onBreak(org.bukkit.event.block.BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (!data.containsKey(p.getUniqueId())) return;
        if (p.getGameMode() == GameMode.CREATIVE) return;
        Material type = e.getBlock().getType();
        if (type.name().contains("TRAPDOOR") || type == Material.BOOKSHELF) e.setCancelled(true);
    }

    @EventHandler
    public void onInteractBlock(PlayerInteractEvent e) {
        if (e.getClickedBlock() == null) return;
        Player p = e.getPlayer();
        if (!data.containsKey(p.getUniqueId())) return;
        if (p.getGameMode() == GameMode.CREATIVE) return;
        if (Tag.TRAPDOORS.isTagged(e.getClickedBlock().getType())) e.setCancelled(true);
    }

    // ─────────────────────────────────────────────
    // QUIT
    // ─────────────────────────────────────────────

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID uuid = e.getPlayer().getUniqueId();

        if (data.containsKey(uuid) && !rankings.containsKey(uuid)) {
            activePlayerAmount--;
            data.get(uuid).setDisconnected(true); // NEU
        }

        cooldownTasks.computeIfPresent(uuid, (u, task) -> { task.cancel(); return null; });
        cooldownTasks.remove(uuid);
    }

    // ─────────────────────────────────────────────
    // COOLDOWN BAR
    // ─────────────────────────────────────────────

    private void startCooldownBar(Player p) {
        if (cooldownTasks.containsKey(p.getUniqueId())) {
            cooldownTasks.get(p.getUniqueId()).cancel();
        }

        BukkitRunnable task = new BukkitRunnable() {
            int ticks = 4;

            @Override
            public void run() {
                ticks--;
                p.setExp(Math.max(0f, ticks / 4f));
                p.setLevel(0);
                if (ticks <= 0) {
                    p.setExp(0f);
                    p.setLevel(0);
                    cancel();
                    cooldownTasks.remove(p.getUniqueId());
                }
            }
        };

        cooldownTasks.put(p.getUniqueId(), task);
        task.runTaskTimer(TunierServer.getInstance(), 0L, 1L);
    }

    // ─────────────────────────────────────────────
    // END RANKING
    // ─────────────────────────────────────────────


    private void showEndRanking() {
        Bukkit.broadcast(Component.text("§8§m================================"));
        Bukkit.broadcast(Component.text("§6§l       Jump & Run Ergebnisse"));
        Bukkit.broadcast(Component.text("§8§m================================"));

        if (!rankings.isEmpty()) {
            Bukkit.broadcast(Component.text("§e§l🏁 Ziel erreicht:"));

            List<Map.Entry<UUID, Integer>> sorted = new ArrayList<>(rankings.entrySet());
            sorted.sort(Map.Entry.comparingByValue());

            for (Map.Entry<UUID, Integer> entry : sorted) {
                Player p        = Bukkit.getPlayer(entry.getKey());
                String name     = p != null ? p.getName() : "Unbekannt";
                TeamData team   = teamManager.getTeamByPlayer(entry.getKey());
                String teamStr  = team != null ? " §8[" + team.getColor() + team.getName() + "§8]" : "";
                long t          = finishTimes.getOrDefault(entry.getKey(), 0L);
                String timeStr  = t > 0 ? " §8| §a" + formatTime(t) : "";

                String medal = switch (entry.getValue()) {
                    case 1  -> "§6🥇";
                    case 2  -> "§7🥈";
                    case 3  -> "§c🥉";
                    default -> "§7#" + entry.getValue();
                };

                Bukkit.broadcast(Component.text(medal + " §f" + name + teamStr + timeStr));
            }
            Bukkit.broadcast(Component.text(" "));
        }

        // Nicht ins Ziel – sortiert nach Checkpoints
        List<Map.Entry<UUID, PlayerData>> notFinished = new ArrayList<>();
        for (Map.Entry<UUID, PlayerData> entry : data.entrySet()) {
            if (!rankings.containsKey(entry.getKey())) notFinished.add(entry);
        }

        if (!notFinished.isEmpty()) {
            notFinished.sort((a, b) -> Integer.compare(
                    b.getValue().getCheckpoint(), a.getValue().getCheckpoint()));

            Bukkit.broadcast(Component.text("§c§l📍 Nicht ins Ziel:"));
            for (Map.Entry<UUID, PlayerData> entry : notFinished) {
                Player p       = Bukkit.getPlayer(entry.getKey());
                String name    = p != null ? p.getName() : "Unbekannt";
                TeamData team  = teamManager.getTeamByPlayer(entry.getKey());
                String teamStr = team != null ? " §8[" + team.getColor() + team.getName() + "§8]" : "";

                Bukkit.broadcast(Component.text(
                        "§7➤ §f" + name + teamStr + " §8- §eCP §a" + entry.getValue().getCheckpoint()
                ));
            }
            Bukkit.broadcast(Component.text(" "));
        }

        // Team Ranking
        Bukkit.broadcast(Component.text("§b§l🏆 Team Ranking:"));
        List<TeamData> teams = new ArrayList<>(teamManager.getTeams().values());
        teams.sort((a, b) -> Integer.compare(
                scoreManager.getPoints(b.getName()), scoreManager.getPoints(a.getName())));

        int rank = 1;
        for (TeamData team : teams) {
            int points = scoreManager.getPoints(team.getName());

            // Bester Spieler im Team
            UUID bestUuid  = null;
            int  bestScore = -1;
            String bestExtra = "";

            for (UUID uuid : team.getPlayers()) {
                if (rankings.containsKey(uuid)) {
                    int score = 10000 - rankings.get(uuid);
                    if (score > bestScore) {
                        bestScore = score;
                        bestUuid  = uuid;
                        long t    = finishTimes.getOrDefault(uuid, 0L);
                        bestExtra = " §8(Ziel §8| §a" + formatTime(t) + "§8)";
                    }
                } else {
                    PlayerData pd = data.get(uuid);
                    int score = pd != null ? pd.getCheckpoint() : 0;
                    if (score > bestScore) {
                        bestScore = score;
                        bestUuid  = uuid;
                        bestExtra = " §8(CP §e" + score + "§8)";
                    }
                }
            }

            String mvp = "";
            if (bestUuid != null) {
                Player mvpP = Bukkit.getPlayer(bestUuid);
                mvp = " §8| §aMVP: §f" + (mvpP != null ? mvpP.getName() : "?") + bestExtra;
            }

            String prefix = rank == 1 ? "§6§l" : rank == 2 ? "§7§l" : "§c§l";
            Bukkit.broadcast(Component.text(
                    prefix + "#" + rank + " §f" + team.getName() + " §8- §e" + points + " Punkte" + mvp
            ));
            rank++;
        }

        Bukkit.broadcast(Component.text("§8§m================================"));
    }


    @EventHandler
    public void onDamage(org.bukkit.event.entity.EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (!data.containsKey(p.getUniqueId())) return;
        e.setCancelled(true);
    }

    public boolean isGameStarted() {
        return gameStarted;
    }

    @EventHandler
    public void onCommand(org.bukkit.event.player.PlayerCommandPreprocessEvent e) {
        if (!data.containsKey(e.getPlayer().getUniqueId())) return;
        String cmd = e.getMessage().toLowerCase();
        if (cmd.startsWith("/bp") || cmd.startsWith("/backpack")) {
            e.setCancelled(true);
            e.getPlayer().sendActionBar(Component.text("§cIm Jump & Run nicht verfügbar!"));
        }
    }
}