package io.github.mapreset.restore;

import java.util.List;

public record RestorePlan(List<RestoreOperation> operations, long scanned, long hashed, long bytesHashed, long unchanged) { }
