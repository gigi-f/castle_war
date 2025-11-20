package com.castlewar.ai.king;

import com.badlogic.gdx.ai.fsm.DefaultStateMachine;
import com.castlewar.ai.AiAgent;
import com.castlewar.ai.AiContext;
import com.castlewar.entity.King;

public final class KingAgent extends AiAgent<King> {
    private final DefaultStateMachine<King, KingState> stateMachine;

    public KingAgent(King owner, AiContext context) {
        super(owner, context);
        this.stateMachine = new DefaultStateMachine<>(owner, KingState.PATROL);
        setStateMachine(stateMachine);
    }

    public void changeState(KingState nextState) {
        stateMachine.changeState(nextState);
    }
}
