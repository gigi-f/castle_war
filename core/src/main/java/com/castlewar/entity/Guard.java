package com.castlewar.entity;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.castlewar.world.GridWorld;

public class Guard extends Unit {
    public enum GuardType {
        ENTOURAGE,
        PATROL
    }

    private static final String[] NAMES = {"Guard", "Sentry", "Warden", "Protector", "Shield", "Knight"};

    private final GuardType type;
    private Entity targetToFollow; // For Entourage
    private float moveTimer = 0f;
    private Vector3 targetPosition = null;

    public Guard(float x, float y, float z, Team team, GuardType type) {
        super(x, y, z, team, generateName(type), 80f, 50f);
        this.type = type;
    }

    private static String generateName(GuardType type) {
        return (type == GuardType.ENTOURAGE ? "Royal " : "Castle ") + 
               NAMES[MathUtils.random(NAMES.length - 1)] + " " + MathUtils.random(100, 999);
    }

    public void setTargetToFollow(Entity target) {
        this.targetToFollow = target;
    }

    public GuardType getType() {
        return type;
    }

    @Override
    public void update(float delta, GridWorld world) {
        if (targetPosition != null) {
            // Move towards target
            float speed = 2.5f; // Slightly faster than King
            Vector3 direction = new Vector3(targetPosition).sub(position).nor();
            float distance = position.dst(targetPosition);
            
            if (distance < speed * delta) {
                position.set(targetPosition);
                targetPosition = null;
                moveTimer = MathUtils.random(0.5f, 2f);
            } else {
                position.add(direction.scl(speed * delta));
            }
        } else {
            moveTimer -= delta;
            if (moveTimer <= 0) {
                decideNextMove(world);
            }
        }
    }

    private void decideNextMove(GridWorld world) {
        if (type == GuardType.ENTOURAGE && targetToFollow != null) {
            followTarget(world);
        } else {
            patrol(world);
        }
    }

    private void followTarget(GridWorld world) {
        // If far, move closer. If close, wander nearby.
        float dist = position.dst(targetToFollow.getPosition());
        if (dist > 3f) {
            // Move towards target
            // Simple pathfinding: move to adjacent block closest to target
            // For now, just pick a random valid move that reduces distance
            pickMoveTowards(world, targetToFollow.getPosition());
        } else {
            // Wander nearby
            patrol(world);
        }
    }

    private void pickMoveTowards(GridWorld world, Vector3 target) {
        int currentX = Math.round(position.x);
        int currentY = Math.round(position.y);
        int currentZ = Math.round(position.z);

        float bestDist = Float.MAX_VALUE;
        Vector3 bestMove = null;

        // Check neighbors
        int[][] offsets = {{1,0,0}, {-1,0,0}, {0,1,0}, {0,-1,0}};
        for (int[] off : offsets) {
            int nx = currentX + off[0];
            int ny = currentY + off[1];
            int nz = currentZ; // Flat movement first
            
            Vector3 move = getValidMoveTarget(world, nx, ny, nz);
            if (move != null) {
                float d = Vector3.dst(move.x, move.y, move.z, target.x, target.y, target.z);
                if (d < bestDist) {
                    bestDist = d;
                    bestMove = move;
                }
            }
        }
        
        // Also check stairs
        GridWorld.BlockState currentBlock = world.getBlock(currentX, currentY, currentZ);
        if (isStair(currentBlock)) {
             int[] zOffs = {1, -1};
             for (int dz : zOffs) {
                 if (isValidMove(world, currentX, currentY, currentZ + dz)) {
                     float d = Vector3.dst(currentX, currentY, currentZ + dz, target.x, target.y, target.z);
                     if (d < bestDist) {
                         bestDist = d;
                         bestMove = new Vector3(currentX, currentY, currentZ + dz);
                     }
                 }
             }
        }

        if (bestMove != null) {
            targetPosition = bestMove;
        } else {
            patrol(world); // Fallback
        }
    }

    private void patrol(GridWorld world) {
        // Similar to King's wander logic
        int currentX = Math.round(position.x);
        int currentY = Math.round(position.y);
        int currentZ = Math.round(position.z);

        int dx = MathUtils.random(-1, 1);
        int dy = MathUtils.random(-1, 1);
        if (dx != 0 && dy != 0) dy = 0;
        if (dx == 0 && dy == 0) return;

        int newX = currentX + dx;
        int newY = currentY + dy;
        int newZ = currentZ;

        Vector3 move = getValidMoveTarget(world, newX, newY, newZ);
        if (move != null) {
            targetPosition = move;
        } else {
             GridWorld.BlockState currentBlock = world.getBlock(currentX, currentY, currentZ);
            if (isStair(currentBlock)) {
                int dz = MathUtils.randomSign();
                if (isValidMove(world, currentX, currentY, currentZ + dz)) {
                    targetPosition = new Vector3(currentX, currentY, currentZ + dz);
                }
            }
        }
    }
}
