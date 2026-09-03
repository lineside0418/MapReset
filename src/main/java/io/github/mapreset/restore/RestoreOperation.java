package io.github.mapreset.restore;

import io.github.mapreset.io.FileInfo;
import io.github.mapreset.template.TemplateFileEntry;

public record RestoreOperation(Type type, String path, TemplateFileEntry template, FileInfo current) {
    public enum Type { COPY, DELETE }
}
