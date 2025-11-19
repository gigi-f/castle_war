package com.castlewar.entity;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.MathUtils;
import com.castlewar.world.GridWorld;

public abstract class Unit extends Entity {
    protected String name;
    protected float hp;
    protected float maxHp;
    protected float stamina;
    protected float maxStamina;
    protected Vector3 targetPosition;

    public Unit(float x, float y, float z, Team team, String name, float maxHp, float maxStamina) {
        super(x, y, z, team);
        this.name = name;
        this.maxHp = maxHp;
        this.hp = maxHp;
        this.maxStamina = maxStamina;
        this.stamina = maxStamina;
    }

    public String getName() { return name; }
    public float getHp() { return hp; }
    public float getMaxHp() { return maxHp; }

    protected boolean canClimb = false;

    public boolean isValidMove(GridWorld world, int x, int y, int z) {
        // Bounds check
        if (x < 0 || x >= world.getWidth() || y < 0 || y >= world.getDepth() || z < 0 || z >= world.getHeight()) {
            return false;
        }

        GridWorld.BlockState targetBlock = world.getBlock(x, y, z);
        
        // Check if target is passable
        if (world.isOpaque(targetBlock)) {
            return false;
        }

        // Check footing
        GridWorld.BlockState belowBlock = world.getBlock(x, y, z - 1);
        boolean solidFooting = world.isSolid(belowBlock) || isStair(targetBlock);
        
        // Climbing logic
        if (canClimb && isAdjacentToWall(world, x, y, z)) {
            return true; // Climbing ignores footing
        }

        return solidFooting;
    }

    // Helper to check if a move to (x,y,z) is valid, possibly involving a hop to z+1
    public Vector3 getValidMoveTarget(GridWorld world, int x, int y, int z) {
        // 1. Check direct move
        if (isValidMove(world, x, y, z)) {
            return new Vector3(x, y, z);
        }
        
        // 2. Check hop (move to x,y,z+1)
        // Condition: Target (x,y,z) is solid (obstacle), but (x,y,z+1) is air.
        // And we are moving from current Z.
        // Actually, we are at current pos. We want to move to neighbor (x,y).
        // If (x,y,z) is blocked, try (x,y,z+1).
        
        GridWorld.BlockState targetBlock = world.getBlock(x, y, z);
        if (world.isSolid(targetBlock)) {
            // Obstacle. Check above.
            if (z + 1 < world.getHeight()) {
                if (isValidMove(world, x, y, z + 1)) {
                    return new Vector3(x, y, z + 1);
                }
            }
        }
        
        // 3. Check drop (move to x,y,z-1)?
        // If (x,y,z) is AIR and below is AIR, maybe we fall/drop?
        // For now, let's just handle hopping up.
        
        return null;
    }

    protected boolean isAdjacentToWall(GridWorld world, int x, int y, int z) {
        int[][] offsets = {{1,0}, {-1,0}, {0,1}, {0,-1}};
        for (int[] off : offsets) {
            GridWorld.BlockState b = world.getBlock(x + off[0], y + off[1], z);
            if (world.isOpaque(b)) return true;
        }
        return false;
    }

    protected boolean isStair(GridWorld.BlockState block) {
        return block == GridWorld.BlockState.CASTLE_WHITE_STAIR || 
               block == GridWorld.BlockState.CASTLE_BLACK_STAIR;
    }
    
    protected boolean isFloor(GridWorld.BlockState block) {
         return block == GridWorld.BlockState.CASTLE_WHITE_FLOOR ||
                block == GridWorld.BlockState.CASTLE_BLACK_FLOOR;
    }

    public void pickSmartMove(GridWorld world, Vector3 target) {
        int cx = Math.round(position.x);
        int cy = Math.round(position.y);
        int cz = Math.round(position.z);

        // 1. Try to climb if target is above
        if (target.z > position.z) {
            // Look for neighbors that go UP
            int[][] offsets = {{1,0}, {-1,0}, {0,1}, {0,-1}, {1,1}, {1,-1}, {-1,1}, {-1,-1}};
            for (int[] off : offsets) {
                int nx = cx + off[0];
                int ny = cy + off[1];
                // Check for z+1 move
                Vector3 move = getValidMoveTarget(world, nx, ny, cz); // This handles the hop check internally?
                // Wait, getValidMoveTarget(x,y,z) checks if we can move to (x,y,z) OR (x,y,z+1).
                // If it returns z+1, that's a climb!
                
                if (move != null && move.z > position.z) {
                    // Found a step up! Take it immediately.
                    targetPosition = move;
                    return;
                }
            }
        }

        // 2. Standard greedy movement (with stuck avoidance)
        float bestDist = Float.MAX_VALUE;
        Vector3 bestMove = null;
        
        int[][] offsets = {{1,0}, {-1,0}, {0,1}, {0,-1}};
        // Shuffle offsets to avoid bias
        for (int i = 0; i < offsets.length; i++) {
            int ii = MathUtils.random(offsets.length - 1);
            int[] temp = offsets[i];
            offsets[i] = offsets[ii];
            offsets[ii] = temp;
        }

        for (int[] off : offsets) {
            int nx = cx + off[0];
            int ny = cy + off[1];
            Vector3 move = getValidMoveTarget(world, nx, ny, cz);
            
            if (move != null) {
                float d = Vector3.dst(move.x, move.y, move.z, target.x, target.y, target.z);
                // If on stairs, relax distance constraint to allow moving around the spiral
                if (target.z != position.z) {
                     d *= 0.9f; // Bias towards moving even if distance is slightly worse? 
                     // No, that doesn't help with local minima.
                }
                
                if (d < bestDist) {
                    bestDist = d;
                    bestMove = move;
                }
            }
        }
        
        if (bestMove != null) {
            targetPosition = bestMove;
        }
    }

    protected void checkEnvironment(GridWorld world) {
        int x = Math.round(position.x);
        int y = Math.round(position.y);
        int z = Math.round(position.z);
        
        if (x >= 0 && x < world.getWidth() && y >= 0 && y < world.getDepth() && z >= 0 && z < world.getHeight()) {
            GridWorld.BlockState block = world.getBlock(x, y, z);
            if (block == GridWorld.BlockState.WATER) {
                hp = 0; // Instant death
            }
        }
        
        // Fall damage or void death?
        if (z < 0) hp = 0;
    }
}
