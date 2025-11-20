package com.castlewar.ai.guard;

import com.badlogic.gdx.ai.fsm.State;
import com.badlogic.gdx.ai.msg.Telegram;
import com.castlewar.ai.AiContext;
import com.castlewar.entity.Guard;
import com.castlewar.entity.Unit.AwarenessIcon;
import com.castlewar.world.GridWorld;

/**
 * First-pass guard state that simply proxies legacy behaviour through the FSM pipeline.
 */
public enum GuardState implements State<Guard> {
    PATROL {
        @Override
        public void update(Guard guard) {
            AiContext ctx = guard.getAiAgent().getContext();
            GridWorld world = ctx != null ? ctx.getGrid() : null;
            if (world == null) {
                return;
            }
            float delta = guard.getAiDeltaSnapshot();

            guard.decayAlertMemory(delta);
            guard.updateFacing();
            guard.scanForEnemies(ctx.getEntities(), world);

            if (guard.needsToFlee()) {
                guard.changeState(FLEE);
                return;
            }

            if (hasLiveTarget(guard)) {
                guard.changeState(ENGAGE);
                return;
            }

            if (guard.hasInvestigationTarget()) {
                guard.changeState(ALERT);
                return;
            }

            guard.updateMovement(delta);

            if (guard.isMoveTimerReady()) {
                guard.decideNextMove(world);
            }
        }
    },

    ALERT {
        @Override
        public void enter(Guard guard) {
            guard.triggerAwarenessCue(AwarenessIcon.INVESTIGATE);
        }

        @Override
        public void update(Guard guard) {
            AiContext ctx = guard.getAiAgent().getContext();
            GridWorld world = ctx != null ? ctx.getGrid() : null;
            if (world == null) {
                return;
            }
            float delta = guard.getAiDeltaSnapshot();

            guard.decayAlertMemory(delta);
            guard.updateFacing();
            guard.scanForEnemies(ctx.getEntities(), world);

            if (guard.needsToFlee()) {
                guard.changeState(FLEE);
                return;
            }

            if (hasLiveTarget(guard)) {
                guard.changeState(ENGAGE);
                return;
            }

            if (!guard.hasInvestigationTarget()) {
                guard.changeState(PATROL);
                return;
            }

            guard.moveTowardLastSighting(world);
            guard.updateMovement(delta);
        }
    },

    ENGAGE {
        @Override
        public void enter(Guard guard) {
            guard.triggerAwarenessCue(AwarenessIcon.ALERT);
        }

        @Override
        public void update(Guard guard) {
            AiContext ctx = guard.getAiAgent().getContext();
            GridWorld world = ctx != null ? ctx.getGrid() : null;
            if (world == null) {
                return;
            }

            float delta = guard.getAiDeltaSnapshot();

            guard.decayAlertMemory(delta);
            guard.updateFacing();
            guard.updateAttack();

            if (guard.needsToFlee()) {
                guard.changeState(FLEE);
                return;
            }

            if (!hasLiveTarget(guard)) {
                if (guard.hasInvestigationTarget()) {
                    guard.changeState(ALERT);
                } else {
                    guard.changeState(PATROL);
                }
                return;
            }

            guard.updateMovement(delta);
        }
    },

    FLEE {
        @Override
        public void enter(Guard guard) {
            guard.stopMoving();
        }

        @Override
        public void update(Guard guard) {
            AiContext ctx = guard.getAiAgent().getContext();
            GridWorld world = ctx != null ? ctx.getGrid() : null;
            if (world == null) {
                return;
            }
            float delta = guard.getAiDeltaSnapshot();

            guard.decayAlertMemory(delta);
            guard.updateFacing();
            guard.retreatFromThreat(world);
            guard.updateMovement(delta);
            guard.scanForEnemies(ctx.getEntities(), world);

            if (!guard.needsToFlee()) {
                if (hasLiveTarget(guard)) {
                    guard.changeState(ENGAGE);
                } else if (guard.hasInvestigationTarget()) {
                    guard.changeState(ALERT);
                } else {
                    guard.changeState(PATROL);
                }
            }
        }
    };

    @Override
    public void enter(Guard guard) {}

    @Override
    public void update(Guard guard) {}

    @Override
    public void exit(Guard guard) {}

    @Override
    public boolean onMessage(Guard guard, Telegram telegram) {
        return false;
    }

    private static boolean hasLiveTarget(Guard guard) {
        return guard.getTargetEnemy() != null && !guard.getTargetEnemy().isDead();
    }
}
