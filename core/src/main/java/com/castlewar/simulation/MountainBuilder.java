package com.castlewar.simulation;

import com.badlogic.gdx.math.MathUtils;
import com.castlewar.world.GridWorld;

/**
 * Builder for generating perimeter mountains around the world.
 */
public class MountainBuilder {
    private static final int BASE_MOUNTAIN_WIDTH = 15;
    private static final long TERRAIN_SEED = 12345L;
    
    private final GridWorld gridWorld;

    public MountainBuilder(GridWorld gridWorld) {
        this.gridWorld = gridWorld;
    }

    /**
     * Builds perimeter mountains with natural-looking curves around the world edges.
     */
    public void buildPerimeterMountains() {
        int width = gridWorld.getWidth();
        int depth = gridWorld.getDepth();
        int height = gridWorld.getHeight();
        
        int baseHeight = 15;
        int peakHeight = height - 5;
        
        // Use a fixed seed for consistent terrain
        MathUtils.random.setSeed(TERRAIN_SEED);
        
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < depth; y++) {
                int distToEdge = calculateDistanceToEdge(x, y, width, depth);
                float currentMountainWidth = calculateVariableMountainWidth(x, y);
                
                if (distToEdge < currentMountainWidth) {
                    int terrainHeight = calculateTerrainHeight(distToEdge, currentMountainWidth, 
                                                               baseHeight, peakHeight, height);
                    fillMountainBlocks(x, y, terrainHeight);
                }
            }
        }
    }

    private int calculateDistanceToEdge(int x, int y, int width, int depth) {
        int distToEdgeX = Math.min(x, width - 1 - x);
        int distToEdgeY = Math.min(y, depth - 1 - y);
        return Math.min(distToEdgeX, distToEdgeY);
    }

    private float calculateVariableMountainWidth(int x, int y) {
        // Combine multiple frequencies for organic curves
        float wave1 = MathUtils.sin(x * 0.05f) + MathUtils.cos(y * 0.05f);
        float wave2 = MathUtils.sin(y * 0.1f) * 0.5f + MathUtils.cos(x * 0.15f) * 0.5f;
        float widthVariation = (wave1 + wave2) * 4.0f;
        return BASE_MOUNTAIN_WIDTH + widthVariation;
    }

    private int calculateTerrainHeight(int distToEdge, float mountainWidth, 
                                      int baseHeight, int peakHeight, int maxHeight) {
        float noise = MathUtils.random(0.8f, 1.2f);
        float edgeFactor = 1.0f - ((float)distToEdge / mountainWidth);
        edgeFactor = Math.max(0, edgeFactor);
        
        // Non-linear falloff for steep cliffs
        edgeFactor = edgeFactor * edgeFactor;
        
        int terrainHeight = (int)(baseHeight + (peakHeight - baseHeight) * edgeFactor * noise);
        return MathUtils.clamp(terrainHeight, 0, maxHeight - 1);
    }

    private void fillMountainBlocks(int x, int y, int terrainHeight) {
        for (int z = 0; z <= terrainHeight; z++) {
            GridWorld.BlockState existing = gridWorld.getBlock(x, y, z);
            if (existing == GridWorld.BlockState.AIR || existing == GridWorld.BlockState.GRASS) {
                gridWorld.setBlock(x, y, z, GridWorld.BlockState.MOUNTAIN_ROCK);
            }
        }
    }
}
