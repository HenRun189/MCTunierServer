package me.HenRun189.tunierServer.commands;

import me.HenRun189.tunierServer.cast.CastGUI;
import me.HenRun189.tunierServer.cast.CastManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;

public class CastCommand implements CommandExecutor, TabCompleter {

    private final CastManager castManager;
    private final CastGUI     castGUI;

    public CastCommand(CastManager castManager, CastGUI castGUI) {
        this.castManager = castManager;
        this.castGUI     = castGUI;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        // Kein Argument → GUI öffnen (nur für Cast-Mitglieder)
        if (args.length == 0) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage("§cNur Spieler können das Cast-Menü öffnen!");
                return true;
            }
            if (!castManager.isCast(p.getUniqueId())) {
                p.sendMessage(Component.text("§cDu bist kein Cast-Mitglied!"));
                return true;
            }
            castGUI.openMain(p);
            return true;
        }

        // Unterkommandos → nur für OPs
        if (!sender.isOp()) {
            sender.sendMessage("§cKeine Rechte!");
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {

            case "add" -> {
                if (args.length < 2) { sender.sendMessage("§cUsage: /cast add <Spieler>"); return true; }
                Player target = Bukkit.getPlayerExact(args[1]);
                UUID uuid;
                String name;
                if (target != null) {
                    uuid = target.getUniqueId();
                    name = target.getName();
                } else {
                    @SuppressWarnings("deprecation")
                    OfflinePlayer off = Bukkit.getOfflinePlayer(args[1]);
                    uuid = off.getUniqueId();
                    name = off.getName() != null ? off.getName() : args[1];
                }
                castManager.addCast(uuid);
                sender.sendMessage(Component.text("§a✔ §f" + name + " §7ist jetzt Cast-Mitglied."));
                if (target != null)
                    target.sendMessage(Component.text("§6Du bist jetzt §lCast§6! Nutze §e/cast §6für das Menü."));
            }

            case "remove" -> {
                if (args.length < 2) { sender.sendMessage("§cUsage: /cast remove <Spieler>"); return true; }
                Player target = Bukkit.getPlayerExact(args[1]);
                UUID uuid;
                String name;
                if (target != null) {
                    uuid = target.getUniqueId();
                    name = target.getName();
                } else {
                    @SuppressWarnings("deprecation")
                    OfflinePlayer off = Bukkit.getOfflinePlayer(args[1]);
                    uuid = off.getUniqueId();
                    name = off.getName() != null ? off.getName() : args[1];
                }
                castManager.removeCast(uuid);
                sender.sendMessage(Component.text("§c✖ §f" + name + " §7ist kein Cast-Mitglied mehr."));
                if (target != null)
                    target.sendMessage(Component.text("§cDu wurdest als Cast-Mitglied entfernt."));
            }

            case "list" -> {
                Set<UUID> cast = castManager.getCastPlayers();
                if (cast.isEmpty()) {
                    sender.sendMessage(Component.text("§7Keine Cast-Mitglieder registriert."));
                    return true;
                }
                sender.sendMessage(Component.text("§6§lCast-Mitglieder §8(" + cast.size() + "):"));
                for (UUID uuid : cast) {
                    Player p = Bukkit.getPlayer(uuid);
                    String status = p != null ? "§a● Online" : "§8● Offline";
                    @SuppressWarnings("deprecation")
                    String name = p != null ? p.getName()
                        : (Bukkit.getOfflinePlayer(uuid).getName() != null
                            ? Bukkit.getOfflinePlayer(uuid).getName()
                            : uuid.toString());
                    sender.sendMessage(Component.text("  §f" + name + " " + status));
                }
            }

            default -> {
                sender.sendMessage("§6Cast-Befehle:");
                sender.sendMessage("§e/cast §7– Cast-Menü öffnen");
                sender.sendMessage("§e/cast add <Spieler> §7– Cast-Mitglied hinzufügen §8(OP)");
                sender.sendMessage("§e/cast remove <Spieler> §7– Cast-Mitglied entfernen §8(OP)");
                sender.sendMessage("§e/cast list §7– Alle Cast-Mitglieder §8(OP)");
            }
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1) return filterStart(List.of("add", "remove", "list"), args[0]);
        if (args.length == 2 && (args[0].equalsIgnoreCase("add") || args[0].equalsIgnoreCase("remove"))) {
            List<String> names = new ArrayList<>();
            Bukkit.getOnlinePlayers().forEach(p -> names.add(p.getName()));
            return filterStart(names, args[1]);
        }
        return List.of();
    }

    private List<String> filterStart(List<String> options, String prefix) {
        return options.stream()
            .filter(s -> s.toLowerCase().startsWith(prefix.toLowerCase()))
            .toList();
    }
}
