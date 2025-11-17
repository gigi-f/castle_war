package com.castlewar.voxel;

/**
 * Represents a 16×16×16 chunk of voxels for efficient storage and culling.
 */
public class Chunk {
    public static final int CHUNK_SIZE = 16;
    
    private final BlockType[][][] blocks = new BlockType[CHUNK_SIZE][CHUNK_SIZE][CHUNK_SIZE];
    private final int chunkX;
    private final int chunkY;
    private final int chunkZ;
    private boolean dirty = true;

    public Chunk(int chunkX, int chunkY, int chunkZ) {
        this.chunkX = chunkX;
        this.chunkY = chunkY;
        this.chunkZ = chunkZ;
        
        // Initialize all blocks as air
        for (int x = 0; x < CHUNK_SIZE; x++) {
            for (int y = 0; y < CHUNK_SIZE; y++) {
                for (int z = 0; z < CHUNK_SIZE; z++) {
                    blocks[x][y][z] = BlockType.AIR;
                }
            }
        }
    }

    public BlockType getBlock(int x, int y, int z) {
        if (x < 0 || x >= CHUNK_SIZE || y < 0 || y >= CHUNK_SIZE || z < 0 || z >= CHUNK_SIZE) {
            return BlockType.AIR;
        }
        return blocks[x][y][z];
    }

    public void setBlock(int x, int y, int z, BlockType type) {
        if (x < 0 || x >= CHUNK_SIZE || y < 0 || y >= CHUNK_SIZE || z < 0 || z >= CHUNK_SIZE) {
            return;
        }
        blocks[x][y][z] = type;
        dirty = true;
    }

    public int getChunkX() {
        return chunkX;
    }

    public int getChunkY() {
        return chunkY;
    }

    public int getChunkZ() {
        return chunkZ;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void clearDirty() {
        dirty = false;
    }
}
