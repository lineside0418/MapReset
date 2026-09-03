package io.github.mapreset.io;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class RegionFileScanner {
    public Map<String, FileInfo> scan(Path root, Set<String> directories) throws IOException {
        if (Files.isSymbolicLink(root)) throw new IOException("World/template root is a symbolic link: " + root);
        Map<String, FileInfo> files = new LinkedHashMap<>();
        for (String directory : directories) {
            Path dir = root.resolve(directory).normalize();
            if (!dir.startsWith(root.normalize())) throw new IOException("Unsafe managed directory");
            if (!Files.exists(dir, LinkOption.NOFOLLOW_LINKS)) continue;
            if (Files.isSymbolicLink(dir) || !Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Managed directory is not a real directory: " + dir);
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
                for (Path file : stream) {
                    if (Files.isSymbolicLink(file)) throw new IOException("Symbolic link in managed directory: " + file);
                    if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) continue;
                    String rawRelative = root.relativize(file).normalize().toString().replace('\\', '/');
                    if (!ManagedArtifact.isAllowed(rawRelative)) continue;
                    String relative = ManagedArtifact.normalize(root.relativize(file));
                    files.put(relative, new FileInfo(relative, file, Files.size(file)));
                }
            }
        }
        return files;
    }
}
