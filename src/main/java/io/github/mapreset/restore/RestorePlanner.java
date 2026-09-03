package io.github.mapreset.restore;

import io.github.mapreset.io.FileHasher;
import io.github.mapreset.io.FileInfo;
import io.github.mapreset.template.TemplateFileEntry;
import io.github.mapreset.template.TemplateManifest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class RestorePlanner {
    private final FileHasher hasher;
    public RestorePlanner(FileHasher hasher) { this.hasher = hasher; }
    public RestorePlan plan(TemplateManifest manifest, Map<String, FileInfo> current) throws IOException {
        Map<String, TemplateFileEntry> template = new HashMap<>();
        for (TemplateFileEntry entry : manifest.files()) template.put(entry.path(), entry);
        List<RestoreOperation> operations = new ArrayList<>();
        long hashed = 0, bytesHashed = 0, unchanged = 0;
        for (TemplateFileEntry expected : manifest.files()) {
            FileInfo actual = current.remove(expected.path());
            if (actual == null || actual.size() != expected.size()) {
                operations.add(new RestoreOperation(RestoreOperation.Type.COPY, expected.path(), expected, actual));
                continue;
            }
            hashed++; bytesHashed += actual.size();
            if (!expected.hash().equals(hasher.sha256(actual.path()))) operations.add(new RestoreOperation(RestoreOperation.Type.COPY, expected.path(), expected, actual));
            else unchanged++;
        }
        for (FileInfo extra : current.values()) operations.add(new RestoreOperation(RestoreOperation.Type.DELETE, extra.relativePath(), null, extra));
        return new RestorePlan(List.copyOf(operations), template.size() + current.size(), hashed, bytesHashed, unchanged);
    }
}
