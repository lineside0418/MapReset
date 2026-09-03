package io.github.mapreset;

import io.github.mapreset.command.MapResetCommand;
import io.github.mapreset.command.MapResetTabCompleter;
import io.github.mapreset.config.MessageBundle;
import io.github.mapreset.config.PluginSettings;
import io.github.mapreset.io.AsyncIoExecutor;
import io.github.mapreset.io.AtomicFileReplacer;
import io.github.mapreset.io.FileHasher;
import io.github.mapreset.io.RegionFileScanner;
import io.github.mapreset.map.MapManager;
import io.github.mapreset.notification.NotificationManager;
import io.github.mapreset.restore.RestoreManager;
import io.github.mapreset.restore.TransactionStore;
import io.github.mapreset.template.TemplateCreator;
import io.github.mapreset.template.TemplateStorage;
import io.github.mapreset.world.WorldLifecycleManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Executor;

public final class MapResetPlugin extends JavaPlugin implements Listener {
    private final AsyncIoExecutor ioExecutor = new AsyncIoExecutor();
    private volatile PluginSettings settings;
    private volatile MessageBundle messages;
    private MapManager maps;
    private NotificationManager notifications;
    private volatile TemplateStorage templates;
    private volatile RestoreManager restores;

    @Override public void onEnable() {
        saveDefaultConfig();
        if (!new File(getDataFolder(), "messages.yml").exists()) saveResource("messages.yml", false);
        try {
            loadSettings();
            maps = new MapManager(this); maps.load();
            notifications = new NotificationManager(this);
            initializeServices();
        } catch (RuntimeException ex) {
            getLogger().severe("MapReset configuration failed: " + ex.getMessage());
            getServer().getPluginManager().disablePlugin(this); return;
        }
        PluginCommand command = getCommand("mapreset");
        if (command == null) throw new IllegalStateException("plugin.yml command missing");
        command.setExecutor(new MapResetCommand(this)); command.setTabCompleter(new MapResetTabCompleter(this));
        getServer().getPluginManager().registerEvents(this, this);
        restores.recoverIncomplete();
    }
    @Override public void onDisable() {
        if (restores != null) restores.shutdown();
        getServer().getScheduler().cancelTasks(this);
        ioExecutor.close();
    }
    private void loadSettings() { reloadConfig(); settings = PluginSettings.from(getConfig()); messages = MessageBundle.load(new File(getDataFolder(), "messages.yml")); }
    private void initializeServices() {
        ioExecutor.configure(settings.parallelism(), settings.queueLimit());
        RegionFileScanner scanner = new RegionFileScanner();
        FileHasher hasher = new FileHasher(settings.hashBufferBytes());
        AtomicFileReplacer replacer = new AtomicFileReplacer(settings.bufferBytes());
        templates = new TemplateStorage(getDataFolder().toPath(), scanner, hasher, replacer);
        restores = new RestoreManager(this, ioExecutor, templates, new TemplateCreator(templates), scanner, hasher, replacer,
                new TransactionStore(getDataFolder().toPath()), new WorldLifecycleManager(this));
    }
    public ConfigurationReloadResult reloadExternal() {
        if (restores != null && restores.isBusy()) return new ConfigurationReloadResult(false, "A template creation or restore is active");
        PluginSettings oldSettings = settings; MessageBundle oldMessages = messages;
        try { loadSettings(); initializeServices(); return new ConfigurationReloadResult(true, ""); }
        catch (RuntimeException ex) {
            settings = oldSettings; messages = oldMessages;
            String reason = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            getLogger().warning("Configuration reload rejected; previous settings remain active: " + reason);
            return new ConfigurationReloadResult(false, reason);
        }
    }
    @EventHandler public void onWorldLoad(WorldLoadEvent event) { if (restores != null) restores.onWorldLoad(event); }
    public void persistMaps() {
        try { maps.save(); } catch (IOException ex) { getLogger().severe("Could not save maps.yml: " + ex.getMessage()); }
    }
    public PluginSettings settings() { return settings; }
    public MessageBundle messages() { return messages; }
    public MapManager maps() { return maps; }
    public NotificationManager notifications() { return notifications; }
    public TemplateStorage templates() { return templates; }
    public RestoreManager restores() { return restores; }
    public Executor templateExecutor() { return ioExecutor; }
    public Executor mainExecutor() { return getServer().getScheduler().getMainThreadExecutor(this); }
    public record ConfigurationReloadResult(boolean success, String reason) { }
}
