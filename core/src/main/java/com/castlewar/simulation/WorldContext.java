package com.castlewar.simulation;

import com.castlewar.entity.MovingCube;
import com.castlewar.world.GridWorld;

/**
 * Shared simulation context so multiple windows can render the same world state.
 */
public class WorldContext {
    private static final int CASTLE_WIDTH = 14;
    private static final int CASTLE_HEIGHT = 14;
    private static final int CASTLE_INTERIOR_LEVELS = 6;
    private static final int CASTLE_BATTLEMENT_LEVEL = CASTLE_INTERIOR_LEVELS + 1;
    private static final int CASTLE_ROOF_LEVEL = CASTLE_BATTLEMENT_LEVEL + 1;
    private static final int CASTLE_TURRET_TOP_LEVEL = CASTLE_ROOF_LEVEL + 2;

    private final SimulationConfig config;
    private final GridWorld gridWorld;
    private final int undergroundDepth;
    private final float totalVerticalBlocks;

    private final MovingCube movingCube;

    private float leftCastleGateX;
    private float leftCastleGateY;
    private float rightCastleGateX;
    private float rightCastleGateY;

    public WorldContext(SimulationConfig config) {
        this.config = config;
        this.gridWorld = new GridWorld(
            (int) config.getWorldWidth(),
            (int) config.getWorldDepth(),
            (int) config.getWorldHeight()
        );
        this.undergroundDepth = gridWorld.getHeight();
        this.totalVerticalBlocks = gridWorld.getHeight() + undergroundDepth;

        buildCastles();

        float cubeZ = 0f;
        this.movingCube = new MovingCube(
            leftCastleGateX,
            leftCastleGateY,
            cubeZ,
            8f,
            leftCastleGateX,
            rightCastleGateX
        );
    }

    public SimulationConfig getConfig() {
        return config;
    }

    public GridWorld getGridWorld() {
        return gridWorld;
    }

    public int getUndergroundDepth() {
        return undergroundDepth;
    }

    public float getTotalVerticalBlocks() {
        return totalVerticalBlocks;
    }

    public MovingCube getMovingCube() {
        return movingCube;
    }

    public void update(float delta) {
        movingCube.update(delta);
    }

    private void buildCastles() {
        int width = gridWorld.getWidth();
        int depth = gridWorld.getDepth();
        int centerY = depth / 2;
        int castleStartY = centerY - CASTLE_HEIGHT / 2;
        int horizontalMargin = 10;
        int leftStartX = horizontalMargin;
        int rightStartX = width - CASTLE_WIDTH - horizontalMargin;

        buildMultiLevelCastle(leftStartX, castleStartY, CASTLE_WIDTH, CASTLE_HEIGHT,
            GridWorld.BlockState.CASTLE_WHITE, true);
        leftCastleGateX = leftStartX + CASTLE_WIDTH - 1;
        leftCastleGateY = castleStartY + CASTLE_HEIGHT / 2f;

        buildMultiLevelCastle(rightStartX, castleStartY, CASTLE_WIDTH, CASTLE_HEIGHT,
            GridWorld.BlockState.CASTLE_BLACK, false);
        rightCastleGateX = rightStartX;
        rightCastleGateY = castleStartY + CASTLE_HEIGHT / 2f;
    }

    private void buildMultiLevelCastle(int startX, int startY, int width, int height,
                                       GridWorld.BlockState wallType, boolean gateOnRight) {
        GridWorld.BlockState floorType = getFloorType(wallType);

        buildCastle(startX, startY, width, height, wallType, floorType, gateOnRight);

        for (int level = 1; level <= CASTLE_ROOF_LEVEL; level++) {
            if (level <= CASTLE_INTERIOR_LEVELS) {
                buildCastleLevel(startX, startY, width, height, level, wallType, floorType);
            } else if (level == CASTLE_BATTLEMENT_LEVEL) {
                buildBattlements(startX, startY, width, height, level, wallType, floorType);
            } else {
                buildRoof(startX, startY, width, height, level, wallType, floorType);
            }
        }

        buildTurret(startX, startY, wallType);
        buildTurret(startX + width - 1, startY, wallType);
        buildTurret(startX, startY + height - 1, wallType);
        buildTurret(startX + width - 1, startY + height - 1, wallType);
    }

