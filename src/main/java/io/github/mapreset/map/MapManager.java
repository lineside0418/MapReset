package io.github.mapreset.map;

import io.github.mapreset.MapResetPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

public final class MapManager {
    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.-]{0,63}");
    private final MapResetPlugin plugin;
    private final File file;
    private final Map<String, GameMap> maps = new LinkedHashMap<>();

    public MapManager(MapResetPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "maps.yml");
    }

    public void load() {
        maps.clear();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("maps");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) continue;
            try {
                String worldName = section.getString("world-name", "");
                if (!isSafeIdentifier(id) || !isSafeIdentifier(worldName)) throw new IllegalArgumentException("unsafe map or world name");
                GameMap map = new GameMap(id, worldName);
                map.state(parseState(section.getString("state", "READY")));
                map.lastRestore(new RestoreMetrics(
                        section.getLong("last-restore.files-scanned"), section.getLong("last-restore.files-hashed"),
                        section.getLong("last-restore.files-changed"), section.getLong("last-restore.files-copied"),
                        section.getLong("last-restore.files-deleted"), section.getLong("last-restore.bytes-hashed"),
                        section.getLong("last-restore.bytes-copied"), section.getLong("last-restore.duration-millis"),
                        section.getString("last-restore.result", "NEVER")));
                maps.put(id.toLowerCase(Locale.ROOT), map);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Ignoring invalid map entry " + id + ": " + ex.getMessage());
            }
        }
    }

    private MapState parseState(String value) {
        try { return MapState.valueOf(value); } catch (IllegalArgumentException ex) { return MapState.ERROR; }
    }

    public static boolean isSafeIdentifier(String value) {
        return value != null && SAFE_IDENTIFIER.matcher(value).matches() && !value.contains("..");
    }

    public void save() throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        for (GameMap map : maps.values()) {
            String base = "maps." + map.name();
            yaml.set(base + ".world-name", map.worldName());
            yaml.set(base + ".state", map.state().name());
            RestoreMetrics m = map.lastRestore();
            yaml.set(base + ".last-restore.files-scanned", m.filesScanned());
            yaml.set(base + ".last-restore.files-hashed", m.filesHashed());
            yaml.set(base + ".last-restore.files-changed", m.filesChanged());
            yaml.set(base + ".last-restore.files-copied", m.filesCopied());
            yaml.set(base + ".last-restore.files-deleted", m.filesDeleted());
            yaml.set(base + ".last-restore.bytes-hashed", m.bytesHashed());
            yaml.set(base + ".last-restore.bytes-copied", m.bytesCopied());
            yaml.set(base + ".last-restore.duration-millis", m.durationMillis());
            yaml.set(base + ".last-restore.result", m.result());
        }
        yaml.save(file);
    }

    public boolean add(GameMap map) {
        String key = map.name().toLowerCase(Locale.ROOT);
        if (maps.containsKey(key)) return false;
        maps.put(key, map); return true;
    }
    public Optional<GameMap> get(String name) { return Optional.ofNullable(maps.get(name.toLowerCase(Locale.ROOT))); }
    public GameMap remove(String name) { return maps.remove(name.toLowerCase(Locale.ROOT)); }
    public Collection<GameMap> all() { return java.util.List.copyOf(maps.values()); }
}
