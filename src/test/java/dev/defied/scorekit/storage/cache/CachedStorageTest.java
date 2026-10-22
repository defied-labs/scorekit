package dev.defied.scorekit.storage.cache;

import dev.defied.scorekit.codec.ScoreType;
import dev.defied.scorekit.storage.LeaderboardId;
import dev.defied.scorekit.storage.LeaderboardStorage;
import dev.defied.scorekit.storage.LeaderboardStorageContract;
import dev.defied.scorekit.storage.Parallel;
import dev.defied.scorekit.storage.memory.InMemoryStorage;
import dev.defied.scorekit.storage.memory.InMemoryStorageFactory;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class CachedStorageTest extends LeaderboardStorageContract {

    private static final LeaderboardId ID = new LeaderboardId("test", "board");

    private final InMemoryStorageFactory memory = new InMemoryStorageFactory();
    private final ControlledPrimary<Double> primary = new ControlledPrimary<>(new InMemoryStorage<>(ScoreType.NUMERIC));
    private final ControlledCache<Double> cache = new ControlledCache<>(memory.createCache(ID, ScoreType.NUMERIC));
    private final CachedStorage<Double> cached = new CachedStorage<>(primary, cache);

    @Override
    protected <S> LeaderboardStorage<S> create(ScoreType<S> type) {
        return new CachedStorageFactory(memory, memory).create(ID, type);
    }

    @Test
    void readWhileColdWarmsUpInBackground() throws InterruptedException {
        primary.put("a", 1.0);
        primary.put("b", 2.0);

        assertEquals(Optional.of(1.0), cached.score("a"));
        awaitWarm(cached, cache.state());

        assertEquals(primary.top(10), cache.top(10));
    }

    @Test
    void writesReachBothStorages() {
        cached.warmUp();

        cached.put("a", 3.0);
        assertEquals(Optional.of(3.0), primary.score("a"));
        assertEquals(Optional.of(3.0), cache.score("a"));

        cached.remove("a");
        assertEquals(Optional.empty(), primary.score("a"));
        assertEquals(Optional.empty(), cache.score("a"));
    }

    @Test
    void changesDuringWarmUpAreReconciled() throws InterruptedException {
        primary.put("removed", 5.0);
        primary.put("updated", 1.0);
        CountDownLatch scanned = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        primary.afterScan = () -> {
            scanned.countDown();
            awaitLatch(resume);
        };

        Thread warmer = Thread.ofVirtual().start(cached::warmUp);
        assertTrue(scanned.await(5, TimeUnit.SECONDS));
        cached.remove("removed");
        cached.put("updated", 9.0);
        cached.put("added", 3.0);
        resume.countDown();
        assertTrue(warmer.join(Duration.ofSeconds(5)));

        assertTrue(cache.state().isWarm());
        assertEquals(Optional.empty(), cache.score("removed"));
        assertEquals(Optional.of(9.0), cache.score("updated"));
        assertEquals(primary.top(10), cache.top(10));
    }

    @Test
    void concurrentWritersFromSeveralInstancesConverge() throws Exception {
        List<CachedStorage<Double>> instances = List.of(cached, new CachedStorage<>(primary, cache));
        cached.warmUp();

        Parallel.run(8, () -> {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            for (int i = 0; i < 2_000; i++) {
                CachedStorage<Double> instance = instances.get(random.nextInt(instances.size()));
                String key = "k" + random.nextInt(20);
                if (random.nextInt(4) == 0) {
                    instance.remove(key);
                } else {
                    instance.put(key, (double) random.nextInt(100));
                }
            }
        });

        assertEquals(primary.top(Integer.MAX_VALUE), cache.top(Integer.MAX_VALUE));
    }

    @Test
    void failedCacheWriteIsRepairedBySweep() {
        cached.warmUp();
        cache.failing = true;

        cached.put("a", 1.0);
        cache.failing = false;
        assertEquals(Optional.empty(), cache.score("a"));

        cached.sweep();
        assertEquals(Optional.of(1.0), cache.score("a"));
    }

    @Test
    void writesThatNeverReachedTheCacheAreRepairedBySweep() {
        cached.put("gone", 1.0);
        cached.warmUp();

        primary.put("lost", 7.0);
        primary.remove("gone");
        cached.sweep();

        assertEquals(Optional.of(7.0), cache.score("lost"));
        assertEquals(Optional.empty(), cache.score("gone"));
    }

    @Test
    void failedCacheReadFallsBackToPrimary() {
        cached.put("a", 1.0);
        cached.warmUp();
        cache.failing = true;

        assertEquals(Optional.of(1.0), cached.score("a"));
        assertEquals(OptionalLong.of(1), cached.rank("a"));
    }

    @Test
    void unavailableCacheIsSkippedForAWhile() {
        cached.warmUp();
        cache.failing = true;
        cached.score("a");
        cache.failing = false;
        int calls = cache.calls.get();

        cached.put("a", 1.0);
        cached.score("a");
        cached.top(10);

        assertEquals(calls, cache.calls.get());
        assertEquals(Optional.of(1.0), cached.score("a"));
    }

    @Test
    void closedFactoryRejectsNewStorages() {
        CachedStorageFactory factory = new CachedStorageFactory(memory, memory);
        factory.create(ID, ScoreType.NUMERIC);

        factory.close();

        assertThrows(IllegalStateException.class, () -> factory.create(ID, ScoreType.NUMERIC));
    }

    static void awaitWarm(LeaderboardStorage<?> storage, CacheState state) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (!state.isWarm()) {
            if (System.nanoTime() > deadline) {
                fail("Cache did not warm up");
            }
            storage.size();
            Thread.sleep(10);
        }
    }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
