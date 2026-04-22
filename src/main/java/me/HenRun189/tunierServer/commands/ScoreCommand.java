package me.HenRun189.tunierServer.commands;

import me.HenRun189.tunierServer.score.ScoreManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class ScoreCommand implements CommandExecutor {

    private final ScoreManager manager;

    public ScoreCommand(ScoreManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (!sender.isOp()) {
            sender.sendMessage("§cKeine Rechte!");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage("§cUsage: /score <team> <punkte>");
            return true;
        }

        String team = args[0];

        int points;
        try {
            points = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cUngültige Zahl!");
            return true;
        }

        // 🔥 FIX: addPoints statt setPoints
        manager.addPoints(team, points);

        sender.sendMessage("§a+" + points + " Punkte für §e" + team);
        return true;
    }
}

//GPT 16:00