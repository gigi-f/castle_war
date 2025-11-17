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
    CASTLE_BLACK_FLOOR
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
}
