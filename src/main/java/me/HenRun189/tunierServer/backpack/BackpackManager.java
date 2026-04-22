package me.HenRun189.tunierServer.backpack;

import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;

import java.util.HashMap;
import java.util.Map;

public class BackpackManager {

    private final Map<String, Inventory> backpacks = new HashMap<>();

    public Inventory getBackpack(String teamName) {
        return backpacks.computeIfAbsent(teamName,
                t -> Bukkit.createInventory(null, 27, "§6Team Backpack"));
    }

    public void clearAll() {
        backpacks.clear();
    }
}