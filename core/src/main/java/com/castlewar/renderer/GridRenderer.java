package com.castlewar.renderer;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.castlewar.world.GridWorld;
import com.castlewar.world.GridWorld.BlockState;

/**
 * Renders a 2D grid world from different perspectives using simple rectangles.
 */
public class GridRenderer {
    private final ShapeRenderer shapeRenderer;
    private final float blockSize;
    
    public GridRenderer(float blockSize) {
        this.shapeRenderer = new ShapeRenderer();
        this.blockSize = blockSize;
    }
    
    /**
     * Render the side view (X/Z plane) at a specific Y slice.
     */
    public void renderSideView(GridWorld world, int ySlice) {
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        
        int width = world.getWidth();
        int height = world.getHeight();
        
        // Draw each block in the X/Z plane at the given Y slice
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < height; z++) {
                BlockState block = world.getBlock(x, ySlice, z);
                if (block != BlockState.AIR) {
                    setBlockColor(block);
                    float screenX = x * blockSize;
                    float screenZ = z * blockSize;
                    shapeRenderer.rect(screenX, screenZ, blockSize, blockSize);
                }
            }
        }
        
        shapeRenderer.end();
    }
    
    /**
     * Render the top-down view (X/Y plane) at a specific Z slice.
     */
    public void renderTopView(GridWorld world, int zSlice) {
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        
        int width = world.getWidth();
        int depth = world.getDepth();
        
        // Draw each block in the X/Y plane at the given Z slice
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < depth; y++) {
                BlockState block = world.getBlock(x, y, zSlice);
                if (block != BlockState.AIR) {
                    setBlockColor(block);
                    float screenX = x * blockSize;
                    float screenY = y * blockSize;
                    shapeRenderer.rect(screenX, screenY, blockSize, blockSize);
                }
            }
        }
        
        shapeRenderer.end();
    }
    
    private void setBlockColor(BlockState block) {
        switch (block) {
            case GRASS:
                shapeRenderer.setColor(0.3f, 0.7f, 0.2f, 1);
                break;
            case DIRT:
                shapeRenderer.setColor(0.6f, 0.4f, 0.2f, 1);
                break;
            case STONE:
                // Using stone as water/moat - blue color
                shapeRenderer.setColor(0.2f, 0.4f, 0.8f, 1);
                break;
            case CASTLE_WHITE:
                shapeRenderer.setColor(0.95f, 0.95f, 0.95f, 1);
                break;
            case CASTLE_BLACK:
                shapeRenderer.setColor(0.15f, 0.15f, 0.15f, 1);
                break;
            case CASTLE_WHITE_FLOOR:
                shapeRenderer.setColor(0.85f, 0.85f, 0.78f, 1);
                break;
            case CASTLE_BLACK_FLOOR:
                shapeRenderer.setColor(0.25f, 0.25f, 0.25f, 1);
                break;
            case CASTLE_WHITE_STAIR:
                shapeRenderer.setColor(0.98f, 0.78f, 0.35f, 1);
                break;
            case CASTLE_BLACK_STAIR:
                shapeRenderer.setColor(0.65f, 0.4f, 0.2f, 1);
                break;
            default:
                shapeRenderer.setColor(Color.WHITE);
                break;
        }
    }
    
    public ShapeRenderer getShapeRenderer() {
        return shapeRenderer;
    }
    
    public void dispose() {
        shapeRenderer.dispose();
    }
}
