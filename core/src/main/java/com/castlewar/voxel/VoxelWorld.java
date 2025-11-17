package com.castlewar.voxel;

import com.badlogic.gdx.math.MathUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages the voxel world with chunk-based storage.
 */
public class VoxelWorld {
    private final Map<Long, Chunk> chunks = new HashMap<>();
    private final int worldWidth;
    private final int worldDepth;
    private final int worldHeight;

    public VoxelWorld(int worldWidth, int worldDepth, int worldHeight) {
        this.worldWidth = worldWidth;
        this.worldDepth = worldDepth;
        this.worldHeight = worldHeight;
        generateFlatTerrain();
    }

    private void generateFlatTerrain() {
        // Create a flat plane at z=0 with grass on top
        int maxChunkX = MathUtils.ceil((float) worldWidth / Chunk.CHUNK_SIZE);
        int maxChunkY = MathUtils.ceil((float) worldDepth / Chunk.CHUNK_SIZE);
        
        for (int cx = -maxChunkX / 2; cx < maxChunkX / 2; cx++) {
            for (int cy = -maxChunkY / 2; cy < maxChunkY / 2; cy++) {
                Chunk chunk = getOrCreateChunk(cx, cy, 0);
                
                // Fill bottom layer with grass
                for (int x = 0; x < Chunk.CHUNK_SIZE; x++) {
                    for (int y = 0; y < Chunk.CHUNK_SIZE; y++) {
                        chunk.setBlock(x, y, 0, BlockType.GRASS);
                    }
                }
            }
        }
    }

    public BlockType getBlock(int worldX, int worldY, int worldZ) {
        int chunkX = Math.floorDiv(worldX, Chunk.CHUNK_SIZE);
        int chunkY = Math.floorDiv(worldY, Chunk.CHUNK_SIZE);
        int chunkZ = Math.floorDiv(worldZ, Chunk.CHUNK_SIZE);
        
        Chunk chunk = chunks.get(chunkKey(chunkX, chunkY, chunkZ));
        if (chunk == null) {
            return BlockType.AIR;
        }
        
        int localX = Math.floorMod(worldX, Chunk.CHUNK_SIZE);
        int localY = Math.floorMod(worldY, Chunk.CHUNK_SIZE);
        int localZ = Math.floorMod(worldZ, Chunk.CHUNK_SIZE);
        
        return chunk.getBlock(localX, localY, localZ);
    }

    public void setBlock(int worldX, int worldY, int worldZ, BlockType type) {
        int chunkX = Math.floorDiv(worldX, Chunk.CHUNK_SIZE);
        int chunkY = Math.floorDiv(worldY, Chunk.CHUNK_SIZE);
        int chunkZ = Math.floorDiv(worldZ, Chunk.CHUNK_SIZE);
        
        Chunk chunk = getOrCreateChunk(chunkX, chunkY, chunkZ);
        
        int localX = Math.floorMod(worldX, Chunk.CHUNK_SIZE);
        int localY = Math.floorMod(worldY, Chunk.CHUNK_SIZE);
        int localZ = Math.floorMod(worldZ, Chunk.CHUNK_SIZE);
        
        chunk.setBlock(localX, localY, localZ, type);
    }

    private Chunk getOrCreateChunk(int chunkX, int chunkY, int chunkZ) {
        long key = chunkKey(chunkX, chunkY, chunkZ);
        return chunks.computeIfAbsent(key, k -> new Chunk(chunkX, chunkY, chunkZ));
    }

    private long chunkKey(int x, int y, int z) {
        return ((long) x & 0xFFFFF) | (((long) y & 0xFFFFF) << 20) | (((long) z & 0xFFFFF) << 40);
    }

    public Iterable<Chunk> getChunks() {
        return chunks.values();
    }

    public int getWorldWidth() {
        return worldWidth;
    }

    public int getWorldDepth() {
        return worldDepth;
    }

    public int getWorldHeight() {
        return worldHeight;
    }
}
