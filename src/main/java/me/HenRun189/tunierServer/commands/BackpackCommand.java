package me.HenRun189.tunierServer.commands;

import me.HenRun189.tunierServer.backpack.BackpackManager;
import me.HenRun189.tunierServer.game.GameManager;
import me.HenRun189.tunierServer.team.TeamManager;
import me.HenRun189.tunierServer.team.TeamData;

import org.bukkit.command.*;
import org.bukkit.entity.Player;

public class BackpackCommand implements CommandExecutor {

    private final BackpackManager backpackManager;
    private final TeamManager teamManager;
    private final GameManager gameManager;

    public BackpackCommand(BackpackManager backpackManager, TeamManager teamManager, GameManager gameManager) {
        this.backpackManager = backpackManager;
        this.teamManager = teamManager;
        this.gameManager = gameManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (!(sender instanceof Player p)) {
            sender.sendMessage("§cNur Spieler!");
            return true;
        }

        // ❗ nur während Game
        if (!gameManager.isGameActive() || gameManager.isPaused()) {
            p.sendMessage("§cBackpack aktuell nicht verfügbar!");
            return true;
        }

        TeamData team = teamManager.getTeamByPlayer(p.getUniqueId());

        if (team == null) {
            p.sendMessage("§cDu bist in keinem Team!");
            return true;
        }

        p.openInventory(backpackManager.getBackpack(team.getName()));
        return true;
    }

}