    private void buildCastle(int startX, int startY, int width, int height,
                             GridWorld.BlockState wallType, GridWorld.BlockState floorType,
                             boolean gateOnRight) {
        for (int x = startX - 1; x < startX + width + 1; x++) {
            for (int y = startY - 1; y < startY + height + 1; y++) {
                if (x >= 0 && x < gridWorld.getWidth() && y >= 0 && y < gridWorld.getDepth()) {
                    if (x == startX - 1 || x == startX + width || y == startY - 1 || y == startY + height) {
                        gridWorld.setBlock(x, y, 0, GridWorld.BlockState.STONE);
                    }
                }
            }
        }

        for (int x = startX; x < startX + width; x++) {
            gridWorld.setBlock(x, startY, 0, wallType);
            gridWorld.setBlock(x, startY + height - 1, 0, wallType);
        }
        for (int y = startY; y < startY + height; y++) {
            gridWorld.setBlock(startX, y, 0, wallType);
            gridWorld.setBlock(startX + width - 1, y, 0, wallType);
        }

        fillInteriorWithFloor(startX, startY, width, height, 0, floorType);

        int gateY = startY + height / 2;
        if (gateOnRight) {
            gridWorld.setBlock(startX + width - 1, gateY, 0, GridWorld.BlockState.AIR);
            gridWorld.setBlock(startX + width, gateY, 0, GridWorld.BlockState.DIRT);
        } else {
            gridWorld.setBlock(startX, gateY, 0, GridWorld.BlockState.AIR);
            gridWorld.setBlock(startX - 1, gateY, 0, GridWorld.BlockState.DIRT);
        }
    }

    private void buildCastleLevel(int startX, int startY, int width, int height, int z,
                                  GridWorld.BlockState wallType, GridWorld.BlockState floorType) {
        for (int x = startX; x < startX + width; x++) {
            gridWorld.setBlock(x, startY, z, wallType);
            gridWorld.setBlock(x, startY + height - 1, z, wallType);
        }
        for (int y = startY; y < startY + height; y++) {
            gridWorld.setBlock(startX, y, z, wallType);
            gridWorld.setBlock(startX + width - 1, y, z, wallType);
        }

        fillInteriorWithFloor(startX, startY, width, height, z, floorType);
    }

    private void buildBattlements(int startX, int startY, int width, int height, int z,
                                  GridWorld.BlockState wallType, GridWorld.BlockState floorType) {
        fillInteriorWithFloor(startX, startY, width, height, z, floorType);

        for (int x = startX; x < startX + width; x++) {
            if (x % 2 == 0) {
                gridWorld.setBlock(x, startY, z, wallType);
                gridWorld.setBlock(x, startY + height - 1, z, wallType);
            }
        }
        for (int y = startY; y < startY + height; y++) {
            if (y % 2 == 0) {
                gridWorld.setBlock(startX, y, z, wallType);
                gridWorld.setBlock(startX + width - 1, y, z, wallType);
            }
        }
    }

    private void buildRoof(int startX, int startY, int width, int height, int z,
                           GridWorld.BlockState wallType, GridWorld.BlockState floorType) {
        for (int x = startX; x < startX + width; x++) {
            gridWorld.setBlock(x, startY, z, wallType);
            gridWorld.setBlock(x, startY + height - 1, z, wallType);
        }
        for (int y = startY; y < startY + height; y++) {
            gridWorld.setBlock(startX, y, z, wallType);
            gridWorld.setBlock(startX + width - 1, y, z, wallType);
        }

        fillInteriorWithFloor(startX, startY, width, height, z, floorType);

        int centerX = startX + width / 2;
        int centerY = startY + height / 2;
        for (int x = startX + 1; x < startX + width - 1; x++) {
            gridWorld.setBlock(x, centerY, z, wallType);
        }
        for (int y = startY + 1; y < startY + height - 1; y++) {
            gridWorld.setBlock(centerX, y, z, wallType);
        }
    }

    private void buildTurret(int x, int y, GridWorld.BlockState wallType) {
        for (int z = 1; z <= CASTLE_TURRET_TOP_LEVEL; z++) {
            gridWorld.setBlock(x, y, z, wallType);
        }
    }

    private void fillInteriorWithFloor(int startX, int startY, int width, int height, int z,
                                       GridWorld.BlockState floorType) {
        for (int x = startX + 1; x < startX + width - 1; x++) {
            for (int y = startY + 1; y < startY + height - 1; y++) {
                gridWorld.setBlock(x, y, z, floorType);
            }
        }
    }

    private GridWorld.BlockState getFloorType(GridWorld.BlockState wallType) {
        return wallType == GridWorld.BlockState.CASTLE_WHITE
            ? GridWorld.BlockState.CASTLE_WHITE_FLOOR
            : GridWorld.BlockState.CASTLE_BLACK_FLOOR;
    }
}
