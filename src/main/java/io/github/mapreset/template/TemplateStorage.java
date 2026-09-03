package io.github.mapreset.template;

import io.github.mapreset.io.AtomicFileReplacer;
import io.github.mapreset.io.FileHasher;
import io.github.mapreset.io.FileInfo;
import io.github.mapreset.io.ManagedArtifact;
import io.github.mapreset.io.JsonFiles;
import io.github.mapreset.io.RegionFileScanner;
import io.github.mapreset.io.SafeFiles;
import io.github.mapreset.world.WorldProfile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class TemplateStorage {
    private final Path templatesRoot;
    private final RegionFileScanner scanner;
    private final FileHasher hasher;
    private final AtomicFileReplacer copier;

    public TemplateStorage(Path dataFolder, RegionFileScanner scanner, FileHasher hasher, AtomicFileReplacer copier) {
        this.templatesRoot = dataFolder.resolve("templates"); this.scanner = scanner; this.hasher = hasher; this.copier = copier;
    }
    public Path directory(String map) { return templatesRoot.resolve(map).normalize(); }
    public boolean hasManifest(String map) {
        Path root = directory(map);
        Path manifest = root.resolve("metadata.json").normalize();
        return manifest.startsWith(root) && Files.isRegularFile(manifest) && !Files.isSymbolicLink(manifest);
    }
    public TemplateManifest create(String map, WorldProfile profile, Set<String> dirs) throws IOException {
        Files.createDirectories(templatesRoot);
        Path target = directory(map);
        if (!target.getParent().equals(templatesRoot)) throw new IOException("Unsafe template path");
        Path stage = templatesRoot.resolve("." + map + ".new-" + UUID.randomUUID());
        Files.createDirectories(stage);
        try {
            Map<String, FileInfo> files = scanner.scan(profile.artifactPath(), dirs);
            List<TemplateFileEntry> entries = new ArrayList<>();
            for (FileInfo info : files.values()) {
                Path output = stage.resolve(info.relativePath());
                copier.copy(info.path(), output);
                entries.add(new TemplateFileEntry(info.relativePath(), info.size(), hasher.sha256(output)));
            }
            entries.sort(Comparator.comparing(TemplateFileEntry::path));
            TemplateManifest manifest = new TemplateManifest(1, profile.minecraftVersion(), profile.name(), profile.key().toString(), Instant.now().toString(), entries);
            Files.writeString(stage.resolve("metadata.json"), encode(manifest), StandardCharsets.UTF_8);
            replaceDirectory(target, stage);
            return manifest;
        } catch (Throwable failure) {
            SafeFiles.deleteTree(stage);
            throw failure;
        }
    }
    private void replaceDirectory(Path target, Path stage) throws IOException {
        Path previous = templatesRoot.resolve("." + target.getFileName() + ".previous-" + UUID.randomUUID());
        if (Files.exists(target)) Files.move(target, previous, StandardCopyOption.REPLACE_EXISTING);
        try { Files.move(stage, target, StandardCopyOption.ATOMIC_MOVE); }
        catch (java.nio.file.AtomicMoveNotSupportedException ex) { Files.move(stage, target, StandardCopyOption.REPLACE_EXISTING); }
        SafeFiles.deleteTree(previous);
    }
    public TemplateManifest load(String map) throws IOException {
        Path root = directory(map); Path file = root.resolve("metadata.json");
        if (!Files.isRegularFile(file) || Files.isSymbolicLink(file)) throw new IOException("Template manifest is missing");
        TemplateManifest manifest;
        try { manifest = decode(Files.readString(file, StandardCharsets.UTF_8)); }
        catch (RuntimeException ex) { throw new IOException("Template manifest is invalid", ex); }
        if (manifest == null || manifest.version() != 1 || manifest.world() == null || manifest.files() == null) throw new IOException("Template manifest has unsupported fields");
        for (TemplateFileEntry entry : manifest.files()) {
            if (entry == null || !ManagedArtifact.isAllowed(entry.path()) || entry.size() < 0 || entry.hash() == null || !entry.hash().matches("[0-9a-f]{64}")) throw new IOException("Invalid manifest entry");
            Path path = root.resolve(entry.path()).normalize();
            if (!path.startsWith(root) || Files.isSymbolicLink(path) || !Files.isRegularFile(path)) throw new IOException("Template artifact is unsafe or missing: " + entry.path());
        }
        return manifest;
    }
    private static String encode(TemplateManifest manifest) {
        StringBuilder out = new StringBuilder("{\n  \"version\": 1,\n");
        out.append("  \"minecraft\": ").append(JsonFiles.quote(manifest.minecraft())).append(",\n");
        out.append("  \"world\": ").append(JsonFiles.quote(manifest.world())).append(",\n");
        out.append("  \"worldKey\": ").append(JsonFiles.quote(manifest.worldKey())).append(",\n");
        out.append("  \"createdAt\": ").append(JsonFiles.quote(manifest.createdAt())).append(",\n  \"files\": [\n");
        for (int i = 0; i < manifest.files().size(); i++) {
            TemplateFileEntry file = manifest.files().get(i);
            out.append("    {\"path\": ").append(JsonFiles.quote(file.path())).append(", \"size\": ").append(file.size()).append(", \"hash\": ").append(JsonFiles.quote(file.hash())).append("}");
            if (i + 1 < manifest.files().size()) out.append(','); out.append('\n');
        }
        return out.append("  ]\n}\n").toString();
    }
    private static TemplateManifest decode(String json) {
        int version = Math.toIntExact(JsonFiles.number(json, "version"));
        List<TemplateFileEntry> entries = new ArrayList<>();
        java.util.regex.Matcher item = java.util.regex.Pattern.compile("\\{\\s*\\\"path\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"\\s*,\\s*\\\"size\\\"\\s*:\\s*(\\d+)\\s*,\\s*\\\"hash\\\"\\s*:\\s*\\\"([0-9a-f]+)\\\"\\s*}").matcher(JsonFiles.arrayBody(json, "files"));
        while (item.find()) entries.add(new TemplateFileEntry(item.group(1), Long.parseLong(item.group(2)), item.group(3)));
        return new TemplateManifest(version, JsonFiles.string(json, "minecraft"), JsonFiles.string(json, "world"), JsonFiles.string(json, "worldKey"), JsonFiles.string(json, "createdAt"), entries);
    }
}
