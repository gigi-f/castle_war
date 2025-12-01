package com.castlewar.entity;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.castlewar.ai.AiContext;
import com.castlewar.ai.king.KingBTAgent;
import com.castlewar.ai.king.KingState;
import com.castlewar.simulation.WorldContext;
import com.castlewar.world.GridWorld;

public class King extends Unit {
    private static final String[] TITLES = {"King", "Emperor", "Lord", "Duke", "Prince"};
    private static final String[] NAMES = {"Arthur", "Richard", "Henry", "Edward", "George", "William", "Charles"};
    private static final String[] SUFFIXES = {"I", "II", "III", "IV", "V", "the Great", "the Wise", "the Bold"};

    private float moveTimer = 0f;
    private final KingBTAgent aiAgent;
    private transient float aiDeltaSnapshot;

    public King(float x, float y, float z, Team team, WorldContext worldContext) {
        super(x, y, z, team, generateName(), 50f, 20f);
        this.aiAgent = new KingBTAgent(this, new AiContext(worldContext));
    }

    private static String generateName() {
        return TITLES[MathUtils.random(TITLES.length - 1)] + " " +
               NAMES[MathUtils.random(NAMES.length - 1)] + " " +
               SUFFIXES[MathUtils.random(SUFFIXES.length - 1)];
    }

    @Override
    public void update(float delta, GridWorld world) {
        checkEnvironment(world);
        if (!beginUpdate(delta, world)) {
            return;
        }
        this.aiDeltaSnapshot = delta;
        aiAgent.update(delta, aiAgent.getContext());
        super.applyPhysics(delta, world);
    }

    private Vector3 patrolTarget;

    private void pickNewTarget(GridWorld world) {
        if (patrolTarget == null || position.dst(patrolTarget) < 2f) {
            // Pick new random target in the world
            int rx = MathUtils.random(1, world.getWidth() - 2);
            int ry = MathUtils.random(1, world.getDepth() - 2);
            int rz = MathUtils.random(0, world.getHeight() - 1);
            // Ensure target is valid floor
            if (world.getBlock(rx, ry, rz) != GridWorld.BlockState.AIR) { // Simple check
                 patrolTarget = new Vector3(rx, ry, rz);
            }
        }
        // Movement disabled: do not plan or set target positions.
    }

    @Override
    protected float getKnockbackStrengthAgainst(Unit target) {
        return 9.0f;
    }

    public void performPatrolBehavior(float delta, GridWorld world) {
        if (targetPosition == null) {
            moveTimer -= delta;
            if (moveTimer <= 0f) {
                pickNewTarget(world);
            }
        }
        applyMovement(delta, world);
    }

    public void moveTowardGuard(GridWorld world, Guard guard) {
        if (guard == null) {
            return;
        }
        // BT handles this now
    }

    public void fleeFromThreat(Unit threat, GridWorld world) {
        if (threat == null) {
            return;
        }
        // BT handles this now
    }

    public void applyMovement(float delta, GridWorld world) {
        // BT now controls movement - this is a legacy method
    }

    public Guard findClosestGuard(java.util.List<Entity> entities) {
        Guard closest = null;
        float bestDist = Float.MAX_VALUE;
        for (Entity entity : entities) {
            if (entity instanceof Guard && entity.getTeam() == this.team && !((Guard) entity).isDead()) {
                float dist = position.dst(entity.getPosition());
                if (dist < bestDist) {
                    bestDist = dist;
                    closest = (Guard) entity;
                }
            }
        }
        return closest;
    }

    public KingBTAgent getAiAgent() {
        return aiAgent;
    }

    public float getAiDeltaSnapshot() {
        return aiDeltaSnapshot;
    }

    public void changeState(KingState nextState) {
        aiAgent.changeState(nextState);
    }
}
