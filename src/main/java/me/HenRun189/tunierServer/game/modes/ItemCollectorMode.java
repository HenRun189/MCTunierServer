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

import java.util.*;

public class ItemCollectorMode implements GameMode, Listener {

    private final ScoreManager scoreManager;
    private final TeamManager teamManager;

    private final Map<String, Set<Material>> teamItems = new HashMap<>();

    private BossBar bossBar;
    private int time = 900;
    private int taskId = -1;

    public ItemCollectorMode(TeamManager teamManager, ScoreManager scoreManager) {
        this.teamManager = teamManager;
        this.scoreManager = scoreManager;
    }

    @Override
    public void start() {

        stop();

        teamItems.clear();
        time = 900;

        bossBar = Bukkit.createBossBar("§bItem Collector", BarColor.BLUE, BarStyle.SOLID);

        for (Player p : Bukkit.getOnlinePlayers()) {
            bossBar.addPlayer(p);
        }

        Bukkit.getPluginManager().registerEvents(this, TunierServer.getInstance());

        taskId = Bukkit.getScheduler().runTaskTimer(TunierServer.getInstance(), () -> {

            time--;

            int minutes = time / 60;
            int seconds = time % 60;
            String timeString = String.format("%02d:%02d", minutes, seconds);

            bossBar.setTitle("§bItems sammeln §7| §e" + timeString);
            bossBar.setProgress(Math.max(0, time / 900.0));

            if (time <= 0) stop();

        }, 0, 20).getTaskId();

        Bukkit.broadcastMessage("§a§lItem Collector gestartet!");
    }

    @Override
    public void stop() {

        HandlerList.unregisterAll(this);

        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }

        if (bossBar != null) {
            bossBar.removeAll();
        }

        Bukkit.broadcastMessage("§cGame beendet!");
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
    public void handleEvent(Event event) {}
}