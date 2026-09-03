package io.github.mapreset.notification;

import io.github.mapreset.MapResetPlugin;
import io.github.mapreset.config.MessageBundle;
import io.github.mapreset.config.PluginSettings;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;

public final class NotificationManager {
    private final MapResetPlugin plugin;
    public NotificationManager(MapResetPlugin plugin) { this.plugin = plugin; }
    public void send(String messageKey, Map<String, ?> values) {
        send(messageKey, values, null);
    }
    /** Broadcasts to configured administrators, excluding the command source to avoid duplicate feedback. */
    public void send(String messageKey, Map<String, ?> values, CommandSender source) {
        String event = switch (messageKey) {
            case "start", "template-start" -> "start";
            case "aborted-players" -> "aborted";
            case "completed", "completed-warning", "template-complete" -> "completed";
            case "error" -> "error";
            case "recovery" -> "recovery";
            case "progress" -> "progress";
            case "custom-warning", "reload-warning" -> "warning";
            default -> "";
        };
        if (!event.isEmpty() && !plugin.getConfig().getBoolean("notifications.events." + event, true)) return;
        PluginSettings settings = plugin.settings();
        MessageBundle messages = plugin.messages();
        Component message = messages.renderComponent(messageKey, values, settings.prefix());
        if (settings.consoleNotifications() && !(source instanceof ConsoleCommandSender)) {
            plugin.getLogger().info(messages.renderPlain(messageKey, values, settings.prefix()));
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player != source && player.getScoreboardTags().contains(settings.adminTag())) player.sendMessage(message);
        }
    }
    /** Sends a command result regardless of notification-event filters. */
    public void reply(CommandSender sender, String messageKey, Map<String, ?> values) {
        sender.sendMessage(plugin.messages().renderComponent(messageKey, values, plugin.settings().prefix()));
    }
    public boolean canNotify(String event) { return plugin.getConfig().getBoolean("notifications.events." + event, true); }
}
