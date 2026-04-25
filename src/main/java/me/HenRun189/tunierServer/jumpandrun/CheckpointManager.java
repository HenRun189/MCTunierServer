package me.HenRun189.tunierServer.jumpandrun;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.*;

public class CheckpointManager {

    private final Map<UUID, PlayerData> data = new HashMap<>();

    public PlayerData get(Player p) {
        return data.computeIfAbsent(p.getUniqueId(), PlayerData::new);
    }

    public boolean isCheckpoint(Block block) {
        return block.getType() == Material.LIGHT_WEIGHTED_PRESSURE_PLATE;
    }

    public void handleMove(Player p, Block block) {

        if (!isCheckpoint(block)) return;

        PlayerData d = get(p);

        int newCheckpoint = block.getLocation().getBlockY(); // simpel erstmal

        if (newCheckpoint <= d.getCheckpoint()) return;

        d.setCheckpoint(newCheckpoint);
        d.setLastCheckpoint(block.getLocation().add(0.5, 1, 0.5));

        p.sendMessage("§aCheckpoint erreicht!");

        p.spawnParticle(Particle.HAPPY_VILLAGER, block.getLocation().add(0.5,1,0.5), 20);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
    }

    public void handleFall(Player p) {

        PlayerData d = get(p);

        d.addFall();

        if (d.getLastCheckpoint() != null) {
            p.teleport(d.getLastCheckpoint());
        }
    }
}