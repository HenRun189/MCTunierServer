package me.HenRun189.tunierServer.world;

import org.bukkit.*;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.UUID;

import me.HenRun189.tunierServer.team.TeamData;

public class WorldManager {

    public World createTeamWorld(String teamName, long seed) {
        String worldName = "game_" + teamName.toLowerCase();

        WorldCreator creator = new WorldCreator(worldName);
        creator.environment(World.Environment.NORMAL);
        creator.type(WorldType.NORMAL);
        creator.seed(seed); // 🔥 DAS IST DER WICHTIGE PART

        World world = Bukkit.createWorld(creator);

        // gleiche spawn position für fairness
        Location spawn = world.getHighestBlockAt(0,0).getLocation().add(0,1,0);
        world.setSpawnLocation(spawn);

        return world;
    }

    public void teleportTeam(TeamData team, World world) {

        Location spawn = world.getSpawnLocation();

        for (UUID uuid : team.getPlayers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.teleport(spawn);
            }
        }
    }

    public void deleteWorld(World world) {

        if (world == null) return;

        Bukkit.unloadWorld(world, false);

        File folder = world.getWorldFolder();
        deleteFolder(folder);
    }

    private void deleteFolder(File file) {
        if (file.isDirectory()) {
            for (File f : file.listFiles()) {
                deleteFolder(f);
            }
        }
        file.delete();
    }
}