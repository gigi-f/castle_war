package com.castlewar.entity;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.MathUtils;
import com.castlewar.world.GridWorld;
import java.util.PriorityQueue;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

public abstract class Unit extends Entity {
    protected String name;
    protected float hp;
    protected float maxHp;
    protected float stamina;
    protected float maxStamina;
    protected Vector3 targetPosition;
    protected List<Vector3> currentPath = new ArrayList<>();

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

    // Combat stats
    protected float attackTimer = 0f;
    protected float attackDamage = 10f;
    protected float attackRange = 1.5f;
    protected float attackCooldown = 1.0f;

    public void takeDamage(float amount) {
        hp -= amount;
        if (hp < 0) hp = 0;
    }

    public boolean isDead() {
        return hp <= 0;
    }
    
    public void attack(Unit target) {
        if (attackTimer <= 0 && !isDead() && !target.isDead()) {
            target.takeDamage(attackDamage);
            attackTimer = attackCooldown;
        }
    }

    public void pickSmartMove(GridWorld world, Vector3 target) {
        // If we have a path, follow it
        if (currentPath != null && !currentPath.isEmpty()) {
             Vector3 next = currentPath.get(0);
             // Check if the next step is still valid/adjacent
             if (position.dst(next) < 2.5f) {
                 targetPosition = next;
                 currentPath.remove(0);
                 return;
             } else {
                 // Path invalid (too far or teleported), recalculate
                 if (currentPath != null) currentPath.clear();
             }
        }
        
        // Calculate new path
        currentPath = findPath(world, position, target);
        if (currentPath != null && !currentPath.isEmpty()) {
             targetPosition = currentPath.get(0);
             currentPath.remove(0);
        }
    }

    private List<Vector3> findPath(GridWorld world, Vector3 start, Vector3 end) {
        PriorityQueue<Node> openSet = new PriorityQueue<>(Comparator.comparingDouble(Node::f));
        Set<Node> closedSet = new HashSet<>();
        
        int sx = Math.round(start.x);
        int sy = Math.round(start.y);
        int sz = Math.round(start.z);
        int ex = Math.round(end.x);
        int ey = Math.round(end.y);
        int ez = Math.round(end.z);
        
        Node startNode = new Node(sx, sy, sz, 0, Vector3.dst(sx, sy, sz, ex, ey, ez), null);
        openSet.add(startNode);
        
        int iterations = 0;
        int maxIterations = 5000; // Increased limit for larger maps
        
        Node bestNode = startNode;
        float bestDist = startNode.h;
        
        while (!openSet.isEmpty() && iterations < maxIterations) {
            Node current = openSet.poll();
            iterations++;
            
            if (current.x == ex && current.y == ey && Math.abs(current.z - ez) <= 1) {
                return reconstructPath(current);
            }
            
            if (closedSet.contains(current)) continue;
            closedSet.add(current);
            
            if (current.h < bestDist) {
                bestDist = current.h;
                bestNode = current;
            }
            
            // Neighbors
            // 1. Horizontal/Diagonal
            int[][] offsets = {{1,0}, {-1,0}, {0,1}, {0,-1}};
            for (int[] off : offsets) {
                int nx = current.x + off[0];
                int ny = current.y + off[1];
                Vector3 move = getValidMoveTarget(world, nx, ny, current.z);
                
                if (move != null) {
                    float g = current.g + Vector3.dst(current.x, current.y, current.z, move.x, move.y, move.z);
                    float h = Vector3.dst(move.x, move.y, move.z, ex, ey, ez);
                    openSet.add(new Node((int)move.x, (int)move.y, (int)move.z, g, h, current));
                }
            }
            
            // 2. Climbing (Vertical)
            if (canClimb && isAdjacentToWall(world, current.x, current.y, current.z)) {
                if (isValidMove(world, current.x, current.y, current.z + 1)) {
                    float g = current.g + 1;
                    float h = Vector3.dst(current.x, current.y, current.z + 1, ex, ey, ez);
                    openSet.add(new Node(current.x, current.y, current.z + 1, g, h, current));
                }
            }
        }
        
        // If path not found, return path to best node (closest to target)
        if (bestNode != startNode) {
            return reconstructPath(bestNode);
        }
        
        return null;
    }
    
    private List<Vector3> reconstructPath(Node node) {
        List<Vector3> path = new ArrayList<>();
        while (node.parent != null) {
            path.add(new Vector3(node.x, node.y, node.z));
            node = node.parent;
        }
        Collections.reverse(path);
        return path;
    }
    
    private static class Node {
        int x, y, z;
        float g, h;
        Node parent;
        
