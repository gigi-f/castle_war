package com.castlewar.world;

/**
 * A simple 2D grid representing the world state.
 * Each cell can contain different block types.
 */
public class GridWorld {
    private final int width;   // X dimension
    private final int depth;   // Y dimension
    private final int height;  // Z dimension
    
    // 3D array: [x][y][z]
    private final BlockState[][][] blocks;
    
    public enum BlockState {
        AIR,
        GRASS,
        DIRT,
        STONE,
    CASTLE_WHITE,
    CASTLE_BLACK,
    CASTLE_WHITE_FLOOR,
    CASTLE_BLACK_FLOOR,
    CASTLE_WHITE_STAIR,
    CASTLE_BLACK_STAIR,
    DOOR,
    WINDOW,
    WATER,
    MOUNTAIN_ROCK
    }
    
    public GridWorld(int width, int depth, int height) {
        this.width = width;
        this.depth = depth;
        this.height = height;
        this.blocks = new BlockState[width][depth][height];
        
        // Initialize with air
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < depth; y++) {
                for (int z = 0; z < height; z++) {
                    blocks[x][y][z] = BlockState.AIR;
                }
            }
        }
        
        // Create flat grass terrain at z=0
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < depth; y++) {
                blocks[x][y][0] = BlockState.GRASS;
            }
        }
    }
    
    public BlockState getBlock(int x, int y, int z) {
        if (x < 0 || x >= width || y < 0 || y >= depth) {
            return BlockState.AIR;
        }
        
        // Handle negative z (underground)
        if (z < 0) {
            // Underground is filled with dirt, getting stonier as you go deeper
            if (z < -5) {
                return BlockState.STONE;
            } else {
                return BlockState.DIRT;
            }
        }
        
        if (z >= height) {
            return BlockState.AIR;
        }
        
        return blocks[x][y][z];
    }
    
    public void setBlock(int x, int y, int z, BlockState state) {
        if (x < 0 || x >= width || y < 0 || y >= depth || z < 0 || z >= height) {
            return;
        }
        blocks[x][y][z] = state;
    }
    
    public int getWidth() {
        return width;
    }
    
    public int getDepth() {
        return depth;
    }
    
    public int getHeight() {
        return height;
    }

    public boolean hasLineOfSight(float x1, float y1, float z1, float x2, float y2, float z2) {
        // Simple raycasting / stepping
        float dist = (float)Math.sqrt(Math.pow(x2-x1, 2) + Math.pow(y2-y1, 2) + Math.pow(z2-z1, 2));
        if (dist < 0.1f) return true;
        
        float steps = dist * 2; // 2 checks per block unit for safety
        float dx = (x2 - x1) / steps;
        float dy = (y2 - y1) / steps;
        float dz = (z2 - z1) / steps;
        
        float cx = x1;
        float cy = y1;
        float cz = z1;
        
        for (int i = 0; i < steps; i++) {
            cx += dx;
            cy += dy;
            cz += dz;
            
            // Don't check the very end point (target might be IN a block or just on edge)
            if (i > steps - 2) break;
            
            BlockState block = getBlock(Math.round(cx), Math.round(cy), Math.round(cz));
            if (isOpaque(block)) {
                return false;
            }
        }
        return true;
    }

    public boolean isOpaque(BlockState block) {
        return block != BlockState.AIR && 
               block != BlockState.CASTLE_WHITE_STAIR && 
               block != BlockState.CASTLE_BLACK_STAIR &&
               block != BlockState.WINDOW; // Windows are transparent for LOS
    }
    
    public boolean isSolid(BlockState block) {
        return block != BlockState.AIR && 
               block != BlockState.WATER; // Water is not solid (can fall in)
    }
}
