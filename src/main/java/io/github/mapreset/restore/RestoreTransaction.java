package io.github.mapreset.restore;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class RestoreTransaction {
    public String state = "RESTORING";
    public String phase = "PREPARED";
    public String map;
    public String world;
    public String startedAt = Instant.now().toString();
    public List<String> filesPending = new ArrayList<>();
    public List<String> filesCompleted = new ArrayList<>();
    public RestoreTransaction() { }
    public RestoreTransaction(String map, String world, List<String> pending) { this.map = map; this.world = world; this.filesPending = new ArrayList<>(pending); }
}
