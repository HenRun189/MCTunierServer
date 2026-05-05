package me.HenRun189.tunierServer.game.modes;

import me.HenRun189.tunierServer.team.*;
import me.HenRun189.tunierServer.score.ScoreManager;
import me.HenRun189.tunierServer.achievement.AchievementManager;
import me.HenRun189.tunierServer.TunierServer;

import org.bukkit.*;
import org.bukkit.boss.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.event.player.PlayerJoinEvent;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;

import java.util.*;

public class AchievementMode extends AbstractGameMode implements Listener {

    private final TeamManager teamManager;
    private final ScoreManager scoreManager;
    private final AchievementManager achievementManager;

    private final Map<String, String> currentAdvancement = new HashMap<>();
    private final Map<String, Integer> skips = new HashMap<>();
    private final Map<String, Integer> index = new HashMap<>();
    private final List<String> rotation = new ArrayList<>();

    private final Map<String, BossBar> teamBossBars = new HashMap<>();

    // Wie viele Advancements hat jeder Spieler persoenlich abgeschlossen
    private final Map<UUID, Integer> playerAdvCount = new HashMap<>();

    // Welches Advancement hat welcher Spieler zuletzt completed (fuer den Broadcast)
    private final Map<UUID, String> playerLastAdv = new HashMap<>();

    public AchievementMode(TeamManager teamManager, ScoreManager scoreManager, AchievementManager achievementManager) {
        super(1500, teamManager);
        this.teamManager = teamManager;
        this.scoreManager = scoreManager;
        this.achievementManager = achievementManager;
    }

