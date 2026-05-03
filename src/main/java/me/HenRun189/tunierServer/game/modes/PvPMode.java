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

    private static final int BORDER_PHASE1_START_SEC = 300; // 5 min
    private static final int BORDER_PHASE2_START_SEC = 780; // 13 min
    private static final int TOTAL_SECONDS           = 15 * 60;

    private boolean phase1Started = false;
    private boolean phase2Started = false;

    // ── Einstellungen ──────────────────────────────────────────────
    /** false = kein Friendly Fire (Standard); true = Teamkollegen können sich töten */
    private boolean friendlyFire  = false;
    /** true = Win-Condition aus (Solo-Test); false = normal */
    private boolean soloTestMode  = false;

    // ── BossBar ────────────────────────────────────────────────────
    private BossBar timerBar;
    private boolean registered   = false;
    private int     survivalTick = 0;

    public PvPMode(TeamManager teamManager, ScoreManager scoreManager) {
        super(TOTAL_SECONDS, teamManager);
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
        survivalTick  = 0;
        phase1Started = false;
        phase2Started = false;

        // Team-Kill-Counter initialisieren
        for (TeamData t : teamManager.getTeams().values()) {
            teamKillCount.put(t.getName(), 0);
        }

        // Border – Mitte ist 16, 70, 26
        border = world.getWorldBorder();
        border.setCenter(BORDER_CENTER_X, BORDER_CENTER_Z);
        border.setSize(460); // Radius 230 × 2

        // BossBar
        timerBar = Bukkit.createBossBar("§6§lHunger Games §8| §715:00", BarColor.GREEN, BarStyle.SOLID);

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

        int elapsed = TOTAL_SECONDS - time;

        updateTimerBar();

        // Survival-Punkte alle 15 Sek
        survivalTick++;
        if (survivalTick >= 15) {
            survivalTick = 0;
            giveSurvivalPoints();
        }

        // Border Phase 1: nach 5 min → Radius 230 → 75 in 5 min
        if (!phase1Started && elapsed >= BORDER_PHASE1_START_SEC) {
            phase1Started = true;
            border.changeSize(150, 300L); // Durchmesser 150 = Radius 75, 300 Sek
            Bukkit.broadcast(Component.text("§c§lDie Border verkleinert sich! §7(→ Radius 75 in 5 min)"));
        }

        // Border Phase 2: bei 13 min → Radius 75 → 5 in 90 Sek (fertig 30s vor Ende)
        if (!phase2Started && elapsed >= BORDER_PHASE2_START_SEC) {
            phase2Started = true;
            border.changeSize(10, 90L); // Durchmesser 10 = Radius 5, 90 Sek
            Bukkit.broadcast(Component.text("§4§lACHTUNG! §cBorder → 5 Blöcke! §7(fertig 30s vor Ende)"));
        }

        checkWinCondition();
    }

    // ══════════════════════════════════════════════════════════════
    //  ActionBar — überschreibt AbstractGameMode
    //  Zeigt: Status | eigene Kills | Team [lebend/gesamt] | Team-Kills
    // ══════════════════════════════════════════════════════════════

    @Override
    protected void updateActionbar() {
        // Gesamtzahl lebender Spieler
        int totalAlive = 0;
        for (UUID uuid : alivePlayers) {
            Player pl = Bukkit.getPlayer(uuid);
            if (pl != null && pl.isOnline() && !pl.isDead() && pl.getGameMode() != GameMode.SPECTATOR)
                totalAlive++;
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            int myKills = killCount.getOrDefault(p.getUniqueId(), 0);
            boolean isAlive = alivePlayers.contains(p.getUniqueId())
                    && p.getGameMode() != GameMode.SPECTATOR;

            // Team-Info berechnen
            TeamData myTeam = teamManager.getTeamByPlayer(p.getUniqueId());
            String teamInfo = "";
            if (myTeam != null) {
                int teamAlive = 0;
                int teamTotal = myTeam.getPlayers().size();
                for (UUID uuid : myTeam.getPlayers()) {
                    if (!alivePlayers.contains(uuid)) continue;
                    Player pl = Bukkit.getPlayer(uuid);
                    if (pl != null && pl.isOnline() && !pl.isDead() && pl.getGameMode() != GameMode.SPECTATOR)
                        teamAlive++;
                }
                int tKills = teamKillCount.getOrDefault(myTeam.getName(), 0);
                teamInfo = " §8| §bTeam: §f" + teamAlive + "§7/§f" + teamTotal
                        + " §8| §bTeam-Kills: §f" + tKills;
            }

            p.sendActionBar(Component.text(
                    (isAlive ? "§a● Leben" : "§c● Zuschauer") +
                            " §8| §c⚔ " + myKills + " Kills" +
                            teamInfo +
                            " §8| §e" + totalAlive + " übrig"
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
        phase1Started  = false;
        phase2Started  = false;
        survivalTick   = 0;
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
                    + " §8| §c" + kills + " Kills §8| §eK/D: " + kd));
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
        int min = time / 60;
        int sec = time % 60;
        double progress = Math.max(0, Math.min(1, (double) time / TOTAL_SECONDS));
        timerBar.setProgress(progress);
        timerBar.setColor(progress > 0.5 ? BarColor.GREEN : progress > 0.25 ? BarColor.YELLOW : BarColor.RED);
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
        p.getInventory().addItem(new ItemStack(Material.GOLDEN_APPLE, 1));
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
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline() || p.isDead() || p.getGameMode() == GameMode.SPECTATOR) continue;
            TeamData team = teamManager.getTeamByPlayer(uuid);
            if (team != null) aliveTeams.add(team.getName());
        }

        if (aliveTeams.size() <= 1) {
            if (!aliveTeams.isEmpty())
                Bukkit.broadcast(Component.text("§6§l" + aliveTeams.iterator().next() + " §6hat gewonnen! 🎉"));
            TunierServer.getInstance().getGameManager().stopGame();
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
        alivePlayers.remove(e.getPlayer().getUniqueId());
        if (timerBar != null) timerBar.removePlayer(e.getPlayer());
        checkWinCondition();
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
}