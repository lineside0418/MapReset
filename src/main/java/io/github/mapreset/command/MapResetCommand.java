package io.github.mapreset.command;

import io.github.mapreset.MapResetPlugin;
import io.github.mapreset.map.GameMap;
import io.github.mapreset.map.MapManager;
import io.github.mapreset.map.MapState;
import io.github.mapreset.map.RestoreMetrics;
import io.github.mapreset.template.TemplateManifest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MapResetCommand implements CommandExecutor {
    private final MapResetPlugin plugin;
    public MapResetCommand(MapResetPlugin plugin) { this.plugin = plugin; }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof ConsoleCommandSender) && !sender.hasPermission("mapreset.admin")) { reply(sender, "no-permission", Map.of()); return true; }
        if (args.length == 0) return usage(sender);
        try {
            return switch (args[0].toLowerCase(Locale.ROOT)) {
                case "create" -> create(sender, args);
                case "delete" -> delete(sender, args);
                case "restore" -> restore(sender, args);
                case "status" -> status(sender, args);
                case "list" -> args.length == 1 ? list(sender) : usage(sender);
                case "reload" -> args.length == 1 ? reload(sender) : usage(sender);
                case "template" -> template(sender, args);
                default -> usage(sender);
            };
        } catch (IllegalArgumentException ex) { reply(sender, "command-error", Map.of("reason", ex.getMessage())); return true; }
    }
    private boolean create(CommandSender sender, String[] args) {
        if (args.length != 3) return usage(sender);
        validate(args[1], "map name"); validate(args[2], "world name");
        if (!plugin.maps().add(new GameMap(args[1], args[2]))) { reply(sender, "map-exists", Map.of("map", args[1])); return true; }
        plugin.persistMaps(); Map<String, Object> values = Map.of("map", args[1], "world", args[2]); reply(sender, "created", values); plugin.notifications().send("created", values, sender); return true;
    }
    private boolean delete(CommandSender sender, String[] args) {
        if (args.length != 2) return usage(sender);
        GameMap map = find(args[1]);
        if (map.state() == MapState.RESTORING || map.state() == MapState.CREATING_TEMPLATE) { reply(sender, "map-busy", Map.of("map", map.name(), "state", map.state())); return true; }
        plugin.maps().remove(map.name()); plugin.persistMaps(); Map<String, Object> values = Map.of("map", map.name(), "world", map.worldName()); reply(sender, "deleted", values); plugin.notifications().send("deleted", values, sender); return true;
    }
    private boolean restore(CommandSender sender, String[] args) { if (args.length != 2) return usage(sender); plugin.restores().startRestore(find(args[1]), sender); return true; }
    private boolean template(CommandSender sender, String[] args) {
        if (args.length != 3) return usage(sender); GameMap map = find(args[2]);
        if ("create".equalsIgnoreCase(args[1])) { plugin.restores().startTemplate(map, sender); return true; }
        if ("info".equalsIgnoreCase(args[1])) {
            plugin.templateExecutor().execute(() -> {
                try {
                    TemplateManifest manifest = plugin.templates().load(map.name());
                    plugin.mainExecutor().execute(() -> reply(sender, "template-info", Map.of("map", map.name(), "world", map.worldName(), "files", manifest.files().size(), "created", manifest.createdAt())));
                } catch (IOException ex) {
                    plugin.mainExecutor().execute(() -> reply(sender, "template-invalid", Map.of("map", map.name(), "reason", ex.getMessage())));
                }
            });
            return true;
        }
        return usage(sender);
    }
    private boolean status(CommandSender sender, String[] args) {
        if (args.length != 2) return usage(sender); GameMap map = find(args[1]); RestoreMetrics m = map.lastRestore();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("map", map.name()); values.put("world", map.worldName()); values.put("state", map.state());
        values.put("error", map.error().isBlank() ? "-" : map.error()); values.put("template", plugin.templates().hasManifest(map.name()) ? "available" : "missing");
        values.put("result", m.result()); values.put("changed", m.filesChanged()); values.put("copied", m.filesCopied()); values.put("deleted", m.filesDeleted()); values.put("duration", m.durationMillis() + "ms");
        plugin.restores().progress(map.name()).ifPresentOrElse(progress -> {
            values.put("phase", progress.phase()); values.put("current", progress.current()); values.put("total", progress.total());
        }, () -> { values.put("phase", "-"); values.put("current", "-"); values.put("total", "-"); });
        reply(sender, "status", values); return true;
    }
    private boolean list(CommandSender sender) { List<GameMap> maps = plugin.maps().all().stream().toList(); reply(sender, maps.isEmpty() ? "list-empty" : "list", Map.of("maps", String.join(", ", maps.stream().map(m -> m.name() + " (" + m.state() + ")").toList()))); return true; }
    private boolean reload(CommandSender sender) {
        MapResetPlugin.ConfigurationReloadResult result = plugin.reloadExternal();
        Map<String, Object> values = result.success() ? Map.of() : Map.of("reason", result.reason());
        String key = result.success() ? "reloaded" : "reload-failed";
        reply(sender, key, values); plugin.notifications().send(key, values, sender); return true;
    }
    private GameMap find(String name) { return plugin.maps().get(name).orElseThrow(() -> new IllegalArgumentException("Unknown map: " + name)); }
    private void validate(String value, String label) { if (!MapManager.isSafeIdentifier(value)) throw new IllegalArgumentException("Invalid " + label + "; use 1-64 letters, numbers, dot, underscore, or hyphen"); }
    private void reply(CommandSender sender, String key, Map<String, ?> values) { plugin.notifications().reply(sender, key, values); }
    private boolean usage(CommandSender sender) { reply(sender, "usage", Map.of()); return true; }
}
