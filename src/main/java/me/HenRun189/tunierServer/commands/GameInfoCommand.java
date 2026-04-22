package me.HenRun189.tunierServer.commands;

import org.bukkit.command.*;

import java.util.List;
import java.util.Arrays;
import java.util.Collections;
import java.util.ArrayList;

public class GameInfoCommand implements CommandExecutor, TabCompleter{

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (args.length == 0) {
            sender.sendMessage("§cUsage: /gameinfo <mode>");
            return true;
        }

        String mode = args[0].toLowerCase();

        switch (mode) {

            case "achievement" -> {
                sender.sendMessage(" ");
                sender.sendMessage("§8§m-----------------------------");
                sender.sendMessage("§6§lAchievement Battle");

                sender.sendMessage("§7➤ Erfülle das vorgegebene Achievement");
                sender.sendMessage("§7➤ Jedes Achievement gibt §a+10 Punkte");

                sender.sendMessage("§7➤ Alle Teams haben die gleiche Reihenfolge");
                sender.sendMessage("§7➤ Eigene Welten pro Team, aber gleicher Seed");

                sender.sendMessage("§7➤ §c3x Skip§7 mit §e/skip");

                sender.sendMessage("§8┃ §e/help §7für Achievement Infos");
                sender.sendMessage("§8┃ §e/bp §7für Team-Backpack");
            }

            case "pvp" -> {
                sender.sendMessage("§6§lPvP");
                sender.sendMessage("§7Besiegt Gegner für Punkte.");
                sender.sendMessage("§7Kills = §aPunkte");
                sender.sendMessage("§7Letztes Team gewinnt!");
            }

            case "jumpandrun" -> {
                sender.sendMessage("§6§lJump and Run");
                sender.sendMessage("§7Schafft Parkour Maps.");
                sender.sendMessage("§7Je schneller desto mehr Punkte.");
            }

            case "itemcollector" -> {
                sender.sendMessage("§6§lItem Collector");
                sender.sendMessage("§7Sammelt bestimmte Items.");
                sender.sendMessage("§7Items geben Punkte.");
            }

            default -> sender.sendMessage("§cUnbekannter Modus!");
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {

        if (args.length == 1) {
            List<String> list = Arrays.asList("achievement", "pvp", "jumpandrun", "itemcollector");

            List<String> result = new ArrayList<>();

            for (String s : list) {
                if (s.toLowerCase().startsWith(args[0].toLowerCase())) {
                    result.add(s);
                }
            }

            return result;
        }

        return new ArrayList<>();
    }
}