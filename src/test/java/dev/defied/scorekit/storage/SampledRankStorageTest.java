package dev.defied.scorekit.storage;

import dev.defied.scorekit.codec.ScoreType;
import dev.defied.scorekit.storage.memory.InMemoryStorage;
import dev.defied.scorekit.storage.memory.InMemoryStorageFactory;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.fail;

class SampledRankStorageTest extends VersionedStorageContract {

    @Override
    protected <S> VersionedStorage<S> create(ScoreType<S> type) {
        return new SampledRankStorage<>(new InMemoryStorage<>(type), type, new RankSampling(1_000, Duration.ofHours(1)));
    }

    @Test
    void ranksAheadOfTheFirstSampleStayExact() {
        SampledRankStorage<Double> storage = sampled(Duration.ofHours(1));
        storage.resample();

        storage.put("leader", 1_000.0);

        assertEquals(OptionalLong.of(1), storage.rank("leader"));
        assertEquals(OptionalLong.of(2), storage.rank("k99"));
    }

    @Test
    void deepRanksDriftByTheChangesSinceSampling() {
        SampledRankStorage<Double> storage = sampled(Duration.ofHours(1));
        storage.resample();
        storage.put("leader", 1_000.0);

        assertEquals(OptionalLong.of(100), storage.rank("k0"));

        storage.resample();
        assertEquals(OptionalLong.of(101), storage.rank("k0"));
    }

    @Test
    void staleSamplesAreRefreshedInBackground() throws InterruptedException {
        SampledRankStorage<Double> storage = sampled(Duration.ofMillis(20));
        storage.resample();
        storage.put("leader", 1_000.0);
        Thread.sleep(30);

        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (!storage.rank("k0").equals(OptionalLong.of(101))) {
            if (System.nanoTime() > deadline) {
                fail("Samples were not refreshed");
            }
            Thread.sleep(10);
        }
    }

    @Test
    void factoriesWrapStoragesThatSampleRanks() {
        StorageSettings settings = new StorageSettings(true, RankSampling.DEFAULT);

        VersionedStorage<Double> storage = new InMemoryStorageFactory().create(new LeaderboardId("test", "board"), ScoreType.NUMERIC, settings);

        assertInstanceOf(SampledRankStorage.class, storage);
    }

    private static SampledRankStorage<Double> sampled(Duration refresh) {
        SampledRankStorage<Double> storage = new SampledRankStorage<>(new InMemoryStorage<>(ScoreType.NUMERIC), ScoreType.NUMERIC, new RankSampling(10, refresh));
        for (int i = 0; i < 100; i++) {
            storage.put("k" + i, (double) i);
        }
        return storage;
    }
}
