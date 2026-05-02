package me.HenRun189.tunierServer.commands;

import me.HenRun189.tunierServer.score.ScoreManager;
import me.HenRun189.tunierServer.team.TeamManager;
import me.HenRun189.tunierServer.team.TeamData;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.HashMap;
import java.util.Map;

public class InfoCommand implements CommandExecutor {

    private final ScoreManager scoreManager;
    private final TeamManager teamManager;

    public InfoCommand(ScoreManager scoreManager, TeamManager teamManager) {
        this.scoreManager = scoreManager;
        this.teamManager = teamManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        sender.sendMessage(" ");
        sender.sendMessage("§8§m------------------------------------------------");
        sender.sendMessage("§6§lTurnier Übersicht");
        sender.sendMessage("§8§m------------------------------------------------");
        sender.sendMessage("§7Team         §eAch  §cPvP  §aItem  §6JnR  §bSpl  §fTotal");

        Map<String, Map<String, Integer>> history = scoreManager.getHistoryPoints();

        for (TeamData team : teamManager.getTeams().values()) {
            String originalName = team.getName();
            String shortName = shorten(originalName, 12);
            String paddedName = String.format("%-12s", shortName);

            Map<String, Integer> data = history.getOrDefault(originalName, new HashMap<>());

            int ach   = data.getOrDefault("Achievement Battle", 0);
            int pvp   = data.getOrDefault("PvP", 0);
            int item  = data.getOrDefault("Item Collector", 0);
            int jnr   = data.getOrDefault("Jump and Run", 0);
            int spl   = data.getOrDefault("Spleef Windcharge", 0);
            int total = scoreManager.getTotalPoints(originalName);

            String line =
                    team.getColor() + paddedName +
                            "§e" + num(ach) +
                            "§c" + num(pvp) +
                            "§a" + num(item) +
                            "§6" + num(jnr) +
                            "§b" + num(spl) +
                            "§f" + num(total);

            sender.sendMessage(line);
        }

        sender.sendMessage("§8§m------------------------------------------------");
        return true;
    }

    private String num(int value) {
        return String.format("%4d ", value);
    }

    private String shorten(String text, int maxLen) {
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen - 1) + "…";
    }
}