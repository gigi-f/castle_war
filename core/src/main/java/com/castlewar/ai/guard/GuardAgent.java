package com.castlewar.ai.guard;

import com.badlogic.gdx.ai.fsm.DefaultStateMachine;
import com.castlewar.ai.AiAgent;
import com.castlewar.ai.AiContext;
import com.castlewar.entity.Guard;

/**
 * Thin shim that binds Guard units to a {@link DefaultStateMachine} so we can
 * migrate behaviour incrementally without losing the legacy control flow.
 */
public final class GuardAgent extends AiAgent<Guard> {

    public GuardAgent(Guard owner, AiContext context) {
        super(owner, context);
        setStateMachine(new DefaultStateMachine<>(owner, GuardState.ACTIVE));
    }
}
