package me.HenRun189.tunierServer.commands;

import me.HenRun189.tunierServer.team.*;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;

public class TeamCommand implements CommandExecutor, TabCompleter {

    private final TeamManager manager;

    private static final List<String> SUBS = List.of(
            "create", "join", "leave", "add", "remove", "edit", "delete", "list"
    );

    public TeamCommand(TeamManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        // nur bestimmte cmds op
        boolean isList = args.length >= 1 && args[0].equalsIgnoreCase("list");

        if (!sender.isOp() && !isList) {
            sender.sendMessage("§cKeine Rechte!");
            return true;
        }

        if (!(sender instanceof Player p)) {
            sender.sendMessage("§cNur Spieler können das nutzen!");
            return true;
        }

        if (args.length == 0) {
            sendHelp(p);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {

            case "create" -> {
                if (args.length < 4) {
                    p.sendMessage("§cUsage: /team create <name> <color> <prefix>");
                    return true;
                }

                ChatColor color = parseColor(args[2], p);
                if (color == null) return true;

                manager.createTeam(args[1], color, args[3]);
                p.sendMessage("§aTeam erstellt!");
            }

            case "join" -> {
                if (args.length < 2) {
                    p.sendMessage("§cUsage: /team join <name>");
                    return true;
                }

                if (manager.getTeam(args[1]) == null) {
                    p.sendMessage("§cTeam existiert nicht!");
                    return true;
                }

                manager.joinTeam(args[1], p);
                p.sendMessage("§aDu bist dem Team beigetreten!");
            }

            case "leave" -> {
                manager.removePlayer(p);
                p.sendMessage("§cDu hast dein Team verlassen!");
            }

            case "add" -> {
                if (args.length < 3) {
                    p.sendMessage("§cUsage: /team add <team> <spieler>");
                    return true;
                }

                Player target = Bukkit.getPlayerExact(args[2]);
                if (target == null) {
                    p.sendMessage("§cSpieler nicht gefunden!");
                    return true;
                }

                if (manager.getTeam(args[1]) == null) {
                    p.sendMessage("§cTeam existiert nicht!");
                    return true;
                }

                manager.addPlayerToTeam(args[1], target);
                p.sendMessage("§aSpieler hinzugefügt!");
            }

            case "remove" -> {
                if (args.length < 2) {
                    p.sendMessage("§cUsage: /team remove <spieler>");
                    return true;
                }

                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    p.sendMessage("§cSpieler nicht gefunden!");
                    return true;
                }

                manager.removePlayer(target);
                p.sendMessage("§cSpieler entfernt!");
            }

            case "edit" -> {
                if (args.length < 4) {
                    p.sendMessage("§cUsage: /team edit <team> <color> <prefix>");
                    return true;
                }

                ChatColor color = parseColor(args[2], p);
                if (color == null) return true;

                if (manager.getTeam(args[1]) == null) {
                    p.sendMessage("§cTeam existiert nicht!");
                    return true;
                }

                manager.editTeam(args[1], color, args[3]);
                p.sendMessage("§aTeam bearbeitet!");
            }

            case "delete" -> {
                if (args.length < 2) {
                    p.sendMessage("§cUsage: /team delete <name>");
                    return true;
                }

                manager.deleteTeam(args[1]);
                p.sendMessage("§cTeam gelöscht!");
            }

            case "list" -> {
                p.sendMessage("§6§lTeams:");

                for (TeamData team : manager.getTeams().values()) {

                    StringBuilder players = new StringBuilder();

                    for (UUID uuid : team.getPlayers()) {
                        Player pl = Bukkit.getPlayer(uuid);
                        if (pl != null && pl.isOnline()) {
                            players.append(pl.getName()).append(", ");
                        }
                    }

                    String playerList = players.length() > 0
                            ? players.substring(0, players.length() - 2)
                            : "keine";

                    p.sendMessage("§e" + team.getName() +
                            " §7[" + team.getPlayers().size() + "] §8→ §f" + playerList);
                }
            }

            default -> p.sendMessage("§cUnbekannter Befehl!");
        }

        return true;
    }

    private void sendHelp(Player p) {
        p.sendMessage("§6§lTeam Commands:");
        p.sendMessage("§e/team create <name> <color> <prefix>");
        p.sendMessage("§e/team join <name>");
        p.sendMessage("§e/team leave");
        p.sendMessage("§e/team add <team> <spieler>");
        p.sendMessage("§e/team remove <spieler>");
        p.sendMessage("§e/team edit <team> <color> <prefix>");
        p.sendMessage("§e/team delete <name>");
        p.sendMessage("§e/team list");
    }

    private ChatColor parseColor(String input, Player p) {
        try {
            return ChatColor.valueOf(input.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            p.sendMessage("§cUngültige Farbe!");
            return null;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {

        // Nur bestimmte Sachen für OP blocken
        boolean isList = args.length >= 1 && args[0].equalsIgnoreCase("list");

        if (!sender.isOp() && !isList) {
            return Collections.emptyList();
        }

        if (args.length == 1) {

            if (!sender.isOp()) {
                return filter(List.of("list"), args[0]); //  nur das sehen normale Spieler
            }

            return filter(SUBS, args[0]); //  OP sieht alles
        }

        if (args.length == 2) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "join", "delete", "edit", "add" -> {
                    return filter(new ArrayList<>(manager.getTeams().keySet()), args[1]);
                }
                case "remove" -> {
                    return filter(Bukkit.getOnlinePlayers().stream()
                            .map(Player::getName)
                            .toList(), args[1]);
                }
            }
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("add")) {
            return filter(Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .toList(), args[2]);
        }

        if ((args.length == 3 && args[0].equalsIgnoreCase("create")) ||
                (args.length == 3 && args[0].equalsIgnoreCase("edit"))) {

            return filter(Arrays.stream(ChatColor.values())
                    .map(Enum::name)
                    .toList(), args[2]);
        }

        return Collections.emptyList();
    }

    private List<String> filter(List<String> list, String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();

        for (String s : list) {
            if (s.toLowerCase(Locale.ROOT).startsWith(lower)) {
                result.add(s);
            }
        }

        return result;
    }
}

//GPT 12:30