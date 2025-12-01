package com.castlewar.ai.assassin;

import com.castlewar.ai.AiContext;
import com.castlewar.entity.Assassin;

public class AssassinAgent {
    private final Assassin owner;
    private final AiContext context;

    public AssassinAgent(Assassin owner, AiContext context) {
        this.owner = owner;
        this.context = context;
    }

    public void update(float delta) {
        // no-op stub
    }

    public AiContext getContext() {
        return context;
    }

    public com.castlewar.ai.assassin.AssassinState getCurrentState() {
        return com.castlewar.ai.assassin.AssassinState.IDLE;
    }

    public void changeState(com.castlewar.ai.assassin.AssassinState s) {
        // no-op
    }
}
