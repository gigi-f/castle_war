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
    private final DefaultStateMachine<Guard, GuardState> stateMachine;

    public GuardAgent(Guard owner, AiContext context) {
        super(owner, context);
        this.stateMachine = new DefaultStateMachine<>(owner, GuardState.PATROL);
        setStateMachine(stateMachine);
    }

    public void changeState(GuardState nextState) {
        stateMachine.changeState(nextState);
    }
}
