package io.github.mapreset.io;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

public final class SafeFiles {
    private SafeFiles() { }
    public static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return;
        if (Files.isSymbolicLink(root)) throw new IOException("Refusing to delete symbolic link: " + root);
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override public java.nio.file.FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (Files.isSymbolicLink(file)) throw new IOException("Refusing to traverse symbolic link: " + file);
                Files.delete(file); return java.nio.file.FileVisitResult.CONTINUE;
            }
            @Override public java.nio.file.FileVisitResult postVisitDirectory(Path dir, IOException ex) throws IOException {
                if (ex != null) throw ex;
                Files.delete(dir); return java.nio.file.FileVisitResult.CONTINUE;
            }
        });
    }
}
