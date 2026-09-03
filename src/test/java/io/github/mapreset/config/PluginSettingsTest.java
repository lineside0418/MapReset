package io.github.mapreset.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PluginSettingsTest {
    @Test void acceptsTheDefaultOperationalConfiguration() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("notifications.admin-tag", "developer");
        PluginSettings settings = PluginSettings.from(yaml);
        assertEquals("developer", settings.adminTag());
        assertEquals(1, settings.parallelism());
    }

    @Test void rejectsInvalidValuesBeforeTheyCanReplaceActiveSettings() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("notifications.admin-tag", "developer");
        yaml.set("io.parallelism", 0);
        assertThrows(IllegalArgumentException.class, () -> PluginSettings.from(yaml));
    }
}
