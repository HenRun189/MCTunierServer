package me.HenRun189.tunierServer.commands;

import me.HenRun189.tunierServer.game.GameManager;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

public class AllowMoveCommand implements CommandExecutor {

    private final GameManager gameManager;

    public AllowMoveCommand(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (!sender.isOp()) {
            sender.sendMessage("§cKeine Rechte!");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage("§cUsage: /allowmove <player> <true/false>");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage("§cSpieler nicht gefunden!");
            return true;
        }

        if (!args[1].equalsIgnoreCase("true") && !args[1].equalsIgnoreCase("false")) {
            sender.sendMessage("§cBitte true oder false angeben!");
            return true;
        }

        boolean allow = args[1].equalsIgnoreCase("true");

        if (allow) {
            gameManager.unfreezePlayer(target);
            sender.sendMessage("§a" + target.getName() + " darf sich bewegen");
        } else {
            gameManager.freezePlayer(target);
            sender.sendMessage("§c" + target.getName() + " wurde gefreezed");
        }

        return true;
    }
}