    @Override
    public void start() {
        currentAdvancement.clear();
        skips.clear();
        index.clear();
        rotation.clear();
        teamBossBars.clear();
        playerAdvCount.clear();
        playerLastAdv.clear();

        for (TeamData team : teamManager.getTeams().values()) {
            BossBar bar = Bukkit.createBossBar("§aAchievement Battle", BarColor.GREEN, BarStyle.SOLID);
            for (UUID uuid : team.getPlayers()) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) bar.addPlayer(p);
            }
            teamBossBars.put(team.getName(), bar);
        }

        List<String> pool = new ArrayList<>(AchievementManager.ADVANCEMENT_POOL);
        Collections.shuffle(pool);
        if (pool.isEmpty()) pool.add("story/mine_stone");
        rotation.addAll(pool);

        for (TeamData team : teamManager.getTeams().values()) {
            currentAdvancement.put(team.getName(), rotation.get(0));
            index.put(team.getName(), 0);
            skips.put(team.getName(), 3);
        }

        Bukkit.getPluginManager().registerEvents(this, TunierServer.getInstance());

        super.start();
    }

    // =========================
    //  START
    // =========================

    @Override
    protected void onGameStart() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            TeamData team = teamManager.getTeamByPlayer(p.getUniqueId());
            if (team == null) continue;

            String adv = currentAdvancement.get(team.getName());

            p.showTitle(Title.title(
                    Component.text("§a§lGO!"),
                    Component.text("§e" + achievementManager.getDisplayName(adv))
            ));

            p.sendMessage("§6§lSTART!");
            p.sendMessage("§eYour task: §f" + achievementManager.getDisplayName(adv));
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        }
    }

    // =========================
    //  LOOP
    // =========================

    @Override
    protected void onGameTick() {
        // nothing per tick needed
    }

    @Override
    protected void onSecond() {
        double maxTime = 1500.0;

        for (TeamData team : teamManager.getTeams().values()) {
            BossBar bar = teamBossBars.get(team.getName());
            if (bar == null) continue;

            String adv = currentAdvancement.get(team.getName());
            if (adv == null) continue;

            bar.setProgress(Math.max(0, (double) time / maxTime));
            bar.setTitle("§a" + achievementManager.getDisplayName(adv)
                    + " §8| §e" + scoreManager.getPoints(team.getName()) + " Pkt");
        }
    }

    // =========================
    //  STOP
    // =========================

    @Override
    public void stop() {
        showEndScreen();
        super.stop();

        HandlerList.unregisterAll(this);

        for (BossBar bar : teamBossBars.values()) {
            bar.removeAll();
        }

        teamBossBars.clear();
        playerAdvCount.clear();
        playerLastAdv.clear();
    }

    // =========================
    //  ADVANCEMENT EVENT
    // =========================

    @EventHandler
    public void onAdvancement(PlayerAdvancementDoneEvent e) {
        e.message(null);

        Player p = e.getPlayer();
        TeamData team = teamManager.getTeamByPlayer(p.getUniqueId());
        if (team == null) return;

        String done = e.getAdvancement().getKey().getKey();
        if (done.contains("recipes")) return;

        String teamName = team.getName();
        String target = currentAdvancement.get(teamName);
        if (target == null || !done.equals(target)) return;

        scoreManager.addPoints(teamName, 10);

        // Pro-Spieler-Counter
        playerAdvCount.merge(p.getUniqueId(), 1, Integer::sum);
        playerLastAdv.put(p.getUniqueId(), done);

        int i = index.getOrDefault(teamName, 0) + 1;
        index.put(teamName, i);

        String next = rotation.get(i % rotation.size());
        currentAdvancement.put(teamName, next);

        for (UUID uuid : team.getPlayers()) {
            Player pl = Bukkit.getPlayer(uuid);
            if (pl != null && pl.isOnline()) {
                pl.showTitle(Title.title(
                        Component.text("§a✔ Completed!"),
                        Component.text("§eNext: " + achievementManager.getDisplayName(next))
                ));
                pl.sendMessage("§a✔ §e" + p.getName() + " §fhat §b" + achievementManager.getDisplayName(done) + " §fabgeschlossen!");
                pl.sendMessage("§eNext task: §f" + achievementManager.getDisplayName(next));
                pl.playSound(pl.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);
            }
        }

        resetAdvancements(team.getPlayers());
    }

    private void resetAdvancements(Collection<UUID> players) {
        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) continue;

            Bukkit.getScheduler().runTask(TunierServer.getInstance(), () -> {
                Iterator<Advancement> it = Bukkit.advancementIterator();
                while (it.hasNext()) {
                    Advancement adv = it.next();
                    AdvancementProgress progress = p.getAdvancementProgress(adv);
                    for (String crit : new HashSet<>(progress.getAwardedCriteria())) {
                        progress.revokeCriteria(crit);
                    }
                }
            });
        }
    }

    // =========================
    //  RANKING
    // =========================

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

    // =========================
    //  END SCREEN
    // =========================

    private void showEndScreen() {
        Bukkit.broadcast(Component.text(" "));
        Bukkit.broadcast(Component.text("§8§m════════════════════════════════"));
        Bukkit.broadcast(Component.text("    §6§l🏆 ACHIEVEMENT BATTLE ENDE 🏆"));
        Bukkit.broadcast(Component.text("§8§m════════════════════════════════"));

        // 1. TEAM RANKING
        Bukkit.broadcast(Component.text(" "));
        Bukkit.broadcast(Component.text("§e§l📊 Team Ranking:"));
        List<TeamData> teams = getRanking();
        int rank = 1;
        for (TeamData team : teams) {
            int points = scoreManager.getPoints(team.getName());
            int completed = points / 10;
            String mvp = getTeamMVP(team);
            String medal = switch (rank) {
                case 1 -> "§6§l🥇";
                case 2 -> "§7§l🥈";
                case 3 -> "§c§l🥉";
                default -> "§8  #" + rank;
            };
            String mvpSuffix = mvp.isEmpty() ? "" : " §8| §aMVP: §f" + mvp;
            Bukkit.broadcast(Component.text(medal + " §f" + team.getName()
                    + " §8- §a" + completed + " Advancements §8(" + points + " Pkt)" + mvpSuffix));
            rank++;
        }

        // 2. TOP ACHIEVER
        Bukkit.broadcast(Component.text(" "));
        Bukkit.broadcast(Component.text("§a§l🎖 Top Achiever:"));
        List<Map.Entry<UUID, Integer>> sorted = new ArrayList<>(playerAdvCount.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        int shown = 0;
        for (Map.Entry<UUID, Integer> entry : sorted) {
            if (shown >= 5) break;
            if (entry.getValue() == 0) continue;
            Player p    = Bukkit.getPlayer(entry.getKey());
            String name = p != null ? p.getName() : "Unbekannt";
            int count   = entry.getValue();
            TeamData t  = teamManager.getTeamByPlayer(entry.getKey());
            String tTag = t != null ? " §8[" + t.getName() + "§8]" : "";
            String advMedal = switch (shown) {
                case 0 -> "§6§l🥇";
                case 1 -> "§7§l🥈";
                case 2 -> "§c§l🥉";
                default -> "  §8" + (shown + 1) + ".";
            };
            Bukkit.broadcast(Component.text(advMedal + " §f" + name + tTag + " §8| §a" + count + " Advancements"));
            shown++;
        }
        if (shown == 0) Bukkit.broadcast(Component.text("§7  Niemand hat ein Advancement abgeschlossen."));

        // 3. ALLE SPIELER
        Bukkit.broadcast(Component.text(" "));
        Bukkit.broadcast(Component.text("§b§l📋 Alle Spieler:"));
        List<UUID> allPlayers = new ArrayList<>(playerAdvCount.keySet());
        allPlayers.sort((a, b) -> Integer.compare(
                playerAdvCount.getOrDefault(b, 0),
                playerAdvCount.getOrDefault(a, 0)
        ));
        for (UUID uuid : allPlayers) {
            Player p    = Bukkit.getPlayer(uuid);
            String name = p != null ? p.getName() : "Unbekannt";
            int count   = playerAdvCount.getOrDefault(uuid, 0);
            TeamData t  = teamManager.getTeamByPlayer(uuid);
            String tTag = t != null ? " §8[" + t.getName() + "§8]" : "";
            Bukkit.broadcast(Component.text("§7➤ §f" + name + tTag + " §8| §a" + count + " Advancements"));
        }

        Bukkit.broadcast(Component.text(" "));
        Bukkit.broadcast(Component.text("§8§m════════════════════════════════"));
    }

    private String getTeamMVP(TeamData team) {
        String best = "";
        int bestCount = 0;
        for (UUID uuid : team.getPlayers()) {
            int count = playerAdvCount.getOrDefault(uuid, 0);
            if (count > bestCount) {
                bestCount = count;
                Player p = Bukkit.getPlayer(uuid);
                best = p != null ? p.getName() : "";
            }
        }
        return best;
    }

    // =========================
    //  MISC
    // =========================

    public String getCurrentAdvancement(String teamName) {
        return currentAdvancement.get(teamName);
    }

    @Override
    public void handleEvent(Event event) {}

    public boolean skip(String teamName) {
        int left = skips.getOrDefault(teamName, 0);
        if (left <= 0) return false;

        skips.put(teamName, left - 1);

        int i = index.getOrDefault(teamName, 0) + 1;
        index.put(teamName, i);

        String next = rotation.get(i % rotation.size());
        currentAdvancement.put(teamName, next);

        TeamData team = teamManager.getTeam(teamName);
        if (team == null) return false;

        for (UUID uuid : team.getPlayers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.sendMessage("§cSkipped! (" + (left - 1) + " left)");
                p.sendMessage("§eNext: §f" + achievementManager.getDisplayName(next));
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.8f);
            }
        }

        resetAdvancements(team.getPlayers());
        return true;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        TeamData team = teamManager.getTeamByPlayer(p.getUniqueId());
        if (team == null) return;

        BossBar bar = teamBossBars.get(team.getName());
        if (bar == null) return;

        bar.addPlayer(p);
    }
}