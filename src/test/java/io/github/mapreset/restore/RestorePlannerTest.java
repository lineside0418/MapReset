package io.github.mapreset.restore;

import io.github.mapreset.io.FileHasher;
import io.github.mapreset.io.FileInfo;
import io.github.mapreset.template.TemplateFileEntry;
import io.github.mapreset.template.TemplateManifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RestorePlannerTest {
    @TempDir Path temp;
    @Test void plansMissingChangedAndExtraArtifacts() throws Exception {
        FileHasher hasher = new FileHasher(1024);
        Path equal = temp.resolve("equal.mca"); Path changed = temp.resolve("changed.mca"); Path extra = temp.resolve("extra.mca");
        Files.writeString(equal, "same"); Files.writeString(changed, "wrong"); Files.writeString(extra, "extra");
        TemplateManifest manifest = new TemplateManifest(1, "1.21.11", "world", "minecraft:world", "now", List.of(
                new TemplateFileEntry("region/r.0.0.mca", 4, hasher.sha256(equal)),
                new TemplateFileEntry("entities/r.0.0.mca", 4, hasher.sha256(equal)),
                new TemplateFileEntry("poi/c.0.0.mcc", 4, hasher.sha256(equal))));
        Map<String, FileInfo> current = new LinkedHashMap<>();
        current.put("region/r.0.0.mca", new FileInfo("region/r.0.0.mca", equal, Files.size(equal)));
        current.put("entities/r.0.0.mca", new FileInfo("entities/r.0.0.mca", changed, Files.size(changed)));
        current.put("region/c.1.1.mcc", new FileInfo("region/c.1.1.mcc", extra, Files.size(extra)));
        RestorePlan plan = new RestorePlanner(hasher).plan(manifest, current);
        assertEquals(3, plan.operations().size());
        assertEquals(2, plan.operations().stream().filter(o -> o.type() == RestoreOperation.Type.COPY).count());
        assertEquals(1, plan.operations().stream().filter(o -> o.type() == RestoreOperation.Type.DELETE).count());
        assertEquals(1, plan.unchanged());
    }
}
