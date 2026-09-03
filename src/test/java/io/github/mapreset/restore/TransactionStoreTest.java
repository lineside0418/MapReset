package io.github.mapreset.restore;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TransactionStoreTest {
    @TempDir Path temp;
    @Test void transactionSurvivesReloadUntilSuccess() throws Exception {
        TransactionStore store = new TransactionStore(temp);
        RestoreTransaction tx = store.create("battle", "battle_world", List.of("COPY:region/r.0.0.mca"));
        store.complete("battle", tx, "COPY:region/r.0.0.mca");
        RestoreTransaction reloaded = store.load("battle");
        assertEquals("RESTORING", reloaded.state);
        assertEquals(List.of("COPY:region/r.0.0.mca"), reloaded.filesCompleted);
        store.success("battle", tx); store.remove("battle");
        assertTrue(store.incomplete().isEmpty());
    }
}
