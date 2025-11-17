package com.castlewar.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.castlewar.entity.MovingCube;
import com.castlewar.renderer.GridRenderer;
import com.castlewar.simulation.SimulationConfig;
import com.castlewar.world.GridWorld;

/**
 * Screen displaying the world in top-down view with two castles.
 */
public class DualViewScreen implements Screen {
    private enum ViewMode {
        TOP_DOWN,
        SIDE_SCROLLER
    }

    private final SimulationConfig config;
    private final GridWorld gridWorld;
    private final GridRenderer gridRenderer;
    private final SpriteBatch overlayBatch;
    private final BitmapFont overlayFont;
    private final int undergroundDepth;
    private final float totalVerticalBlocks;
    
    // Cameras / viewports for each view mode
    private OrthographicCamera topDownCamera;
    private Viewport topDownViewport;
    private OrthographicCamera sideCamera;
    private Viewport sideViewport;
    private final float sideCameraPanSpeed = 200f; // pixels per second
    private static final float SIDE_VIEW_MIN_ZOOM = 0.5f;
    private static final float SIDE_VIEW_MAX_ZOOM = 3.0f;
    private static final float SIDE_VIEW_ZOOM_STEP = 0.15f;
    private ViewMode viewMode;
    private boolean keySlashPressed;
    private int sideViewSlice;
    private boolean keyMinusPressed;
    private boolean keyEqualsPressed;
    private boolean splitViewEnabled;
    private boolean keyMPressed;
    
    private final float blockSize = 10f;

    private static final int CASTLE_WIDTH = 14;
    private static final int CASTLE_HEIGHT = 14;
    private static final int CASTLE_INTERIOR_LEVELS = 6;
    private static final int CASTLE_BATTLEMENT_LEVEL = CASTLE_INTERIOR_LEVELS + 1;
    private static final int CASTLE_ROOF_LEVEL = CASTLE_BATTLEMENT_LEVEL + 1;
    private static final int CASTLE_TURRET_TOP_LEVEL = CASTLE_ROOF_LEVEL + 2;
    
    // Moving cube traveling between castles
    private MovingCube movingCube;
    
    // Castle gate positions
    private float leftCastleGateX;
    private float leftCastleGateY;
    private float rightCastleGateX;
    private float rightCastleGateY;
    
    // Current Z-level being viewed
    private int currentLayer;
    private boolean keyCommaPressed;
    private boolean keyPeriodPressed;

    public DualViewScreen(SimulationConfig config, GridWorld gridWorld) {
        this.config = config;
        this.gridWorld = gridWorld;
        this.gridRenderer = new GridRenderer(blockSize);
        this.overlayBatch = new SpriteBatch();
        this.overlayFont = new BitmapFont();
        this.overlayFont.setColor(Color.WHITE);
        this.undergroundDepth = gridWorld.getHeight(); // mirror underground depth with sky height
        this.totalVerticalBlocks = gridWorld.getHeight() + undergroundDepth;
        
        // Initialize cameras and viewports for each view
        float worldWidth = gridWorld.getWidth() * blockSize;
        float worldDepth = gridWorld.getDepth() * blockSize;
        float totalVerticalPixels = totalVerticalBlocks * blockSize;
        
        topDownCamera = new OrthographicCamera();
        topDownViewport = new FitViewport(worldWidth, worldDepth, topDownCamera);
        topDownCamera.position.set(worldWidth / 2f, worldDepth / 2f, 0);
        topDownCamera.update();
        
        float desiredSideWidth = Math.min(worldWidth, blockSize * 20f);
        float desiredSideHeight = Math.min(totalVerticalPixels, blockSize * 12f);
        sideCamera = new OrthographicCamera();
        sideViewport = new FitViewport(desiredSideWidth, desiredSideHeight, sideCamera);
        float groundPixelY = undergroundDepth * blockSize;
        sideCamera.position.set(worldWidth / 2f, groundPixelY, 0);
        sideCamera.update();
        clampSideCameraPosition();
        
    viewMode = ViewMode.TOP_DOWN;
    keySlashPressed = false;
    keyMinusPressed = false;
    keyEqualsPressed = false;
    splitViewEnabled = false;
    keyMPressed = false;
        sideViewSlice = gridWorld.getDepth() / 2;
        
        // Build castles
        buildCastles();
        
        // Position cube to move between castle gates
    float cubeZ = 0; // Ground level
    movingCube = new MovingCube(leftCastleGateX, leftCastleGateY, cubeZ, 8f, leftCastleGateX, rightCastleGateX);
        
        // Start viewing at ground level
        currentLayer = 0;
        keyCommaPressed = false;
        keyPeriodPressed = false;
    }
    
