package me.HenRun189.tunierServer.commands;

import me.HenRun189.tunierServer.TunierServer;
import me.HenRun189.tunierServer.score.ScoreManager;
import me.HenRun189.tunierServer.team.TeamData;
import me.HenRun189.tunierServer.team.TeamManager;

import net.kyori.adventure.text.Component;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * /final              -> Top 2 Teams nach Total-Punkten
 * /final <a> <b>      -> manuell festlegen
 */
public class FinalCommand implements CommandExecutor, TabCompleter {

    private final TeamManager  teamManager;
    private final ScoreManager scoreManager;

    public FinalCommand(TeamManager teamManager, ScoreManager scoreManager) {
        this.teamManager  = teamManager;
        this.scoreManager = scoreManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!sender.hasPermission("tunier.admin")) {
            sender.sendMessage(Component.text("§cKeine Berechtigung."));
            return true;
        }

        TeamData a;
        TeamData b;

        if (args.length == 0) {
            // Top 2 nach Total-Punkten
            List<TeamData> sorted = new ArrayList<>(teamManager.getTeams().values());
            sorted.sort(Comparator.comparingInt(
                    (TeamData t) -> scoreManager.getTotalPoints(t.getName())
            ).reversed());

            if (sorted.size() < 2) {
                sender.sendMessage(Component.text("§cEs gibt weniger als 2 Teams!"));
                return true;
            }

            a = sorted.get(0);
            b = sorted.get(1);

            sender.sendMessage(Component.text("§a[Final] §7Top 2 ermittelt:"));
            sender.sendMessage(Component.text("§6#1 §e" + a.getName()
                    + " §8(" + scoreManager.getTotalPoints(a.getName()) + " Pkt)"));
            sender.sendMessage(Component.text("§7#2 §e" + b.getName()
                    + " §8(" + scoreManager.getTotalPoints(b.getName()) + " Pkt)"));

        } else if (args.length == 2) {
            a = teamManager.getTeam(args[0]);
            b = teamManager.getTeam(args[1]);

            if (a == null) {
                sender.sendMessage(Component.text("§cTeam nicht gefunden: " + args[0]));
                return true;
            }
            if (b == null) {
                sender.sendMessage(Component.text("§cTeam nicht gefunden: " + args[1]));
                return true;
            }
            if (a.getName().equals(b.getName())) {
                sender.sendMessage(Component.text("§cBeide Teams müssen unterschiedlich sein!"));
                return true;
            }

        } else {
            sender.sendMessage(Component.text("§cUsage: /final [<teamA> <teamB>]"));
            return true;
        }

        // Final starten
        TunierServer.getInstance().getGameManager().startFinal(a, b);

        sender.sendMessage(Component.text("§a[Final] §7Finale gestartet: §e"
                + a.getName() + " §7vs §e" + b.getName()));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1 || args.length == 2) {
            for (String name : teamManager.getTeams().keySet()) {
                if (name.toLowerCase().startsWith(args[args.length - 1].toLowerCase())) {
                    out.add(name);
                }
            }
        }
        return out;
    }
}