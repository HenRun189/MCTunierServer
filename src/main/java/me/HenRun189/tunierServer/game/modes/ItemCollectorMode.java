package me.HenRun189.tunierServer.game.modes;

import me.HenRun189.tunierServer.score.ScoreManager;
import me.HenRun189.tunierServer.team.TeamManager;
import me.HenRun189.tunierServer.TunierServer;

import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.boss.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.scoreboard.*;

import me.HenRun189.tunierServer.team.TeamData;

import java.util.*;

public class ItemCollectorMode extends AbstractGameMode implements Listener {

    private final ScoreManager scoreManager;
    private final TeamManager teamManager;

    // Items pro Team (kein Duplikat zaehlen)
    private final Map<String, Set<Material>> teamItems = new HashMap<>();

    // Wie viele neue Items hat jeder Spieler persoenlich gefunden
    private final Map<UUID, Integer> playerItemCount = new HashMap<>();

    private BossBar bossBar;
    private Scoreboard scoreboard;
    private Objective objective;

    public ItemCollectorMode(TeamManager teamManager, ScoreManager scoreManager) {
        super(1200, teamManager);
        this.teamManager = teamManager;
        this.scoreManager = scoreManager;
    }

    @Override
    public void start() {
        teamItems.clear();
        playerItemCount.clear();

        bossBar = Bukkit.createBossBar("Item Race", BarColor.BLUE, BarStyle.SOLID);

        setupScoreboard();

        for (Player p : Bukkit.getOnlinePlayers()) {
            bossBar.addPlayer(p);
            p.setScoreboard(scoreboard);
        }

        Bukkit.getPluginManager().registerEvents(this, TunierServer.getInstance());

        super.start();
    }

    private void setupScoreboard() {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        scoreboard = manager.getNewScoreboard();
        objective = scoreboard.registerNewObjective("itemrace", "dummy", "§bItem Race");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        updateScoreboard();
    }

    private void updateScoreboard() {
        if (objective == null) return;

        for (String entry : scoreboard.getEntries()) {
            scoreboard.resetScores(entry);
        }

        List<TeamData> teams = new ArrayList<>(teamManager.getTeams().values());
        teams.sort((a, b) -> Integer.compare(
                teamItems.getOrDefault(b.getName(), Collections.emptySet()).size(),
                teamItems.getOrDefault(a.getName(), Collections.emptySet()).size()
        ));

        int line = teams.size();
        for (TeamData team : teams) {
            int collected = teamItems.getOrDefault(team.getName(), Collections.emptySet()).size();
            String entry = "§e" + team.getName() + " §8| §a" + collected + " Items";
            objective.getScore(entry).setScore(line--);
        }
    }

    @Override
    protected void onGameTick() {
        // nothing per tick needed
    }

    @Override
    protected void onSecond() {
        double maxTime = 1200.0;
        int minutes = time / 60;
        int seconds = time % 60;
        String timeString = String.format("%02d:%02d", minutes, seconds);
        bossBar.setTitle("§bItem Race §7| §e" + timeString);
        bossBar.setProgress(Math.max(0, (double) time / maxTime));
        updateScoreboard();
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        handleItem(p, e.getItem().getItemStack().getType());
    }

