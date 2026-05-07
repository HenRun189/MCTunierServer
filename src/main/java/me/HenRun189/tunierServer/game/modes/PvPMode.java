package me.HenRun189.tunierServer.game.modes;

import me.HenRun189.tunierServer.TunierServer;
import me.HenRun189.tunierServer.score.ScoreManager;
import me.HenRun189.tunierServer.team.TeamData;
import me.HenRun189.tunierServer.team.TeamManager;

import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.Particle;

import org.bukkit.event.entity.EntityDamageByEntityEvent;


import java.util.*;
import java.util.Random;

import static java.lang.Math.abs;

public class PvPMode extends AbstractGameMode implements Listener {

    private final ScoreManager scoreManager;
    private final Set<UUID> alivePlayers = new HashSet<>();

    private Location centerLoc;

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

    private static final int[][] chestPos = {
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

    private final ItemChestLoot[] possibleChestLoot = {};

    private final double extraDistAdd = 0; // noch setzten !!!!
    private final double smoothDistributionAbsolute = 0; // noch setzten und mindestens über 0
    private final double smoothDistributionRoot = 0; // noch setzten und mindestens über 0
    private final double avrgLootAmount = 0;

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

        centerLoc = new Location(world, 16,0,26);

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

        //

        ArrayList<Location> chestLocations = new ArrayList<>();

        Location nloc1 = new Location(world, 245,35,-203);
        Location nloc2 = new Location(world, -214,135,255);

        int smallX = (int) Math.floor(Math.min(nloc1.getX(), nloc2.getX()));
        int bigX = (int) Math.floor(Math.max(nloc1.getX(), nloc2.getX()));

        int smallY = (int) Math.floor(Math.min(nloc1.getY(), nloc2.getY()));
        int bigY = (int) Math.floor(Math.max(nloc1.getY(), nloc2.getY()));

        int smallZ = (int) Math.floor(Math.min(nloc1.getZ(), nloc2.getZ()));
        int bigZ = (int) Math.floor(Math.max(nloc1.getZ(), nloc2.getZ()));

        for (int x = smallX; x <= bigX; x++) {
            for (int y = smallY; y <= bigY; y++) {
                for (int z = smallZ; z <= bigZ; z++) {
                    if (world.getBlockAt(x, y, z).getType() == Material.CHEST) {
                        Location loc = new Location(world, x, y, z);
                        chestLocations.add(loc);

                        Bukkit.getLogger().info(
                                "Chest " + (chestLocations.size() - 1) +
                                        ": X=" + x + " Y=" + y + " Z=" + z
                        );

                        world.spawnParticle(Particle.HEART, loc.clone().add(0.5, 1.0, 0.5), 20, 0.25, 0.25, 0.25, 0);
                        world.spawnParticle(Particle.FLAME, loc.clone().add(0.5, 1.2, 0.5), 6, 0.1, 0.1, 0.1);
                    }
                }
            }
        }



        for (int i = 0; i < chestPos.length; i++) {

            double dist = centerLoc.distance(new Location(world, chestPos[i][0], chestPos[i][1], chestPos[i][2]));

            double lootValue = dist + extraDistAdd;
            double leftLootValue = lootValue;

            ArrayList<ItemChestLoot> allowedChestLoot = new ArrayList<>();

            Block block = (new Location(world, chestPos[i][0], chestPos[i][1], chestPos[i][2])).getBlock();
            Chest chest = (Chest) block.getState();
            Inventory inventory = chest.getInventory();


            while (true) {

                ArrayList<Double> probabilityValues = new ArrayList<Double>();
                double probabilitySum = 0;

                for (int j = 0; j < possibleChestLoot.length; j++) {
                    if (possibleChestLoot[j].value <= lootValue) {
                        ItemChestLoot icl = new ItemChestLoot(possibleChestLoot[j].item, possibleChestLoot[j].value);
                        allowedChestLoot.add(icl);

                        double probability = icl.getLootValueOnDist(lootValue / avrgLootAmount);
                        probabilityValues.add(probability);
                        probabilitySum += probability;
                    }
                }

                Random r = new Random();
                double selectedItem = r.nextDouble() * probabilitySum;
                double probabilityCounter = 0;
                for (int j = 0; probabilityCounter < probabilitySum; j++) {
                    if ((probabilityCounter + probabilityValues.get(j)) > selectedItem) {

                        inventory.addItem(allowedChestLoot.get(j).item);
                        leftLootValue -= allowedChestLoot.get(j).value;
                        break;
                    }
                    else {
                        probabilityCounter += probabilityValues.get(j);
                    }
                }

                if (leftLootValue <= 0) {
                    break;
                }
            }

            chest.update();
        }


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





    public class ItemChestLoot {
        ItemStack item;
        double value;

        public ItemChestLoot(ItemStack arg_item, double arg_value) {
            item = arg_item;
            value = arg_value;
        }

        public double getLootValueOnDist(double lootValue) {
            return 1 / (expn((abs(lootValue - value) + smoothDistributionAbsolute), 1 / smoothDistributionRoot));
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

    public double expn(double base, double exponent) {
        if (base <= 0) return 0; // ← Guard
        return Math.exp(exponent * Math.log(base));
    }
}




/*
[20:56:33] [Server thread/INFO]: Chest 0: X=-119 Y=64 Z=35
[20:56:33] [Server thread/INFO]: Chest 1: X=-119 Y=65 Z=27
[20:56:33] [Server thread/INFO]: Chest 2: X=-119 Y=73 Z=-21
[20:56:33] [Server thread/INFO]: Chest 3: X=-119 Y=73 Z=38
[20:56:33] [Server thread/INFO]: Chest 4: X=-119 Y=85 Z=11
[20:56:33] [Server thread/INFO]: Chest 5: X=-119 Y=111 Z=-5
[20:56:33] [Server thread/INFO]: Chest 6: X=-119 Y=124 Z=12
[20:56:33] [Server thread/INFO]: Chest 7: X=-118 Y=66 Z=-14
[20:56:33] [Server thread/INFO]: Chest 8: X=-117 Y=70 Z=33
[20:56:33] [Server thread/INFO]: Chest 9: X=-114 Y=69 Z=-9
[20:56:33] [Server thread/INFO]: Chest 10: X=-114 Y=93 Z=115
[20:56:33] [Server thread/INFO]: Chest 11: X=-113 Y=66 Z=3
[20:56:33] [Server thread/INFO]: Chest 12: X=-110 Y=84 Z=112
[20:56:33] [Server thread/INFO]: Chest 13: X=-109 Y=63 Z=124
[20:56:33] [Server thread/INFO]: Chest 14: X=-107 Y=71 Z=109
[20:56:33] [Server thread/INFO]: Chest 15: X=-79 Y=63 Z=148
[20:56:33] [Server thread/INFO]: Chest 16: X=-77 Y=64 Z=82
[20:56:33] [Server thread/INFO]: Chest 17: X=-73 Y=81 Z=76
[20:56:33] [Server thread/INFO]: Chest 18: X=-64 Y=80 Z=-45
[20:56:33] [Server thread/INFO]: Chest 19: X=-63 Y=64 Z=-47
[20:56:33] [Server thread/INFO]: Chest 20: X=-63 Y=66 Z=-45
[20:56:33] [Server thread/INFO]: Chest 21: X=-58 Y=63 Z=23
[20:56:33] [Server thread/INFO]: Chest 22: X=-49 Y=57 Z=115
[20:56:33] [Server thread/INFO]: Chest 23: X=-49 Y=63 Z=-8
[20:56:33] [Server thread/INFO]: Chest 24: X=-45 Y=61 Z=4
[20:56:33] [Server thread/INFO]: Chest 25: X=-38 Y=65 Z=-108
[20:56:33] [Server thread/INFO]: Chest 26: X=-35 Y=89 Z=-85
[20:56:33] [Server thread/INFO]: Chest 27: X=-34 Y=67 Z=-108
[20:56:33] [Server thread/INFO]: Chest 28: X=-32 Y=67 Z=-109
[20:56:33] [Server thread/INFO]: Chest 29: X=-31 Y=81 Z=44
[20:56:33] [Server thread/INFO]: Chest 30: X=-31 Y=90 Z=-1
[20:56:33] [Server thread/INFO]: Chest 31: X=-30 Y=67 Z=-5
[20:56:33] [Server thread/INFO]: Chest 32: X=-29 Y=75 Z=57
[20:56:33] [Server thread/INFO]: Chest 33: X=-28 Y=65 Z=157
[20:56:33] [Server thread/INFO]: Chest 34: X=-28 Y=85 Z=18
[20:56:33] [Server thread/INFO]: Chest 35: X=-27 Y=66 Z=-160
[20:56:33] [Server thread/INFO]: Chest 36: X=-26 Y=80 Z=155
[20:56:33] [Server thread/INFO]: Chest 37: X=-26 Y=81 Z=18
[20:56:33] [Server thread/INFO]: Chest 38: X=-26 Y=91 Z=-115
[20:56:33] [Server thread/INFO]: Chest 39: X=-25 Y=81 Z=32
[20:56:33] [Server thread/INFO]: Chest 40: X=-25 Y=87 Z=-114
[20:56:33] [Server thread/INFO]: Chest 41: X=-18 Y=65 Z=189
[20:56:33] [Server thread/INFO]: Chest 42: X=-18 Y=68 Z=85
[20:56:33] [Server thread/INFO]: Chest 43: X=-17 Y=64 Z=-21
[20:56:33] [Server thread/INFO]: Chest 44: X=-17 Y=71 Z=61
[20:56:33] [Server thread/INFO]: Chest 45: X=-17 Y=95 Z=-7
[20:56:33] [Server thread/INFO]: Chest 46: X=-15 Y=80 Z=-144
[20:56:33] [Server thread/INFO]: Chest 47: X=-14 Y=65 Z=135
[20:56:33] [Server thread/INFO]: Chest 48: X=-14 Y=68 Z=-127
[20:56:33] [Server thread/INFO]: Chest 49: X=-13 Y=73 Z=43
[20:56:33] [Server thread/INFO]: Chest 50: X=-12 Y=97 Z=-150
[20:56:33] [Server thread/INFO]: Chest 51: X=-10 Y=56 Z=-66
[20:56:33] [Server thread/INFO]: Chest 52: X=-10 Y=109 Z=171
[20:56:33] [Server thread/INFO]: Chest 53: X=-8 Y=86 Z=-5
[20:56:33] [Server thread/INFO]: Chest 54: X=-8 Y=88 Z=75
[20:56:33] [Server thread/INFO]: Chest 55: X=-7 Y=67 Z=-137
[20:56:33] [Server thread/INFO]: Chest 56: X=-6 Y=66 Z=195
[20:56:33] [Server thread/INFO]: Chest 57: X=-3 Y=91 Z=-17
[20:56:33] [Server thread/INFO]: Chest 58: X=-2 Y=65 Z=-143
[20:56:33] [Server thread/INFO]: Chest 59: X=-2 Y=124 Z=169
[20:56:33] [Server thread/INFO]: Chest 60: X=0 Y=68 Z=-1
[20:56:33] [Server thread/INFO]: Chest 61: X=1 Y=65 Z=188
[20:56:33] [Server thread/INFO]: Chest 62: X=2 Y=97 Z=159
[20:56:33] [Server thread/INFO]: Chest 63: X=2 Y=99 Z=219
[20:56:33] [Server thread/INFO]: Chest 64: X=6 Y=109 Z=-16
[20:56:33] [Server thread/INFO]: Chest 65: X=7 Y=74 Z=-122
[20:56:33] [Server thread/INFO]: Chest 66: X=8 Y=66 Z=161
[20:56:33] [Server thread/INFO]: Chest 67: X=9 Y=72 Z=-123
[20:56:33] [Server thread/INFO]: Chest 68: X=9 Y=121 Z=-11
[20:56:34] [Server thread/INFO]: Chest 69: X=10 Y=65 Z=182
[20:56:34] [Server thread/INFO]: Chest 70: X=10 Y=69 Z=26
[20:56:34] [Server thread/INFO]: Chest 71: X=11 Y=111 Z=-10
[20:56:34] [Server thread/INFO]: Chest 72: X=11 Y=117 Z=202
[20:56:34] [Server thread/INFO]: Chest 73: X=12 Y=69 Z=22
[20:56:34] [Server thread/INFO]: Chest 74: X=12 Y=69 Z=30
[20:56:34] [Server thread/INFO]: Chest 75: X=12 Y=77 Z=77
[20:56:34] [Server thread/INFO]: Chest 76: X=12 Y=105 Z=-18
[20:56:34] [Server thread/INFO]: Chest 77: X=13 Y=67 Z=-138
[20:56:34] [Server thread/INFO]: Chest 78: X=13 Y=67 Z=-137
[20:56:34] [Server thread/INFO]: Chest 79: X=13 Y=74 Z=-122
[20:56:34] [Server thread/INFO]: Chest 80: X=13 Y=79 Z=-128
[20:56:34] [Server thread/INFO]: Chest 81: X=13 Y=96 Z=63
[20:56:34] [Server thread/INFO]: Chest 82: X=13 Y=118 Z=169
[20:56:34] [Server thread/INFO]: Chest 83: X=14 Y=69 Z=26
[20:56:34] [Server thread/INFO]: Chest 84: X=14 Y=88 Z=58
[20:56:34] [Server thread/INFO]: Chest 85: X=14 Y=119 Z=193
[20:56:34] [Server thread/INFO]: Chest 86: X=15 Y=69 Z=27
[20:56:34] [Server thread/INFO]: Chest 87: X=15 Y=78 Z=53
[20:56:34] [Server thread/INFO]: Chest 88: X=15 Y=107 Z=70
[20:56:34] [Server thread/INFO]: Chest 89: X=15 Y=108 Z=180
[20:56:34] [Server thread/INFO]: Chest 90: X=16 Y=69 Z=20
[20:56:34] [Server thread/INFO]: Chest 91: X=16 Y=69 Z=32
[20:56:34] [Server thread/INFO]: Chest 92: X=16 Y=70 Z=25
[20:56:34] [Server thread/INFO]: Chest 93: X=16 Y=90 Z=-6
[20:56:34] [Server thread/INFO]: Chest 94: X=17 Y=59 Z=-21
[20:56:34] [Server thread/INFO]: Chest 95: X=17 Y=69 Z=-29
[20:56:34] [Server thread/INFO]: Chest 96: X=17 Y=70 Z=26
[20:56:34] [Server thread/INFO]: Chest 97: X=17 Y=107 Z=64
[20:56:34] [Server thread/INFO]: Chest 98: X=18 Y=74 Z=27
[20:56:34] [Server thread/INFO]: Chest 99: X=18 Y=98 Z=-10
[20:56:34] [Server thread/INFO]: Chest 100: X=19 Y=91 Z=73
[20:56:34] [Server thread/INFO]: Chest 101: X=19 Y=108 Z=179
[20:56:34] [Server thread/INFO]: Chest 102: X=19 Y=109 Z=173
[20:56:34] [Server thread/INFO]: Chest 103: X=19 Y=121 Z=198
[20:56:34] [Server thread/INFO]: Chest 104: X=19 Y=129 Z=195
[20:56:34] [Server thread/INFO]: Chest 105: X=20 Y=61 Z=-15
[20:56:34] [Server thread/INFO]: Chest 106: X=20 Y=63 Z=160
[20:56:34] [Server thread/INFO]: Chest 107: X=20 Y=68 Z=-130
[20:56:34] [Server thread/INFO]: Chest 108: X=20 Y=69 Z=22
[20:56:34] [Server thread/INFO]: Chest 109: X=20 Y=69 Z=30
[20:56:34] [Server thread/INFO]: Chest 110: X=20 Y=96 Z=70
[20:56:34] [Server thread/INFO]: Chest 111: X=20 Y=124 Z=64
[20:56:34] [Server thread/INFO]: Chest 112: X=22 Y=69 Z=26
[20:56:34] [Server thread/INFO]: Chest 113: X=23 Y=60 Z=-23
[20:56:34] [Server thread/INFO]: Chest 114: X=23 Y=102 Z=162
[20:56:34] [Server thread/INFO]: Chest 115: X=23 Y=107 Z=64
[20:56:34] [Server thread/INFO]: Chest 116: X=27 Y=88 Z=-140
[20:56:34] [Server thread/INFO]: Chest 117: X=32 Y=87 Z=68
[20:56:34] [Server thread/INFO]: Chest 118: X=32 Y=101 Z=69
[20:56:34] [Server thread/INFO]: Chest 119: X=35 Y=76 Z=58
[20:56:34] [Server thread/INFO]: Chest 120: X=35 Y=93 Z=72
[20:56:34] [Server thread/INFO]: Chest 121: X=36 Y=65 Z=196
[20:56:34] [Server thread/INFO]: Chest 122: X=36 Y=84 Z=150
[20:56:34] [Server thread/INFO]: Chest 123: X=36 Y=100 Z=189
[20:56:34] [Server thread/INFO]: Chest 124: X=37 Y=65 Z=90
[20:56:34] [Server thread/INFO]: Chest 125: X=37 Y=92 Z=168
[20:56:34] [Server thread/INFO]: Chest 126: X=42 Y=80 Z=77
[20:56:34] [Server thread/INFO]: Chest 127: X=42 Y=84 Z=202
[20:56:34] [Server thread/INFO]: Chest 128: X=43 Y=64 Z=130
[20:56:34] [Server thread/INFO]: Chest 129: X=43 Y=70 Z=-33
[20:56:34] [Server thread/INFO]: Chest 130: X=45 Y=80 Z=78
[20:56:34] [Server thread/INFO]: Chest 131: X=45 Y=83 Z=64
[20:56:34] [Server thread/INFO]: Chest 132: X=47 Y=65 Z=190
[20:56:34] [Server thread/INFO]: Chest 133: X=48 Y=68 Z=-131
[20:56:34] [Server thread/INFO]: Chest 134: X=49 Y=70 Z=152
[20:56:34] [Server thread/INFO]: Chest 135: X=50 Y=96 Z=-137
[20:56:34] [Server thread/INFO]: Chest 136: X=51 Y=67 Z=-103
[20:56:34] [Server thread/INFO]: Chest 137: X=51 Y=84 Z=-33
[20:56:34] [Server thread/INFO]: Chest 138: X=51 Y=109 Z=-128
[20:56:34] [Server thread/INFO]: Chest 139: X=52 Y=71 Z=-148
[20:56:34] [Server thread/INFO]: Chest 140: X=52 Y=92 Z=-2
[20:56:34] [Server thread/INFO]: Chest 141: X=53 Y=37 Z=-132
[20:56:34] [Server thread/INFO]: Chest 142: X=53 Y=84 Z=5
[20:56:34] [Server thread/INFO]: Chest 143: X=54 Y=66 Z=60
[20:56:34] [Server thread/INFO]: Chest 144: X=54 Y=78 Z=-140
[20:56:34] [Server thread/INFO]: Chest 145: X=56 Y=65 Z=207
[20:56:34] [Server thread/INFO]: Chest 146: X=56 Y=78 Z=34
[20:56:34] [Server thread/INFO]: Chest 147: X=57 Y=77 Z=-141
[20:56:34] [Server thread/INFO]: Chest 148: X=57 Y=80 Z=31
[20:56:34] [Server thread/INFO]: Chest 149: X=63 Y=83 Z=72
[20:56:34] [Server thread/INFO]: Chest 150: X=63 Y=90 Z=-131
[20:56:34] [Server thread/INFO]: Chest 151: X=63 Y=91 Z=15
[20:56:34] [Server thread/INFO]: Chest 152: X=64 Y=63 Z=-40
[20:56:34] [Server thread/INFO]: Chest 153: X=67 Y=66 Z=-43
[20:56:34] [Server thread/INFO]: Chest 154: X=70 Y=63 Z=87
[20:56:34] [Server thread/INFO]: Chest 155: X=70 Y=64 Z=-102
[20:56:34] [Server thread/INFO]: Chest 156: X=73 Y=65 Z=185
[20:56:34] [Server thread/INFO]: Chest 157: X=73 Y=88 Z=-39
[20:56:34] [Server thread/INFO]: Chest 158: X=77 Y=82 Z=67
[20:56:34] [Server thread/INFO]: Chest 159: X=80 Y=99 Z=19
[20:56:34] [Server thread/INFO]: Chest 160: X=81 Y=68 Z=56
[20:56:34] [Server thread/INFO]: Chest 161: X=82 Y=53 Z=65
[20:56:34] [Server thread/INFO]: Chest 162: X=82 Y=72 Z=-120
[20:56:34] [Server thread/INFO]: Chest 163: X=83 Y=94 Z=17
[20:56:34] [Server thread/INFO]: Chest 164: X=84 Y=63 Z=68
[20:56:34] [Server thread/INFO]: Chest 165: X=86 Y=65 Z=135
[20:56:34] [Server thread/INFO]: Chest 166: X=88 Y=94 Z=18
[20:56:34] [Server thread/INFO]: Chest 167: X=93 Y=57 Z=105
[20:56:34] [Server thread/INFO]: Chest 168: X=96 Y=66 Z=19
[20:56:34] [Server thread/INFO]: Chest 169: X=96 Y=97 Z=14
[20:56:34] [Server thread/INFO]: Chest 170: X=101 Y=59 Z=177
[20:56:34] [Server thread/INFO]: Chest 171: X=106 Y=48 Z=43
[20:56:34] [Server thread/INFO]: Chest 172: X=107 Y=71 Z=14
[20:56:34] [Server thread/INFO]: Chest 173: X=125 Y=47 Z=36
[20:56:34] [Server thread/INFO]: Chest 174: X=130 Y=60 Z=95
[20:56:34] [Server thread/INFO]: Chest 175: X=138 Y=64 Z=96
[20:56:34] [Server thread/INFO]: Chest 176: X=138 Y=64 Z=97
[20:56:34] [Server thread/INFO]: Chest 177: X=138 Y=64 Z=98
[20:56:34] [Server thread/INFO]: Chest 178: X=138 Y=64 Z=99
[20:56:34] [Server thread/INFO]: Chest 179: X=138 Y=65 Z=95
[20:56:34] [Server thread/INFO]: Chest 180: X=138 Y=65 Z=96
[20:56:34] [Server thread/INFO]: Chest 181: X=138 Y=65 Z=97
[20:56:34] [Server thread/INFO]: Chest 182: X=138 Y=65 Z=98
[20:56:34] [Server thread/INFO]: Chest 183: X=138 Y=65 Z=99
[20:56:34] [Server thread/INFO]: Chest 184: X=138 Y=66 Z=96
[20:56:34] [Server thread/INFO]: Chest 185: X=138 Y=66 Z=97
[20:56:34] [Server thread/INFO]: Chest 186: X=138 Y=66 Z=98
[20:56:34] [Server thread/INFO]: Chest 187: X=138 Y=66 Z=99
[20:56:34] [Server thread/INFO]: Chest 188: X=138 Y=67 Z=98
[20:56:34] [Server thread/INFO]: Chest 189: X=138 Y=67 Z=99
[20:56:34] [Server thread/INFO]: Chest 190: X=150 Y=63 Z=-50
[20:56:34] [Server thread/INFO]: Chest 191: X=151 Y=65 Z=61
[20:56:34] [Server thread/INFO]: Chest 192: X=153 Y=63 Z=98
[20:56:34] [Server thread/INFO]: Chest 193: X=158 Y=50 Z=14
[20:56:34] [Server thread/INFO]: Chest 194: X=158 Y=66 Z=-24
[20:56:34] [Server thread/INFO]: Chest 195: X=158 Y=66 Z=5
[20:56:34] [Server thread/INFO]: Chest 196: X=160 Y=81 Z=-27
[20:56:34] [Server thread/INFO]: Chest 197: X=163 Y=67 Z=-13
[20:56:34] [Server thread/INFO]: Chest 198: X=165 Y=86 Z=-25
[20:56:34] [Server thread/INFO]: Chest 199: X=167 Y=100 Z=-22
[20:56:34] [Server thread/INFO]: Chest 200: X=169 Y=110 Z=-23
[20:56:34] [Server thread/INFO]: Chest 201: X=172 Y=67 Z=-14
[20:56:34] [Server thread/INFO]: Chest 202: X=172 Y=86 Z=12
[20:56:34] [Server thread/INFO]: Chest 203: X=176 Y=64 Z=-53
[20:56:34] [Server thread/INFO]: Chest 204: X=181 Y=65 Z=16
[20:56:34] [Server thread/INFO]: Chest 205: X=206 Y=78 Z=-8
 */