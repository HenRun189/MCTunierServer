package me.HenRun189.tunierServer.jumpandrun;

import me.HenRun189.tunierServer.TunierServer;
import me.HenRun189.tunierServer.listeners.VisibilityToggleItem;
import me.HenRun189.tunierServer.team.TeamData;
import me.HenRun189.tunierServer.team.TeamManager;

import net.kyori.adventure.text.Component;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.*;

public class JnRVisibilityManager implements Listener {

    public enum Mode { GHOST, TEAM, HIDDEN }

    public static final int TOGGLE_SLOT = 1;

    private static final String NO_COLLIDE_TEAM = "jnr_nocollide";

    private final TeamManager teamManager;
    private final Map<UUID, Mode> modes = new HashMap<>();
    private boolean active = false;

    private Scoreboard jnrBoard = null;

    // Caster/Mods die IMMER alle sehen sollen
    private final Set<UUID> spectators = new HashSet<>();

    public JnRVisibilityManager(TeamManager teamManager) {
        this.teamManager = teamManager;
    }

    public void enable(Collection<Player> players) {
        active = true;
        setupNoCollisionTeam(players);

        for (Player p : players) {
            modes.put(p.getUniqueId(), Mode.GHOST);
            p.setGlowing(false);
            p.setInvulnerable(true);
            // KEIN INVISIBILITY POTION mehr!
        }

        refreshAll();
        Bukkit.getPluginManager().registerEvents(this, TunierServer.getInstance());
    }

