package me.HenRun189.tunierServer.listeners;

import me.HenRun189.tunierServer.TunierServer;
import me.HenRun189.tunierServer.jumpandrun.JnRVisibilityManager.Mode;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkEffectMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public class VisibilityToggleItem {

    public static NamespacedKey KEY;

    public static void init() {
        KEY = new NamespacedKey(TunierServer.getInstance(), "visibility_toggle");
    }

    public static ItemStack create(Mode mode) {
        ItemStack item = new ItemStack(Material.FIREWORK_STAR);
        FireworkEffectMeta meta = (FireworkEffectMeta) item.getItemMeta();

        Color color;
        Component name;
        String loreLine;

        switch (mode) {
            case GHOST  -> { color = Color.LIME;   name = Component.text("Sichtbarkeit: Ghost",    NamedTextColor.GREEN);  loreLine = "§7Alle Spieler §asichtbar §7(Glow)"; }
            case TEAM   -> { color = Color.YELLOW; name = Component.text("Sichtbarkeit: Nur Team", NamedTextColor.YELLOW); loreLine = "§7Nur §eTeammitglieder §7sichtbar"; }
            case HIDDEN -> { color = Color.RED;    name = Component.text("Sichtbarkeit: Niemand",  NamedTextColor.RED);    loreLine = "§7Alle Spieler §cverborgen"; }
            default     -> { color = Color.WHITE;  name = Component.text("Sichtbarkeit");                                  loreLine = ""; }
        }

        meta.setEffect(FireworkEffect.builder()
                .withColor(color)
                .with(FireworkEffect.Type.BALL)
                .build());
        meta.displayName(name);
        meta.lore(List.of(
                Component.text(loreLine),
                Component.text("§8» Rechtsklick zum Wechseln")
        ));
        meta.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, mode.name());
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isToggleItem(ItemStack item) {
        if (item == null || item.getType() != Material.FIREWORK_STAR) return false;
        var meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(KEY, PersistentDataType.STRING);
    }

    public static Mode getMode(ItemStack item) {
        if (item == null) return Mode.GHOST;
        var meta = item.getItemMeta();
        if (meta == null) return Mode.GHOST;
        String s = meta.getPersistentDataContainer().get(KEY, PersistentDataType.STRING);
        if (s == null) return Mode.GHOST;
        try { return Mode.valueOf(s); } catch (IllegalArgumentException e) { return Mode.GHOST; }
    }
}
