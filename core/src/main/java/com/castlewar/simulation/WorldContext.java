package com.castlewar.simulation;

import com.badlogic.gdx.math.MathUtils;
import com.castlewar.entity.Entity;
import com.castlewar.entity.King;
import com.castlewar.entity.MovingCube;
import com.castlewar.entity.Team;
import com.castlewar.world.GridWorld;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared simulation context so multiple windows can render the same world state.
 */
public class WorldContext {
    private static final int EDGE_MARGIN = 20;
    private static final int MIN_BATTLEFIELD_WIDTH = 180;

    private static final class CastleLayout {
        final int width;
        final int height;
        final int interiorLevels;
        final int battlementLevel;
        final int roofLevel;
        final int turretTopLevel;
        final int roomFloors;
        final int courtyardMargin;
        final int stairWidth;
        final int stairSpacing;
        final boolean gateOnRight;
        final GridWorld.BlockState wallType;

        CastleLayout(int width, int height, int interiorLevels, int roomFloors,
                     int courtyardMargin, int stairWidth, int stairSpacing,
                     boolean gateOnRight, GridWorld.BlockState wallType) {
            this.width = width;
            this.height = height;
            this.interiorLevels = interiorLevels;
            this.roomFloors = roomFloors;
            this.courtyardMargin = courtyardMargin;
            this.stairWidth = stairWidth;
            this.stairSpacing = stairSpacing;
            this.gateOnRight = gateOnRight;
            this.wallType = wallType;
            this.battlementLevel = interiorLevels + 1;
            this.roofLevel = battlementLevel + 1;
            this.turretTopLevel = roofLevel + 2;
        }
    }

    private final SimulationConfig config;
    private final GridWorld gridWorld;
    private final CastleLayout[] castleLayouts;
    private final int undergroundDepth;
    private final float totalVerticalBlocks;

    private final MovingCube movingCube;
    private final List<Entity> entities;

    private float leftCastleGateX;
    private float leftCastleGateY;
    private float rightCastleGateX;
    private float rightCastleGateY;
    private int battlefieldStartX;
    private int battlefieldEndX;

