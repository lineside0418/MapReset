package io.github.mapreset.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public final class MessageBundle {
    private final YamlConfiguration messages;
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final PlainTextComponentSerializer PLAIN_TEXT = PlainTextComponentSerializer.plainText();

    private MessageBundle(YamlConfiguration messages) { this.messages = messages; }

    /** Loads messages strictly so a bad edit cannot silently replace active feedback. */
    public static MessageBundle load(File file) {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (IOException | InvalidConfigurationException ex) {
            throw new IllegalArgumentException("messages.yml is invalid: " + ex.getMessage(), ex);
        }
        return new MessageBundle(yaml);
    }

    public String render(String key, Map<String, ?> values, String prefix) {
        String result = messages.getString(key, fallback(key));
        result = result.replace("<prefix>", prefix);
        for (Map.Entry<String, ?> entry : values.entrySet()) result = result.replace("<" + entry.getKey() + ">", String.valueOf(entry.getValue()));
        // maps.yml/messages.yml from pre-1.0 custom-mode builds may retain this
        // optional placeholder. Keep those installations readable after migration.
        if ("created".equals(key)) result = result.replace(" (<mode>)", "").replace("<mode>", "");
        return result;
    }
    public Component renderComponent(String key, Map<String, ?> values, String prefix) {
        String input = render(key, values, prefix);
        try {
            return MINI_MESSAGE.deserialize(input);
        } catch (RuntimeException ex) {
            // A malformed administrator edit must never prevent a lifecycle error from reaching players.
            return Component.text(input);
        }
    }
    public String renderPlain(String key, Map<String, ?> values, String prefix) {
        return PLAIN_TEXT.serialize(renderComponent(key, values, prefix));
    }
    private static String fallback(String key) {
        return switch (key) {
            case "start" -> "<prefix><aqua><bold><map></bold> の復元を開始します</aqua><gray> — World: <white><world>";
            case "template-start" -> "<prefix><aqua><bold><map></bold> のTemplate作成を開始します";
            case "template-complete" -> "<prefix><green><bold><map></bold> のTemplate作成が完了しました</green><gray> — Files: <white><files>";
            case "template-info" -> "<prefix><aqua>Template <white><map></white></aqua><gray> — World: <white><world></white>, Files: <white><files></white>, Created: <white><created>";
            case "template-invalid" -> "<prefix><red>Template <white><map></white> は使用できません: <reason>";
            case "aborted-players" -> "<prefix><red><map> の復元を中止しました。</red><gray> <white><world></white> に <yellow><players></yellow> players が残っています。";
            case "completed" -> "<prefix><green><bold><map> の復元が完了しました</bold></green><gray> — Changed: <white><changed></white>, Restored: <white><restored></white>, Deleted: <white><deleted></white>, Copied: <white><copied></white>, Duration: <white><duration>";
            case "completed-warning" -> "<prefix><gold><bold><map> の復元は警告付きで完了しました</bold></gold><gray> — Result: <yellow><result></yellow>, Changed: <white><changed></white>, Duration: <white><duration>";
            case "progress" -> "<prefix><yellow><map></yellow><gray> <phase>: <white><current></white> / <white><total></white>, Changed: <white><changed>";
            case "error" -> "<prefix><red><bold><map> は ERROR です</bold></red><gray>: <white><reason>";
            case "recovery" -> "<prefix><gold><bold>WARNING</bold>: <white><map></white> has an incomplete restore transaction. Run <yellow>/mapreset restore <map></yellow> to recover.";
            case "reloaded" -> "<prefix><green>Configuration and messages were reloaded.";
            case "reload-failed" -> "<prefix><red>Reload was rejected: <white><reason></white>.</red><gray> The previous settings remain active.";
            case "created" -> "<prefix><green>Registered <white><map></white> → <white><world></white>.";
            case "deleted" -> "<prefix><yellow>Unregistered <white><map></white>.</yellow><gray> Templates and backups were kept.";
            case "map-exists" -> "<prefix><red>Map <white><map></white> is already registered.";
            case "map-busy" -> "<prefix><red><map> is busy</red><gray> (state: <white><state></white>). Wait for it to finish, then retry.";
            case "list" -> "<prefix><aqua>Registered maps</aqua><gray>: <white><maps>";
            case "list-empty" -> "<prefix><gray>No maps are registered. Start with <white>/mapreset create <map> <world>";
            case "status" -> "<prefix><aqua><bold>Map: <map></bold></aqua>\n<gray>World: <white><world>\nState: <white><state>\nPhase: <white><phase></white> <gray>(<white><current></white>/<white><total></white>)\nTemplate: <white><template>\nLast restore: <white><result></white> <gray>— Changed: <white><changed></white>, Copied: <white><copied></white>, Deleted: <white><deleted></white>, Duration: <white><duration></white>\nError: <white><error>";
            case "usage" -> "<gray>Usage: <white>/mapreset <create|delete|template|restore|status|list|reload>";
            case "no-permission" -> "<red>You need the <white>mapreset.admin <red>permission.";
            case "command-error" -> "<prefix><red>MapReset: <reason>";
            case "queued" -> "<prefix><yellow><map></yellow><gray>: queued; validating world and template.";
            case "phase" -> "<prefix><yellow><map></yellow><gray>: <phase>";
            case "nms-custom-disabled" -> "<prefix><red><map>: custom-dimension.nms-reload is disabled. No files were changed.";
            case "custom-warning" -> "<prefix><gold><map> uses Paper 1.21.11 NMS custom-dimension reload; a backup is required.";
            case "reload-warning" -> "<prefix><gold><map>: reload verification mismatch was allowed by configuration: <reason>";
            default -> key;
        };
    }
}