    private void handleItem(Player player, Material mat) {
        if (!mat.isItem() || mat == Material.AIR) return;

        var team = teamManager.getTeamByPlayer(player.getUniqueId());
        if (team == null) return;

        String teamName = team.getName();

        teamItems.putIfAbsent(teamName, new HashSet<>());
        Set<Material> items = teamItems.get(teamName);

        if (items.contains(mat)) return;

        items.add(mat);
        scoreManager.addPoints(teamName, 1);

        playerItemCount.merge(player.getUniqueId(), 1, Integer::sum);

        String message = "§a+1 §8| §e" + player.getName() + " §ffand §b" + formatMaterialName(mat);

        for (Player p : Bukkit.getOnlinePlayers()) {
            TeamData playerTeam = teamManager.getTeamByPlayer(p.getUniqueId());
            if (playerTeam != null && playerTeam.getName().equals(teamName)) {
                p.sendMessage(message);
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.5f);
            }
        }
    }

    private String formatMaterialName(Material mat) {
        String raw = mat.name().toLowerCase().replace("_", " ");
        StringBuilder sb = new StringBuilder();
        for (String word : raw.split(" ")) {
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1))
                        .append(" ");
            }
        }
        return sb.toString().trim();
    }

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
    public void stop() {
        showEndScreen();
        super.stop();

        HandlerList.unregisterAll(this);

        if (bossBar != null) {
            bossBar.removeAll();
        }

        if (scoreboard != null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            }
        }

        teamItems.clear();
        playerItemCount.clear();
    }

    @Override
    protected void onGameStart() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendTitle("§bItem Race", "§7Sammle Items!", 10, 60, 10);
        }
    }

    @Override
    protected int getPoints(String teamName) {
        return scoreManager.getPoints(teamName);
    }

    @Override
    public void handleEvent(Event event) {}

    // ══════════════════════════════════════════════════════════════
    //  END SCREEN
    // ══════════════════════════════════════════════════════════════

    private void showEndScreen() {
        Bukkit.broadcast(Component.text(" "));
        Bukkit.broadcast(Component.text("§8§m════════════════════════════════"));
        Bukkit.broadcast(Component.text("       §6§l🏆 ITEM RACE ENDE 🏆"));
        Bukkit.broadcast(Component.text("§8§m════════════════════════════════"));

        // 1. TEAM RANKING
        Bukkit.broadcast(Component.text(" "));
        Bukkit.broadcast(Component.text("§e§l📊 Team Ranking:"));
        List<TeamData> teams = getRanking();
        int rank = 1;
        for (TeamData team : teams) {
            int collected = teamItems.getOrDefault(team.getName(), Collections.emptySet()).size();
            String mvp = getTeamMVP(team);
            String medal = switch (rank) {
                case 1 -> "§6§l🥇";
                case 2 -> "§7§l🥈";
                case 3 -> "§c§l🥉";
                default -> "§8  #" + rank;
            };
            String mvpSuffix = mvp.isEmpty() ? "" : " §8| §aMVP: §f" + mvp;
            Bukkit.broadcast(Component.text(medal + " §f" + team.getName() + " §8- §b" + collected + " Items" + mvpSuffix));
            rank++;
        }

        // 2. TOP SAMMLER
        Bukkit.broadcast(Component.text(" "));
        Bukkit.broadcast(Component.text("§b§l🎒 Top Sammler:"));
        List<Map.Entry<UUID, Integer>> sortedCollectors = new ArrayList<>(playerItemCount.entrySet());
        sortedCollectors.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        int shown = 0;
        for (Map.Entry<UUID, Integer> entry : sortedCollectors) {
            if (shown >= 5) break;
            if (entry.getValue() == 0) continue;
            Player p    = Bukkit.getPlayer(entry.getKey());
            String name = p != null ? p.getName() : "Unbekannt";
            int count   = entry.getValue();
            TeamData t  = teamManager.getTeamByPlayer(entry.getKey());
            String tTag = t != null ? " §8[" + t.getName() + "§8]" : "";
            String itemMedal = switch (shown) {
                case 0 -> "§6§l🥇";
                case 1 -> "§7§l🥈";
                case 2 -> "§c§l🥉";
                default -> "  §8" + (shown + 1) + ".";
            };
            Bukkit.broadcast(Component.text(itemMedal + " §f" + name + tTag + " §8| §b" + count + " Items"));
            shown++;
        }
        if (shown == 0) Bukkit.broadcast(Component.text("§7  Niemand hat Items gesammelt."));

        // 3. ALLE SPIELER
        Bukkit.broadcast(Component.text(" "));
        Bukkit.broadcast(Component.text("§b§l📋 Alle Spieler:"));
        List<UUID> allPlayers = new ArrayList<>(playerItemCount.keySet());
        allPlayers.sort((a, b) -> Integer.compare(
                playerItemCount.getOrDefault(b, 0),
                playerItemCount.getOrDefault(a, 0)
        ));
        for (UUID uuid : allPlayers) {
            Player p    = Bukkit.getPlayer(uuid);
            String name = p != null ? p.getName() : "Unbekannt";
            int count   = playerItemCount.getOrDefault(uuid, 0);
            TeamData t  = teamManager.getTeamByPlayer(uuid);
            String tTag = t != null ? " §8[" + t.getName() + "§8]" : "";
            Bukkit.broadcast(Component.text("§7➤ §f" + name + tTag + " §8| §b" + count + " Items"));
        }

        Bukkit.broadcast(Component.text(" "));
        Bukkit.broadcast(Component.text("§8§m════════════════════════════════"));
    }

    private String getTeamMVP(TeamData team) {
        String best = "";
        int bestCount = 0;
        for (UUID uuid : team.getPlayers()) {
            int count = playerItemCount.getOrDefault(uuid, 0);
            if (count > bestCount) {
                bestCount = count;
                Player p = Bukkit.getPlayer(uuid);
                best = p != null ? p.getName() : "";
            }
        }
        return best;
    }
}