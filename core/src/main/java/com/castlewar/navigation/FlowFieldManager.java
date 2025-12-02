package com.castlewar.navigation;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.castlewar.world.GridWorld;
import com.castlewar.world.GridWorld.BlockState;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;

/**
 * Flow field pathfinding for efficient group movement.
 * 
 * <h2>How It Works</h2>
 * <ol>
 *   <li>Generate an integration field from goal (BFS cost map)</li>
 *   <li>Generate a flow field from integration field (direction vectors)</li>
 *   <li>Units sample the flow field to get movement directions</li>
 * </ol>
 * 
 * <h2>Advantages Over A*</h2>
 * <ul>
 *   <li>O(1) queries per unit once field is generated</li>
 *   <li>All units to same goal share one field</li>
 *   <li>Smooth group movement without path clumping</li>
 * </ul>
 */
public class FlowFieldManager {
    
    // ═══════════════════════════════════════════════════════════════════════════
    // CONSTANTS
    // ═══════════════════════════════════════════════════════════════════════════
    
    /** Cost of moving to an impassable cell */
    private static final int BLOCKED = Integer.MAX_VALUE;
    /** Cost of diagonal movement */
    private static final int DIAGONAL_COST = 14;
    /** Cost of cardinal movement */
    private static final int CARDINAL_COST = 10;
    /** Cache expiration time in seconds */
    private static final float CACHE_LIFETIME = 5f;
    
    // ═══════════════════════════════════════════════════════════════════════════
    // FLOW FIELD DATA
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Represents a cached flow field for a specific goal.
     */
    public static class FlowField {
        /** Goal position */
        public final Vector2 goal;
        /** Layer/height level */
        public final int layer;
        /** Direction vectors for each cell */
        public final Vector2[][] directions;
        /** Width of field */
        public final int width;
        /** Height of field */
        public final int depth;
        /** When this field was created */
        public final float creationTime;
        
        public FlowField(Vector2 goal, int layer, Vector2[][] directions, int width, int depth, float time) {
            this.goal = new Vector2(goal);
            this.layer = layer;
            this.directions = directions;
            this.width = width;
            this.depth = depth;
            this.creationTime = time;
        }
        
        /**
         * Gets the flow direction at a world position.
         * Returns zero vector if out of bounds.
         */
        public Vector2 getDirection(float worldX, float worldY) {
            int cellX = (int) worldX;
            int cellY = (int) worldY;
            
            if (cellX < 0 || cellX >= width || cellY < 0 || cellY >= depth) {
                return Vector2.Zero;
            }
            
            return directions[cellX][cellY];
        }
        