    private void buildCastles() {
        int width = gridWorld.getWidth();
        int depth = gridWorld.getDepth();
        int centerY = depth / 2;
    int castleStartY = centerY - CASTLE_HEIGHT / 2;
    int horizontalMargin = 10;
    int leftStartX = horizontalMargin;
    int rightStartX = width - CASTLE_WIDTH - horizontalMargin;

    // Left castle (white)
    buildMultiLevelCastle(leftStartX, castleStartY, CASTLE_WIDTH, CASTLE_HEIGHT,
        GridWorld.BlockState.CASTLE_WHITE, true);
    leftCastleGateX = leftStartX + CASTLE_WIDTH - 1;
    leftCastleGateY = castleStartY + CASTLE_HEIGHT / 2;
        
    // Right castle (black)
    buildMultiLevelCastle(rightStartX, castleStartY, CASTLE_WIDTH, CASTLE_HEIGHT,
        GridWorld.BlockState.CASTLE_BLACK, false);
    rightCastleGateX = rightStartX;
    rightCastleGateY = castleStartY + CASTLE_HEIGHT / 2;
    }

    private boolean isTopViewActive() {
        return splitViewEnabled || viewMode == ViewMode.TOP_DOWN;
    }

    private boolean isSideViewActive() {
        return splitViewEnabled || viewMode == ViewMode.SIDE_SCROLLER;
    }

    private void applyTopDownViewport(int x, int y, int width, int height) {
        topDownViewport.setScreenBounds(x, y, Math.max(1, width), Math.max(1, height));
        topDownViewport.apply();
    }

    private void applySideViewport(int x, int y, int width, int height) {
        sideViewport.setScreenBounds(x, y, Math.max(1, width), Math.max(1, height));
        sideViewport.apply();
    }
    
    private void buildMultiLevelCastle(int startX, int startY, int width, int height, 
                                       GridWorld.BlockState wallType, boolean gateOnRight) {
        GridWorld.BlockState floorType = getFloorType(wallType);

        // Ground level: walls with moat and gate
        buildCastle(startX, startY, width, height, wallType, floorType, gateOnRight);
        
        // Build upper levels (scaled for larger castles)
        for (int level = 1; level <= CASTLE_ROOF_LEVEL; level++) {
            if (level <= CASTLE_INTERIOR_LEVELS) {
                // Middle levels: full walls with floors
                buildCastleLevel(startX, startY, width, height, level, wallType, floorType);
            } else if (level == CASTLE_BATTLEMENT_LEVEL) {
                // Battlements (crenellations) with interior walkway
                buildBattlements(startX, startY, width, height, level, wallType, floorType);
            } else {
                // Rooftop / observation deck
                buildRoof(startX, startY, width, height, level, wallType, floorType);
            }
        }
        
        // Add corner turrets that extend above the rooftop for extra height
        buildTurret(startX, startY, wallType);
        buildTurret(startX + width - 1, startY, wallType);
        buildTurret(startX, startY + height - 1, wallType);
        buildTurret(startX + width - 1, startY + height - 1, wallType);
    }
    
