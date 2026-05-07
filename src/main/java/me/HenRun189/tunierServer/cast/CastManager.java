package me.HenRun189.tunierServer.cast;

import me.HenRun189.tunierServer.TunierServer;
import me.HenRun189.tunierServer.score.ScoreManager;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scoreboard.*;

import java.io.*;
import java.util.*;

public class CastManager {

    private final Set<UUID> castPlayers = new HashSet<>();
    private final File saveFile;
    private final ScoreManager scoreManager;
    private boolean healthDisplayActive = false;

    public CastManager(ScoreManager scoreManager) {
        this.scoreManager = scoreManager;
        this.saveFile = new File(TunierServer.getInstance().getDataFolder(), "cast.yml");
        load();
    }

    public void addCast(UUID uuid) {
        castPlayers.add(uuid);
        save();
    }

    public void removeCast(UUID uuid) {
        castPlayers.remove(uuid);
        save();
    }

    public boolean isCast(UUID uuid) { return castPlayers.contains(uuid); }

    public Set<UUID> getCastPlayers() { return Collections.unmodifiableSet(castPlayers); }

    public boolean isHealthDisplayActive() { return healthDisplayActive; }

    // Fügt BELOW_NAME Herz-Anzeige zum Spieler-Scoreboard hinzu (sichtbar für alle)
    public void toggleHealthDisplay() {
        Scoreboard board = scoreManager.getBoard();
        Objective existing = board.getObjective("cast_hp");
        if (existing != null) {
            existing.unregister();
            healthDisplayActive = false;
        } else {
            try {
                Objective obj = board.registerNewObjective(
                    "cast_hp",
                    Criteria.HEALTH,
                    Component.text("§c❤"),
                    RenderType.HEARTS
                );
                obj.setDisplaySlot(DisplaySlot.BELOW_NAME);
                healthDisplayActive = true;
            } catch (Exception e) {
                TunierServer.getInstance().getLogger()
                    .warning("[Cast] Health-Anzeige Fehler: " + e.getMessage());
            }
        }
    }

    // Aufräumen beim Server-Stop
    public void cleanup() {
        Scoreboard board = scoreManager.getBoard();
        Objective obj = board.getObjective("cast_hp");
        if (obj != null) obj.unregister();
        healthDisplayActive = false;
    }

    // ── Persistenz ────────────────────────────────────────────────

    private void load() {
        if (!saveFile.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(saveFile);
        for (String s : cfg.getStringList("cast")) {
            try { castPlayers.add(UUID.fromString(s)); } catch (Exception ignored) {}
        }
    }

    public void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        List<String> list = new ArrayList<>();
        castPlayers.forEach(u -> list.add(u.toString()));
        cfg.set("cast", list);
        try {
            cfg.save(saveFile);
        } catch (IOException e) {
            TunierServer.getInstance().getLogger()
                .warning("[Cast] Datei konnte nicht gespeichert werden!");
        }
    }
}