        Node(int x, int y, int z, float g, float h, Node parent) {
            this.x = x; this.y = y; this.z = z;
            this.g = g; this.h = h;
            this.parent = parent;
        }
        
        float f() { return g + h; }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Node)) return false;
            Node n = (Node)o;
            return x == n.x && y == n.y && z == n.z;
        }
        
        @Override
        public int hashCode() {
            return x * 31 * 31 + y * 31 + z;
        }
    }

    // Physics fields
    protected float speed = 4f;
    protected float gravity = 30f;
    protected boolean onGround = false;
    protected Vector3 tmp = new Vector3();

    protected void applyPhysics(float delta, GridWorld world) {
        // Apply gravity
        velocity.z -= gravity * delta;
        
        // Apply velocity
        float nextX = position.x + velocity.x * delta;
        float nextY = position.y + velocity.y * delta;
        float nextZ = position.z + velocity.z * delta;

        // Collision Detection
        boolean stepped = false;

        // Check X
        if (isValidPos(world, nextX, position.y, position.z)) {
            position.x = nextX;
        } else if (onGround && isValidPos(world, nextX, position.y, position.z + 1)) {
            // Auto-step up
            position.x = nextX;
            position.z += 1;
            stepped = true;
        } else {
            velocity.x = 0;
        }

        // Check Y
        if (isValidPos(world, position.x, nextY, position.z)) {
            position.y = nextY;
        } else if (onGround && !stepped && isValidPos(world, position.x, nextY, position.z + 1)) {
            // Auto-step up
            position.y = nextY;
            position.z += 1;
            stepped = true;
        } else {
            velocity.y = 0;
        }

        // Check Z (Gravity/Jumping)
        if (stepped) {
            velocity.z = 0;
            onGround = true;
        } else {
            if (isValidPos(world, position.x, position.y, nextZ)) {
                position.z = nextZ;
                onGround = false;
            } else {
                if (velocity.z < 0) {
                    onGround = true;
                    // Hit floor. Snap to the top of the block we hit.
                    // The block we hit is at floor(nextZ).
                    // We want to be at floor(nextZ) + 1.001f.
                    // However, if nextZ is very close to integer, floor might be the block below.
                    // If we are at 1.0 and nextZ is 0.9. floor(0.9) is 0. +1.001 is 1.001. Correct.
                    position.z = (float)Math.floor(nextZ) + 1.001f;
                    
                    // Backup check: if still invalid, push up more
                    if (!isValidPos(world, position.x, position.y, position.z)) {
                         position.z += 0.1f; 
                    }
                    velocity.z = 0; // Stop falling
                } else {
                    velocity.z = 0; // Hit ceiling
                }
            }
        }
        
        // Bounds check
        position.x = MathUtils.clamp(position.x, 0, world.getWidth() - 1);
        position.y = MathUtils.clamp(position.y, 0, world.getDepth() - 1);
        position.z = MathUtils.clamp(position.z, 0, world.getHeight() - 1);
    }

    protected boolean isValidPos(GridWorld world, float x, float y, float z) {
        // Collision radius to prevent camera clipping through walls
        float radius = 0.6f; // Player width/2
        
        int bz = (int)Math.floor(z); 
        int bzHead = (int)Math.floor(z + 1.5f);

        // Check center position
        int bx = Math.round(x);
        int by = Math.round(y);
        if (world.isSolid(world.getBlock(bx, by, bz))) return false;
        if (world.isSolid(world.getBlock(bx, by, bzHead))) return false;
        
        // Check 4 points around the entity at the collision radius
        // This prevents the player from getting so close to walls that the camera clips through
        float[][] offsets = {
            {radius, 0},
            {-radius, 0},
            {0, radius},
            {0, -radius}
        };
        
        for (float[] offset : offsets) {
            int checkX = Math.round(x + offset[0]);
            int checkY = Math.round(y + offset[1]);
            if (world.isSolid(world.getBlock(checkX, checkY, bz))) return false;
            if (world.isSolid(world.getBlock(checkX, checkY, bzHead))) return false;
        }
        
        return true;
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

    protected Unit targetEnemy;

    public void scanForEnemies(java.util.List<Entity> entities) {
        float closestDist = 10f; // Vision range
        Unit closest = null;
        
        for (Entity e : entities) {
            if (e instanceof Unit && e.getTeam() != this.team && !((Unit)e).isDead()) {
                float d = position.dst(e.getPosition());
                if (d < closestDist) {
                     closestDist = d;
                     closest = (Unit)e;
                }
            }
        }
        targetEnemy = closest;
    }
}
