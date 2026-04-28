package me.HenRun189.tunierServer.jumpandrun;

import org.bukkit.Location;
import java.util.UUID;
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayList;

public class PlayerData {

    private ArrayList<Location> checkpoints = new ArrayList<>();

    private final UUID uuid;

    private int checkpoint = 0;
    private int falls = 0;
    private Location lastCheckpoint;

    private final Set<Location> reachedCheckpoints = new HashSet<>();

    private long startTime;
    private long finishTime;

    public void startTimer() {
        this.startTime = System.currentTimeMillis();
    }

    public void finishTimer() {
        this.finishTime = System.currentTimeMillis();
    }

    public long getTime() {
        return finishTime - startTime;
    }

    public Set<Location> getReachedCheckpoints() {
        return reachedCheckpoints;
    }

    public PlayerData(UUID uuid) {
        this.uuid = uuid;
    }

    public int getCheckpoint() {
        return checkpoint;
    }

    public void setCheckpoint(int checkpoint) {
        this.checkpoint = checkpoint;
    }

    public int getFalls() {
        return falls;
    }

    public void addFall() {
        this.falls++;
    }

    public UUID getUuid() {
        return uuid;
    }

    public Location getLastCheckpoint() {
        return lastCheckpoint;
    }

    public ArrayList<Location> getCheckpoints() {
        return checkpoints;
    }

    public void setLastCheckpoint(Location lastCheckpoint) {
        this.lastCheckpoint = lastCheckpoint;
    }
}