    private void buildCastleLevel(int startX, int startY, int width, int height, int z, 
                                  GridWorld.BlockState wallType, GridWorld.BlockState floorType) {
        // Build walls (hollow rectangle) at this level
        for (int x = startX; x < startX + width; x++) {
            // Top and bottom walls
            gridWorld.setBlock(x, startY, z, wallType);
            gridWorld.setBlock(x, startY + height - 1, z, wallType);
        }
        for (int y = startY; y < startY + height; y++) {
            // Left and right walls
            gridWorld.setBlock(startX, y, z, wallType);
            gridWorld.setBlock(startX + width - 1, y, z, wallType);
        }

        // Fill interior with a lighter floor to make this level visible
        fillInteriorWithFloor(startX, startY, width, height, z, floorType);
    }
    
    private void buildBattlements(int startX, int startY, int width, int height, int z, 
                                  GridWorld.BlockState wallType, GridWorld.BlockState floorType) {
        // Add a continuous floor under the crenellations for readability
        fillInteriorWithFloor(startX, startY, width, height, z, floorType);

        // Crenellations pattern: merlon, gap, merlon, gap...
        // Top and bottom walls
        for (int x = startX; x < startX + width; x++) {
            if (x % 2 == 0) { // Every other block
                gridWorld.setBlock(x, startY, z, wallType);
                gridWorld.setBlock(x, startY + height - 1, z, wallType);
            }
        }
        // Left and right walls
        for (int y = startY; y < startY + height; y++) {
            if (y % 2 == 0) { // Every other block
                gridWorld.setBlock(startX, y, z, wallType);
                gridWorld.setBlock(startX + width - 1, y, z, wallType);
            }
        }
    }

    private void buildRoof(int startX, int startY, int width, int height, int z,
                           GridWorld.BlockState wallType, GridWorld.BlockState floorType) {
        // Solid parapet around the rooftop
        for (int x = startX; x < startX + width; x++) {
            gridWorld.setBlock(x, startY, z, wallType);
            gridWorld.setBlock(x, startY + height - 1, z, wallType);
        }
        for (int y = startY; y < startY + height; y++) {
            gridWorld.setBlock(startX, y, z, wallType);
            gridWorld.setBlock(startX + width - 1, y, z, wallType);
        }

        // Fill rooftop interior
        fillInteriorWithFloor(startX, startY, width, height, z, floorType);

        // Add a cross brace to make the rooftop distinct
        int centerX = startX + width / 2;
        int centerY = startY + height / 2;
        for (int x = startX + 1; x < startX + width - 1; x++) {
            gridWorld.setBlock(x, centerY, z, wallType);
        }
        for (int y = startY + 1; y < startY + height - 1; y++) {
            gridWorld.setBlock(centerX, y, z, wallType);
        }
    }

    private GridWorld.BlockState getFloorType(GridWorld.BlockState wallType) {
        return (wallType == GridWorld.BlockState.CASTLE_WHITE)
            ? GridWorld.BlockState.CASTLE_WHITE_FLOOR
            : GridWorld.BlockState.CASTLE_BLACK_FLOOR;
    }

    private void fillInteriorWithFloor(int startX, int startY, int width, int height, int z,
                                       GridWorld.BlockState floorType) {
        for (int x = startX + 1; x < startX + width - 1; x++) {
            for (int y = startY + 1; y < startY + height - 1; y++) {
                gridWorld.setBlock(x, y, z, floorType);
            }
        }
    }
    
    private void buildTurret(int x, int y, GridWorld.BlockState wallType) {
        // Tower from ground to above the roof (extend extra levels for presence)
        for (int z = 1; z <= CASTLE_TURRET_TOP_LEVEL; z++) {
            gridWorld.setBlock(x, y, z, wallType);
        }
    }
    
