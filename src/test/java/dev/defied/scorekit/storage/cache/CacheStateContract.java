package dev.defied.scorekit.storage.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
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
    void newStateIsColdAndRecordsDirtyKeys() {
        assertFalse(state.isWarm());
        assertTrue(state.markDirtyUnlessWarm("a"));
    }

    @Test
    void warmUpCompletesOnlyWhenNoKeysAreDirty() {
        long epoch = state.beginWarmUp().orElseThrow();
        assertTrue(state.markDirtyUnlessWarm("a"));

        assertFalse(state.completeWarmUp(epoch));
        assertEquals(List.of("a"), state.pollDirty(10));
        assertTrue(state.completeWarmUp(epoch));

        assertTrue(state.isWarm());
        assertFalse(state.markDirtyUnlessWarm("b"));
        assertEquals(OptionalLong.empty(), state.beginWarmUp());
    }

    @Test
    void beginWarmUpDiscardsEarlierDirtyKeys() {
        state.markDirtyUnlessWarm("a");

        state.beginWarmUp().orElseThrow();

        assertEquals(List.of(), state.pollDirty(10));
    }

    @Test
    void onlyOneWarmUpRunsAtATime() {
        long first = state.beginWarmUp().orElseThrow();

        assertEquals(OptionalLong.empty(), state.beginWarmUp());
        state.abortWarmUp(first);

        long second = state.beginWarmUp().orElseThrow();
        assertTrue(second > first);
        assertFalse(state.renewWarmUp(first));
        assertTrue(state.renewWarmUp(second));
    }

    @Test
    void invalidateFencesRunningWarmUp() {
        long epoch = state.beginWarmUp().orElseThrow();

        state.invalidate();

        assertFalse(state.renewWarmUp(epoch));
        assertFalse(state.completeWarmUp(epoch));
        assertFalse(state.isWarm());
        state.abortWarmUp(epoch);
        assertTrue(state.beginWarmUp().isPresent());
    }

    @Test
    void invalidateMakesWarmStateCold() {
        long epoch = state.beginWarmUp().orElseThrow();
        state.completeWarmUp(epoch);

        state.invalidate();

        assertFalse(state.isWarm());
        assertTrue(state.markDirtyUnlessWarm("a"));
    }
}
