package me.HenRun189.tunierServer.commands;

import me.HenRun189.tunierServer.game.GameManager;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;

public class PauseCommand implements CommandExecutor {

    private final GameManager gameManager;

    public PauseCommand(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        // ❌ Nur Spieler
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§cNur Spieler!");
            return true;
        }

        // ❌ Kein OP
        if (!p.isOp()) {
            p.sendMessage("§cKeine Rechte!");
            return true;
        }

        // ❌ Kein Spiel läuft
        if (!gameManager.isGameRunning()) {
            p.sendMessage("§cKein Spiel läuft!");
            return true;
        }

        // ❌ Schon pausiert
        if (gameManager.isPaused()) {
            p.sendMessage("§cGame ist schon pausiert!");
            return true;
        }

        // ✅ Pause aktivieren
        gameManager.setPaused(true);

        // 📢 Nachricht
        Bukkit.broadcast(Component.text("§c⏸ Game pausiert!"));

        // 🧍 Spieler Anzeige
        for (Player all : Bukkit.getOnlinePlayers()) {
            all.showTitle(Title.title(
                    Component.text("§cPAUSIERT"),
                    Component.text("§7Warte...")
            ));
        }

        // 🐄 ALLE MOBS FREEZEN (SEHR WICHTIG)
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof LivingEntity living) {
                    living.setAI(false);
                }
            }
        }

        return true;
    }
}

//GPT-13