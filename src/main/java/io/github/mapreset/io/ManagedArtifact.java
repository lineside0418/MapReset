package io.github.mapreset.io;

import java.nio.file.Path;
import java.util.regex.Pattern;

public final class ManagedArtifact {
    private static final Pattern PATH = Pattern.compile("^(region|entities|poi)/(?:r|c)\\.-?\\d+\\.-?\\d+\\.(?:mca|mcc)$");
    private ManagedArtifact() { }
    public static boolean isAllowed(String relative) { return PATH.matcher(relative.replace('\\', '/')).matches(); }
    public static String normalize(Path relative) {
        String value = relative.normalize().toString().replace('\\', '/');
        if (!isAllowed(value)) throw new IllegalArgumentException("Unmanaged region artifact: " + value);
        return value;
    }
}
