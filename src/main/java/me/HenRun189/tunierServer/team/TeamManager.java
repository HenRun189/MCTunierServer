package me.HenRun189.tunierServer.team;
import me.HenRun189.tunierServer.TunierServer;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class TeamManager {

    private final Map<String, TeamData> teams = new HashMap<>();
    private Scoreboard board;

    private final File file;
    private final FileConfiguration config;

    public TeamManager(Scoreboard board) {

        this.board = board;

        file = new File(TunierServer.getInstance().getDataFolder(), "teams.yml");

        if (!file.exists()) {
            file.getParentFile().mkdirs();
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        config = YamlConfiguration.loadConfiguration(file);

        loadTeams();
    }

    public Scoreboard getBoard() {
        return board;
    }

    public void createTeam(String name, ChatColor color, String prefix) {
        if (teams.containsKey(name)) return;
        if (board == null) return;

        Team team = board.getTeam(name);

        // ✅ verhindert doppelte Registrierung
        if (team == null) {
            team = board.registerNewTeam(name);
        }

        team.setPrefix(color + "[" + prefix + "] ");
        team.setSuffix(" §7");
        team.setColor(color);

        team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.ALWAYS);
        team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);

        teams.put(name, new TeamData(name, color, prefix));

        refreshScoreboard();
    }

    public void deleteTeam(String name) {
        if (board == null) return;

        Team team = board.getTeam(name);

        if (team != null) {
            for (String entry : new HashSet<>(team.getEntries())) {
                team.removeEntry(entry);
            }
            team.unregister();
        }

        teams.remove(name);

        refreshScoreboard();
    }

    public void joinTeam(String name, Player player) {
        if (player == null || board == null) return;

        TeamData data = teams.get(name);
        if (data == null) return;

        removePlayer(player);

        data.addPlayer(player.getUniqueId());

        Team team = board.getTeam(name);
        if (team != null) {
            team.addEntry(player.getName());
        }

        refreshScoreboard();
        saveTeams();
    }

    public void addPlayerToTeam(String teamName, Player player) {
        joinTeam(teamName, player);
    }

    public void removePlayer(Player player) {
        if (player == null || board == null) return;

        UUID uuid = player.getUniqueId();
        String playerName = player.getName();

        for (TeamData team : teams.values()) {

            team.removePlayer(uuid);

            Team bukkitTeam = board.getTeam(team.getName());
            if (bukkitTeam != null) {
                bukkitTeam.removeEntry(playerName);
            }
        }

        saveTeams();
    }

    public void editTeam(String name, ChatColor color, String prefix) {
        if (board == null) return;

        TeamData data = teams.get(name);
        if (data == null) return;

        Team team = board.getTeam(name);
        if (team != null) {
            team.setPrefix(color + prefix + " §7| " + color);
            team.setSuffix("§r");
            team.setColor(color);

            team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.ALWAYS);
            team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
        }

        data.setColor(color);
        data.setPrefix(prefix);

        for (UUID uuid : data.getPlayers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                TunierServer.getInstance().getScoreManager().applyToPlayer(p);
            }
        }

        refreshScoreboard();
        saveTeams();
    }

    public void resetAllTeams() {
        if (board == null) return;

        for (Team team : new HashSet<>(board.getTeams())) {
            for (String entry : new HashSet<>(team.getEntries())) {
                team.removeEntry(entry);
            }
            team.unregister();
        }

        teams.clear();


        for (Player p : Bukkit.getOnlinePlayers()) {
            TunierServer.getInstance().getScoreManager().applyToPlayer(p);
        }

        saveTeams();
    }

    public Map<String, TeamData> getTeams() {
        return teams;
    }

    public TeamData getTeam(String name) {
        return teams.get(name);
    }

    public TeamData getTeamByPlayer(UUID uuid) {
        for (TeamData team : teams.values()) {
            if (team.getPlayers().contains(uuid)) {
                return team;
            }
        }
        return null;
    }

    // ---------------- SAVE / LOAD ----------------

    public void saveTeams() {

        config.set("teams", null);

        for (TeamData team : teams.values()) {

            String path = "teams." + team.getName();

            config.set(path + ".color", team.getColor().name());
            config.set(path + ".prefix", team.getPrefix());

            List<String> players = new ArrayList<>();
            for (UUID uuid : team.getPlayers()) {
                players.add(uuid.toString());
            }

            config.set(path + ".players", players);
        }

        try {
            config.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void loadTeams() {

        if (!config.contains("teams")) return;
        if (board == null) return;

        for (String name : config.getConfigurationSection("teams").getKeys(false)) {

            ChatColor color = ChatColor.valueOf(config.getString("teams." + name + ".color"));
            String prefix = config.getString("teams." + name + ".prefix");

            createTeam(name, color, prefix);

            List<String> players = config.getStringList("teams." + name + ".players");

            TeamData data = teams.get(name);

            for (String uuidStr : players) {
                UUID uuid = UUID.fromString(uuidStr);

                if (data != null) {
                    data.addPlayer(uuid);

                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null && p.isOnline()) {
                        Team team = board.getTeam(name);
                        if (team != null) {
                            team.addEntry(p.getName());
                        }
                    }
                }
            }
        }
    }

    private void refreshScoreboard() {
        if (board == null) return;

        for (Player p : Bukkit.getOnlinePlayers()) {
            TunierServer.getInstance().getScoreManager().applyToPlayer(p);
        }

        // 🔥 Scoreboard Inhalte neu setzen
        if (TunierServer.getInstance() != null) {
            TunierServer plugin = TunierServer.getInstance();
            if (plugin.getScoreManager() != null) {
                plugin.getScoreManager().updateScoreboard();
            }
        }
    }

    public void applyTeamToPlayer(Player player) {
        if (player == null || board == null) return;

        TeamData data = getTeamByPlayer(player.getUniqueId());
        if (data == null) return;

        Team team = board.getTeam(data.getName());
        if (team != null) {
            team.addEntry(player.getName());
        }

        TunierServer.getInstance().getScoreManager().applyToPlayer(player);
        TunierServer.getInstance().getScoreManager().updateScoreboard();
    }

    public void reapplyAllTeams() {
        if (board == null) return;

        for (TeamData data : teams.values()) {
            Team team = board.getTeam(data.getName());
            if (team == null) {
                team = board.registerNewTeam(data.getName());
            }
            team.setPrefix(data.getColor() + "[" + data.getPrefix() + "] ");
            team.setSuffix(" §7");
            team.setColor(data.getColor());
            team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.ALWAYS);
            team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);

            for (UUID uuid : data.getPlayers()) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline()) {
                    if (!team.hasEntry(p.getName())) team.addEntry(p.getName());
                }
            }
        }
    }
}

//GPT 23