    public void disable() {
        active = false;
        HandlerList.unregisterAll(this);

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.setGlowing(false);
            p.setInvulnerable(false);

            if (VisibilityToggleItem.isToggleItem(p.getInventory().getItem(TOGGLE_SLOT))) {
                p.getInventory().setItem(TOGGLE_SLOT, null);
            }

            // Alle wieder sichtbar machen
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (!p.equals(other)) p.showPlayer(TunierServer.getInstance(), other);
            }
        }

        if (jnrBoard != null) {
            jnrBoard = null;
        }

        modes.clear();

        // Haupt-Scoreboard wiederherstellen + Tab-Farben
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
        TunierServer.getInstance().getTeamManager().reapplyAllTeams();
        for (Player p : Bukkit.getOnlinePlayers()) {
            TunierServer.getInstance().getTeamManager().applyTeamToPlayer(p);
        }

        spectators.clear();
    }

    public void setSpectatorMode(Player spectator) {
        modes.remove(spectator.getUniqueId());
        spectators.add(spectator.getUniqueId());

        spectator.setGlowing(false);

        if (VisibilityToggleItem.isToggleItem(spectator.getInventory().getItem(TOGGLE_SLOT))) {
            spectator.getInventory().setItem(TOGGLE_SLOT, null);
        }

        // Spectator sieht alle
        for (Player p : Bukkit.getOnlinePlayers()) {
            spectator.showPlayer(TunierServer.getInstance(), p);
        }

        // Alle sehen den Spectator normal
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.equals(spectator)) {
                p.showPlayer(TunierServer.getInstance(), spectator);
            }
        }
    }

    /**
     * Spieler der KEIN Teilnehmer ist (Caster, Mod, Zuschauer von außen)
     * sieht immer alle und ist für alle sichtbar.
     */
    public void registerExternalSpectator(Player p) {
        spectators.add(p.getUniqueId());
        // Spectator sieht alle
        for (Player other : Bukkit.getOnlinePlayers()) {
            p.showPlayer(TunierServer.getInstance(), other);
            // Alle sehen den Spectator
            other.showPlayer(TunierServer.getInstance(), p);
        }
    }

    public boolean isActive() { return active; }

    // ==========================================
    // TOGGLE ITEM
    // ==========================================

    @EventHandler
    public void onToggle(PlayerInteractEvent e) {
        if (!active) return;
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player p = e.getPlayer();
        if (!modes.containsKey(p.getUniqueId())) return;
        if (!VisibilityToggleItem.isToggleItem(p.getInventory().getItemInMainHand())) return;

        e.setCancelled(true);

        Mode next = switch (modes.get(p.getUniqueId())) {
            case GHOST  -> Mode.TEAM;
            case TEAM   -> Mode.HIDDEN;
            case HIDDEN -> Mode.GHOST;
        };

        setMode(p, next);

        p.sendActionBar(Component.text(switch (next) {
            case GHOST  -> "§7Sichtbarkeit: §fGhost §8(alle)";
            case TEAM   -> "§eSichtbarkeit: §eNur Team";
            case HIDDEN -> "§cSichtbarkeit: §cNiemand";
        }));

        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, switch (next) {
            case GHOST  -> 1.2f;
            case TEAM   -> 1.0f;
            case HIDDEN -> 0.7f;
        });
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!active) return;
        if (e.getDamager() instanceof Player && e.getEntity() instanceof Player) e.setCancelled(true);
    }

    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        if (!active) return;
        if (!modes.containsKey(e.getPlayer().getUniqueId())) return;
        if (e.getRightClicked() instanceof Player) e.setCancelled(true);
    }

    // ==========================================
    // VISIBILITY LOGIC
    // ==========================================

    public void setMode(Player player, Mode mode) {
        modes.put(player.getUniqueId(), mode);
        player.setGlowing(mode == Mode.TEAM);
        applyFor(player);
        player.getInventory().setItem(TOGGLE_SLOT, VisibilityToggleItem.create(mode));
    }

    public Mode getMode(Player player) {
        return modes.getOrDefault(player.getUniqueId(), Mode.GHOST);
    }

    private void applyFor(Player viewer) {
        // Externe Spectators / Caster sehen immer alle → skip
        if (spectators.contains(viewer.getUniqueId())) return;

        Mode mode = modes.getOrDefault(viewer.getUniqueId(), Mode.GHOST);
        TeamData viewerTeam = teamManager.getTeamByPlayer(viewer.getUniqueId());

        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target.equals(viewer)) continue;

            // Spectators (Caster/Mods/Finished) immer anzeigen
            if (spectators.contains(target.getUniqueId()) || !modes.containsKey(target.getUniqueId())) {
                viewer.showPlayer(TunierServer.getInstance(), target);
                continue;
            }

            switch (mode) {
                case GHOST  -> viewer.showPlayer(TunierServer.getInstance(), target);
                case HIDDEN -> viewer.hidePlayer(TunierServer.getInstance(), target);
                case TEAM   -> {
                    TeamData targetTeam = teamManager.getTeamByPlayer(target.getUniqueId());
                    boolean sameTeam = viewerTeam != null && viewerTeam.equals(targetTeam);
                    if (sameTeam) viewer.showPlayer(TunierServer.getInstance(), target);
                    else          viewer.hidePlayer(TunierServer.getInstance(), target);
                }
            }
        }
    }

    private void refreshAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (spectators.contains(p.getUniqueId())) {
                // Spectator sieht alle, alle sehen Spectator
                //registerExternalSpectator(p);                         -------------------------------
            } else if (modes.containsKey(p.getUniqueId())) {
                applyFor(p);
            }
        }
    }

    private void setupNoCollisionTeam(Collection<Player> players) {
        jnrBoard = Bukkit.getScoreboardManager().getNewScoreboard();
        Team team = jnrBoard.registerNewTeam(NO_COLLIDE_TEAM);
        team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);

        for (Player p : players) {
            team.addEntry(p.getName());
            p.setScoreboard(jnrBoard);
        }
    }

    public void onPlayerRejoin(Player p) {
        if (!active) return;

        // Ist kein Teilnehmer (kein Team) → als externer Spectator behandeln
        if (teamManager.getTeamByPlayer(p.getUniqueId()) == null) {
            registerExternalSpectator(p);
            return;
        }

        setMode(p, Mode.GHOST);
        p.setInvulnerable(true);

        if (jnrBoard != null) {
            Team team = jnrBoard.getTeam(NO_COLLIDE_TEAM);
            if (team != null && !team.hasEntry(p.getName())) {
                team.addEntry(p.getName());
            }
            p.setScoreboard(jnrBoard);
        }

        refreshAll();
    }

    public boolean isKnownSpectator(Player p) {
        return spectators.contains(p.getUniqueId());
    }
}