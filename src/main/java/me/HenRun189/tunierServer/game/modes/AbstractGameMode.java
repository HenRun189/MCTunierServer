package me.HenRun189.tunierServer.game.modes;

import me.HenRun189.tunierServer.game.GameMode;
import me.HenRun189.tunierServer.TunierServer;
import me.HenRun189.tunierServer.team.TeamData;
import me.HenRun189.tunierServer.team.TeamManager;

import org.bukkit.*;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;

import java.util.*;

public abstract class AbstractGameMode implements GameMode {

    protected int time;
    protected int taskId = -1;
    protected TeamManager teamManager;

    public AbstractGameMode(int gameTime, TeamManager teamManager) {
        this.time = gameTime;
        this.teamManager = teamManager;
    }

    @Override
    public void start() {

        if (taskId != -1) stop();

        taskId = Bukkit.getScheduler().runTaskTimer(TunierServer.getInstance(), new Runnable() {

            boolean started = false;
            int pauseTick = 0;

            @Override
            public void run() {

                // Pause
                if (TunierServer.getInstance().getGameManager().isPaused()) {

                    pauseTick++;

                    if (pauseTick % 20 == 0) {
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            p.sendActionBar(Component.text("§c⏸ PAUSIERT"));
                        }
                    }

                    return;
                }

                // GAME START (OHNE EXTRA COUNTDOWN)
                if (!started) {
                    started = true;
                    onGameStart();
                    return;
                }

                // GAME LOOP
                time--;

                onGameTick();
                updateActionbar();

                if (time == 300 * 20) broadcast("§e5 minutes left!");
                if (time == 60 * 20) broadcast("§c1 minute left!");

                if (time <= 10 * 20 && time > 0) {
                    broadcast("§cNoch " + time + " Sekunden!");
                }

                if (time <= 0) {
                    TunierServer.getInstance().getGameManager().stopGame();
                }
            }

        }, 0, 1).getTaskId();
    }

    @Override
    public void stop() {

        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }

        List<TeamData> ranking = getRanking();

        if (!ranking.isEmpty()) {

            int bestScore = getPoints(ranking.get(0).getName());

            List<String> winners = new ArrayList<>();

            for (TeamData t : ranking) {
                if (getPoints(t.getName()) == bestScore) {
                    winners.add(t.getName());
                }
            }


            for (Player p : Bukkit.getOnlinePlayers()) {

                TeamData team = null;

                // WICHTIG: Team holen (je nach System)
                if (teamManager != null) {
                    team = teamManager.getTeamByPlayer(p.getUniqueId());
                }

                int place = -1;

                if (team != null) {
                    for (int i = 0; i < ranking.size(); i++) {
                        if (ranking.get(i).getName().equals(team.getName())) {
                            place = i + 1;
                            break;
                        }
                    }
                }

                p.showTitle(Title.title(
                        Component.text("§6Dein Platz: #" + (place == -1 ? "-" : place)),
                        Component.text(
                                winners.size() == 1
                                        ? "§7Gewinner: §e" + winners.get(0)
                                        : "§7Unentschieden: §e" + String.join(", ", winners)
                        )
                ));

                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            }

        }

        Bukkit.broadcast(Component.text("§6§lRanking :"));

        int place = 1;
        for (TeamData t : ranking) {
            Bukkit.broadcast(Component.text(
                    "§e#" + place++ + " §f" + t.getName() +
                            " §7- §a" + getPoints(t.getName())
            ));
        }

        //Bukkit.broadcast(Component.text("§cGame ended!"));
    }

    protected void broadcast(String msg) {
        Bukkit.broadcast(Component.text(msg));
    }

    protected void updateActionbar() {
        int minutes = time / (60*20);
        int seconds = time % (60*20);
        String timeString = String.format("%02d:%02d", minutes, seconds);

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendActionBar(Component.text("§6⏱ " + timeString));
        }
    }

    // ABSTRACTS
    protected abstract void onGameStart();
    protected abstract void onGameTick();
    protected abstract List<TeamData> getRanking();
    protected abstract int getPoints(String teamName);
}