package me.HenRun189.tunierServer.score;

import me.HenRun189.tunierServer.team.TeamManager;
import me.HenRun189.tunierServer.team.TeamData;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;

import java.util.*;
import java.util.function.Function;

public class ScoreManager {

    private final Map<String, Integer> gamePoints  = new HashMap<>();
    private final Map<String, Integer> totalPoints = new HashMap<>();
    private final Map<String, Map<String, Integer>> historyPoints = new HashMap<>();

    private final TeamManager teamManager;

    private Scoreboard board;
    private Objective  objective;

    private String currentGame = "Warten auf Spiel";

    // PvP-Live-Daten (null wenn kein PvP aktiv)
    private Function<String, Integer> pvpAliveSupplier    = null;
    private Function<String, Integer> pvpTeamKillSupplier = null;

    public ScoreManager(TeamManager teamManager) {
        this.teamManager = teamManager;
    }

    public void setPvpSuppliers(Function<String, Integer> aliveSupplier,
                                Function<String, Integer> teamKillSupplier) {
        this.pvpAliveSupplier    = aliveSupplier;
        this.pvpTeamKillSupplier = teamKillSupplier;
    }

    public void clearPvpSuppliers() {
        this.pvpAliveSupplier    = null;
        this.pvpTeamKillSupplier = null;
    }

    // ── POINTS ─────────────────────────────────────────────────────

    public void addPoints(String team, int amount) {
        gamePoints.put(team, gamePoints.getOrDefault(team, 0) + amount);
        totalPoints.put(team, totalPoints.getOrDefault(team, 0) + amount);
        historyPoints.putIfAbsent(team, new HashMap<>());
        historyPoints.get(team).merge(currentGame, amount, Integer::sum);
        updateScoreboard();
    }

    public void resetGame()            { gamePoints.clear(); updateScoreboard(); }
    public int  getPoints(String team) { return gamePoints.getOrDefault(team, 0); }
    public int  getTotalPoints(String team) { return totalPoints.getOrDefault(team, 0); }

    public void setCurrentGame(String name) { this.currentGame = name; updateScoreboard(); }

    // ── SCOREBOARD ─────────────────────────────────────────────────

    public void setupScoreboard() {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) return;
        board = manager.getMainScoreboard();
        objective = board.getObjective("main");
        if (objective == null)
            objective = board.registerNewObjective("main", "dummy", "§6§lMCTurnier");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        updateScoreboard();
        for (Player p : Bukkit.getOnlinePlayers()) p.setScoreboard(board);
        if (teamManager != null) teamManager.reapplyAllTeams();
    }

    public void updateScoreboard() {
        if (board == null || objective == null || teamManager == null) return;

        // Alte Sidebar-Einträge löschen
        for (String entry : new HashSet<>(board.getEntries())) {
            boolean isTeamMember = false;
            for (Team t : board.getTeams()) {
                if (t.hasEntry(entry)) { isTeamMember = true; break; }
            }
            if (!isTeamMember) board.resetScores(entry);
        }

        Collection<TeamData> teams = teamManager.getTeams().values();
        if (teams.isEmpty()) {
            objective.getScore("§7Keine Teams").setScore(3);
            objective.getScore("§f").setScore(2);
            objective.getScore("§7" + currentGame).setScore(1);
            return;
        }

        List<TeamData> sorted = new ArrayList<>(teams);
        sorted.sort((a, b) -> {
            int pA = currentGame.equals("Warten auf Spiel") ? getTotalPoints(a.getName()) : getPoints(a.getName());
            int pB = currentGame.equals("Warten auf Spiel") ? getTotalPoints(b.getName()) : getPoints(b.getName());
            return Integer.compare(pB, pA);
        });

        boolean isPvP = pvpAliveSupplier != null;

        // Zeilen von oben nach unten (höherer Score = weiter oben)
        // Layout:
        //   §7 [Leer]
        //   #1 TeamA » 120pts  [2/3 ❤ | 5⚔]   ← kompakt eine Zeile im PvP
        //   #2 TeamB » 80pts   [1/2 ❤ | 2⚔]
        //   §0 [Leer]
        //   §7Hunger Games
        //   §5twitch...

        int score = sorted.size() + 5;

        objective.getScore("§7 ").setScore(score--);

        int place = 1;
        for (TeamData team : sorted) {
            int points = currentGame.equals("Warten auf Spiel")
                    ? getTotalPoints(team.getName())
                    : getPoints(team.getName());

            String placePrefix = switch (place) {
                case 1 -> "§6#1";
                case 2 -> "§7#2";
                case 3 -> "§c#3";
                default -> "§8#" + place;
            };

            String line;
            if (isPvP) {
                // Kompakte PvP-Zeile: #1 TeamA » 80 | 2/3❤ 4⚔
                int alive  = pvpAliveSupplier.apply(team.getName());
                int total  = team.getPlayers().size();
                int tKills = pvpTeamKillSupplier.apply(team.getName());
                line = placePrefix + " " + team.getColor() + team.getName()
                        + " §8»§a" + points
                        + " §8|§f" + alive + "§7/" + total + "§c❤"
                        + " §8|§e" + tKills + "§7⚔"
                        // Eindeutiger Suffix damit Bukkit keine Duplikate wirft
                        + "§" + Integer.toHexString(place);
            } else {
                line = placePrefix + " " + team.getColor() + team.getName()
                        + " §8» §a" + points
                        + "§" + Integer.toHexString(place);
            }

            objective.getScore(line).setScore(score--);
            place++;
        }

        objective.getScore("§0 ").setScore(score--);
        objective.getScore("§7" + currentGame + " ").setScore(score--);
        objective.getScore("§5twitch.tv/henrun189 ").setScore(score);
    }

    public void applyToPlayer(Player player) {
        if (board != null) player.setScoreboard(board);
    }

    public Scoreboard getBoard() { return board; }
    public Map<String, Map<String, Integer>> getHistoryPoints() { return historyPoints; }
}