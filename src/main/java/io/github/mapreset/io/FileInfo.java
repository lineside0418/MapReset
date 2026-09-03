package io.github.mapreset.io;

import java.nio.file.Path;

public record FileInfo(String relativePath, Path path, long size) { }
