package me.HenRun189.tunierServer.commands;

import me.HenRun189.tunierServer.game.GameManager;
import me.HenRun189.tunierServer.game.modes.AchievementMode;
import me.HenRun189.tunierServer.team.TeamManager;
import me.HenRun189.tunierServer.team.TeamData;

import org.bukkit.command.*;
import org.bukkit.entity.Player;

public class SkipCommand implements CommandExecutor {

    private final GameManager manager;
    private final TeamManager teamManager;

    public SkipCommand(GameManager manager, TeamManager teamManager) {
        this.manager = manager;
        this.teamManager = teamManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (!(sender instanceof Player p)) {
            sender.sendMessage("§cNur Spieler können das nutzen!");
            return true;
        }

        // ❗ FIX: kein Game aktiv
        if (manager.getCurrentMode() == null) {
            p.sendMessage("§cEs läuft kein Spiel!");
            return true;
        }

        // ❗ FIX: falscher Modus
        if (!(manager.getCurrentMode() instanceof AchievementMode mode)) {
            p.sendMessage("§cDieser Command ist nur im Achievement-Modus verfügbar!");
            return true;
        }

        TeamData team = teamManager.getTeamByPlayer(p.getUniqueId());

        if (team == null) {
            p.sendMessage("§cDu bist in keinem Team!");
            return true;
        }

        boolean success = mode.skip(team.getName());

        if (success) {
            //p.sendMessage("§eAchievement übersprungen!");
        } else {
            p.sendMessage("§cKein Skip mehr verfügbar!");
        }

        return true;
    }
}

//GPT 12:30