        /**
         * Checks if the field is expired.
         */
        public boolean isExpired(float currentTime) {
            return currentTime - creationTime > CACHE_LIFETIME;
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // STATE
    // ═══════════════════════════════════════════════════════════════════════════
    
    /** Cached flow fields by goal hash */
    private final Map<Long, FlowField> cache = new HashMap<>();
    private float currentTime = 0f;
    
    // ═══════════════════════════════════════════════════════════════════════════
    // SINGLETON
    // ═══════════════════════════════════════════════════════════════════════════
    
    private static FlowFieldManager instance;
    
    public static FlowFieldManager getInstance() {
        if (instance == null) {
            instance = new FlowFieldManager();
        }
        return instance;
    }
    
    private FlowFieldManager() {}
    
    // ═══════════════════════════════════════════════════════════════════════════
    // UPDATE
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Updates the manager, cleaning expired cache entries.
     */
    public void update(float delta) {
        currentTime += delta;
        
        // Clean expired entries
        cache.entrySet().removeIf(entry -> entry.getValue().isExpired(currentTime));
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // FLOW FIELD GENERATION
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Gets or generates a flow field for the given goal.
     * 
     * @param goalX  Goal X position
     * @param goalY  Goal Y position
     * @param layer  Height layer
     * @param world  Grid world for passability checks
     * @return Flow field for navigation
     */
    public FlowField getFlowField(float goalX, float goalY, int layer, GridWorld world) {
        // Check cache
        long key = hashGoal((int) goalX, (int) goalY, layer);
        FlowField cached = cache.get(key);
        if (cached != null && !cached.isExpired(currentTime)) {
            return cached;
        }
        
        // Generate new field
        FlowField field = generateFlowField(goalX, goalY, layer, world);
        cache.put(key, field);
        return field;
    }
    
    /**
     * Generates a new flow field for the given goal.
     */
    private FlowField generateFlowField(float goalX, float goalY, int layer, GridWorld world) {
        int width = world.getWidth();
        int depth = world.getDepth();
        int goalCellX = (int) goalX;
        int goalCellY = (int) goalY;
        
        // Step 1: Generate integration field (cost map)
        int[][] integrationField = generateIntegrationField(goalCellX, goalCellY, layer, world);
        
        // Step 2: Generate flow field (direction vectors)
        Vector2[][] directions = generateDirections(integrationField, width, depth);
        
        return new FlowField(
            new Vector2(goalX, goalY),
            layer,
            directions,
            width,
            depth,
            currentTime
        );
    }
    
    /**
     * Generates an integration field using Dijkstra/BFS from goal.
     * Each cell contains the cost to reach the goal.
     */
    private int[][] generateIntegrationField(int goalX, int goalY, int layer, GridWorld world) {
        int width = world.getWidth();
        int depth = world.getDepth();
        int[][] costField = new int[width][depth];
        
        // Initialize all cells as blocked
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < depth; y++) {
                costField[x][y] = BLOCKED;
            }
        }
        
        // Goal has zero cost
        if (goalX >= 0 && goalX < width && goalY >= 0 && goalY < depth) {
            costField[goalX][goalY] = 0;
        } else {
            return costField; // Invalid goal
        }
        
        // BFS from goal
        Queue<int[]> openSet = new ArrayDeque<>();
        openSet.add(new int[] { goalX, goalY, 0 });
        
        // 8-directional neighbors (x offset, y offset, cost)
        int[][] neighbors = {
            { 1, 0, CARDINAL_COST }, { -1, 0, CARDINAL_COST },
            { 0, 1, CARDINAL_COST }, { 0, -1, CARDINAL_COST },
            { 1, 1, DIAGONAL_COST }, { 1, -1, DIAGONAL_COST },
            { -1, 1, DIAGONAL_COST }, { -1, -1, DIAGONAL_COST }
        };
        
        while (!openSet.isEmpty()) {
            int[] current = openSet.poll();
            int cx = current[0];
            int cy = current[1];
            int currentCost = current[2];
            
            for (int[] n : neighbors) {
                int nx = cx + n[0];
                int ny = cy + n[1];
                int moveCost = n[2];
                
                if (nx < 0 || nx >= width || ny < 0 || ny >= depth) continue;
                
                // Check passability
                if (!isPassable(nx, ny, layer, world)) continue;
                
                int newCost = currentCost + moveCost;
                
                if (newCost < costField[nx][ny]) {
                    costField[nx][ny] = newCost;
                    openSet.add(new int[] { nx, ny, newCost });
                }
            }
        }
        
        return costField;
    }
    
    /**
     * Generates flow directions from the integration field.
     * Each cell points toward its lowest-cost neighbor.
     */
    private Vector2[][] generateDirections(int[][] integrationField, int width, int depth) {
        Vector2[][] directions = new Vector2[width][depth];
        
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < depth; y++) {
                directions[x][y] = calculateFlowDirection(x, y, integrationField, width, depth);
            }
        }
        
