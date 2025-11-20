package com.castlewar.ai.assassin;

import com.badlogic.gdx.ai.fsm.DefaultStateMachine;
import com.castlewar.ai.AiAgent;
import com.castlewar.ai.AiContext;
import com.castlewar.entity.Assassin;

public final class AssassinAgent extends AiAgent<Assassin> {
    private final DefaultStateMachine<Assassin, AssassinState> stateMachine;

    public AssassinAgent(Assassin owner, AiContext context) {
        super(owner, context);
        this.stateMachine = new DefaultStateMachine<>(owner, AssassinState.SNEAK);
        setStateMachine(stateMachine);
    }

    public void changeState(AssassinState nextState) {
        stateMachine.changeState(nextState);
    }
}