    private void buildCastle(int startX, int startY, int width, int height, GridWorld.BlockState wallType,
                             GridWorld.BlockState floorType, boolean gateOnRight) {
        // Build moat (water) around castle - 1 block wider on all sides
        for (int x = startX - 1; x < startX + width + 1; x++) {
            for (int y = startY - 1; y < startY + height + 1; y++) {
                if (x >= 0 && x < gridWorld.getWidth() && y >= 0 && y < gridWorld.getDepth()) {
                    // Only place water in the moat area (not inside castle)
                    if (x == startX - 1 || x == startX + width || y == startY - 1 || y == startY + height) {
                        gridWorld.setBlock(x, y, 0, GridWorld.BlockState.STONE); // Using stone for water
                    }
                }
            }
        }
        
        // Build walls (hollow rectangle)
        for (int x = startX; x < startX + width; x++) {
            // Top and bottom walls
            gridWorld.setBlock(x, startY, 0, wallType);
            gridWorld.setBlock(x, startY + height - 1, 0, wallType);
        }
        for (int y = startY; y < startY + height; y++) {
            // Left and right walls
            gridWorld.setBlock(startX, y, 0, wallType);
            gridWorld.setBlock(startX + width - 1, y, 0, wallType);
        }

        // Fill the ground level interior with floor tiles for readability
        fillInteriorWithFloor(startX, startY, width, height, 0, floorType);
        
        // Create gate (opening) in the middle of the wall closest to center
        int gateY = startY + height / 2;
        if (gateOnRight) {
            // Left castle - gate on right wall, plus bridge over moat
            gridWorld.setBlock(startX + width - 1, gateY, 0, GridWorld.BlockState.AIR);
            gridWorld.setBlock(startX + width, gateY, 0, GridWorld.BlockState.DIRT); // Bridge
        } else {
            // Right castle - gate on left wall, plus bridge over moat
            gridWorld.setBlock(startX, gateY, 0, GridWorld.BlockState.AIR);
            gridWorld.setBlock(startX - 1, gateY, 0, GridWorld.BlockState.DIRT); // Bridge
        }
    }

    @Override
    public void render(float delta) {
        // Handle input for view toggling and navigation
        handleViewToggle();
        handleSplitViewToggle();
        handleLayerInput();
        if (gridWorld.getDepth() > 0) {
            sideViewSlice = Math.max(0, Math.min(sideViewSlice, gridWorld.getDepth() - 1));
        }
        handleSideZoomInput();
        handleSideCameraPan(delta);
        
        // Update moving cube
        movingCube.update(delta);
        
        // Clear screen with light gray background
        Gdx.gl.glClearColor(0.9f, 0.9f, 0.9f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        if (splitViewEnabled) {
            renderSplitViews();
        } else if (viewMode == ViewMode.TOP_DOWN) {
            renderTopDownFullView();
        } else {
            renderSideScrollerFullView();
        }
        
        // Display view info overlay/logging
        renderOverlay();
    }
    
    private void handleViewToggle() {
        if (Gdx.input.isKeyPressed(Input.Keys.SLASH)) {
            if (!keySlashPressed) {
                viewMode = (viewMode == ViewMode.TOP_DOWN) ? ViewMode.SIDE_SCROLLER : ViewMode.TOP_DOWN;
                Gdx.app.log("DualViewScreen", "Switched to " + viewMode.name().toLowerCase().replace('_', ' ') + " view");
            }
            keySlashPressed = true;
        } else {
            keySlashPressed = false;
        }
    }

    private void handleSplitViewToggle() {
        if (Gdx.input.isKeyPressed(Input.Keys.M)) {
            if (!keyMPressed) {
                splitViewEnabled = !splitViewEnabled;
                Gdx.app.log("DualViewScreen", splitViewEnabled ? "Split view enabled" : "Split view disabled");
            }
            keyMPressed = true;
        } else {
            keyMPressed = false;
        }
    }

    private void handleLayerInput() {
        // Comma key - move down through layers/slices
        boolean topActive = isTopViewActive();
        boolean sideActive = isSideViewActive();

        if (Gdx.input.isKeyPressed(Input.Keys.COMMA)) {
            if (!keyCommaPressed) {
                if (topActive) {
                    int nextLayer = Math.max(-undergroundDepth, currentLayer - 1);
                    if (nextLayer != currentLayer) {
                        currentLayer = nextLayer;
                        String levelDesc = currentLayer < 0 ? "Underground " + (-currentLayer) : "Layer " + currentLayer;
                        Gdx.app.log("DualViewScreen", levelDesc);
                    }
                }
                if (sideActive) {
                    sideViewSlice = Math.max(0, sideViewSlice - 1);
                    Gdx.app.log("DualViewScreen", "Side view slice Y=" + sideViewSlice);
                }
            }
            keyCommaPressed = true;
        } else {
            keyCommaPressed = false;
        }
        
        // Period key - move up through layers/slices
        if (Gdx.input.isKeyPressed(Input.Keys.PERIOD)) {
            if (!keyPeriodPressed) {
                if (topActive && currentLayer < gridWorld.getHeight() - 1) {
                    currentLayer++;
                    String levelDesc = currentLayer < 0 ? "Underground " + (-currentLayer) : "Layer " + currentLayer;
                    Gdx.app.log("DualViewScreen", levelDesc);
                }
                if (sideActive && sideViewSlice < gridWorld.getDepth() - 1) {
                    sideViewSlice++;
                    Gdx.app.log("DualViewScreen", "Side view slice Y=" + sideViewSlice);
                }
            }
            keyPeriodPressed = true;
        } else {
            keyPeriodPressed = false;
        }
    }

    private void handleSideZoomInput() {
        if (!isSideViewActive()) {
            keyMinusPressed = false;
            keyEqualsPressed = false;
            return;
        }
        boolean updated = false;
        if (Gdx.input.isKeyPressed(Input.Keys.MINUS)) {
            if (!keyMinusPressed) {
                sideCamera.zoom = Math.min(SIDE_VIEW_MAX_ZOOM, sideCamera.zoom + SIDE_VIEW_ZOOM_STEP);
                updated = true;
            }
            keyMinusPressed = true;
        } else {
            keyMinusPressed = false;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.EQUALS)) {
            if (!keyEqualsPressed) {
                sideCamera.zoom = Math.max(SIDE_VIEW_MIN_ZOOM, sideCamera.zoom - SIDE_VIEW_ZOOM_STEP);
                updated = true;
            }
            keyEqualsPressed = true;
        } else {
            keyEqualsPressed = false;
        }
        if (updated) {
            clampSideCameraPosition();
        }
    }