        return directions;
    }
    
    /**
     * Calculates the flow direction for a single cell.
     */
    private Vector2 calculateFlowDirection(int x, int y, int[][] costField, int width, int depth) {
        int currentCost = costField[x][y];
        
        // If blocked or at goal, no direction
        if (currentCost == BLOCKED || currentCost == 0) {
            return new Vector2(0, 0);
        }
        
        int lowestCost = currentCost;
        int bestDx = 0;
        int bestDy = 0;
        
        // Check all 8 neighbors
        int[][] neighbors = {
            { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 },
            { 1, 1 }, { 1, -1 }, { -1, 1 }, { -1, -1 }
        };
        
        for (int[] n : neighbors) {
            int nx = x + n[0];
            int ny = y + n[1];
            
            if (nx < 0 || nx >= width || ny < 0 || ny >= depth) continue;
            
            int neighborCost = costField[nx][ny];
            if (neighborCost < lowestCost) {
                lowestCost = neighborCost;
                bestDx = n[0];
                bestDy = n[1];
            }
        }
        
        Vector2 direction = new Vector2(bestDx, bestDy);
        if (direction.len2() > 0) {
            direction.nor(); // Normalize
        }
        return direction;
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // PASSABILITY
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Checks if a cell is passable for ground units.
     */
    private boolean isPassable(int x, int y, int layer, GridWorld world) {
        // Get block at position
        BlockState block = world.getBlock(x, y, layer);
        
        // Air, floors, grass are passable
        if (block == BlockState.AIR || block == BlockState.GRASS) {
            // Check if there's solid ground below
            BlockState below = world.getBlock(x, y, layer - 1);
            return below != BlockState.AIR && below != BlockState.WATER;
        }
        
        // Floors are passable
        if (block == BlockState.CASTLE_WHITE_FLOOR || 
            block == BlockState.CASTLE_BLACK_FLOOR) {
            return true;
        }
        
        // Doors are passable
        if (block == BlockState.DOOR) {
            return true;
        }
        
        // Stairs are passable (though they connect layers)
        if (block == BlockState.CASTLE_WHITE_STAIR ||
            block == BlockState.CASTLE_BLACK_STAIR) {
            return true;
        }
        
        // Solid blocks are not passable
        return false;
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // UNIT NAVIGATION
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Gets the movement direction for a unit based on flow field.
     * 
     * @param unitPos  Current unit position
     * @param goalPos  Destination position
     * @param world    Grid world
     * @return Direction vector for movement (normalized)
     */
    public Vector3 getMovementDirection(Vector3 unitPos, Vector3 goalPos, GridWorld world) {
        int layer = (int) unitPos.z;
        FlowField field = getFlowField(goalPos.x, goalPos.y, layer, world);
        
        Vector2 dir2D = field.getDirection(unitPos.x, unitPos.y);
        return new Vector3(dir2D.x, dir2D.y, 0);
    }
    
    /**
     * Interpolates flow direction for smoother movement.
     * Uses bilinear interpolation between cell centers.
     */
    public Vector3 getInterpolatedDirection(Vector3 unitPos, Vector3 goalPos, GridWorld world) {
        int layer = (int) unitPos.z;
        FlowField field = getFlowField(goalPos.x, goalPos.y, layer, world);
        
        float fx = unitPos.x;
        float fy = unitPos.y;
        
        int x0 = (int) Math.floor(fx);
        int y0 = (int) Math.floor(fy);
        int x1 = x0 + 1;
        int y1 = y0 + 1;
        
        // Get directions at four corners
        Vector2 d00 = field.getDirection(x0, y0);
        Vector2 d10 = field.getDirection(x1, y0);
        Vector2 d01 = field.getDirection(x0, y1);
        Vector2 d11 = field.getDirection(x1, y1);
        
        // Bilinear interpolation
        float tx = fx - x0;
        float ty = fy - y0;
        
        float dx = lerp(lerp(d00.x, d10.x, tx), lerp(d01.x, d11.x, tx), ty);
        float dy = lerp(lerp(d00.y, d10.y, tx), lerp(d01.y, d11.y, tx), ty);
        
        Vector3 result = new Vector3(dx, dy, 0);
        if (result.len2() > 0) {
            result.nor();
        }
        return result;
    }
    
    private float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // UTILITIES
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Creates a hash key for a goal position.
     */
    private long hashGoal(int x, int y, int layer) {
        return ((long) x << 32) | ((long) y << 16) | layer;
    }
    
    /**
     * Invalidates cached flow fields (call when world changes).
     */
    public void invalidateCache() {
        cache.clear();
    }
    
    /**
     * Invalidates flow fields near a position (for local changes).
     */
    public void invalidateNear(Vector3 position, float radius) {
        int px = (int) position.x;
        int py = (int) position.y;
        int pz = (int) position.z;
        
        cache.entrySet().removeIf(entry -> {
            FlowField field = entry.getValue();
            float dist = Math.abs(field.goal.x - px) + Math.abs(field.goal.y - py);
            return dist <= radius && field.layer == pz;
        });
    }
    
    /**
     * Clears all cached data.
     */
    public void clear() {
        cache.clear();
        currentTime = 0f;
    }
}
