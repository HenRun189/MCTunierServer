package me.HenRun189.tunierServer.team;

import org.bukkit.ChatColor;
import java.util.*;
import org.bukkit.*;


public class TeamData {

    private final String name;
    private ChatColor color;
    private String prefix;

    private final Set<UUID> players = new HashSet<>();

    public TeamData(String name, ChatColor color, String prefix) {
        this.name = name;
        this.color = color;
        this.prefix = prefix;
    }

    public String getName() {
        return name;
    }

    public ChatColor getColor() {
        return color;
    }

    public String getPrefix() {
        return prefix;
    }

    //  FIX: unmodifiable zurückgeben (sehr wichtig)
    public Set<UUID> getPlayers() {
        return Collections.unmodifiableSet(players);
    }

    //  interne Methoden für Manager
    public void addPlayer(UUID uuid) {
        players.add(uuid);
    }

    public void removePlayer(UUID uuid) {
        players.remove(uuid);
    }

    public void setColor(ChatColor color) {
        this.color = color;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    private Location spawnLocation;

    public Location getSpawnLocation() {
        return spawnLocation;
    }

    public void setSpawnLocation(Location loc) {
        this.spawnLocation = loc;
    }
}

//GPT 12:30