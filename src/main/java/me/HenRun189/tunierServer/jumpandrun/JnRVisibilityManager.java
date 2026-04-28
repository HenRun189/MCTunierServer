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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;

public class JnRVisibilityManager implements Listener {

    public enum Mode { GHOST, TEAM, HIDDEN }

    public static final int TOGGLE_SLOT = 1; // Hotbar-Slot 2

    private static final String NO_COLLIDE_TEAM = "jnr_nocollide";

    private final TeamManager teamManager;

    // UUID → Mode; players removed from this map are spectators
    private final Map<UUID, Mode> modes = new HashMap<>();
    private boolean active = false;

    public JnRVisibilityManager(TeamManager teamManager) {
        this.teamManager = teamManager;
    }

    // ==========================================
    // LIFECYCLE
    // ==========================================

    /** Called once all participants are in the world (preGame phase). */
    public void enable(Collection<Player> players) {
        active = true;

        setupNoCollisionTeam(players);

        for (Player p : players) {
            modes.put(p.getUniqueId(), Mode.GHOST);
            p.setGlowing(false); // glow is managed per-mode via setMode()
            p.setInvulnerable(true);
            applyTransparency(p); // slight ghost transparency via INVISIBILITY
        }

        refreshAll();
        Bukkit.getPluginManager().registerEvents(this, TunierServer.getInstance());
    }

    /** Called in JumpAndRunMode.stop(). Fully resets all affected players. */
    public void disable() {
        active = false;
        HandlerList.unregisterAll(this);

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.setGlowing(false);
            p.setInvulnerable(false);
            removeTransparency(p);

            // Remove toggle item if still in the slot
            if (VisibilityToggleItem.isToggleItem(p.getInventory().getItem(TOGGLE_SLOT))) {
                p.getInventory().setItem(TOGGLE_SLOT, null);
            }

            // Restore full visibility
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (!p.equals(other)) p.showPlayer(TunierServer.getInstance(), other);
            }
        }

        // Clear no-collision team entries
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = board.getTeam(NO_COLLIDE_TEAM);
        if (team != null) {
            for (String entry : new HashSet<>(team.getEntries())) {
                team.removeEntry(entry);
            }
        }

        modes.clear();
    }

    /**
     * Called when a player finishes (steps on diamond block → SPECTATOR).
     * Removes them from the toggle cycle and forces them to see everyone.
     */
    public void setSpectatorMode(Player spectator) {
        // Remove from active modes so toggle does nothing
        modes.remove(spectator.getUniqueId());

        spectator.setGlowing(false);
        removeTransparency(spectator);

        // Remove toggle item — spectators don't need it
        if (VisibilityToggleItem.isToggleItem(spectator.getInventory().getItem(TOGGLE_SLOT))) {
            spectator.getInventory().setItem(TOGGLE_SLOT, null);
        }

        // Force-show every active participant to this spectator
        for (Player p : Bukkit.getOnlinePlayers()) {
            spectator.showPlayer(TunierServer.getInstance(), p);
        }
    }

    public boolean isActive() {
        return active;
    }

    // ==========================================
    // TOGGLE ITEM — right-click cycles GHOST → TEAM → HIDDEN → GHOST
    // ==========================================

    @EventHandler
    public void onToggle(PlayerInteractEvent e) {
        if (!active) return;
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player p = e.getPlayer();
        if (!modes.containsKey(p.getUniqueId())) return; // spectator → ignore
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

    // ==========================================
    // NO INTERACTION
    // ==========================================

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!active) return;
        if (e.getDamager() instanceof Player && e.getEntity() instanceof Player) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        if (!active) return;
        if (!modes.containsKey(e.getPlayer().getUniqueId())) return;
        if (e.getRightClicked() instanceof Player) {
            e.setCancelled(true);
        }
    }

    // ==========================================
    // VISIBILITY LOGIC
    // ==========================================

    public void setMode(Player player, Mode mode) {
        modes.put(player.getUniqueId(), mode);
        // TEAM mode: player glows in team color so visible teammates stand out
        // GHOST / HIDDEN: no glow, just the INVISIBILITY transparency
        player.setGlowing(mode == Mode.TEAM);
        applyFor(player);
        player.getInventory().setItem(TOGGLE_SLOT, VisibilityToggleItem.create(mode));
    }

    public Mode getMode(Player player) {
        return modes.getOrDefault(player.getUniqueId(), Mode.GHOST);
    }

    /** Applies what `viewer` sees, based on their personal mode. */
    private void applyFor(Player viewer) {
        Mode mode = modes.getOrDefault(viewer.getUniqueId(), Mode.GHOST);
        TeamData viewerTeam = teamManager.getTeamByPlayer(viewer.getUniqueId());

        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target.equals(viewer)) continue;
            // Skip players who are spectators (not in modes map) — spectators always visible to viewer
            if (!modes.containsKey(target.getUniqueId())) {
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
            if (modes.containsKey(p.getUniqueId())) applyFor(p);
        }
    }

    // ==========================================
    // TRANSPARENCY — slight ghost look (no particles, no icon)
    // ==========================================

    private void applyTransparency(Player p) {
        p.addPotionEffect(new PotionEffect(
                PotionEffectType.INVISIBILITY,
                Integer.MAX_VALUE,
                0,
                false, // ambient
                false, // particles
                false  // icon
        ));
    }

    private void removeTransparency(Player p) {
        p.removePotionEffect(PotionEffectType.INVISIBILITY);
    }

    // ==========================================
    // NO COLLISION
    // ==========================================

    private void setupNoCollisionTeam(Collection<Player> players) {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = board.getTeam(NO_COLLIDE_TEAM);

        if (team == null) {
            team = board.registerNewTeam(NO_COLLIDE_TEAM);
            team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
        }

        for (Player p : players) {
            if (!team.hasEntry(p.getName())) team.addEntry(p.getName());
        }
    }
}
