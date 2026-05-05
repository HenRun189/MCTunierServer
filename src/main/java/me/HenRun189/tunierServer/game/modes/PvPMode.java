package me.HenRun189.tunierServer.game.modes;

import me.HenRun189.tunierServer.TunierServer;
import me.HenRun189.tunierServer.score.ScoreManager;
import me.HenRun189.tunierServer.team.TeamData;
import me.HenRun189.tunierServer.team.TeamManager;

import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.*;

public class PvPMode extends AbstractGameMode implements Listener {

    private final ScoreManager scoreManager;
    private final Set<UUID> alivePlayers = new HashSet<>();

    // ── Kill/Death-Tracking ────────────────────────────────────────
    private final Map<UUID, Integer> killCount  = new HashMap<>();
    private final Map<UUID, Integer> deathCount = new HashMap<>();

    // ── Team Kill-Tracking (für Scoreboard) ───────────────────────
    private final Map<String, Integer> teamKillCount = new HashMap<>();

    // ── Spawn-Koordinaten (x, y, z) ────────────────────────────────
    private static final int[][] SPAWN_COORDS = {
            {38, 67, 32},
            {32, 67, 42},
            {22, 67, 48},
            {10, 67, 48},
            {0,  67, 42},
            {-5, 67, 32},
            {-5, 67, 20},
            {0,  67,  10},
            {10, 67,  4},
            {22, 67,  4},
            {32, 67, 10},
            {38, 67, 20}
    };

    // ── Welt & Border ──────────────────────────────────────────────
    private World world;
    private WorldBorder border;

    // Border-Mitte: 16, 70, 26
    private static final double BORDER_CENTER_X = 16;
    private static final double BORDER_CENTER_Z = 26;

    // Border-Phasen (in Sekunden seit Spielstart)
    // Phase 1: Bei 300s (5min) → schrumpft über 600s auf Radius 75 (fertig bei 900s = 15min)
    // Phase 2: Bei 1080s (18min) → schrumpft über 120s auf Radius 4 (fertig bei 1200s = 20min)
    private static final int BORDER_PHASE1_START_SEC = 300;  // 5 min
    private static final int BORDER_PHASE2_START_SEC = 1080; // 18 min

    private boolean phase1Started = false;
    private boolean phase2Started = false;

    // ── Einstellungen ──────────────────────────────────────────────
    /** false = kein Friendly Fire (Standard); true = Teamkollegen können sich töten */
    private boolean friendlyFire  = false;
    /** true = Win-Condition aus (Solo-Test); false = normal */
    private boolean soloTestMode  = false;

    // ── BossBar ────────────────────────────────────────────────────
    private BossBar timerBar;
    private boolean registered    = false;
    private int     survivalTick  = 0;
    private int     elapsedSeconds = 0; // eigener Hochzähl-Timer, unabhängig von AbstractGameMode
    private final Set<String> playerPlacedBlocks = new HashSet<>();
    private final Map<UUID, Location> spawnLocations = new HashMap<>();
    private final Map<UUID, Long> disconnectedAt = new HashMap<>();

    public PvPMode(TeamManager teamManager, ScoreManager scoreManager) {
        super(-1, teamManager); // -1 = kein Zeitlimit, Spiel endet nur durch Win-Condition
        this.scoreManager = scoreManager;
    }

    public void setSoloTestMode(boolean enabled) { this.soloTestMode = enabled; }
    public void setFriendlyFire(boolean enabled)  { this.friendlyFire  = enabled; }

    // ══════════════════════════════════════════════════════════════
    //  GAME START
    // ══════════════════════════════════════════════════════════════

