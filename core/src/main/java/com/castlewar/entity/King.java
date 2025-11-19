package com.castlewar.entity;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.castlewar.world.GridWorld;

public class King extends Unit {
    private static final String[] TITLES = {"King", "Emperor", "Lord", "Duke", "Prince"};
    private static final String[] NAMES = {"Arthur", "Richard", "Henry", "Edward", "George", "William", "Charles"};
    private static final String[] SUFFIXES = {"I", "II", "III", "IV", "V", "the Great", "the Wise", "the Bold"};

    private float moveTimer = 0f;
    private float moveInterval = 2f; // Time between moves
    private Vector3 targetPosition = null;

    public King(float x, float y, float z, Team team) {
        super(x, y, z, team, generateName(), 50f, 20f);
    }

    private static String generateName() {
        return TITLES[MathUtils.random(TITLES.length - 1)] + " " +
               NAMES[MathUtils.random(NAMES.length - 1)] + " " +
               SUFFIXES[MathUtils.random(SUFFIXES.length - 1)];
    }

    @Override
    public void update(float delta, GridWorld world) {
        checkEnvironment(world);
        if (hp <= 0) return;

        if (targetPosition != null) {
            // Move towards target
            float speed = 2f;
            Vector3 direction = new Vector3(targetPosition).sub(position).nor();
            float distance = position.dst(targetPosition);
            
            if (distance < speed * delta) {
                position.set(targetPosition);
                targetPosition = null;
                moveTimer = MathUtils.random(1f, 3f); // Wait before next move
            } else {
                position.add(direction.scl(speed * delta));
            }
        } else {
            moveTimer -= delta;
            if (moveTimer <= 0) {
                pickNewTarget(world);
            }
        }
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
        
        if (patrolTarget != null) {
            pickSmartMove(world, patrolTarget);
        }
    }
}
