package me.HenRun189.tunierServer.listeners;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.HenRun189.tunierServer.team.TeamManager;
import me.HenRun189.tunierServer.team.TeamData;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

public class ChatListener implements Listener {

    private final TeamManager teamManager;

    public ChatListener(TeamManager teamManager) {
        this.teamManager = teamManager;
    }

    @EventHandler
    public void onChat(AsyncChatEvent e) {

        Player p = e.getPlayer();
        TeamData team = teamManager.getTeamByPlayer(p.getUniqueId());

        if (team == null) return;

        TextColor color = adventureColor(team.getColor());

        e.renderer((source, displayName, msg, viewer) ->
                Component.text("[" + team.getPrefix() + "] ", color)
                        .append(Component.text(p.getName(), color))
                        .append(Component.text("» ", NamedTextColor.GRAY))
                        .append(msg.colorIfAbsent(NamedTextColor.WHITE))
        );
    }

    /**
     * Mappt org.bukkit.ChatColor auf Adventure TextColor
     * (ChatColor.asAdventure() existiert in Paper 26.x nicht mehr).
     */
    private static TextColor adventureColor(ChatColor chatColor) {
        return switch (chatColor) {
            case BLACK -> TextColor.color(0x000000);
            case DARK_BLUE -> TextColor.color(0x0000AA);
            case DARK_GREEN -> TextColor.color(0x00AA00);
            case DARK_AQUA -> TextColor.color(0x00AAAA);
            case DARK_RED -> TextColor.color(0xAA0000);
            case DARK_PURPLE -> TextColor.color(0xAA00AA);
            case GOLD -> TextColor.color(0xFFAA00);
            case GRAY -> TextColor.color(0xAAAAAA);
            case DARK_GRAY -> TextColor.color(0x555555);
            case BLUE -> TextColor.color(0x5555FF);
            case GREEN -> TextColor.color(0x55FF55);
            case AQUA -> TextColor.color(0x55FFFF);
            case RED -> TextColor.color(0xFF5555);
            case LIGHT_PURPLE -> TextColor.color(0xFF55FF);
            case YELLOW -> TextColor.color(0xFFFF55);
            case WHITE -> TextColor.color(0xFFFFFF);
            default -> NamedTextColor.WHITE; // Format-Codes (BOLD, RESET, ...) fallen hier durch
        };
    }
}