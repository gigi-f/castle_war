package com.castlewar.ai.guard;

import com.castlewar.ai.AiContext;
import com.castlewar.entity.Guard;

public class GuardAgent {
    private final Guard owner;
    private final AiContext context;

    public GuardAgent(Guard owner, AiContext context) {
        this.owner = owner;
        this.context = context;
    }

    public void update(float delta) {
        // no-op stub
    }

    public AiContext getContext() {
        return context;
    }

    public com.castlewar.ai.guard.GuardState getCurrentState() {
        return com.castlewar.ai.guard.GuardState.PATROL;
    }

    public void changeState(com.castlewar.ai.guard.GuardState s) {
        // no-op
    }
}
