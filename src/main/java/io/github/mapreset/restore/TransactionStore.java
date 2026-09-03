package io.github.mapreset.restore;

import io.github.mapreset.io.JsonFiles;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

public final class TransactionStore {
    private final Path root;
    public TransactionStore(Path dataFolder) { root = dataFolder.resolve("transactions"); }
    public Path path(String map) { return root.resolve(map + ".json"); }
    public RestoreTransaction create(String map, String world, List<String> pending) throws IOException {
        RestoreTransaction tx = new RestoreTransaction(map, world, pending); write(map, tx); return tx;
    }
    public void phase(String map, RestoreTransaction tx, String phase) throws IOException { tx.phase = phase; write(map, tx); }
    public void complete(String map, RestoreTransaction tx, String file) throws IOException { tx.filesPending.remove(file); tx.filesCompleted.add(file); write(map, tx); appendJournal(map, "completed " + file); }
    public void success(String map, RestoreTransaction tx) throws IOException { tx.state = "SUCCESS"; tx.phase = "COMPLETE"; write(map, tx); }
    public void remove(String map) throws IOException { Files.deleteIfExists(path(map)); Files.deleteIfExists(root.resolve(map + ".journal")); }
    public RestoreTransaction load(String map) throws IOException {
        try { return decode(Files.readString(path(map), StandardCharsets.UTF_8)); }
        catch (RuntimeException ex) { throw new IOException("Invalid transaction", ex); }
    }
    public List<Path> incomplete() throws IOException {
        if (!Files.isDirectory(root)) return List.of();
        try (var stream = Files.list(root)) { return stream.filter(p -> p.getFileName().toString().endsWith(".json")).toList(); }
    }
    private void write(String map, RestoreTransaction tx) throws IOException {
        Files.createDirectories(root);
        Path target = path(map), temp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(temp, encode(tx), StandardCharsets.UTF_8);
        try (var channel = java.nio.channels.FileChannel.open(temp, java.nio.file.StandardOpenOption.WRITE)) { channel.force(true); }
        try { Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (java.nio.file.AtomicMoveNotSupportedException ex) { Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING); }
    }
    private void appendJournal(String map, String line) throws IOException {
        Files.writeString(root.resolve(map + ".journal"), line + System.lineSeparator(), StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        try (var channel = java.nio.channels.FileChannel.open(root.resolve(map + ".journal"), java.nio.file.StandardOpenOption.WRITE)) { channel.force(true); }
    }
    private static String encode(RestoreTransaction tx) {
        return "{\n  \"state\": " + JsonFiles.quote(tx.state) + ",\n  \"phase\": " + JsonFiles.quote(tx.phase) + ",\n  \"map\": " + JsonFiles.quote(tx.map) + ",\n  \"world\": " + JsonFiles.quote(tx.world) + ",\n  \"startedAt\": " + JsonFiles.quote(tx.startedAt) + ",\n  \"filesPending\": " + strings(tx.filesPending) + ",\n  \"filesCompleted\": " + strings(tx.filesCompleted) + "\n}\n";
    }
    private static String strings(List<String> values) { return "[" + values.stream().map(JsonFiles::quote).collect(java.util.stream.Collectors.joining(", ")) + "]"; }
    private static RestoreTransaction decode(String json) {
        RestoreTransaction tx = new RestoreTransaction(); tx.state = JsonFiles.string(json, "state"); tx.phase = JsonFiles.string(json, "phase"); tx.map = JsonFiles.string(json, "map"); tx.world = JsonFiles.string(json, "world"); tx.startedAt = JsonFiles.string(json, "startedAt"); tx.filesPending = JsonFiles.stringArray(json, "filesPending"); tx.filesCompleted = JsonFiles.stringArray(json, "filesCompleted"); return tx;
    }
}
