package com.castlewar.simulation;

/**
 * Configuration parameters for the simulation world.
 */
public class SimulationConfig {
    private float worldWidth = 500f;
    private float worldDepth = 500f;
    private float worldHeight = 100f;

    public float getWorldWidth() {
        return worldWidth;
    }

    public void setWorldWidth(float worldWidth) {
        this.worldWidth = worldWidth;
    }

    public float getWorldDepth() {
        return worldDepth;
    }

    public void setWorldDepth(float worldDepth) {
        this.worldDepth = worldDepth;
    }

    public float getWorldHeight() {
        return worldHeight;
    }

    public void setWorldHeight(float worldHeight) {
        this.worldHeight = worldHeight;
    }
}
