package dev.snooped.bedrockmenu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BedrockSwimStateTest {
    @Test void advancesFromPreviousActualSwimmingAndReachesExactEndpoints() {
        var state = new BedrockSwimState.Transition();
        state.advance(100, false);
        state.advance(101, true);
        assertEquals(0F, state.amount(), "Starting swimming does not advance the prior-state blend yet");
        for (int tick = 102; tick <= 111; tick++) state.advance(tick, true);
        assertEquals(1F, state.amount());
        state.advance(112, false);
        assertTrue(state.stopped());
        assertEquals(1F, state.amount(), "The stop tick still advances from swimming");
        state.advance(113, false);
        assertFalse(state.stopped());
        assertEquals(.9F, state.amount());
        for (int tick = 114; tick <= 122; tick++) state.advance(tick, false);
        assertEquals(0F, state.amount(), "No float remainder may keep suppressing water jump");
    }

    @Test void repeatedSimulationHookDoesNotAdvanceOrConsumeTheStopEdge() {
        var state = new BedrockSwimState.Transition();
        state.advance(1, true);
        state.advance(2, false);
        assertTrue(state.stopped());
        assertEquals(.1F, state.amount());
        state.advance(2, false);
        assertTrue(state.stopped());
        assertEquals(.1F, state.amount());
        assertTrue(state.isCurrent(2));
        assertFalse(state.isCurrent(3));
    }

    @Test void physicsGapsAndTickRewindsDiscardStaleBlendAndStopEdges() {
        var state = new BedrockSwimState.Transition();
        state.advance(1, true);
        state.advance(2, true);
        state.advance(20, false);
        assertEquals(0F, state.amount());
        assertFalse(state.stopped());
        state.advance(21, true);
        state.advance(22, true);
        state.advance(1, false);
        assertEquals(0F, state.amount());
        assertFalse(state.stopped());
    }
}
