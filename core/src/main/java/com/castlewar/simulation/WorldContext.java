package com.castlewar.simulation;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.castlewar.debug.AiDebugLog;
import com.castlewar.entity.Assassin;
import com.castlewar.entity.Entity;
import com.castlewar.entity.Guard;
import com.castlewar.entity.King;
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
    private final AiDebugLog debugLog = new AiDebugLog();

    public static final class CastleBounds {
        private final int minX;
        private final int maxX;
        private final int minY;
        private final int maxY;
        private final int minZ;
        private final int maxZ;

        public CastleBounds(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
            this.minX = minX;
            this.maxX = maxX;
            this.minY = minY;
            this.maxY = maxY;
            this.minZ = minZ;
            this.maxZ = maxZ;
        }

        public int getMinX() { return minX; }
        public int getMaxX() { return maxX; }
        public int getMinY() { return minY; }
        public int getMaxY() { return maxY; }
        public int getMinZ() { return minZ; }
        public int getMaxZ() { return maxZ; }
    }

    private final List<Entity> entities;

    private float leftCastleGateX;
    private float leftCastleGateY;
    private float rightCastleGateX;
    private float rightCastleGateY;
    private int battlefieldStartX;
    private int battlefieldEndX;
    private CastleBounds whiteCastleBounds;
    private CastleBounds blackCastleBounds;

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
        buildPerimeterMountains();
    }

    private void buildPerimeterMountains() {
        int width = gridWorld.getWidth();
        int depth = gridWorld.getDepth();
        int height = gridWorld.getHeight();
        
        // Mountain parameters
        int baseHeight = 15; // Minimum height of mountains
        int peakHeight = height - 5; // Maximum height
        int baseMountainWidth = 15; // Base width
        
        // Use a fixed seed for consistent terrain
        MathUtils.random.setSeed(12345);
        
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < depth; y++) {
                // Calculate distance to nearest edge
                int distToEdgeX = Math.min(x, width - 1 - x);
                int distToEdgeY = Math.min(y, depth - 1 - y);
                int distToEdge = Math.min(distToEdgeX, distToEdgeY);
                
                // Calculate variable mountain width using sine waves for natural curves
                // Combine multiple frequencies for more organic look
                float wave1 = MathUtils.sin(x * 0.05f) + MathUtils.cos(y * 0.05f); // Low freq
                float wave2 = MathUtils.sin(y * 0.1f) * 0.5f + MathUtils.cos(x * 0.15f) * 0.5f; // Med freq
                
                float widthVariation = (wave1 + wave2) * 4.0f; // Vary by +/- ~6-8 blocks
                float currentMountainWidth = baseMountainWidth + widthVariation;
                
                // Only build mountains near the edge
                if (distToEdge < currentMountainWidth) {
                    // Calculate height based on distance to edge (closer to edge = higher)
                    // Use noise to make it jagged
                    float noise = MathUtils.random(0.8f, 1.2f);
                    
                    // Normalize distance based on the LOCAL width
                    float edgeFactor = 1.0f - ((float)distToEdge / currentMountainWidth); 
                    if (edgeFactor < 0) edgeFactor = 0;
                    
                    // Make the falloff non-linear (steep cliffs)
                    edgeFactor = edgeFactor * edgeFactor;
                    
                    int terrainHeight = (int)(baseHeight + (peakHeight - baseHeight) * edgeFactor * noise);
                    
                    // Clamp height
                    terrainHeight = MathUtils.clamp(terrainHeight, 0, height - 1);
                    
                    // Fill blocks
                    for (int z = 0; z <= terrainHeight; z++) {
                        // Don't overwrite existing castle blocks or water
                        if (gridWorld.getBlock(x, y, z) == GridWorld.BlockState.AIR || 
                            gridWorld.getBlock(x, y, z) == GridWorld.BlockState.GRASS) {
                            
                            // Top blocks are grass/snow (optional), rest is rock
                            // For now, just use mountain rock
                            gridWorld.setBlock(x, y, z, GridWorld.BlockState.MOUNTAIN_ROCK);
                        }
                    }
                }
            }
        }
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
    int castleLevels = MathUtils.clamp(config.getCastleLevels() * 3, 2, levelLimit);
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

    public AiDebugLog getDebugLog() {
        return debugLog;
    }

    public CastleBounds getCastleBounds(Team team) {
        return team == Team.WHITE ? whiteCastleBounds : blackCastleBounds;
    }

    public void update(float delta) {
        // Update AI and Physics
        for (Entity entity : entities) {
            if (entity instanceof com.castlewar.entity.Unit) {
                com.castlewar.entity.Unit unit = (com.castlewar.entity.Unit) entity;
                if (!unit.isCorpse()) {
                    unit.scanForEnemies(entities, gridWorld);
                }
            }
            if (entity instanceof Assassin) {
                Assassin assassin = (Assassin) entity;
                if (!assassin.isCorpse()) {
                    assassin.checkForGuards(entities, gridWorld, delta);
                }
            }
            entity.update(delta, gridWorld);
        }
        
        // Remove expired corpses
        entities.removeIf(e -> e instanceof com.castlewar.entity.Unit && ((com.castlewar.entity.Unit)e).shouldDespawn());
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
        whiteCastleBounds = new CastleBounds(
            leftStartX,
            leftStartX + leftLayout.width - 1,
            leftStartY,
            leftStartY + leftLayout.height - 1,
            0,
            leftLayout.battlementLevel + 1
        );
        spawnKing(leftLayout, leftStartX, leftStartY, Team.WHITE);
        
        leftCastleGateX = leftLayout.gateOnRight
            ? leftStartX + leftLayout.width - 1
            : leftStartX;
        leftCastleGateY = leftStartY + leftLayout.height / 2f;
        battlefieldStartX = leftStartX + leftLayout.width;

        if (rightLayout != null && rightStartX >= 0) {
            buildMultiLevelCastle(rightLayout, rightStartX, rightStartY);
            blackCastleBounds = new CastleBounds(
                rightStartX,
                rightStartX + rightLayout.width - 1,
                rightStartY,
                rightStartY + rightLayout.height - 1,
                0,
                rightLayout.battlementLevel + 1
            );
            spawnKing(rightLayout, rightStartX, rightStartY, Team.BLACK);
            
            rightCastleGateX = rightLayout.gateOnRight
                ? rightStartX + rightLayout.width - 1
                : rightStartX;
            rightCastleGateY = rightStartY + rightLayout.height / 2f;
            battlefieldEndX = rightStartX;
        } else {
            blackCastleBounds = whiteCastleBounds;
            rightCastleGateX = leftCastleGateX;
            rightCastleGateY = leftCastleGateY;
            battlefieldEndX = worldWidth - horizontalMargin;
        }

        battlefieldStartX = Math.max(0, battlefieldStartX);
        battlefieldEndX = Math.min(worldWidth, battlefieldEndX);
        if (battlefieldEndX <= battlefieldStartX) {
            battlefieldEndX = Math.min(worldWidth, battlefieldStartX + Math.max(1, minBattlefieldWidth));
        }
        
        // Spawn Assassins
        for (Team team : Team.values()) {
            float startX = (team == Team.WHITE) ? leftStartX : rightStartX;
            float startY = (team == Team.WHITE) ? leftStartY : rightStartY;
            CastleLayout layout = (team == Team.WHITE) ? leftLayout : rightLayout;
            if (layout == null) continue;

            float assassinX = (team == Team.WHITE) ? battlefieldStartX + 5 : battlefieldEndX - 5;
            float assassinY = startY + layout.height / 2f;
            float assassinZ = 1; // Spawn at ground level (z=1, same as King)
            Assassin assassin = new Assassin(assassinX, assassinY, assassinZ, team, this);
            entities.add(assassin);
        }
        
        // Set Assassin targets
        King whiteKing = null;
        King blackKing = null;
        for (Entity e : entities) {
            if (e instanceof King) {
                if (e.getTeam() == Team.WHITE) whiteKing = (King)e;
                else blackKing = (King)e;
            }
        }
        
        for (Entity e : entities) {
            if (e instanceof Assassin) {
                Assassin a = (Assassin)e;
                if (a.getTeam() == Team.WHITE) {
                    a.setTargetKing(blackKing);
                    // Target Right Castle Gate (Bridge Start)
                    // Right Castle starts at rightStartX. Gate is on Left side (facing center).
                    // Bridge starts at rightStartX - 1.
                    float gateX = rightStartX - 1; 
                    float gateY = rightStartY + rightLayout.height / 2f;
                    a.setInfiltrationTarget(new Vector3(gateX, gateY, 0));
                } else {
                    a.setTargetKing(whiteKing);
                    // Target Left Castle Gate (Bridge Start)
                    // Left Castle starts at leftStartX. Gate is on Right side (facing center).
                    // Bridge starts at leftStartX + width.
                    float gateX = leftStartX + leftLayout.width;
                    float gateY = leftStartY + leftLayout.height / 2f;
                    a.setInfiltrationTarget(new Vector3(gateX, gateY, 0));
                }
            }
        }
        
        // Spawn Streaker (Debug)
        float midX = worldWidth / 2f;
        float midY = centerY;
        // Targets: Bridge entrances
        Vector3 leftT = new Vector3(leftStartX + leftLayout.width, leftStartY + leftLayout.height/2f, 0);
        Vector3 rightT = new Vector3(rightStartX - 1, rightStartY + rightLayout.height/2f, 0);
        
        // Streaker streaker = new Streaker(midX, midY, 10, Team.WHITE, leftT, rightT);
        // entities.add(streaker);
    }

    private void buildSouthTurretWall(CastleLayout layout, int startX, int startY) {
        int r = 6;
        int x1 = startX + r;
        int x2 = startX + layout.width - 1 - r;
        int y1 = startY + r; // southernmost turret Y coordinate
        int wallY = y1 - 4; // place wall south of turret openings
        int wallThickness = 5;

        // Build wall across the southern side between the two turrets
        // Start at the outer face of the left turret and end at the outer face of the right turret
        int wallStartX = x1 + r; // Right edge of left turret
        int wallEndX = x2 - r;   // Left edge of right turret
        
        for (int y = wallY - wallThickness/2; y <= wallY + wallThickness/2; y++) {
            for (int x = wallStartX; x <= wallEndX; x++) {
                for (int z = 0; z < layout.battlementLevel; z++) {
                    gridWorld.setBlock(x, y, z, layout.wallType);
                }
                // Battlements pattern
                if ((x + y) % 2 == 0) {
                    gridWorld.setBlock(x, y, layout.battlementLevel, layout.wallType);
                }
            }
        }
        
        // Add archer windows on the outer (south) face at the second level
        // Windows are spaced every 5 blocks
        int windowSpacing = 5;
        
        for (int x = wallStartX + 2; x <= wallEndX - 2; x += windowSpacing) {
            // Window structure:
            // Z=12: walkway floor (already built)
            // Z=13: railing (1 block high wall - prevents accidental falls)
            // Z=14-15: air opening (2 blocks high - archer shooting position)
            // Z=16+: continues as normal wall
            
            // Cut the window through the ENTIRE wall thickness
            for (int y = wallY - wallThickness/2; y <= wallY + wallThickness/2; y++) {
                // Keep the railing at Z=13
                // Clear Z=14 and Z=15 for the window opening
                gridWorld.setBlock(x, y, 14, GridWorld.BlockState.AIR);
                gridWorld.setBlock(x, y, 15, GridWorld.BlockState.AIR);
            }
        }
    }

    private void buildNorthernWall(CastleLayout layout, int startX, int startY) {
        int r = 6;
        int x1 = startX + r;
        int x2 = startX + layout.width - 1 - r;
        int y2 = startY + layout.height - 1 - r; // northernmost turret Y coordinate
        int wallY = y2 + 4; // place wall north of turret openings
        int wallThickness = 5;

        // Build wall across the northern side between the two turrets
        // Start at the outer face of the left turret and end at the outer face of the right turret
        int wallStartX = x1 + r; // Right edge of left turret
        int wallEndX = x2 - r;   // Left edge of right turret
        
        for (int y = wallY - wallThickness/2; y <= wallY + wallThickness/2; y++) {
            for (int x = wallStartX; x <= wallEndX; x++) {
                for (int z = 0; z < layout.battlementLevel; z++) {
                    gridWorld.setBlock(x, y, z, layout.wallType);
                }
                // Battlements pattern
                if ((x + y) % 2 == 0) {
                    gridWorld.setBlock(x, y, layout.battlementLevel, layout.wallType);
                }
            }
        }
        
        // Add archer windows on the outer (north) face at the second level
        // Windows are spaced every 5 blocks
        int windowSpacing = 5;
        
        for (int x = wallStartX + 2; x <= wallEndX - 2; x += windowSpacing) {
            // Window structure:
            // Z=12: walkway floor (already built)
            // Z=13: railing (1 block high wall - prevents accidental falls)
            // Z=14-15: air opening (2 blocks high - archer shooting position)
            // Z=16+: continues as normal wall
            
            // Cut the window through the ENTIRE wall thickness
            for (int y = wallY - wallThickness/2; y <= wallY + wallThickness/2; y++) {
                // Keep the railing at Z=13
                // Clear Z=14 and Z=15 for the window opening
                gridWorld.setBlock(x, y, 14, GridWorld.BlockState.AIR);
                gridWorld.setBlock(x, y, 15, GridWorld.BlockState.AIR);
            }
        }
    }

    private void buildSouthWalkway(CastleLayout layout, int startX, int startY) {
        int r = 6;
        int x1 = startX + r;
        int x2 = startX + layout.width - 1 - r;
        int y1 = startY + r;
        
        // Second level is at z=12 (first level is 0-11, second level is 12-23)
        int walkwayZ = 12;
        int walkwayWidth = 3; // 3 blocks wide walkway
        
        // The walkway should run from door to door
        // Doors face East for left turret and West for right turret
        // They are positioned on the East side of (x1, y1) and West side of (x2, y1)
        
        // Walkway runs along the south side, connecting the doors
        int walkwayY = y1 - 2; // Position the walkway just south of the turret centers
        
        GridWorld.BlockState floorType = getFloorType(layout.wallType);
        
        // Build the walkway floor from turret to turret
        int walkwayStartX = x1 + r; // Right edge of left turret (where door exits)
        int walkwayEndX = x2 - r;   // Left edge of right turret (where door exits)
        
        for (int x = walkwayStartX; x <= walkwayEndX; x++) {
            for (int y = walkwayY - walkwayWidth/2; y <= walkwayY + walkwayWidth/2; y++) {
                gridWorld.setBlock(x, y, walkwayZ, floorType);
                // Clear space above for headroom
                for (int clearZ = walkwayZ + 1; clearZ < walkwayZ + 6; clearZ++) {
                    gridWorld.setBlock(x, y, clearZ, GridWorld.BlockState.AIR);
                }
            }
        }
    }

    private void buildNorthWalkway(CastleLayout layout, int startX, int startY) {
        int r = 6;
        int x1 = startX + r;
        int x2 = startX + layout.width - 1 - r;
        int y2 = startY + layout.height - 1 - r;
        
        // Second level is at z=12
        int walkwayZ = 12;
        int walkwayWidth = 3;
        
        // Walkway runs along the north side, connecting the doors
        int walkwayY = y2 + 2; // Position the walkway just north of the turret centers
        
        GridWorld.BlockState floorType = getFloorType(layout.wallType);
        
        // Build the walkway floor from turret to turret
        int walkwayStartX = x1 + r; // Right edge of left turret
        int walkwayEndX = x2 - r;   // Left edge of right turret
        
        for (int x = walkwayStartX; x <= walkwayEndX; x++) {
            for (int y = walkwayY - walkwayWidth/2; y <= walkwayY + walkwayWidth/2; y++) {
                gridWorld.setBlock(x, y, walkwayZ, floorType);
                // Clear space above for headroom
                for (int clearZ = walkwayZ + 1; clearZ < walkwayZ + 6; clearZ++) {
                    gridWorld.setBlock(x, y, clearZ, GridWorld.BlockState.AIR);
                }
            }
        }
    }

    private void buildSecondFloor(CastleLayout layout, int startX, int startY) {
        int r = 6;
        int x1 = startX + r;
        int x2 = startX + layout.width - 1 - r;
        int y1 = startY + r;
        int y2 = startY + layout.height - 1 - r;
        
        int floorZ = 12; // Second level
        
        // Use stair type for wooden appearance
        GridWorld.BlockState stairType = getStairType(layout.wallType);
        
        // Fill the interior space between the walkways
        // South walkway is at y1 - 2, north walkway is at y2 + 2
        // We'll fill from turret to turret in X, and between the walkways in Y
        
        int floorStartX = x1 + r; // Right edge of left turrets
        int floorEndX = x2 - r;   // Left edge of right turrets
        
        // Fill between the walkways
        // South walkway ends around y1 - 2 + 1 (half width)
        // North walkway starts around y2 + 2 - 1 (half width)
        int floorStartY = y1 - 2 + 2; // Just past south walkway
        int floorEndY = y2 + 2 - 2;   // Just before north walkway
        
        for (int x = floorStartX; x <= floorEndX; x++) {
            for (int y = floorStartY; y <= floorEndY; y++) {
                gridWorld.setBlock(x, y, floorZ, stairType);
                // Clear space above for headroom
                for (int clearZ = floorZ + 1; clearZ < floorZ + 6; clearZ++) {
                    gridWorld.setBlock(x, y, clearZ, GridWorld.BlockState.AIR);
                }
            }
        }
    }

    private void buildSecondFloorCeiling(CastleLayout layout, int startX, int startY) {
        int r = 6;
        int x1 = startX + r;
        int x2 = startX + layout.width - 1 - r;
        int y1 = startY + r;
        int y2 = startY + layout.height - 1 - r;
        
        // Ceiling is just below the battlements
        int ceilingZ = layout.battlementLevel - 1;
        
        // Use stair type for wooden appearance
        GridWorld.BlockState stairType = getStairType(layout.wallType);
        
        // Cover the same area as the second floor
        int ceilingStartX = x1 + r; // Right edge of left turrets
        int ceilingEndX = x2 - r;   // Left edge of right turrets
        
        int ceilingStartY = y1 - 2 + 2; // Just past south walkway
        int ceilingEndY = y2 + 2 - 2;   // Just before north walkway
        
        for (int x = ceilingStartX; x <= ceilingEndX; x++) {
            for (int y = ceilingStartY; y <= ceilingEndY; y++) {
                gridWorld.setBlock(x, y, ceilingZ, stairType);
            }
        }
    }

    private void buildMultiLevelCastle(CastleLayout layout, int startX, int startY) {
        GridWorld.BlockState floorType = getFloorType(layout.wallType);
        GridWorld.BlockState stairType = getStairType(layout.wallType);

        int r = 6; // Radius of spiral shaft
        int x1 = startX + r;
        int x2 = startX + layout.width - 1 - r;
        int y1 = startY + r;
        int y2 = startY + layout.height - 1 - r;

        // Build 4 corner staircases
        // SW (x1, y1) -> Face East (0)
        buildSpiralStaircase(layout, x1, y1, stairType, 0);
        // SE (x2, y1) -> Face West (2)
        buildSpiralStaircase(layout, x2, y1, stairType, 2);
        // NW (x1, y2) -> Face East (0)
        buildSpiralStaircase(layout, x1, y2, stairType, 0);
        // NE (x2, y2) -> Face West (2)
        buildSpiralStaircase(layout, x2, y2, stairType, 2);

        // Build all required perimeter walls
        buildFrontWall(layout, startX, startY);
        // Build rear wall (opposite side from front, no gate)
        buildRearWall(layout, startX, startY);
        // Build northern wall connecting the northern towers
        buildNorthernWall(layout, startX, startY);
        // Add northern walkway
        buildNorthWalkway(layout, startX, startY);
        // Connect southernmost turrets with a wall south of their openings
        buildSouthTurretWall(layout, startX, startY);
        // Add second-level walkway connecting the south turrets
        buildSouthWalkway(layout, startX, startY);
        // Build second floor connecting the walkways
        buildSecondFloor(layout, startX, startY);
        // Add ceiling to second floor
        buildSecondFloorCeiling(layout, startX, startY);
    }

    private void buildFrontWall(CastleLayout layout, int startX, int startY) {
        int r = 6;
        int x1 = startX + r;
        int x2 = startX + layout.width - 1 - r;
        int y1 = startY + r;
        int y2 = startY + layout.height - 1 - r;
        
        int wallX = layout.gateOnRight ? x2 : x1;
        int wallThickness = 5;
        
        // Connect the towers (overlap slightly to ensure seal)
        // Towers are at y1 and y2. 
        // We want to fill from y1 + 4 to y2 - 4
        int wallStartY = y1 + 4;
        int wallEndY = y2 - 4;
        
        // Build Wall
        for (int x = wallX - wallThickness/2; x <= wallX + wallThickness/2; x++) {
            for (int y = wallStartY; y <= wallEndY; y++) {
                for (int z = 0; z < layout.battlementLevel; z++) {
                    gridWorld.setBlock(x, y, z, layout.wallType);
                }
                // Battlements
                if ((x + y) % 2 == 0) {
                    gridWorld.setBlock(x, y, layout.battlementLevel, layout.wallType);
                }
            }
        }
        
        // Carve Archway
        int centerY = startY + layout.height / 2;
        int archWidth = 12;
        int archHeight = 10;
        
        for (int x = wallX - wallThickness/2; x <= wallX + wallThickness/2; x++) {
            for (int y = centerY - archWidth/2; y <= centerY + archWidth/2; y++) {
                for (int z = 0; z < archHeight; z++) {
                    gridWorld.setBlock(x, y, z, GridWorld.BlockState.AIR);
                }
            }
        }
    }

    private void buildRearWall(CastleLayout layout, int startX, int startY) {
        int r = 6;
        int x1 = startX + r;
        int x2 = startX + layout.width - 1 - r;
        int y1 = startY + r;
        int y2 = startY + layout.height - 1 - r;
        
        // Rear wall is on opposite side from front wall
        int wallX = layout.gateOnRight ? x1 : x2;
        int wallThickness = 5;
        
        // Connect the towers - same Y range as front wall
        int wallStartY = y1 + 4;
        int wallEndY = y2 - 4;
        
        // Build Wall
        for (int x = wallX - wallThickness/2; x <= wallX + wallThickness/2; x++) {
            for (int y = wallStartY; y <= wallEndY; y++) {
                for (int z = 0; z < layout.battlementLevel; z++) {
                    gridWorld.setBlock(x, y, z, layout.wallType);
                }
                // Battlements
                if ((x + y) % 2 == 0) {
                    gridWorld.setBlock(x, y, layout.battlementLevel, layout.wallType);
                }
            }
        }
        
        // No archway on rear wall - it's solid for defense
    }

    private void buildCastle(CastleLayout layout, int startX, int startY,
                             GridWorld.BlockState floorType) {
        int width = layout.width;
        int height = layout.height;
        GridWorld.BlockState wallType = layout.wallType;
        buildMoat(layout, startX, startY);
        buildDrawbridge(layout, startX, startY, floorType);

        for (int x = startX; x < startX + width; x++) {
            gridWorld.setBlock(x, startY, 0, wallType);
            gridWorld.setBlock(x, startY + height - 1, 0, wallType);
            // Add windows
            if ((x - startX) % 4 == 2) {
                gridWorld.setBlock(x, startY, 2, GridWorld.BlockState.WINDOW);
                gridWorld.setBlock(x, startY + height - 1, 2, GridWorld.BlockState.WINDOW);
            }
        }
        for (int y = startY; y < startY + height; y++) {
            gridWorld.setBlock(startX, y, 0, wallType);
            gridWorld.setBlock(startX + width - 1, y, 0, wallType);
            // Add windows
            if ((y - startY) % 4 == 2) {
                gridWorld.setBlock(startX, y, 2, GridWorld.BlockState.WINDOW);
                gridWorld.setBlock(startX + width - 1, y, 2, GridWorld.BlockState.WINDOW);
            }
        }

        // Removed fillInteriorWithFloor call

        int gateY = startY + height / 2;
        int gateWidth = 4; // Wider gate
        int gateStart = gateY - gateWidth / 2;
        
        if (layout.gateOnRight) {
            for (int i = 0; i < gateWidth; i++) {
                gridWorld.setBlock(startX + width - 1, gateStart + i, 0, GridWorld.BlockState.DOOR);
                gridWorld.setBlock(startX + width, gateStart + i, 0, GridWorld.BlockState.DIRT);
            }
        } else {
            for (int i = 0; i < gateWidth; i++) {
                gridWorld.setBlock(startX, gateStart + i, 0, GridWorld.BlockState.DOOR);
                gridWorld.setBlock(startX - 1, gateStart + i, 0, GridWorld.BlockState.DIRT);
            }
        }
    }

    private void buildMoat(CastleLayout layout, int startX, int startY) {
        int width = layout.width;
        int height = layout.height;
        int moatWidthBase = 6;
        
        // Bounding box for moat
        int minX = startX - moatWidthBase - 4;
        int maxX = startX + width + moatWidthBase + 4;
        int minY = startY - moatWidthBase - 4;
        int maxY = startY + height + moatWidthBase + 4;
        
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                if (x < 0 || x >= gridWorld.getWidth() || y < 0 || y >= gridWorld.getDepth()) continue;
                
                // Distance to castle rectangle
                int dx = Math.max(startX - x, Math.max(0, x - (startX + width - 1)));
                int dy = Math.max(startY - y, Math.max(0, y - (startY + height - 1)));
                float dist = (float)Math.sqrt(dx*dx + dy*dy);
                
                // Irregularity
                float noise = MathUtils.sin(x * 0.2f) * MathUtils.cos(y * 0.2f) * 2.0f;
                float currentMoatWidth = moatWidthBase + noise;
                
                if (dist > 0 && dist <= currentMoatWidth) {
                    // Don't overwrite castle or interior
                    if (gridWorld.getBlock(x, y, 0) != GridWorld.BlockState.WATER) {
                         gridWorld.setBlock(x, y, 0, GridWorld.BlockState.WATER);
                         // Clear above water
                         for(int z=1; z<5; z++) gridWorld.setBlock(x, y, z, GridWorld.BlockState.AIR);
                    }
                }
            }
        }
    }

    private void buildDrawbridge(CastleLayout layout, int startX, int startY, GridWorld.BlockState floorType) {
        int gateY = startY + layout.height / 2;
        int gateWidth = 4;
        int gateStart = gateY - gateWidth / 2;
        int bridgeLength = 10; // Enough to cross moat
        
        int dir = layout.gateOnRight ? 1 : -1;
        int bridgeStartX = layout.gateOnRight ? startX + layout.width : startX - 1;
        
        for (int i = 0; i < bridgeLength; i++) {
            int x = bridgeStartX + i * dir;
            for (int j = 0; j < gateWidth; j++) {
                int y = gateStart + j;
                if (x >= 0 && x < gridWorld.getWidth() && y >= 0 && y < gridWorld.getDepth()) {
                    gridWorld.setBlock(x, y, 0, floorType); // Bridge floor
                    // Clear air above bridge
                    for(int z=1; z<4; z++) gridWorld.setBlock(x, y, z, GridWorld.BlockState.AIR);
                }
            }
        }
    }

    private void buildCastleLevel(CastleLayout layout, int startX, int startY, int z,
                                  GridWorld.BlockState floorType, boolean buildFloor) {
        int width = layout.width;
        int height = layout.height;
        GridWorld.BlockState wallType = layout.wallType;
        for (int x = startX; x < startX + width; x++) {
            gridWorld.setBlock(x, startY, z, wallType);
            gridWorld.setBlock(x, startY + height - 1, z, wallType);
            // Windows on upper floors
            if ((x - startX) % 4 == 2) {
                gridWorld.setBlock(x, startY, z, GridWorld.BlockState.WINDOW);
                gridWorld.setBlock(x, startY + height - 1, z, GridWorld.BlockState.WINDOW);
            }
        }
        for (int y = startY; y < startY + height; y++) {
            gridWorld.setBlock(startX, y, z, wallType);
            gridWorld.setBlock(startX + width - 1, y, z, wallType);
            // Windows on upper floors
            if ((y - startY) % 4 == 2) {
                gridWorld.setBlock(startX, y, z, GridWorld.BlockState.WINDOW);
                gridWorld.setBlock(startX + width - 1, y, z, GridWorld.BlockState.WINDOW);
            }
        }

        // Removed fillInteriorWithFloor call
    }

    private void buildBattlements(CastleLayout layout, int startX, int startY, int z,
                                  GridWorld.BlockState floorType) {
        int width = layout.width;
        int height = layout.height;
        GridWorld.BlockState wallType = layout.wallType;
        // Removed fillInteriorWithFloor call

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

        // Removed fillInteriorWithFloor call

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



    private void carveGate(CastleLayout layout, int startX, int startY, GridWorld.BlockState floorType) {
        int gateY = startY + layout.height / 2;
        int gateWidth = 4;
        int gateStart = gateY - gateWidth / 2;
        int gateHeight = 4;
        
        int gateX = layout.gateOnRight ? startX + layout.width - 1 : startX;
        
        for (int y = gateStart; y < gateStart + gateWidth; y++) {
            // Floor at z=0
            gridWorld.setBlock(gateX, y, 0, floorType);
            
            // Air for gate opening
            for (int z = 1; z <= gateHeight; z++) {
                gridWorld.setBlock(gateX, y, z, GridWorld.BlockState.AIR);
            }
        }
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
                King king = new King(spawnX, spawnY, spawnZ, team, this);
            entities.add(king);
            
            // Spawn Entourage Guards
            for (int i = 0; i < 2; i++) {
                float gx = spawnX + (i == 0 ? 1 : -1);
                float gy = spawnY + (i == 0 ? 1 : -1);
                Guard guard = new Guard(gx, gy, spawnZ, team, Guard.GuardType.ENTOURAGE, this);
                guard.setTargetToFollow(king);
                entities.add(guard);
            }
            
            // Spawn Patrol Guards
            for (int i = 0; i < 2; i++) {
                float px = startX + layout.width / 2f + MathUtils.random(-2, 2);
                float py = startY + layout.height / 2f + MathUtils.random(-2, 2);
                float pz = layout.battlementLevel; // Patrol battlements initially
                Guard guard = new Guard(px, py, pz, team, Guard.GuardType.PATROL, this);
                entities.add(guard);
            }
            
            // Add gates
            // ... (Gate logic if any)
            
    }

    private void buildSpiralStaircase(CastleLayout layout, int centerX, int centerY, GridWorld.BlockState stairType, int orientation) {
        int radius = 6; // 12x12 shaft (13x13 actually)
        int minX = centerX - radius;
        int maxX = centerX + radius;
        int minY = centerY - radius;
        int maxY = centerY + radius;

        // Build shaft walls
        // Build shaft walls
        // Stop walls at battlementLevel - 1 so the landing can sit on top
        for (int z = 0; z < layout.battlementLevel; z++) {
            for (int x = minX; x <= maxX; x++) {
                gridWorld.setBlock(x, minY, z, layout.wallType);
                gridWorld.setBlock(x, maxY, z, layout.wallType);
            }
            for (int y = minY; y <= maxY; y++) {
                gridWorld.setBlock(minX, y, z, layout.wallType);
                gridWorld.setBlock(maxX, y, z, layout.wallType);
            }
            // Central pillar (3x3)
            for (int x = centerX - 1; x <= centerX + 1; x++) {
                for (int y = centerY - 1; y <= centerY + 1; y++) {
                    gridWorld.setBlock(x, y, z, layout.wallType);
                }
            }
        }
        
        // Build the landing at the top
        buildStaircaseLanding(layout, centerX, centerY, layout.battlementLevel, layout.wallType, getFloorType(layout.wallType));

        // Ground floor entrance based on orientation
        int entranceWidth = 4;
        int entranceHeight = 5;
        
        if (orientation == 0) { // East
             for (int y = centerY - entranceWidth/2; y <= centerY + entranceWidth/2; y++) {
                for (int z = 1; z <= entranceHeight; z++) {
                    gridWorld.setBlock(maxX, y, z, GridWorld.BlockState.AIR);
                }
            }
        } else if (orientation == 1) { // North
             for (int x = centerX - entranceWidth/2; x <= centerX + entranceWidth/2; x++) {
                for (int z = 1; z <= entranceHeight; z++) {
                    gridWorld.setBlock(x, maxY, z, GridWorld.BlockState.AIR);
                }
            }
        } else if (orientation == 2) { // West
             for (int y = centerY - entranceWidth/2; y <= centerY + entranceWidth/2; y++) {
                for (int z = 1; z <= entranceHeight; z++) {
                    gridWorld.setBlock(minX, y, z, GridWorld.BlockState.AIR);
                }
            }
        } else { // South (Default)
             for (int x = centerX - entranceWidth/2; x <= centerX + entranceWidth/2; x++) {
                for (int z = 1; z <= entranceHeight; z++) {
                    gridWorld.setBlock(x, minY, z, GridWorld.BlockState.AIR);
                }
            }
        }
        
        // Pave the bottom floor
        for (int x = minX + 1; x < maxX; x++) {
            for (int y = minY + 1; y < maxY; y++) {
                gridWorld.setBlock(x, y, 0, GridWorld.BlockState.CASTLE_WHITE_FLOOR);
            }
        }

        // Build continuous spiral steps
        // Floor height is 6 blocks.
        // We want 1 full revolution per floor (6 blocks rise).
        
        for (int level = 0; level < layout.interiorLevels / 12 + 1; level++) {
            int baseZ = level * 12;
            
            // Iterate over the annulus
            for (int x = minX + 1; x < maxX; x++) {
                for (int y = minY + 1; y < maxY; y++) {
                    int dx = x - centerX;
                    int dy = y - centerY;
                    double dist = Math.sqrt(dx*dx + dy*dy);
                    
                    // Path width: inner radius 2, extend to walls
                if (dist >= 2) {
                        // Calculate angle (0 to 1)
                        // atan2 returns -PI to PI. 
                        // We want 0 at South (entrance) -> East -> North -> West
                        // South is (0, -1). atan2(0, -1) = -PI/2? No, atan2(y, x).
                        // atan2(-1, 0) = -PI/2.
                        // Let's use standard math angle: East=0, North=PI/2, West=PI, South=-PI/2.
                        // We want spiral to go up counter-clockwise? Or clockwise?
                        // Let's go Counter-Clockwise starting from South.
                        // South (-PI/2) -> East (0) -> North (PI/2) -> West (PI).
                        
                        double angle = Math.atan2(dy, dx); // -PI to PI
                    
                    // Calculate start angle based on orientation
                    double startAngle = -Math.PI / 2.0; // South default
                    if (orientation == 0) startAngle = 0; // East
                    else if (orientation == 1) startAngle = Math.PI / 2.0; // North
                    else if (orientation == 2) startAngle = Math.PI; // West
                    
                    // Normalize fraction based on startAngle
                    double fraction = (angle - startAngle) / (2 * Math.PI);
                    if (fraction < 0) fraction += 1.0;
                    if (fraction >= 1.0) fraction -= 1.0;
                        
                        // Calculate Z height for this block
                        int zOffset = (int)(fraction * 12);
                        int z = baseZ + zOffset;
                        
                        if (z > layout.interiorLevels) continue;
                        
                        gridWorld.setBlock(x, y, z, stairType);
                    // Thicken the stairs to prevent gaps/falling through
                    if (z - 1 >= 0) {
                        gridWorld.setBlock(x, y, z - 1, stairType);
                    }
                    
                    // Clear headroom (5 blocks)
                    for (int h = 1; h <= 5; h++) {
                        gridWorld.setBlock(x, y, z + h, GridWorld.BlockState.AIR);
                    }
                        
                        // Check for floor connections
                        // At the start of the loop (South, fraction ~ 0), we are at baseZ.
                        // This aligns with the floor level.
                        // We need to ensure the connection to the branched floors is open.
                        // Branched floors connect at South, North, East, West.
                        // South: fraction 0, z = baseZ.
                        // East: fraction 0.25, z = baseZ + 1.5 -> 1 or 2.
                        // North: fraction 0.5, z = baseZ + 3.
                        // West: fraction 0.75, z = baseZ + 4.5 -> 4 or 5.
                        
                        // The branched floors are all at 'baseZ'.
                        // So only the South side aligns perfectly with the floor height!
                        // This is a problem with a spiral ramp.
                        // The North side is at baseZ + 3.
                        // But the floor is at baseZ.
                        
                        // Solution:
                        // 1. Landings: Flatten the spiral at cardinal directions?
                        // 2. Ramps: The branched corridors must slope up/down to meet the spiral?
                        // 3. Just connect at South?
                        // The user wants "branched floors".
                        // If I only connect at South, it's less "branched".
                        // But physically, a spiral ramp only meets a flat plane at one line.
                        
                        // Let's enforce connections at South (Entrance/Exit).
                        // The ring corridor in `buildBranchedFloors` is at `z`.
                        // The spiral at South is at `z`.
                        // So South connection works.
                        // What about other directions?
                        // The ring corridor is flat.
                        // The spiral is higher.
                        // So from the ring, you look into the shaft and see the spiral rising.
                        // You can jump onto the spiral?
                        // Or we build the ring corridor to match the spiral? No, that's complex.
                        
                        // Let's stick to South connection for the main floor access.
                        // And maybe punch holes elsewhere if they align?
                        
                        // For now, just ensuring the spiral is continuous is the main goal.
                        // And the South entrance/exit aligns with the floor.
                    }
                }
            }
            
            // Ensure the doorway is open at floor level, aligned with orientation
            if (level > 0) {
                int entranceW = 4;
                GridWorld.BlockState floorType = getFloorType(layout.wallType);
                
                if (orientation == 0) { // East
                    for (int y = centerY - entranceW/2; y <= centerY + entranceW/2; y++) {
                        gridWorld.setBlock(maxX, y, baseZ, floorType);
                        for(int h=1; h<=5; h++) gridWorld.setBlock(maxX, y, baseZ+h, GridWorld.BlockState.AIR);
                    }
                } else if (orientation == 1) { // North
                    for (int x = centerX - entranceW/2; x <= centerX + entranceW/2; x++) {
                        gridWorld.setBlock(x, maxY, baseZ, floorType);
                        for(int h=1; h<=5; h++) gridWorld.setBlock(x, maxY, baseZ+h, GridWorld.BlockState.AIR);
                    }
                } else if (orientation == 2) { // West
                    for (int y = centerY - entranceW/2; y <= centerY + entranceW/2; y++) {
                        gridWorld.setBlock(minX, y, baseZ, floorType);
                        for(int h=1; h<=5; h++) gridWorld.setBlock(minX, y, baseZ+h, GridWorld.BlockState.AIR);
                    }
                } else { // South
                    for (int x = centerX - entranceW/2; x <= centerX + entranceW/2; x++) {
                        gridWorld.setBlock(x, minY, baseZ, floorType);
                        for(int h=1; h<=5; h++) gridWorld.setBlock(x, minY, baseZ+h, GridWorld.BlockState.AIR);
                    }
                }
            }
        }
    }

    private void buildBranchedFloors(CastleLayout layout, int startX, int startY, int z,
                                     GridWorld.BlockState floorType) {
        int centerX = startX + layout.width / 2;
        int centerY = startY + layout.height / 2;
        
        int radius = 7; // Outside the shaft walls (radius 6 + 1)
        int minX = centerX - radius;
        int maxX = centerX + radius;
        int minY = centerY - radius;
        int maxY = centerY + radius;
        
        // Build a ring corridor around the shaft (4 wide)
        for (int x = minX - 2; x <= maxX + 2; x++) {
            for (int y = minY - 2; y <= maxY + 2; y++) {
                // Don't build inside shaft (radius 6)
                if (Math.abs(x - centerX) <= 6 && Math.abs(y - centerY) <= 6) continue;
                
                // Only build in the ring
                if (Math.abs(x - centerX) > 10 || Math.abs(y - centerY) > 10) continue;

                gridWorld.setBlock(x, y, z, floorType);
                // Clear headroom (5 blocks)
                for (int h = 1; h <= 5; h++) {
                    gridWorld.setBlock(x, y, z+h, GridWorld.BlockState.AIR);
                }
            }
        }
        
        // Branch out rooms (connected to ring)
        // North Room
        buildRoom(centerX - 8, centerY + 10, centerX + 8, startY + layout.height - 2, z, layout.wallType, floorType);
        // South Room
        buildRoom(centerX - 8, startY + 1, centerX + 8, centerY - 10, z, layout.wallType, floorType);
        // East Room
        buildRoom(centerX + 10, centerY - 8, startX + layout.width - 2, centerY + 8, z, layout.wallType, floorType);
        // West Room
        buildRoom(startX + 1, centerY - 8, centerX - 10, centerY + 8, z, layout.wallType, floorType);
    }

    private void buildRoom(int minX, int minY, int maxX, int maxY, int z,
                           GridWorld.BlockState wallType, GridWorld.BlockState floorType) {
        if (minX >= maxX || minY >= maxY) return;
        
        // Floor
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                gridWorld.setBlock(x, y, z, floorType);
                // Clear air (5 blocks)
                for (int h = 1; h <= 5; h++) {
                    gridWorld.setBlock(x, y, z+h, GridWorld.BlockState.AIR);
                }
            }
        }
    }

    private void buildStaircaseLanding(CastleLayout layout, int centerX, int centerY, int z, 
                                     GridWorld.BlockState wallType, GridWorld.BlockState floorType) {
        int landingRadius = 8; // Wider than shaft (6)
        
        for (int x = centerX - landingRadius; x <= centerX + landingRadius; x++) {
            for (int y = centerY - landingRadius; y <= centerY + landingRadius; y++) {
                double dist = Math.sqrt(Math.pow(x - centerX, 2) + Math.pow(y - centerY, 2));
                
                if (dist <= landingRadius) {
                    // Floor
                    gridWorld.setBlock(x, y, z, floorType);
                    
                    // Clear air above
                    for(int h=1; h<=4; h++) gridWorld.setBlock(x, y, z+h, GridWorld.BlockState.AIR);

                    // Battlements
                    if (dist >= landingRadius - 1) {
                        // Crenellations
                        // Simple logic: solid block if (x+y) is even
                        boolean isMerlon = ((x + y) % 2 == 0);
                        
                        if (isMerlon) {
                            gridWorld.setBlock(x, y, z + 1, wallType);
                        }
                    }
                }
            }
        }
    }
}
