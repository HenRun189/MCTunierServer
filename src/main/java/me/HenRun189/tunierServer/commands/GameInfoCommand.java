package me.HenRun189.tunierServer.commands;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.*;

import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;

public class GameInfoCommand implements CommandExecutor, TabCompleter{

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (args.length == 0) {
            sender.sendMessage("§cUsage: /gameinfo <mode>");
            return true;
        }

        String mode = args[0].toLowerCase();

        switch (mode.toLowerCase()) {
            case "achievement" -> {
                Bukkit.broadcast(Component.text(" "));
                Bukkit.broadcast(Component.text("§8§m-----------------------------"));
                Bukkit.broadcast(Component.text("§6§lAchievement Battle"));
                Bukkit.broadcast(Component.text("§7➤ Erfülle das vorgegebene Achievement"));
                Bukkit.broadcast(Component.text("§7➤ Jedes Achievement gibt §a+10 Punkte"));
                //Bukkit.broadcast(Component.text("§7➤ Alle Teams haben die gleiche Reihenfolge"));
                //Bukkit.broadcast(Component.text("§7➤ Eigene Welten pro Team, aber gleicher Seed"));
                Bukkit.broadcast(Component.text("§7➤ §c3x Skip§7 mit §e/skip"));
                Bukkit.broadcast(Component.text("§8┃ §e/help §7für Achievement Infos"));
                Bukkit.broadcast(Component.text("§8┃ §e/bp §7für Team-Backpack"));
                Bukkit.broadcast(Component.text("§8§m-----------------------------"));
            }
            case "pvp"          -> {
                Bukkit.broadcast(Component.text(" "));
                Bukkit.broadcast(Component.text("§8§m-----------------------------"));
                Bukkit.broadcast(Component.text("§6§lHunger Game"));
                Bukkit.broadcast(Component.text("§7➤ Pro kill gibt es §a+20Punkte"));
                Bukkit.broadcast(Component.text("§7➤ Desto länger man überlebt des to mehr Punkte"));
                Bukkit.broadcast(Component.text("§7➤ In Truhen ist Loot. Blöcke abbauen geht nicht"));
                Bukkit.broadcast(Component.text("§7➤ Worldboarder verkleinert sich"));
                Bukkit.broadcast(Component.text("§8§m-----------------------------"));
            }
            case "jumpandrun" -> {
                Bukkit.broadcast(Component.text(" "));
                Bukkit.broadcast(Component.text("§8§m-----------------------------"));
                Bukkit.broadcast(Component.text("§6§lJump and Run"));
                Bukkit.broadcast(Component.text("§7➤ Erreiche das Ziel so schnell wie möglich"));
                Bukkit.broadcast(Component.text("§7➤ §6Goldblöcke §7markieren Checkpoints"));
                Bukkit.broadcast(Component.text("§7➤ Jeder Checkpoint bringt deinem Team §a+5 Punkte"));
                Bukkit.broadcast(Component.text("§7➤ Die ersten §e3 Spieler §7im Ziel erhalten Bonuspunkte"));
                Bukkit.broadcast(Component.text("§7➤ Nach §e20 Min §7endet das Spiel, sobald min. ein Spieler im Ziel ist"));
                Bukkit.broadcast(Component.text("§8┃ §7Mit dem §eFeuerwerk §7kannst du die Spielersichtbarkeit umschalten"));
                Bukkit.broadcast(Component.text("§8§m-----------------------------"));
            }
            case "itemcollector"-> {
                Bukkit.broadcast(Component.text(" "));
                Bukkit.broadcast(Component.text("§8§m-----------------------------"));
                Bukkit.broadcast(Component.text("§6§Item Sammler"));
                Bukkit.broadcast(Component.text("§7➤ Sammle und Crafte so viele verschiedene Items wie möglich"));
                Bukkit.broadcast(Component.text("§7➤ Jedes neue Item gibt §a+1 Punkt"));
                Bukkit.broadcast(Component.text("§8┃ §e/bp §7für Team-Backpack"));
                Bukkit.broadcast(Component.text("§8§m-----------------------------"));
            }
            case "spleefwindcharge" -> {
                Bukkit.broadcast(Component.text(" "));
                Bukkit.broadcast(Component.text("§8§m-----------------------------"));
                Bukkit.broadcast(Component.text("§b§lSpleef Windcharge"));
                Bukkit.broadcast(Component.text("§7➤ Desto länger man auf den Platformen ist desto mehr punkte gibt es"));
                Bukkit.broadcast(Component.text("§7➤ Trapdoors löschen sich nach zeit"));
                Bukkit.broadcast(Component.text("§7➤ Nutze §fWindcharges §7um Gegner runterzuschießen"));
                Bukkit.broadcast(Component.text("§7➤ Jede Sekunde §7gibt's einen neuen Windcharge"));
                Bukkit.broadcast(Component.text("§8§m-----------------------------"));
            }
            default -> sender.sendMessage("§cUnbekannter Modus!");
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {

        if (args.length == 1) {
            List<String> list = Arrays.asList("achievement", "pvp", "jumpandrun", "itemcollector", "spleefwindcharge");

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