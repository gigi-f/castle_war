package com.castlewar.voxel;

/**
 * Voxel block types for the world.
 */
public enum BlockType {
    AIR(false, null),
    GRASS(true, new float[]{0.3f, 0.7f, 0.2f}),
    DIRT(true, new float[]{0.6f, 0.4f, 0.2f}),
    STONE(true, new float[]{0.5f, 0.5f, 0.5f}),
    CASTLE_WHITE(true, new float[]{0.95f, 0.95f, 0.95f}),
    CASTLE_BLACK(true, new float[]{0.15f, 0.15f, 0.15f});

    private final boolean solid;
    private final float[] color;

    BlockType(boolean solid, float[] color) {
        this.solid = solid;
        this.color = color;
    }

    public boolean isSolid() {
        return solid;
    }

    public float[] getColor() {
        return color;
    }
}
