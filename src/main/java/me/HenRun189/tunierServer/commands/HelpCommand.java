package me.HenRun189.tunierServer.commands;

import me.HenRun189.tunierServer.game.GameManager;
import me.HenRun189.tunierServer.team.TeamData;

import org.bukkit.command.*;
import org.bukkit.entity.Player;

public class HelpCommand implements CommandExecutor {

    private final GameManager manager;

    public HelpCommand(GameManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (!(sender instanceof Player p)) {
            sender.sendMessage("Nur Spieler!");
            return true;
        }

        // ❗ nur wenn Game läuft
        if (!manager.isGameRunning()) {
            p.sendMessage("§7Kein aktives Spiel.");
            return true;
        }

        String adv = manager.getCurrentAchievement(p);

        if (adv == null) {
            p.sendMessage("§7Kein Ziel verfügbar.");
            return true;
        }

        String name = manager.getAchievementManager().getDisplayName(adv);
        String desc = manager.getAchievementManager().getDescription(adv);

        p.sendMessage("§6§lDein aktuelles Ziel:");
        p.sendMessage("§e" + name);
        p.sendMessage("§7" + desc);

        return true;
    }
}

//hinzugefügt /help mit gPT 12:45