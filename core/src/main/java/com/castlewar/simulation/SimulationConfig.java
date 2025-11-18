package com.castlewar.simulation;

/**
 * Configuration parameters for the simulation world.
 * All values are expressed in block units.
 */
public class SimulationConfig {
    private int worldWidth = 200;
    private int worldDepth = 120;
    private int worldHeight = 64;

    private int castleWidth = 60;
    private int castleDepth = 40;
    private int castleLevels = 8;
    private int castleRearMargin = 20;

    public int getWorldWidth() {
        return worldWidth;
    }

    public void setWorldWidth(int worldWidth) {
        this.worldWidth = Math.max(16, worldWidth);
    }

    public int getWorldDepth() {
        return worldDepth;
    }

    public void setWorldDepth(int worldDepth) {
        this.worldDepth = Math.max(16, worldDepth);
    }

    public int getWorldHeight() {
        return worldHeight;
    }

    public void setWorldHeight(int worldHeight) {
        this.worldHeight = Math.max(8, worldHeight);
    }

    public int getCastleWidth() {
        return castleWidth;
    }

    public void setCastleWidth(int castleWidth) {
        this.castleWidth = Math.max(12, castleWidth);
    }

    public int getCastleDepth() {
        return castleDepth;
    }

    public void setCastleDepth(int castleDepth) {
        this.castleDepth = Math.max(12, castleDepth);
    }

    public int getCastleLevels() {
        return castleLevels;
    }

    public void setCastleLevels(int castleLevels) {
        this.castleLevels = Math.max(2, castleLevels);
    }

    public int getCastleRearMargin() {
        return castleRearMargin;
    }

    public void setCastleRearMargin(int castleRearMargin) {
        this.castleRearMargin = Math.max(2, castleRearMargin);
    }
}
