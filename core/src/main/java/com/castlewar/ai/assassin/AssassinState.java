package com.castlewar.ai.assassin;

import com.badlogic.gdx.ai.fsm.State;
import com.badlogic.gdx.ai.msg.Telegram;
import com.castlewar.ai.AiContext;
import com.castlewar.entity.Assassin;
import com.castlewar.world.GridWorld;

public enum AssassinState implements State<Assassin> {
    SNEAK {
        @Override
        public void update(Assassin assassin) {
            AiContext ctx = assassin.getAiAgent().getContext();
            GridWorld world = ctx != null ? ctx.getGrid() : null;
            if (world == null) {
                return;
            }
            float delta = assassin.getAiDeltaSnapshot();
            assassin.performSneakBehavior(delta, world);

            if (assassin.shouldFlee()) {
                assassin.changeState(ESCAPE);
            } else if (assassin.hasStrikeOpportunity()) {
                assassin.changeState(STRIKE);
            }
        }
    },

    STRIKE {
        @Override
        public void update(Assassin assassin) {
            AiContext ctx = assassin.getAiAgent().getContext();
            GridWorld world = ctx != null ? ctx.getGrid() : null;
            if (world == null) {
                return;
            }
            float delta = assassin.getAiDeltaSnapshot();
            assassin.performStrikeBehavior(delta, world);

            if (assassin.shouldFlee()) {
                assassin.changeState(ESCAPE);
            } else if (!assassin.hasStrikeOpportunity()) {
                assassin.changeState(SNEAK);
            }
        }
    },

    ESCAPE {
        @Override
        public void update(Assassin assassin) {
            AiContext ctx = assassin.getAiAgent().getContext();
            GridWorld world = ctx != null ? ctx.getGrid() : null;
            if (world == null) {
                return;
            }
            float delta = assassin.getAiDeltaSnapshot();
            assassin.performEscapeBehavior(delta, world);

            if (!assassin.shouldFlee()) {
                if (assassin.hasStrikeOpportunity()) {
                    assassin.changeState(STRIKE);
                } else {
                    assassin.changeState(SNEAK);
                }
            }
        }
    };

    @Override
    public void enter(Assassin assassin) {}

    @Override
    public void update(Assassin assassin) {}

    @Override
    public void exit(Assassin assassin) {}

    @Override
    public boolean onMessage(Assassin assassin, Telegram telegram) {
        return false;
    }
}
