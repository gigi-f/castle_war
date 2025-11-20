package com.castlewar.ai.guard;

import com.badlogic.gdx.ai.fsm.State;
import com.badlogic.gdx.ai.msg.Telegram;
import com.castlewar.entity.Guard;

/**
 * First-pass guard state that simply proxies legacy behaviour through the FSM pipeline.
 */
public enum GuardState implements State<Guard> {
    ACTIVE {
        @Override
        public void update(Guard guard) {
            guard.runLegacyBehavior();
        }
    };

    @Override
    public void enter(Guard guard) {
        // No-op until guard FSM grows beyond the legacy passthrough state.
    }

    @Override
    public void update(Guard guard) {
        // States can override if they need per-frame work.
    }

    @Override
    public void exit(Guard guard) {
        // Hook for future transitions (e.g. alert -> engage).
    }

    @Override
    public boolean onMessage(Guard guard, Telegram telegram) {
        return false;
    }
}
