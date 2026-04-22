package me.HenRun189.tunierServer.commands;

import me.HenRun189.tunierServer.score.ScoreManager;
import me.HenRun189.tunierServer.team.TeamManager;
import me.HenRun189.tunierServer.team.TeamData;

import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;

public class InfoCommand implements CommandExecutor {

    private final ScoreManager scoreManager;
    private final TeamManager teamManager;

    public InfoCommand(ScoreManager scoreManager, TeamManager teamManager) {
        this.scoreManager = scoreManager;
        this.teamManager = teamManager;
    }


    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        sender.sendMessage("§6§lTurnier Übersicht:");

        Map<String, Map<String, Integer>> history = scoreManager.getHistoryPoints();

        // Header (fest gebaut)
        sender.sendMessage("§7Team        §eAch  PvP  Item JnR  §aTotal");

        for (TeamData team : teamManager.getTeams().values()) {

            String name = team.getName();

            // ✂️ max Länge 10
            if (name.length() > 10) {
                name = name.substring(0, 9) + "…";
            }

            // 👉 feste spaces (WICHTIG)
            String paddedName = name + " ".repeat(12 - name.length());

            Map<String, Integer> data = history.getOrDefault(team.getName(), new HashMap<>());

            int ach = data.getOrDefault("Achievement Battle", 0);
            int pvp = data.getOrDefault("PvP", 0);
            int item = data.getOrDefault("Item Collector", 0);
            int jnr = data.getOrDefault("Jump and Run", 0);
            int total = scoreManager.getTotalPoints(team.getName());

            String line =
                    team.getColor() + paddedName +
                            "§e" + fix(ach) +
                            fix(pvp) +
                            fix(item) +
                            fix(jnr) +
                            "§a" + fix(total);

            sender.sendMessage(line);
        }

        return true;
    }


    private String fix(int num) {
        return String.format("%-4d", num);
    }

    private String pad(String text, int length) {
        return String.format("%-" + length + "s", text);
    }

    private String padNum(int num) {
        return String.format("%3d", num); // feste Breite!
    }

}