package io.github.mapreset.command;

import io.github.mapreset.MapResetPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.Bukkit;

import java.util.List;

public final class MapResetTabCompleter implements TabCompleter {
    private final MapResetPlugin plugin;
    public MapResetTabCompleter(MapResetPlugin plugin) { this.plugin = plugin; }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("mapreset.admin") && !(sender instanceof org.bukkit.command.ConsoleCommandSender)) return List.of();
        if (args.length == 1) return match(args[0], List.of("create", "delete", "template", "restore", "status", "list", "reload"));
        if (args.length == 2 && args[0].equalsIgnoreCase("template")) return match(args[1], List.of("create", "info"));
        if (args.length == 3 && args[0].equalsIgnoreCase("create")) return match(args[2], Bukkit.getWorlds().stream().map(w -> w.getName()).toList());
        if ((args.length == 2 && List.of("delete", "restore", "status").contains(args[0].toLowerCase())) || (args.length == 3 && args[0].equalsIgnoreCase("template"))) return match(args[args.length - 1], plugin.maps().all().stream().map(m -> m.name()).toList());
        return List.of();
    }
    private List<String> match(String prefix, List<String> candidates) {
        String needle = prefix.toLowerCase(java.util.Locale.ROOT);
        return candidates.stream().filter(v -> v.toLowerCase(java.util.Locale.ROOT).startsWith(needle)).sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }
}
