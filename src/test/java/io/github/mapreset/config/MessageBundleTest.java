package io.github.mapreset.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MessageBundleTest {
    @TempDir Path temp;

    @Test void rendersMiniMessageAndPlaceholdersAsPlainConsoleText() throws Exception {
        Path file = temp.resolve("messages.yml");
        Files.writeString(file, "hello: '<green>Hello <white><name></white></green>'\n");
        MessageBundle bundle = MessageBundle.load(file.toFile());
        assertEquals("Hello Alex", bundle.renderPlain("hello", Map.of("name", "Alex"), "[MapReset] "));
    }

    @Test void missingKeysUseUsefulSafeFallbacks() throws Exception {
        Path file = temp.resolve("messages.yml");
        Files.writeString(file, "{}\n");
        MessageBundle bundle = MessageBundle.load(file.toFile());
        assertEquals("[MapReset] MapReset: bad command", bundle.renderPlain("command-error", Map.of("reason", "bad command"), "[MapReset] "));
    }

    @Test void invalidYamlIsRejectedRatherThanSilentlyReplacingMessages() throws Exception {
        Path file = temp.resolve("messages.yml");
        Files.writeString(file, "message: [unterminated\n");
        assertThrows(IllegalArgumentException.class, () -> MessageBundle.load(file.toFile()));
    }
}
