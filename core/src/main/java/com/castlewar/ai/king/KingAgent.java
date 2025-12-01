package com.castlewar.ai.king;

import com.castlewar.ai.AiContext;
import com.castlewar.entity.King;

public class KingAgent {
    private final King owner;
    private final AiContext context;

    public KingAgent(King owner, AiContext context) {
        this.owner = owner;
        this.context = context;
    }

    public void update(float delta) {
        // no-op stub
    }

    public AiContext getContext() {
        return context;
    }

    public com.castlewar.ai.king.KingState getCurrentState() {
        return com.castlewar.ai.king.KingState.IDLE;
    }

    public void changeState(com.castlewar.ai.king.KingState s) {
        // no-op
    }
}
