package me.HenRun189.tunierServer.commands;

import me.HenRun189.tunierServer.TunierServer;
import me.HenRun189.tunierServer.game.GameManager;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;

import org.bukkit.entity.Player;

public class ResumeCommand implements CommandExecutor {

    private final GameManager gameManager;
    private int taskId = -1;

    public ResumeCommand(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        // ❌ Kein OP
        if (!sender.isOp()) {
            sender.sendMessage("§cKeine Rechte!");
            return true;
        }

        // ❌ Kein Spiel pausiert
        if (!gameManager.isPaused()) {
            sender.sendMessage("§cGame ist nicht pausiert!");
            return true;
        }

        // ❌ Schon Countdown läuft verhindern
        if (taskId != -1) {
            sender.sendMessage("§cResume läuft bereits!");
            return true;
        }

        // ▶️ Countdown starten
        taskId = Bukkit.getScheduler().runTaskTimer(TunierServer.getInstance(), new Runnable() {

            int countdown = 5;

            @Override
            public void run() {

                // 🟢 GO
                if (countdown == 0) {

                    gameManager.setPaused(false);

                    Bukkit.broadcast(Component.text("§a▶ Game geht weiter!"));

                    // 🧍 Spieler Title
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.showTitle(Title.title(
                                Component.text("§aGO!"),
                                Component.text("")
                        ));
                    }

                    // 🐄 MOBS wieder aktivieren
                    for (World world : Bukkit.getWorlds()) {
                        for (Entity entity : world.getEntities()) {
                            if (entity instanceof LivingEntity living) {
                                living.setAI(true);
                            }
                        }
                    }

                    Bukkit.getScheduler().cancelTask(taskId);
                    taskId = -1;
                    return;
                }

                // ⏳ Countdown Anzeige
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.showTitle(Title.title(
                            Component.text("§e" + countdown),
                            Component.text("§7Weiter geht's...")
                    ));
                }

                countdown--;
            }

        }, 0, 20).getTaskId();

        return true;
    }
}


//GPT-13