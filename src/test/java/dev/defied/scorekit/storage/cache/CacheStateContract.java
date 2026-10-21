package dev.defied.scorekit.storage.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class CacheStateContract {

    private CacheState state;

    protected abstract CacheState create();

    @BeforeEach
    void createState() {
        state = create();
    }

    @Test
    void newStateIsCold() {
        assertFalse(state.isWarm());
        assertEquals(OptionalLong.empty(), state.tryStartSweep(60_000));
    }

    @Test
    void completedWarmUpMakesStateWarm() {
        long epoch = state.beginWarmUp().orElseThrow();
        assertTrue(state.renewWarmUp(epoch));

        assertTrue(state.completeWarmUp(epoch, 42));

        assertTrue(state.isWarm());
        assertEquals(OptionalLong.empty(), state.beginWarmUp());
    }

    @Test
    void onlyOneWarmUpRunsAtATime() {
        long first = state.beginWarmUp().orElseThrow();

        assertEquals(OptionalLong.empty(), state.beginWarmUp());
        state.abortWarmUp(first);

        long second = state.beginWarmUp().orElseThrow();
        assertTrue(second > first);
        assertFalse(state.renewWarmUp(first));
        assertFalse(state.completeWarmUp(first, 42));
        assertTrue(state.renewWarmUp(second));
    }

    @Test
    void sweepStartsFromWarmUpWatermarkOncePerInterval() {
        long epoch = state.beginWarmUp().orElseThrow();
        state.completeWarmUp(epoch, 42);

        assertEquals(OptionalLong.of(42), state.tryStartSweep(60_000));
        assertEquals(OptionalLong.empty(), state.tryStartSweep(60_000));
    }

    @Test
    void completedSweepAdvancesWatermark() throws InterruptedException {
        long epoch = state.beginWarmUp().orElseThrow();
        state.completeWarmUp(epoch, 42);
        state.tryStartSweep(1).orElseThrow();

        state.completeSweep(99);
        Thread.sleep(5);

        assertEquals(OptionalLong.of(99), state.tryStartSweep(1));
    }
}
