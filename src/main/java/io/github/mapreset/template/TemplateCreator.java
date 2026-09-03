package io.github.mapreset.template;

import io.github.mapreset.map.GameMap;
import io.github.mapreset.world.WorldProfile;

import java.io.IOException;
import java.util.Set;

public final class TemplateCreator {
    private final TemplateStorage storage;
    public TemplateCreator(TemplateStorage storage) { this.storage = storage; }
    public TemplateManifest create(GameMap map, WorldProfile profile, Set<String> dirs) throws IOException { return storage.create(map.name(), profile, dirs); }
}
