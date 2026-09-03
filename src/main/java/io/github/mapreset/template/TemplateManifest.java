package io.github.mapreset.template;

import java.util.List;

public record TemplateManifest(int version, String minecraft, String world, String worldKey,
                               String createdAt, List<TemplateFileEntry> files) { }
