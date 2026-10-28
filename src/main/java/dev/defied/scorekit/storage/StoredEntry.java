package dev.defied.scorekit.storage;

import dev.defied.scorekit.codec.ScoreType;

import java.util.Comparator;

public record StoredEntry<S>(String key, S score) {

    public static <S> Comparator<StoredEntry<S>> ranking(ScoreType<S> type) {
        Comparator<StoredEntry<S>> ascending = Comparator.comparing(StoredEntry<S>::score, type.order())
                .thenComparing(StoredEntry::key, ScoreType.LEXICOGRAPHIC.order());
        return ascending.reversed();
    }
}
