package com.castlewar.ai;

import com.castlewar.entity.Unit;

/**
 * Extension interface for Units that wish to use the new shared AI services.
 * Implementations expose getAiAgent() to allow state updates to query timers, awareness, movement, targeting.
 * This is optional; existing NPCs can gradually adopt it.
 */
public interface IServicedUnit {
    /**
     * Returns the agent managing this unit's AI.
     */
    TransitionableAgent<? extends Unit, ?> getAiAgent();
}