    @Override
    protected void onGameStart() {
        world = Bukkit.getWorld("pvp_map");
        if (world == null) {
            world = Bukkit.getWorlds().get(0);
            TunierServer.getInstance().getLogger().warning("[PvPMode] Welt 'pvp_map' nicht gefunden! Nutze Fallback.");
        }

        scoreManager.clearPvpSuppliers();
        alivePlayers.clear();
        killCount.clear();
        deathCount.clear();
        teamKillCount.clear();
        playerPlacedBlocks.clear();
        spawnLocations.clear();
        disconnectedAt.clear();
        survivalTick   = 0;
        elapsedSeconds = 0;
        phase1Started  = false;
        phase2Started  = false;

        // Team-Kill-Counter initialisieren
        for (TeamData t : teamManager.getTeams().values()) {
            teamKillCount.put(t.getName(), 0);
        }

        // Border – Mitte ist 16, 70, 26
        border = world.getWorldBorder();
        border.setCenter(BORDER_CENTER_X, BORDER_CENTER_Z);
        border.setSize(460); // Radius 230 × 2

        // BossBar
        timerBar = Bukkit.createBossBar("§6§lHunger Games §8| §70:00", BarColor.GREEN, BarStyle.SOLID);

        // Spawn-Liste mischen → zufällige Reihenfolge, nie doppelt
        List<int[]> shuffledSpawns = new ArrayList<>(Arrays.asList(SPAWN_COORDS));
        Collections.shuffle(shuffledSpawns);

        List<Player> participants = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (teamManager.getTeamByPlayer(p.getUniqueId()) != null) participants.add(p);
        }

        for (int i = 0; i < participants.size(); i++) {
            Player p = participants.get(i);
            alivePlayers.add(p.getUniqueId());
            killCount.put(p.getUniqueId(), 0);
            deathCount.put(p.getUniqueId(), 0);


            preparePlayer(p);

            Location spawnLoc = new Location(world,
                    shuffledSpawns.get(i % shuffledSpawns.size())[0],
                    shuffledSpawns.get(i % shuffledSpawns.size())[1],
                    shuffledSpawns.get(i % shuffledSpawns.size())[2]);
            p.teleport(spawnLoc);
            p.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
            spawnLocations.put(p.getUniqueId(), spawnLoc);

            p.setInvulnerable(true);
            timerBar.addPlayer(p);
        }

        // PvP-Supplier für Scoreboard setzen
        scoreManager.setPvpSuppliers(
                teamName -> {
                    // lebende Spieler dieses Teams zählen
                    TeamData td = teamManager.getTeam(teamName);
                    if (td == null) return 0;
                    int count = 0;
                    for (UUID uuid : td.getPlayers()) {
                        if (!alivePlayers.contains(uuid)) continue;
                        Player pl = Bukkit.getPlayer(uuid);
                        if (pl != null && pl.isOnline() && !pl.isDead() && pl.getGameMode() != GameMode.SPECTATOR)
                            count++;
                    }
                    return count;
                },
                teamName -> teamKillCount.getOrDefault(teamName, 0)
        );

        if (!registered) {
            Bukkit.getPluginManager().registerEvents(this, TunierServer.getInstance());
            registered = true;
        }

