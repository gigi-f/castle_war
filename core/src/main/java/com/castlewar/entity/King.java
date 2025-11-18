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

    private void pickNewTarget(GridWorld world) {
        // Try to find a valid adjacent block (including stairs up/down)
        int currentX = Math.round(position.x);
        int currentY = Math.round(position.y);
        int currentZ = Math.round(position.z);

        // Simple random walk for now
        int dx = MathUtils.random(-1, 1);
        int dy = MathUtils.random(-1, 1);
        
        // Don't move diagonally for now to keep it simple
        if (dx != 0 && dy != 0) dy = 0;
        if (dx == 0 && dy == 0) return;

        int newX = currentX + dx;
        int newY = currentY + dy;
        int newZ = currentZ;

        // Check if valid move (including hop)
        Vector3 target = getValidMoveTarget(world, newX, newY, newZ);
        if (target != null) {
            targetPosition = target;
        } else {
            // Try changing floors if on stairs
            GridWorld.BlockState currentBlock = world.getBlock(currentX, currentY, currentZ);
            if (isStair(currentBlock)) {
                // 50% chance to go up or down if possible
                int dz = MathUtils.randomSign();
                if (isValidMove(world, currentX, currentY, currentZ + dz)) {
                    targetPosition = new Vector3(currentX, currentY, currentZ + dz);
                }
            }
        }
    }
}
