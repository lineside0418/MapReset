package io.github.mapreset.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class FileSafetyTest {
    @TempDir Path temp;

    @Test void artifactWhitelistAcceptsOnlyManagedRegionFiles() {
        assertTrue(ManagedArtifact.isAllowed("region/r.0.-1.mca"));
        assertTrue(ManagedArtifact.isAllowed("entities/c.-3.7.mcc"));
        assertFalse(ManagedArtifact.isAllowed("playerdata/r.0.0.mca"));
        assertFalse(ManagedArtifact.isAllowed("region/../level.dat"));
        assertFalse(ManagedArtifact.isAllowed("region/r.0.0.mca.restoretmp"));
    }

    @Test void scanReturnsOnlyDirectManagedArtifacts() throws Exception {
        Path region = Files.createDirectories(temp.resolve("region"));
        Files.writeString(region.resolve("r.0.0.mca"), "chunk");
        Files.writeString(region.resolve("notes.txt"), "ignored");
        assertEquals(1, new RegionFileScanner().scan(temp, Set.of("region")).size());
    }

    @Test void hashChangesWhenContentChanges() throws Exception {
        Path file = temp.resolve("r.0.0.mca");
        Files.writeString(file, "first");
        FileHasher hasher = new FileHasher(1024);
        String first = hasher.sha256(file);
        Files.writeString(file, "second");
        assertNotEquals(first, hasher.sha256(file));
    }

    @Test void replacementCopiesThroughDestination() throws Exception {
        Path source = temp.resolve("source.mca"); Path destination = temp.resolve("region/r.0.0.mca");
        Files.writeString(source, "template data");
        new AtomicFileReplacer(1024).replace(source, destination);
        assertEquals("template data", Files.readString(destination));
    }
}
