package me.HenRun189.tunierServer.score;

import me.HenRun189.tunierServer.team.TeamManager;
import me.HenRun189.tunierServer.team.TeamData;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;

import java.util.*;

public class ScoreManager {

    private final Map<String, Integer> gamePoints = new HashMap<>();
    private final Map<String, Integer> totalPoints = new HashMap<>();
    private final Map<String, Map<String, Integer>> historyPoints = new HashMap<>();

    private final TeamManager teamManager;

    private Scoreboard board;
    private Objective objective;

    private String currentGame = "Warten auf Spiel";

    public ScoreManager(TeamManager teamManager) {
        this.teamManager = teamManager;
    }

    // 🔥 POINTS
    public void addPoints(String team, int amount) {

        gamePoints.put(team, gamePoints.getOrDefault(team, 0) + amount);
        totalPoints.put(team, totalPoints.getOrDefault(team, 0) + amount);

        historyPoints.putIfAbsent(team, new HashMap<>());
        Map<String, Integer> teamHistory = historyPoints.get(team);

        teamHistory.put(currentGame,
                teamHistory.getOrDefault(currentGame, 0) + amount);

        updateScoreboard();
    }

    public void resetGame() {
        gamePoints.clear();
        updateScoreboard();
    }

    public int getPoints(String team) {
        return gamePoints.getOrDefault(team, 0);
    }

    public int getTotalPoints(String team) {
        return totalPoints.getOrDefault(team, 0);
    }

    public void setCurrentGame(String name) {
        this.currentGame = name;
        updateScoreboard();
    }

    public void setupScoreboard() {

        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) return;

        board = manager.getMainScoreboard();

        objective = board.getObjective("main");
        if (objective == null) {
            objective = board.registerNewObjective("main", "dummy", "§6§lMCTurnier");
        }

        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        updateScoreboard();

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.setScoreboard(board);
        }
    }

    public void updateScoreboard() {

        if (board == null || objective == null || teamManager == null) return;

        for (String entry : new HashSet<>(board.getEntries())) {
            board.resetScores(entry);
        }

        Collection<TeamData> teams = teamManager.getTeams().values();

        if (teams.isEmpty()) {
            objective.getScore("§7Keine Teams").setScore(3);
            objective.getScore("§f").setScore(2);
            objective.getScore("§7" + currentGame).setScore(1);
            return;
        }

        List<TeamData> sorted = new ArrayList<>(teams);

        // 🔥 richtige Sortierung je nach Phase
        sorted.sort((a, b) -> {
            int pointsA = currentGame.equals("Warten auf Spiel")
                    ? getTotalPoints(a.getName())
                    : getPoints(a.getName());

            int pointsB = currentGame.equals("Warten auf Spiel")
                    ? getTotalPoints(b.getName())
                    : getPoints(b.getName());

            return Integer.compare(pointsB, pointsA);
        });

        int score = sorted.size() + 6;

        objective.getScore("§7 ").setScore(score--);

        int place = 1;

        for (TeamData team : sorted) {

            int points = currentGame.equals("Warten auf Spiel")
                    ? getTotalPoints(team.getName())
                    : getPoints(team.getName());

            String line = "§6#" + place + " "
                    + team.getColor() + team.getName()
                    + " §8» §a" + points;

            line = line + "§" + Integer.toHexString(place);

            objective.getScore(line).setScore(score--);
            place++;
        }

        objective.getScore("§0 ").setScore(score--);

        objective.getScore("§7" + currentGame + " ").setScore(score--);

        objective.getScore("§1 ").setScore(score--);

        objective.getScore("§5twitch.tv/henrun189 ").setScore(score--);
    }

    public void applyToPlayer(Player player) {
        if (board != null) {
            player.setScoreboard(board);
        }
    }

    public Scoreboard getBoard() {
        return board;
    }

    public Map<String, Map<String, Integer>> getHistoryPoints() {
        return historyPoints;
    }
}