    public WorldContext(SimulationConfig config) {
        this.config = config;
        this.gridWorld = new GridWorld(
            (int) config.getWorldWidth(),
            (int) config.getWorldDepth(),
            (int) config.getWorldHeight()
        );
        this.entities = new ArrayList<>();
        this.castleLayouts = createCastleLayouts();
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

    private CastleLayout[] createCastleLayouts() {
        int rearMargin = Math.min(config.getCastleRearMargin(), gridWorld.getWidth() / 3);
        int minBattlefieldWidth = Math.min(MIN_BATTLEFIELD_WIDTH, gridWorld.getWidth() / 4);

        int usableWidth = gridWorld.getWidth() - (rearMargin * 2) - minBattlefieldWidth;
        int widthLimit = Math.max(12, usableWidth / 2);
        widthLimit = Math.min(widthLimit, Math.max(12, gridWorld.getWidth() / 2 - rearMargin));
        int castleWidth = MathUtils.clamp(config.getCastleWidth(), 12, widthLimit);

        int depthLimit = Math.max(12, gridWorld.getDepth() - EDGE_MARGIN * 2 - 2);
        int castleDepth = MathUtils.clamp(config.getCastleDepth(), 12, depthLimit);

    int levelLimit = Math.max(2, gridWorld.getHeight() - 4);
    int castleLevels = MathUtils.clamp(config.getCastleLevels(), 2, levelLimit);
    int roomFloors = Math.min(2, castleLevels);

        int minFootprint = Math.min(castleWidth, castleDepth);
        int courtyardMargin = MathUtils.clamp(minFootprint / 6, 2, Math.max(2, minFootprint / 3));
        int stairWidth = MathUtils.clamp(castleDepth / 8, 2, 6);
        int stairSpacing = MathUtils.clamp(castleWidth / 6, 2, 6);

        CastleLayout left = new CastleLayout(
            castleWidth,
            castleDepth,
            castleLevels,
            roomFloors,
            courtyardMargin,
            stairWidth,
            stairSpacing,
            true,
            GridWorld.BlockState.CASTLE_WHITE
        );
        CastleLayout right = new CastleLayout(
            castleWidth,
            castleDepth,
            castleLevels,
            roomFloors,
            courtyardMargin,
            stairWidth,
            stairSpacing,
            false,
            GridWorld.BlockState.CASTLE_BLACK
        );
        return new CastleLayout[] { left, right };
    }

    public SimulationConfig getConfig() {
        return config;
    }

    protected CastleLayout[] getCastleLayouts() {
        return castleLayouts;
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
        for (Entity entity : entities) {
            entity.update(delta, gridWorld);
        }
    }

    public List<Entity> getEntities() {
        return entities;
    }

    private void buildCastles() {
        int worldWidth = gridWorld.getWidth();
        int depth = gridWorld.getDepth();
        int centerY = depth / 2;
        int horizontalMargin = Math.min(config.getCastleRearMargin(), worldWidth / 3);
        int minBattlefieldWidth = Math.min(MIN_BATTLEFIELD_WIDTH, worldWidth / 4);

        CastleLayout[] layouts = getCastleLayouts();
        if (layouts.length == 0) {
            return;
        }

        CastleLayout leftLayout = layouts[0];
        int leftStartY = centerY - leftLayout.height / 2;
        int leftStartX = horizontalMargin;
        CastleLayout rightLayout = layouts.length > 1 ? layouts[1] : null;

        int rightStartX = -1;
        int rightStartY = leftStartY;
        if (rightLayout != null) {
            rightStartY = centerY - rightLayout.height / 2;
            rightStartX = worldWidth - rightLayout.width - horizontalMargin;

            int battlefieldWidth = rightStartX - (leftStartX + leftLayout.width);
            if (battlefieldWidth < minBattlefieldWidth) {
                int shortfall = minBattlefieldWidth - battlefieldWidth;
                int shiftLeft = Math.min(leftStartX - 1, (shortfall + 1) / 2);
                leftStartX = Math.max(1, leftStartX - shiftLeft);
                int shiftRight = shortfall - shiftLeft;
                rightStartX = Math.min(worldWidth - rightLayout.width - 1, rightStartX + shiftRight);
            }
        }

        buildMultiLevelCastle(leftLayout, leftStartX, leftStartY);
        spawnKing(leftLayout, leftStartX, leftStartY, Team.WHITE);
        
        leftCastleGateX = leftLayout.gateOnRight
            ? leftStartX + leftLayout.width - 1
            : leftStartX;
        leftCastleGateY = leftStartY + leftLayout.height / 2f;
        battlefieldStartX = leftStartX + leftLayout.width;

        if (rightLayout != null && rightStartX >= 0) {
            buildMultiLevelCastle(rightLayout, rightStartX, rightStartY);
            spawnKing(rightLayout, rightStartX, rightStartY, Team.BLACK);
            
            rightCastleGateX = rightLayout.gateOnRight
                ? rightStartX + rightLayout.width - 1
                : rightStartX;
            rightCastleGateY = rightStartY + rightLayout.height / 2f;
            battlefieldEndX = rightStartX;
        } else {
            rightCastleGateX = leftCastleGateX;
            rightCastleGateY = leftCastleGateY;
            battlefieldEndX = worldWidth - horizontalMargin;
        }

        battlefieldStartX = Math.max(0, battlefieldStartX);
        battlefieldEndX = Math.min(worldWidth, battlefieldEndX);
        if (battlefieldEndX <= battlefieldStartX) {
            battlefieldEndX = Math.min(worldWidth, battlefieldStartX + Math.max(1, minBattlefieldWidth));
        }
    }

    private void buildMultiLevelCastle(CastleLayout layout, int startX, int startY) {
        GridWorld.BlockState floorType = getFloorType(layout.wallType);
        GridWorld.BlockState stairType = getStairType(layout.wallType);

        buildCastle(layout, startX, startY, floorType);
        carveCourtyardLayer(layout, startX, startY, 0, true);
        maintainCentralCorridors(layout, startX, startY, 0, floorType);

        for (int level = 1; level <= layout.roofLevel; level++) {
            if (level <= layout.interiorLevels) {
                buildCastleLevel(layout, startX, startY, level, floorType);
                carveCourtyardLayer(layout, startX, startY, level, false);
                maintainCentralCorridors(layout, startX, startY, level, floorType);
                if (level <= layout.roomFloors) {
                    buildFloorRooms(layout, startX, startY, level, floorType);
                }
            } else if (level == layout.battlementLevel) {
                buildBattlements(layout, startX, startY, level, floorType);
            } else {
                buildRoof(layout, startX, startY, level, floorType);
                carveCourtyardLayer(layout, startX, startY, level, false);
                maintainCentralCorridors(layout, startX, startY, level, floorType);
            }
        }

        buildGrandStaircase(layout, startX, startY, stairType);

        buildTurret(startX, startY, layout);
        buildTurret(startX + layout.width - 1, startY, layout);
        buildTurret(startX, startY + layout.height - 1, layout);
        buildTurret(startX + layout.width - 1, startY + layout.height - 1, layout);
    }

    private void buildCastle(CastleLayout layout, int startX, int startY,
                             GridWorld.BlockState floorType) {
        int width = layout.width;
        int height = layout.height;
        GridWorld.BlockState wallType = layout.wallType;
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
        if (layout.gateOnRight) {
            gridWorld.setBlock(startX + width - 1, gateY, 0, GridWorld.BlockState.AIR);
            gridWorld.setBlock(startX + width, gateY, 0, GridWorld.BlockState.DIRT);
        } else {
            gridWorld.setBlock(startX, gateY, 0, GridWorld.BlockState.AIR);
            gridWorld.setBlock(startX - 1, gateY, 0, GridWorld.BlockState.DIRT);
        }
    }

    private void buildCastleLevel(CastleLayout layout, int startX, int startY, int z,
                                  GridWorld.BlockState floorType) {
        int width = layout.width;
        int height = layout.height;
        GridWorld.BlockState wallType = layout.wallType;
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

    private void buildBattlements(CastleLayout layout, int startX, int startY, int z,
                                  GridWorld.BlockState floorType) {
        int width = layout.width;
        int height = layout.height;
        GridWorld.BlockState wallType = layout.wallType;
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

    private void buildRoof(CastleLayout layout, int startX, int startY, int z,
                           GridWorld.BlockState floorType) {
        int width = layout.width;
        int height = layout.height;
        GridWorld.BlockState wallType = layout.wallType;
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

    private void buildTurret(int x, int y, CastleLayout layout) {
        for (int z = 1; z <= layout.turretTopLevel; z++) {
            gridWorld.setBlock(x, y, z, layout.wallType);
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

    private void carveCourtyardLayer(CastleLayout layout, int startX, int startY, int z, boolean groundLevel) {
        int width = layout.width;
        int height = layout.height;
        int margin = layout.courtyardMargin;
        int innerMinX = startX + 1;
        int innerMaxX = startX + width - 2;
        int innerMinY = startY + 1;
        int innerMaxY = startY + height - 2;
        int courtyardMinX = innerMinX + margin;
        int courtyardMaxX = innerMaxX - margin;
        int courtyardMinY = innerMinY + margin;
        int courtyardMaxY = innerMaxY - margin;
        if (courtyardMinX >= courtyardMaxX || courtyardMinY >= courtyardMaxY) {
            return;
        }
        int corridorX = startX + width / 2;
        int corridorY = startY + height / 2;
        GridWorld.BlockState filler = groundLevel ? GridWorld.BlockState.GRASS : GridWorld.BlockState.AIR;
        for (int x = courtyardMinX; x <= courtyardMaxX; x++) {
            for (int y = courtyardMinY; y <= courtyardMaxY; y++) {
                if (Math.abs(x - corridorX) <= 1 || Math.abs(y - corridorY) <= 1) {
                    continue;
                }
                gridWorld.setBlock(x, y, z, filler);
            }
        }
    }

    private void maintainCentralCorridors(CastleLayout layout, int startX, int startY, int z,
                                           GridWorld.BlockState floorType) {
        int width = layout.width;
        int height = layout.height;
        int corridorX = startX + width / 2;
        int corridorY = startY + height / 2;
        for (int x = startX + 1; x < startX + width - 1; x++) {
            gridWorld.setBlock(x, corridorY, z, floorType);
        }
        for (int y = startY + 1; y < startY + height - 1; y++) {
            gridWorld.setBlock(corridorX, y, z, floorType);
        }
    }

    private void buildGrandStaircase(CastleLayout layout, int startX, int startY,
                                     GridWorld.BlockState stairType) {
        int gateY = startY + layout.height / 2;
        int direction = layout.gateOnRight ? -1 : 1;
        int currentX = layout.gateOnRight ? startX + layout.width - 2 : startX + 1;
        for (int level = 0; level <= layout.interiorLevels; level++) {
            placeStairSegment(layout, currentX, gateY, level, stairType);
            clearStairwellAbove(layout, currentX, gateY, level + 1);
            currentX += direction * layout.stairSpacing;
            currentX = MathUtils.clamp(currentX, startX + 1, startX + layout.width - 2);
        }
    }

    private void placeStairSegment(CastleLayout layout, int centerX, int gateY, int level,
                                    GridWorld.BlockState stairType) {
        int offsetStart = -layout.stairWidth / 2;
        for (int i = 0; i < layout.stairWidth; i++) {
            int y = gateY + offsetStart + i;
            gridWorld.setBlock(centerX, y, level, stairType);
        }
    }

    private void clearStairwellAbove(CastleLayout layout, int centerX, int gateY, int startLevel) {
        int offsetStart = -layout.stairWidth / 2;
        for (int level = startLevel; level <= layout.roofLevel; level++) {
            for (int i = 0; i < layout.stairWidth; i++) {
                int y = gateY + offsetStart + i;
                gridWorld.setBlock(centerX, y, level, GridWorld.BlockState.AIR);
            }
        }
    }

    private void buildFloorRooms(CastleLayout layout, int startX, int startY, int z,
                                 GridWorld.BlockState floorType) {
        int width = layout.width;
        int height = layout.height;
        GridWorld.BlockState wallType = layout.wallType;
        int innerMinX = startX + 2;
        int innerMaxX = startX + width - 3;
        int innerMinY = startY + 2;
        int innerMaxY = startY + height - 3;
        int corridorX = startX + width / 2;
        int corridorY = startY + height / 2;

        if (z == 1) {
            int northMinY = innerMinY;
            int northMaxY = corridorY - 2;
            int southMinY = corridorY + 2;
            int southMaxY = innerMaxY;
            buildRoomSection(innerMinX, northMinY, innerMaxX, northMaxY, z, wallType,
                new int[][]{{corridorX, northMaxY}});
            buildRoomSection(innerMinX, southMinY, innerMaxX, southMaxY, z, wallType,
                new int[][]{{corridorX, southMinY}});
        } else if (z == 2) {
            int westMinX = innerMinX;
            int westMaxX = corridorX - 2;
            int eastMinX = corridorX + 2;
            int eastMaxX = innerMaxX;
            buildRoomSection(westMinX, innerMinY, westMaxX, innerMaxY, z, wallType,
                new int[][]{{westMaxX, corridorY}});
            buildRoomSection(eastMinX, innerMinY, eastMaxX, innerMaxY, z, wallType,
                new int[][]{{eastMinX, corridorY}});
        }
    }

    private void buildRoomSection(int minX, int minY, int maxX, int maxY, int z,
                                  GridWorld.BlockState wallType, int[][] doorPositions) {
        if (minX >= maxX || minY >= maxY) {
            return;
        }
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                boolean boundary = (x == minX || x == maxX || y == minY || y == maxY);
                if (!boundary) {
                    continue;
                }
                if (isDoorCoordinate(x, y, doorPositions)) {
                    continue;
                }
                gridWorld.setBlock(x, y, z, wallType);
            }
        }
    }

    private boolean isDoorCoordinate(int x, int y, int[][] doorPositions) {
        if (doorPositions == null) {
            return false;
        }
        for (int[] door : doorPositions) {
            if (door != null && door.length == 2 && door[0] == x && door[1] == y) {
                return true;
            }
        }
        return false;
    }

    private GridWorld.BlockState getFloorType(GridWorld.BlockState wallType) {
        return wallType == GridWorld.BlockState.CASTLE_WHITE
            ? GridWorld.BlockState.CASTLE_WHITE_FLOOR
            : GridWorld.BlockState.CASTLE_BLACK_FLOOR;
    }

    private GridWorld.BlockState getStairType(GridWorld.BlockState wallType) {
        return wallType == GridWorld.BlockState.CASTLE_WHITE
            ? GridWorld.BlockState.CASTLE_WHITE_STAIR
            : GridWorld.BlockState.CASTLE_BLACK_STAIR;
    }

    private void spawnKing(CastleLayout layout, int startX, int startY, Team team) {
        // Spawn king in the center of the first floor room
        int centerX = startX + layout.width / 2;
        int centerY = startY + layout.height / 2;
        // Offset slightly to be in a room, not the corridor wall
        int spawnX = centerX + 2; 
        int spawnY = centerY + 2;
        int spawnZ = 1; // First floor
        
        King king = new King(spawnX, spawnY, spawnZ, team);
        entities.add(king);
    }
}
