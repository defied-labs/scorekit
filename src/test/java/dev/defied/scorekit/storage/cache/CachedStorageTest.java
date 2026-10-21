package dev.defied.scorekit.storage.cache;

import dev.defied.scorekit.codec.ScoreType;
import dev.defied.scorekit.storage.LeaderboardId;
import dev.defied.scorekit.storage.LeaderboardStorage;
import dev.defied.scorekit.storage.LeaderboardStorageContract;
import dev.defied.scorekit.storage.memory.InMemoryStorage;
import dev.defied.scorekit.storage.memory.InMemoryStorageFactory;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class CachedStorageTest extends LeaderboardStorageContract {

    private final ControlledStorage<Double> primary = new ControlledStorage<>(new InMemoryStorage<>(ScoreType.NUMERIC));
    private final ControlledStorage<Double> cache = new ControlledStorage<>(new InMemoryStorage<>(ScoreType.NUMERIC));
    private final CacheState state = new InMemoryStorageFactory().cacheState(new LeaderboardId("test", "board"));
    private final CachedStorage<Double> cached = new CachedStorage<>(primary, cache, state);

    @Override
    protected <S> LeaderboardStorage<S> create(ScoreType<S> type) {
        InMemoryStorageFactory memory = new InMemoryStorageFactory();
        return new CachedStorageFactory(memory, memory).create(new LeaderboardId("test", "board"), type);
    }

    @Test
    void readWhileColdWarmsUpInBackground() throws InterruptedException {
        primary.put("a", 1.0);
        primary.put("b", 2.0);

        assertEquals(Optional.of(1.0), cached.score("a"));
        awaitWarm();

        assertEquals(primary.top(10), cache.top(10));
    }

    @Test
    void writesReachBothStoragesOnceWarm() {
        cached.warmUp();
        assertTrue(state.isWarm());

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

        assertTrue(state.isWarm());
        assertEquals(Optional.empty(), cache.score("removed"));
        assertEquals(Optional.of(9.0), cache.score("updated"));
        assertEquals(primary.top(10), cache.top(10));
    }

    @Test
    void failedCacheWriteInvalidatesAndRecovers() throws InterruptedException {
        cached.warmUp();
        cache.failing = true;

        cached.put("a", 1.0);

        assertFalse(state.isWarm());
        assertEquals(Optional.of(1.0), cached.score("a"));
        cache.failing = false;
        awaitWarm();
        assertEquals(Optional.of(1.0), cache.score("a"));
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
    void clearInvalidatesCache() {
        cached.put("a", 1.0);
        cached.warmUp();

        cached.clear();

        assertFalse(state.isWarm());
        assertEquals(0, cached.size());
    }

    private void awaitWarm() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!state.isWarm()) {
            if (System.nanoTime() > deadline) {
                fail("Cache did not warm up");
            }
            cached.size();
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
