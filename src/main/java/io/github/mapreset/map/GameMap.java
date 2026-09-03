package io.github.mapreset.map;

import java.util.Objects;

public final class GameMap {
    private final String name;
    private final String worldName;
    private volatile MapState state = MapState.READY;
    private volatile String error = "";
    private volatile RestoreMetrics lastRestore = RestoreMetrics.empty();

    public GameMap(String name, String worldName) {
        this.name = Objects.requireNonNull(name);
        this.worldName = Objects.requireNonNull(worldName);
    }

    public String name() { return name; }
    public String worldName() { return worldName; }
    public MapState state() { return state; }
    public String error() { return error; }
    public RestoreMetrics lastRestore() { return lastRestore; }
    public void state(MapState value) { state = value; }
    public void error(String value) { error = value == null ? "" : value; state = MapState.ERROR; }
    public void ready() { error = ""; state = MapState.READY; }
    public void lastRestore(RestoreMetrics value) { lastRestore = value; }
}
