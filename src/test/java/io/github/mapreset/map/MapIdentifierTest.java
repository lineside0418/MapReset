package io.github.mapreset.map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapIdentifierTest {
    @Test void acceptsNormalMapAndWorldIdentifiers() {
        assertTrue(MapManager.isSafeIdentifier("battle"));
        assertTrue(MapManager.isSafeIdentifier("world_map_map1"));
        assertTrue(MapManager.isSafeIdentifier("arena-02.v2"));
    }

    @Test void rejectsPathAndBlankIdentifiers() {
        assertFalse(MapManager.isSafeIdentifier("../../server"));
        assertFalse(MapManager.isSafeIdentifier(".."));
        assertFalse(MapManager.isSafeIdentifier(""));
        assertFalse(MapManager.isSafeIdentifier("has space"));
    }
}
