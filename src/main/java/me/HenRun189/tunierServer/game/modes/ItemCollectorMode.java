package me.HenRun189.tunierServer.game.modes;

import me.HenRun189.tunierServer.game.GameMode;
import me.HenRun189.tunierServer.score.ScoreManager;
import me.HenRun189.tunierServer.team.TeamManager;
import me.HenRun189.tunierServer.TunierServer;

import org.bukkit.*;
import org.bukkit.boss.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;

import me.HenRun189.tunierServer.team.TeamData;

import java.util.*;

public class ItemCollectorMode extends AbstractGameMode implements Listener {

    private final ScoreManager scoreManager;
    private final TeamManager teamManager;

    private final Map<String, Set<Material>> teamItems = new HashMap<>();

    private BossBar bossBar;


    public ItemCollectorMode(TeamManager teamManager, ScoreManager scoreManager) {
        super(1200, teamManager); // 🔥 20 Minuten
        this.teamManager = teamManager;
        this.scoreManager = scoreManager;
    }

    @Override
    public void start() {

        teamItems.clear();

        bossBar = Bukkit.createBossBar("§bItem Race", BarColor.BLUE, BarStyle.SOLID);

        for (Player p : Bukkit.getOnlinePlayers()) {
            bossBar.addPlayer(p);
        }

        Bukkit.getPluginManager().registerEvents(this, TunierServer.getInstance());

        super.start(); // 🔥 GANZ WICHTIG
    }

    @Override
    protected void onGameTick() {

        double maxTime = 1200.0;

        int minutes = time / 60;
        int seconds = time % 60;

        String timeString = String.format("%02d:%02d", minutes, seconds);

        bossBar.setTitle("§bItem Race §7| §e" + timeString);
        bossBar.setProgress(Math.max(0, time / maxTime));
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        handleItem(p, e.getItem().getItemStack().getType());
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getCurrentItem() == null) return;

        handleItem(p, e.getCurrentItem().getType());
    }

    private void handleItem(Player player, Material mat) {

        if (!mat.isItem() || mat == Material.AIR) return;

        var team = teamManager.getTeamByPlayer(player.getUniqueId());
        if (team == null) return;

        String teamName = team.getName();

        teamItems.putIfAbsent(teamName, new HashSet<>());
        Set<Material> items = teamItems.get(teamName);

        if (!items.contains(mat)) {

            items.add(mat);

            // 🔥 Punkte = neue Items
            scoreManager.addPoints(teamName, 1);

            Bukkit.broadcastMessage("§a+1 §8| §e" + player.getName() + " §ffand §b" + mat.name());

            for (Player p : Bukkit.getOnlinePlayers()) {
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.5f);
            }
        }
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
        super.stop(); // 🔥 wichtig für Ranking + Titel

        HandlerList.unregisterAll(this);

        if (bossBar != null) {
            bossBar.removeAll();
        }
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
}