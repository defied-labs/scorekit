package dev.defied.scorekit.storage;

import java.util.List;

public record ScanPage<S>(List<StoredEntry<S>> entries, String next) {
}
