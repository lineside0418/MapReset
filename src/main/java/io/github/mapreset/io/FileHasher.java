package io.github.mapreset.io;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class FileHasher {
    private final int bufferSize;
    public FileHasher(int bufferSize) { this.bufferSize = bufferSize; }
    public String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[bufferSize];
            try (InputStream input = Files.newInputStream(file)) {
                for (int read; (read = input.read(buffer)) >= 0;) if (read > 0) digest.update(buffer, 0, read);
            }
            StringBuilder result = new StringBuilder(64);
            for (byte b : digest.digest()) result.append(String.format("%02x", b));
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("JRE has no SHA-256", impossible);
        }
    }
}
