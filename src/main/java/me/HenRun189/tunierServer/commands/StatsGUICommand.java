package me.HenRun189.tunierServer.commands;

import me.HenRun189.tunierServer.score.ScoreManager;
import me.HenRun189.tunierServer.team.TeamManager;
import me.HenRun189.tunierServer.team.TeamData;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class StatsGUICommand implements CommandExecutor {

    private final ScoreManager scoreManager;
    private final TeamManager teamManager;

    public StatsGUICommand(ScoreManager scoreManager, TeamManager teamManager) {
        this.scoreManager = scoreManager;
        this.teamManager = teamManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (!(sender instanceof Player p)) {
            sender.sendMessage("Nur Spieler!");
            return true;
        }

        Inventory inv = Bukkit.createInventory(null, 27, "§6§lTurnier Stats");

        Map<String, Map<String, Integer>> history = scoreManager.getHistoryPoints();

        int slot = 10;

        for (TeamData team : teamManager.getTeams().values()) {

            Map<String, Integer> data = history.getOrDefault(team.getName(), new HashMap<>());

            int ach = data.getOrDefault("Achievement Battle", 0);
            int pvp = data.getOrDefault("PvP", 0);
            int item = data.getOrDefault("Item Collector", 0);
            int jnr = data.getOrDefault("Jump and Run", 0);
            int total = scoreManager.getTotalPoints(team.getName());

            ItemStack itemStack = new ItemStack(Material.PLAYER_HEAD);
            ItemMeta meta = itemStack.getItemMeta();

            meta.setDisplayName(team.getColor() + team.getName());

            List<String> lore = new ArrayList<>();
            lore.add("§7Achievement: §e" + ach);
            lore.add("§7PvP: §c" + pvp);
            lore.add("§7Item: §b" + item);
            lore.add("§7Jump&Run: §d" + jnr);
            lore.add("§8----------------");
            lore.add("§aTotal: §a" + total);

            meta.setLore(lore);
            itemStack.setItemMeta(meta);

            inv.setItem(slot, itemStack);
            slot++;
        }

        p.openInventory(inv);
        return true;
    }
}