        Bukkit.broadcast(Component.text("§8§m════════════════════════════════"));
        Bukkit.broadcast(Component.text("    §6§lHunger Games §7ist gestartet!"));
        Bukkit.broadcast(Component.text("    §7Border startet bei §eRadius 230§7."));
        if (soloTestMode)  Bukkit.broadcast(Component.text("    §c[Solo-Testmodus aktiv]"));
        if (friendlyFire)  Bukkit.broadcast(Component.text("    §c[Friendly Fire aktiviert]"));
        Bukkit.broadcast(Component.text("§8§m════════════════════════════════"));
    }

    // ══════════════════════════════════════════════════════════════
    //  GAME TICK
    // ══════════════════════════════════════════════════════════════

    @Override
    protected void onGameTick() {
        if (world == null) return;
    }

    @Override
    protected void onSecond() {
        if (world == null) return;

        elapsedSeconds++; // eigener Hochzähl-Timer

        updateTimerBar();

        survivalTick++;
        if (survivalTick >= 15) {
            survivalTick = 0;
            giveSurvivalPoints();
        }

        // Phase 1: 5min → Border schrumpft von 460 (R=230) auf 150 (R=75) über 600s (10min)
        if (!phase1Started && elapsedSeconds >= BORDER_PHASE1_START_SEC) {
            phase1Started = true;
            border.changeSize(150, 600L); // 600 Sekunden = 10 Minuten
            Bukkit.broadcast(Component.text("§c§lDie Border verkleinert sich! §7(→ Radius 75 in 10 min)"));
            for (Player p : Bukkit.getOnlinePlayers())
                p.playSound(p.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 1f, 0.5f);
        }

        // Phase 2: 18min → Border schrumpft von 150 (R=75) auf 8 (R=4) über 120s (2min)
        if (!phase2Started && elapsedSeconds >= BORDER_PHASE2_START_SEC) {
            phase2Started = true;
            border.changeSize(8, 120L); // 120 Sekunden = 2 Minuten
            Bukkit.broadcast(Component.text("§4§lACHTUNG! §cBorder → 4 Blöcke Radius! §7(fertig in 2 min)"));
            for (Player p : Bukkit.getOnlinePlayers())
                p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 0.8f);
        }

        if (elapsedSeconds == 20) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.setInvulnerable(false);
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
            }
            Bukkit.broadcast(Component.text("§c§lPvP ist jetzt aktiv!"));
        }

        // in onSecond(), neue if-Blöcke:
        if (elapsedSeconds == BORDER_PHASE1_START_SEC - 10) {
            for (Player p : Bukkit.getOnlinePlayers())
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 0.5f);
        }
        if (elapsedSeconds == BORDER_PHASE2_START_SEC - 10) {
            for (Player p : Bukkit.getOnlinePlayers())
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 0.5f);
        }

        /*if (elapsedSeconds < 20) {
            for (UUID uuid : alivePlayers) {
                Player p = Bukkit.getPlayer(uuid);
                Location loc = spawnLocations.get(uuid);
                if (p != null && loc != null) {
                    p.teleport(loc);
                    p.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
                }
            }
        }*/

        long now = System.currentTimeMillis();
        for (Iterator<Map.Entry<UUID, Long>> it = disconnectedAt.entrySet().iterator(); it.hasNext();) {
            Map.Entry<UUID, Long> entry = it.next();
            if (now - entry.getValue() >= 30_000L) {
                UUID uuid = entry.getKey();
                it.remove();
                alivePlayers.remove(uuid);
                String name = Bukkit.getOfflinePlayer(uuid).getName();
                if (name == null) name = "Unbekannt";
                Bukkit.broadcast(Component.text("§c☠ " + name + " §7wurde wegen Disconnect eliminiert!"));
                checkWinCondition();
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  ActionBar — überschreibt AbstractGameMode
    //  Zeigt: Status | eigene Kills | Team [lebend/gesamt] | Team-Kills
    // ══════════════════════════════════════════════════════════════

    @Override
    protected void updateActionbar() {
        int totalAlive = 0;
        int totalParticipants = killCount.size(); // Gesamtzahl aller Teilnehmer
        for (UUID uuid : alivePlayers) {
            Player pl = Bukkit.getPlayer(uuid);
            if (pl != null && pl.isOnline() && !pl.isDead() && pl.getGameMode() != GameMode.SPECTATOR)
                totalAlive++;
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            int myKills = killCount.getOrDefault(p.getUniqueId(), 0);
            boolean isAlive = alivePlayers.contains(p.getUniqueId())
                    && p.getGameMode() != GameMode.SPECTATOR;

            p.sendActionBar(Component.text(
                    //(isAlive ? "§a● Leben" : "§c● Zuschauer") +
                    " §8| §c⚔ " + myKills + " Kills" +
                            " §8| §e" + totalAlive + "§7/§e" + totalParticipants + " übrig"
            ));
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  STOP
    // ══════════════════════════════════════════════════════════════

    private boolean endScreenShown = false;

    @Override
    public void stop() {
        if (!endScreenShown) {
            endScreenShown = true;
            showEndScreen();
        }

        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }

        // Title anzeigen
        List<TeamData> ranking = getRanking();
        if (!ranking.isEmpty()) {
            int bestScore = getPoints(ranking.get(0).getName());
            List<String> winners = new ArrayList<>();
            for (TeamData t : ranking)
                if (getPoints(t.getName()) == bestScore) winners.add(t.getName());

            for (Player p : Bukkit.getOnlinePlayers()) {
                TeamData team = teamManager.getTeamByPlayer(p.getUniqueId());
                int place = -1;
                if (team != null) {
                    for (int i = 0; i < ranking.size(); i++) {
                        if (ranking.get(i).getName().equals(team.getName())) {
                            place = i + 1;
                            break;
                        }
                    }
                }
                p.showTitle(net.kyori.adventure.title.Title.title(
                        Component.text("§6Dein Platz: #" + (place == -1 ? "-" : place)),
                        Component.text(winners.size() == 1
                                ? "§7Gewinner: §e" + winners.get(0)
                                : "§7Unentschieden: §e" + String.join(", ", winners))
                ));
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            }
        }

        if (timerBar != null) { timerBar.removeAll(); timerBar = null; }
        if (registered) { HandlerList.unregisterAll(this); registered = false; }

        scoreManager.clearPvpSuppliers();
        alivePlayers.clear();
        killCount.clear();
        deathCount.clear();
        teamKillCount.clear();
        playerPlacedBlocks.clear();
        spawnLocations.clear();
        disconnectedAt.clear();
        phase1Started  = false;
        phase2Started  = false;
        survivalTick   = 0;
        elapsedSeconds = 0;
        endScreenShown = false;
    }

    // ══════════════════════════════════════════════════════════════
    //  END SCREEN
    // ══════════════════════════════════════════════════════════════

    private void showEndScreen() {
        Bukkit.broadcast(Component.text(" "));
        Bukkit.broadcast(Component.text("§8§m════════════════════════════════"));
        Bukkit.broadcast(Component.text("       §6§l🏆 HUNGER GAMES ENDE 🏆"));
        Bukkit.broadcast(Component.text("§8§m════════════════════════════════"));

        // 1. TEAM RANKING
        Bukkit.broadcast(Component.text(" "));
        Bukkit.broadcast(Component.text("§e§l📊 Team Ranking:"));
        List<TeamData> teams = getRanking();
        int rank = 1;
        for (TeamData team : teams) {
            int points = scoreManager.getPoints(team.getName());
            String mvp = getTeamMVP(team);
            String medal = switch (rank) {
                case 1 -> "§6§l🥇";
                case 2 -> "§7§l🥈";
                case 3 -> "§c§l🥉";
                default -> "§8  #" + rank;
            };
            String mvpSuffix = mvp.isEmpty() ? "" : " §8| §aMVP: §f" + mvp;
            Bukkit.broadcast(Component.text(medal + " §f" + team.getName() + " §8- §e" + points + " Punkte" + mvpSuffix));
            rank++;
        }

        // 2. TOP KILLER
        Bukkit.broadcast(Component.text(" "));
        Bukkit.broadcast(Component.text("§c§l⚔ Top Killer:"));
        List<Map.Entry<UUID, Integer>> sortedKills = new ArrayList<>(killCount.entrySet());
        sortedKills.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        int shown = 0;
        for (Map.Entry<UUID, Integer> entry : sortedKills) {
            if (shown >= 5) break;
            if (entry.getValue() == 0) continue;
            Player p     = Bukkit.getPlayer(entry.getKey());
            String name  = p != null ? p.getName() : "Unbekannt";
            int kills    = entry.getValue();
            int deaths   = deathCount.getOrDefault(entry.getKey(), 0);
            String kd    = deaths == 0 ? String.valueOf(kills) : String.format("%.2f", (double) kills / deaths);
            TeamData t   = teamManager.getTeamByPlayer(entry.getKey());
            String tTag  = t != null ? " §8[" + t.getName() + "§8]" : "";
            String medal = switch (shown) {
                case 0 -> "§6§l🥇";
                case 1 -> "§7§l🥈";
                case 2 -> "§c§l🥉";
                default -> "  §8" + (shown + 1) + ".";
            };
            Bukkit.broadcast(Component.text(medal + " §f" + name + tTag
                    + " §8| §c" + kills + " Kills §8")); //| §eK/D: " + kd
            shown++;
        }
        if (shown == 0) Bukkit.broadcast(Component.text("§7  Keine Kills in diesem Spiel."));

        // 3. ALLE SPIELER (ohne Tode im Chat, nur Kills + Status)
        Bukkit.broadcast(Component.text(" "));
        Bukkit.broadcast(Component.text("§b§l📋 Alle Spieler:"));
        List<UUID> allPlayers = new ArrayList<>(killCount.keySet());
        allPlayers.sort((a, b) -> Integer.compare(killCount.getOrDefault(b, 0), killCount.getOrDefault(a, 0)));
        for (UUID uuid : allPlayers) {
            Player p      = Bukkit.getPlayer(uuid);
            String name   = p != null ? p.getName() : "Unbekannt";
            int kills     = killCount.getOrDefault(uuid, 0);
            boolean alive = alivePlayers.contains(uuid) && p != null && p.getGameMode() != GameMode.SPECTATOR;
            TeamData t    = teamManager.getTeamByPlayer(uuid);
            String tTag   = t != null ? " §8[" + t.getName() + "§8]" : "";
            String status = alive ? " §a✔ Überlebt" : " §c✘ Eliminiert";
            Bukkit.broadcast(Component.text("§7➤ §f" + name + tTag + " §8| §c" + kills + " Kills" + status));
        }

        Bukkit.broadcast(Component.text(" "));
        Bukkit.broadcast(Component.text("§8§m════════════════════════════════"));
    }

    // ══════════════════════════════════════════════════════════════
    //  HILFSMETHODEN
    // ══════════════════════════════════════════════════════════════

    private void updateTimerBar() {
        if (timerBar == null) return;
        int min = elapsedSeconds / 60;
        int sec = elapsedSeconds % 60;
        // Farbe je nach Border-Phase
        BarColor color;
        if (!phase1Started) color = BarColor.GREEN;
        else if (!phase2Started) color = BarColor.YELLOW;
        else color = BarColor.RED;
        timerBar.setProgress(1.0); // kein Countdown → Bar immer voll
        timerBar.setColor(color);
        timerBar.setTitle(String.format("§6§lHunger Games §8| §7%d:%02d", min, sec));
    }

    private String getTeamMVP(TeamData team) {
        UUID mvpUuid = null;
        int mvpKills = -1;
        for (UUID uuid : team.getPlayers()) {
            int k = killCount.getOrDefault(uuid, 0);
            if (k > mvpKills) { mvpKills = k; mvpUuid = uuid; }
        }
        if (mvpUuid == null || mvpKills == 0) return "";
        Player mvp = Bukkit.getPlayer(mvpUuid);
        return (mvp != null ? mvp.getName() : "?") + " §8(§c" + mvpKills + " Kills§8)";
    }

    private void preparePlayer(Player p) {
        p.getInventory().clear();
        p.getInventory().addItem(new ItemStack(Material.STONE_SWORD));
        p.getInventory().addItem(new ItemStack(Material.COOKED_BEEF, 8));
        //p.getInventory().addItem(new ItemStack(Material.GOLDEN_APPLE, 1));
        p.setHealth(Objects.requireNonNull(p.getAttribute(Attribute.MAX_HEALTH)).getDefaultValue());
        p.setFoodLevel(20);
        p.setSaturation(20);
        p.setFireTicks(0);
        p.setFallDistance(0);
        p.setGameMode(GameMode.SURVIVAL);
        p.setAllowFlight(false);
        p.setFlying(false);
        p.removePotionEffect(PotionEffectType.ABSORPTION);
    }

    private void giveSurvivalPoints() {
        for (UUID uuid : new HashSet<>(alivePlayers)) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline() || p.isDead() || p.getGameMode() == GameMode.SPECTATOR) continue;
            TeamData team = teamManager.getTeamByPlayer(uuid);
            if (team != null) scoreManager.addPoints(team.getName(), 1);
        }
    }

    private void checkWinCondition() {
        if (soloTestMode) return;

        Set<String> aliveTeams = new HashSet<>();
        for (UUID uuid : alivePlayers) {
            if (disconnectedAt.containsKey(uuid)) continue; // Bug 1 Fix
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline() || p.isDead() || p.getGameMode() == GameMode.SPECTATOR) continue;
            TeamData team = teamManager.getTeamByPlayer(uuid);
            if (team != null) aliveTeams.add(team.getName());
        }

        if (aliveTeams.size() <= 1) {
            if (!aliveTeams.isEmpty()) {
                for (Player p : Bukkit.getOnlinePlayers())
                    p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                Bukkit.broadcast(Component.text("§6§l" + aliveTeams.iterator().next() + " §6hat gewonnen! 🎉"));
            }
            TunierServer.getInstance().getGameManager().stopGame(); // ← außerhalb vom isEmpty-Check
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  EVENTS
    // ══════════════════════════════════════════════════════════════

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player dead = e.getEntity();
        alivePlayers.remove(dead.getUniqueId());
        deathCount.merge(dead.getUniqueId(), 1, Integer::sum);
        e.deathMessage(null);

        Bukkit.getScheduler().runTaskLater(TunierServer.getInstance(), () -> {
            dead.spigot().respawn();
            dead.setGameMode(GameMode.SPECTATOR);
            dead.playSound(dead.getLocation(), Sound.ENTITY_PLAYER_DEATH, 1f, 1f);
        }, 2L);

        Player killer = dead.getKiller();
        if (killer == null) {
            Bukkit.broadcast(Component.text("§c☠ " + dead.getName() + " §7wurde eliminiert!"));
            checkWinCondition();
            return;
        }

        TeamData killerTeam = teamManager.getTeamByPlayer(killer.getUniqueId());
        TeamData deadTeam   = teamManager.getTeamByPlayer(dead.getUniqueId());

        // Friendly Fire Check
        boolean sameTeam = killerTeam != null && deadTeam != null
                && killerTeam.getName().equals(deadTeam.getName());

        if (sameTeam && !friendlyFire) {
            // Kein Kill gewertet, nur Elimination melden
            Bukkit.broadcast(Component.text("§c☠ " + dead.getName() + " §7wurde eliminiert!"));
            checkWinCondition();
            return;
        }

        if (killerTeam != null) {
            killCount.merge(killer.getUniqueId(), 1, Integer::sum);
            teamKillCount.merge(killerTeam.getName(), 1, Integer::sum);
            scoreManager.addPoints(killerTeam.getName(), 20);
            killer.playSound(killer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.5f);
            int totalKills = killCount.get(killer.getUniqueId());

            // Nur Kills im Chat, keine Tode
            Bukkit.broadcast(Component.text(
                    "§e⚔ " + killer.getName() + " §7hat §c" + dead.getName() +
                            " §7eliminiert! §8(§a+20 Punkte §8| §c" + totalKills + " Kills§8)"
            ));
        } else {
            Bukkit.broadcast(Component.text("§c☠ " + dead.getName() + " §7wurde eliminiert!"));
        }

        checkWinCondition();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        if (timerBar != null) timerBar.removePlayer(p);
        if (alivePlayers.contains(p.getUniqueId())) {
            disconnectedAt.put(p.getUniqueId(), System.currentTimeMillis());
            Bukkit.broadcast(Component.text("§e⚠ " + p.getName() + " §7hat das Spiel verlassen. §8(30s Reconnect-Zeit)"));
        }
    }

    @EventHandler
    public void onJoin(org.bukkit.event.player.PlayerJoinEvent e) {
        UUID uuid = e.getPlayer().getUniqueId();
        if (disconnectedAt.containsKey(uuid)) {
            disconnectedAt.remove(uuid);
            if (timerBar != null) timerBar.addPlayer(e.getPlayer());
            Bukkit.getScheduler().runTaskLater(TunierServer.getInstance(), () -> {
                Player rejoined = Bukkit.getPlayer(uuid);
                if (rejoined == null) return;
                Location loc = spawnLocations.get(uuid);
                if (loc != null) {
                    rejoined.teleport(loc);
                } else if (world != null) {
                    rejoined.teleport(world.getSpawnLocation()); // Fallback: PvP-Welt Spawn
                }
                rejoined.setFallDistance(0);
                rejoined.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
            }, 5L); // 5 Ticks warten bis Client ready
            Bukkit.broadcast(Component.text("§a✔ " + e.getPlayer().getName() + " §7ist zurückgekehrt!"));
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  ABSTRACT IMPL
    // ══════════════════════════════════════════════════════════════

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
        return scoreManager.getPoints(teamName);
    }

    @Override
    public void handleEvent(Event event) {}

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent e) {
        if (!alivePlayers.contains(e.getPlayer().getUniqueId())) return;
        org.bukkit.block.Block b = e.getBlock();
        playerPlacedBlocks.add(b.getWorld().getName() + "," + b.getX() + "," + b.getY() + "," + b.getZ());
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        if (!alivePlayers.contains(e.getPlayer().getUniqueId())) return;
        org.bukkit.block.Block b = e.getBlock();
        String key = b.getWorld().getName() + "," + b.getX() + "," + b.getY() + "," + b.getZ();
        if (!playerPlacedBlocks.contains(key)) {
            e.setCancelled(true);
            return;
        }
        playerPlacedBlocks.remove(key);
    }

    @EventHandler
    public void onDamage(org.bukkit.event.entity.EntityDamageByEntityEvent e) {
        if (friendlyFire) return;
        if (!(e.getEntity() instanceof Player dead)) return;
        if (!(e.getDamager() instanceof Player attacker)) return;

        TeamData deadTeam     = teamManager.getTeamByPlayer(dead.getUniqueId());
        TeamData attackerTeam = teamManager.getTeamByPlayer(attacker.getUniqueId());

        if (deadTeam != null && attackerTeam != null
                && deadTeam.getName().equals(attackerTeam.getName())) {
            e.setCancelled(true);
        }
    }
}