    private void handleSideCameraPan(float delta) {
        if (!isSideViewActive()) {
            return;
        }
        float moveX = 0f;
        float moveY = 0f;
        if (Gdx.input.isKeyPressed(Input.Keys.LEFT)) {
            moveX -= sideCameraPanSpeed * delta;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.RIGHT)) {
            moveX += sideCameraPanSpeed * delta;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.DOWN)) {
            moveY -= sideCameraPanSpeed * delta;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.UP)) {
            moveY += sideCameraPanSpeed * delta;
        }
        if (moveX == 0f && moveY == 0f) {
            return;
        }
        sideCamera.position.x += moveX;
        sideCamera.position.y += moveY;
        clampSideCameraPosition();
    }

    private void clampSideCameraPosition() {
        float worldWidthPixels = gridWorld.getWidth() * blockSize;
        float worldHeightPixels = totalVerticalBlocks * blockSize;
    float halfViewportWidth = (sideViewport.getWorldWidth() * sideCamera.zoom) / 2f;
    float halfViewportHeight = (sideViewport.getWorldHeight() * sideCamera.zoom) / 2f;
        float minX;
        float maxX;
        if (worldWidthPixels <= sideViewport.getWorldWidth()) {
            minX = maxX = worldWidthPixels / 2f;
        } else {
            minX = halfViewportWidth;
            maxX = worldWidthPixels - halfViewportWidth;
        }
        float minY;
        float maxY;
        if (worldHeightPixels <= sideViewport.getWorldHeight()) {
            minY = maxY = worldHeightPixels / 2f;
        } else {
            minY = halfViewportHeight;
            maxY = worldHeightPixels - halfViewportHeight;
        }
        sideCamera.position.x = MathUtils.clamp(sideCamera.position.x, minX, maxX);
        sideCamera.position.y = MathUtils.clamp(sideCamera.position.y, minY, maxY);
        sideCamera.update();
    }
    
    private void renderTopDownFullView() {
        applyTopDownViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        renderTopDownViewContents();
    }

    private void renderTopDownViewContents() {
        ShapeRenderer sr = gridRenderer.getShapeRenderer();
        sr.setProjectionMatrix(topDownCamera.combined);
        
        // Render all layers from bottom to current layer
        int startLayer = Math.max(-undergroundDepth, Math.min(currentLayer, 0));
        for (int z = startLayer; z <= currentLayer; z++) {
            float opacity = (z == currentLayer) ? 1.0f : 0.5f; // Desaturate lower layers
            renderLayer(z, opacity);
        }
        
        // Render cube if it's on or below the current layer and above ground
        if ((int) movingCube.getZ() <= currentLayer && movingCube.getZ() >= 0) {
            renderCubeTopDown();
        }
    }

    private void renderSideScrollerFullView() {
        applySideViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        renderSideScrollerViewContents();
    }

    private void renderSideScrollerViewContents() {
        ShapeRenderer sr = gridRenderer.getShapeRenderer();
        sr.setProjectionMatrix(sideCamera.combined);
        
        sr.begin(ShapeRenderer.ShapeType.Filled);
        int width = gridWorld.getWidth();
        int maxHeight = gridWorld.getHeight();
        int depth = gridWorld.getDepth();
        int minZ = -undergroundDepth;
        int maxZ = maxHeight - 1;
        
        for (int x = 0; x < width; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                GridWorld.BlockState blockToRender = GridWorld.BlockState.AIR;
                int depthOffset = 0;
                for (int y = sideViewSlice; y < depth; y++) {
                    GridWorld.BlockState candidate = gridWorld.getBlock(x, y, z);
                    if (candidate != GridWorld.BlockState.AIR) {
                        blockToRender = candidate;
                        depthOffset = y - sideViewSlice;
                        break;
                    }
                }
                if (blockToRender == GridWorld.BlockState.AIR) {
                    continue;
                }
                Color color = getBlockColor(blockToRender);
                if (depthOffset > 0) {
                    float fade = 1f - Math.min(depthOffset * 0.12f, 0.6f);
                    color = new Color(
                        color.r * fade,
                        color.g * fade,
                        color.b * fade,
                        1f
                    );
                }
                sr.setColor(color);
                float screenX = x * blockSize;
                float screenZ = (z + undergroundDepth) * blockSize;
                sr.rect(screenX, screenZ, blockSize, blockSize);
            }
        }
        sr.end();
        
        renderCubeSideView();
    }

    private void renderSplitViews() {
        int screenWidth = Gdx.graphics.getWidth();
        int screenHeight = Gdx.graphics.getHeight();
        int lowerHalfHeight = screenHeight / 2;
        int upperHalfHeight = screenHeight - lowerHalfHeight;

        // Top-down view occupies upper half
        applyTopDownViewport(0, lowerHalfHeight, screenWidth, upperHalfHeight);
        renderTopDownViewContents();

        // Side view occupies lower half
        applySideViewport(0, 0, screenWidth, lowerHalfHeight);
        renderSideScrollerViewContents();
    }

    private void renderLayer(int z, float opacity) {
        int width = gridWorld.getWidth();
        int depth = gridWorld.getDepth();
        
        ShapeRenderer sr = gridRenderer.getShapeRenderer();
        sr.begin(ShapeRenderer.ShapeType.Filled);
        
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < depth; y++) {
                GridWorld.BlockState block = gridWorld.getBlock(x, y, z);
                if (block != GridWorld.BlockState.AIR) {
                    Color color = getBlockColor(block);
                    // Apply desaturation to lower layers
                    if (opacity < 1.0f) {
                        float gray = (color.r + color.g + color.b) / 3f;
                        color = new Color(
                            color.r * opacity + gray * (1 - opacity),
                            color.g * opacity + gray * (1 - opacity),
                            color.b * opacity + gray * (1 - opacity),
                            opacity * 0.7f
                        );
                    }
                    sr.setColor(color);
                    float screenX = x * blockSize;
                    float screenY = y * blockSize;
                    sr.rect(screenX, screenY, blockSize, blockSize);
                }
            }
        }
        
        sr.end();
    }
    
    private Color getBlockColor(GridWorld.BlockState block) {
        switch (block) {
            case GRASS:
                return new Color(0.3f, 0.7f, 0.2f, 1);
            case DIRT:
                return new Color(0.6f, 0.4f, 0.2f, 1);
            case STONE:
                return new Color(0.2f, 0.4f, 0.8f, 1);
            case CASTLE_WHITE:
                return new Color(0.95f, 0.95f, 0.95f, 1);
            case CASTLE_BLACK:
                return new Color(0.15f, 0.15f, 0.15f, 1);
            case CASTLE_WHITE_FLOOR:
                return new Color(0.85f, 0.85f, 0.78f, 1);
            case CASTLE_BLACK_FLOOR:
                return new Color(0.25f, 0.25f, 0.25f, 1);
            default:
                return Color.WHITE;
        }
    }
    
    private void renderOverlay() {
        // Render a translucent bar for overlay text (placeholder for font rendering)
        ShapeRenderer sr = gridRenderer.getShapeRenderer();
        sr.begin(ShapeRenderer.ShapeType.Filled);
        sr.setColor(0, 0, 0, 0.7f);
        sr.rect(10, Gdx.graphics.getHeight() - 30, 560, 25);
        sr.end();
        
        String info;
        if (splitViewEnabled) {
            String topDesc = currentLayer < 0 ? "Underground " + (-currentLayer) : "Layer " + currentLayer;
            info = "Split view: Top " + topDesc + ", Side Y=" + sideViewSlice +
                   "  (comma/period adjust, arrows pan, -/= zoom, '/' swap focus, 'm' toggle)";
        } else if (viewMode == ViewMode.TOP_DOWN) {
            String levelDesc = currentLayer < 0 ? "Underground " + (-currentLayer) : "Layer " + currentLayer;
            info = "Top view: " + levelDesc + "  (comma/period to change, '/' for side view, 'm' for split)";
        } else {
            info = "Side view slice Y=" + sideViewSlice + "  (comma/period slice, arrows pan, -/= zoom, '/' for top view, 'm' for split)";
        }
        overlayBatch.begin();
        overlayFont.draw(overlayBatch, info, 20, Gdx.graphics.getHeight() - 10);
        overlayBatch.end();
    }
    
    private void renderCubeTopDown() {
        // Draw cube in top view
        float cubeScreenX = movingCube.getX() * blockSize;
        float cubeScreenY = movingCube.getY() * blockSize;
        float cubeSize = blockSize * 0.8f;
        
        ShapeRenderer sr = gridRenderer.getShapeRenderer();
        sr.begin(ShapeRenderer.ShapeType.Filled);
        sr.setColor(Color.RED);
        sr.rect(cubeScreenX, cubeScreenY, cubeSize, cubeSize);
        sr.end();
    }

    private void renderCubeSideView() {
        if (Math.round(movingCube.getY()) != sideViewSlice) {
            return;
        }
        float cubeZ = movingCube.getZ();
        if (cubeZ < -undergroundDepth || cubeZ >= gridWorld.getHeight()) {
            return;
        }
        float cubeScreenX = movingCube.getX() * blockSize;
        float cubeScreenZ = (cubeZ + undergroundDepth) * blockSize;
        float cubeSize = blockSize * 0.8f;
        
        ShapeRenderer sr = gridRenderer.getShapeRenderer();
        sr.begin(ShapeRenderer.ShapeType.Filled);
        sr.setColor(Color.RED);
        sr.rect(cubeScreenX, cubeScreenZ, cubeSize, cubeSize);
        sr.end();
    }

    @Override
    public void resize(int width, int height) {
        topDownViewport.update(width, height, true);
        sideViewport.update(width, height, false);
        clampSideCameraPosition();
    }

    @Override
    public void show() {
        Gdx.app.log("DualViewScreen", "Top-down castle view activated. Press '/' to toggle side view.");
    }

    @Override
    public void hide() {
    }

    @Override
    public void pause() {
    }

    @Override
    public void resume() {
    }

    @Override
    public void dispose() {
        gridRenderer.dispose();
        overlayBatch.dispose();
        overlayFont.dispose();
    }
}
