package com.castlewar.ai.king;

import com.badlogic.gdx.ai.fsm.State;
import com.badlogic.gdx.ai.msg.Telegram;
import com.castlewar.ai.AiContext;
import com.castlewar.entity.Guard;
import com.castlewar.entity.King;
import com.castlewar.entity.Unit;
import com.castlewar.world.GridWorld;

public enum KingState implements State<King> {
    PATROL {
        @Override
        public void update(King king) {
            AiContext ctx = king.getAiAgent().getContext();
            GridWorld world = ctx != null ? ctx.getGrid() : null;
            if (world == null) {
                return;
            }
            float delta = king.getAiDeltaSnapshot();
            king.performPatrolBehavior(delta, world);

            Unit threat = king.getTargetEnemy();
            if (threat != null && !threat.isDead()) {
                Guard guard = king.findClosestGuard(ctx.getEntities());
                if (guard != null && !guard.isDead()) {
                    king.changeState(HIDE);
                } else {
                    king.changeState(FLEE);
                }
            }
        }
    },

    HIDE {
        @Override
        public void update(King king) {
            AiContext ctx = king.getAiAgent().getContext();
            GridWorld world = ctx != null ? ctx.getGrid() : null;
            if (world == null) {
                return;
            }
            float delta = king.getAiDeltaSnapshot();
            Guard guard = king.findClosestGuard(ctx.getEntities());
            if (guard == null || guard.isDead()) {
                king.changeState(FLEE);
                return;
            }

            king.moveTowardGuard(world, guard);
            king.applyMovement(delta, world);

            Unit threat = king.getTargetEnemy();
            if (threat == null || threat.isDead()) {
                king.changeState(PATROL);
            }
        }
    },

    FLEE {
        @Override
        public void update(King king) {
            AiContext ctx = king.getAiAgent().getContext();
            GridWorld world = ctx != null ? ctx.getGrid() : null;
            if (world == null) {
                return;
            }
            float delta = king.getAiDeltaSnapshot();
            Unit threat = king.getTargetEnemy();
            if (threat == null || threat.isDead()) {
                king.changeState(PATROL);
                return;
            }

            king.fleeFromThreat(threat, world);
            king.applyMovement(delta, world);

            Guard guard = king.findClosestGuard(ctx.getEntities());
            if (guard != null && !guard.isDead()) {
                king.changeState(HIDE);
            }
        }
    };

    @Override
    public void enter(King king) {}

    @Override
    public void update(King king) {}

    @Override
    public void exit(King king) {}

    @Override
    public boolean onMessage(King king, Telegram telegram) {
        return false;
    }
}
