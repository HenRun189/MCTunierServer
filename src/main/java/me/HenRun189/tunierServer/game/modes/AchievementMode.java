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

        // BossBars
        for (TeamData team : teamManager.getTeams().values()) {

            BossBar bar = Bukkit.createBossBar("§aAchievement Battle", BarColor.GREEN, BarStyle.SOLID);

            for (UUID uuid : team.getPlayers()) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) bar.addPlayer(p);
            }

            teamBossBars.put(team.getName(), bar);
        }

        // Rotation
        List<String> pool = new ArrayList<>(AchievementManager.ADVANCEMENT_POOL);
        Collections.shuffle(pool);

        if (pool.isEmpty()) {
            pool.add("story/mine_stone");
        }

        rotation.addAll(pool);

        for (TeamData team : teamManager.getTeams().values()) {
            currentAdvancement.put(team.getName(), rotation.get(0));
            index.put(team.getName(), 0);
            skips.put(team.getName(), 3); //Anzahl an skips -> 3
        }

        Bukkit.getPluginManager().registerEvents(this, TunierServer.getInstance());

        super.start(); // 🔥 GLOBAL SYSTEM
    }

    // =========================
    // 🚀 START
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
    // 🔁 LOOP
    // =========================

    @Override
    protected void onGameTick() {
        // nichts pro Tick nötig
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
            bar.setTitle("§a" + achievementManager.getDisplayName(adv));
        }
    }

    // =========================
    // 🛑 STOP CLEANUP
    // =========================

    @Override
    public void stop() {
        super.stop(); // 🔥 GLOBAL WIN / RANKING

        HandlerList.unregisterAll(this);

        for (BossBar bar : teamBossBars.values()) {
            bar.removeAll();
        }

        teamBossBars.clear();
    }

    // =========================
    // 🏆 ADVANCEMENT
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

                pl.sendMessage("§a✔ Completed!");
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
    // 📊 GLOBAL RANKING (für Abstract)
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

    public String getCurrentAdvancement(String teamName) {
        return currentAdvancement.get(teamName);
    }

    @Override
    public void handleEvent(Event event) {
        // wird nicht benutzt
    }

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

        bar.addPlayer(p); // 🔥 DAS IST DER FIX
    }
}