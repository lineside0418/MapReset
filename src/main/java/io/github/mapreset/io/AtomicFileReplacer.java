package io.github.mapreset.io;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

public final class AtomicFileReplacer {
    private final int bufferSize;
    public AtomicFileReplacer(int bufferSize) { this.bufferSize = bufferSize; }
    public long replace(Path source, Path destination) throws IOException {
        Files.createDirectories(destination.getParent());
        Path temp = destination.resolveSibling(destination.getFileName() + ".mapreset-tmp-" + UUID.randomUUID());
        long copied = 0;
        try (FileChannel input = FileChannel.open(source, StandardOpenOption.READ);
             FileChannel output = FileChannel.open(temp, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            ByteBuffer buffer = ByteBuffer.allocateDirect(bufferSize);
            while (input.read(buffer) >= 0) {
                buffer.flip();
                while (buffer.hasRemaining()) copied += output.write(buffer);
                buffer.clear();
            }
            output.force(true);
        } catch (Throwable failure) {
            Files.deleteIfExists(temp);
            throw failure;
        }
        try {
            Files.move(temp, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(temp, destination, StandardCopyOption.REPLACE_EXISTING);
        }
        return copied;
    }
    public long copy(Path source, Path destination) throws IOException { return replace(source, destination); }
}
