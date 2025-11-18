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
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.castlewar.entity.MovingCube;
import com.castlewar.renderer.GridRenderer;
import com.castlewar.simulation.WorldContext;
import com.castlewar.world.GridWorld;

/**
 * Screen displaying the world in top-down view with two castles.
 */
public class DualViewScreen implements Screen {
    public enum ViewMode {
        TOP_DOWN,
        SIDE_SCROLLER,
        ISOMETRIC
    }

    public static class Options {
        public final ViewMode initialViewMode;
        public final boolean allowViewToggle;
        public final boolean allowSplitView;
        public final boolean updatesSimulation;
        public final String overlayLabel;

        private Options(ViewMode initialViewMode,
                        boolean allowViewToggle,
                        boolean allowSplitView,
                        boolean updatesSimulation,
                        String overlayLabel) {
            this.initialViewMode = initialViewMode;
            this.allowViewToggle = allowViewToggle;
            this.allowSplitView = allowSplitView;
            this.updatesSimulation = updatesSimulation;
            this.overlayLabel = overlayLabel;
        }

        public static Options primaryWindow() {
            return new Options(ViewMode.TOP_DOWN, true, true, true, "Primary");
        }

        public static Options topStandalone() {
            return new Options(ViewMode.TOP_DOWN, false, false, true, "Top");
        }

        public static Options sideStandalone() {
            return new Options(ViewMode.SIDE_SCROLLER, false, false, false, "Side");
        }
    }

    private final WorldContext worldContext;
    private final GridWorld gridWorld;
    private final GridRenderer gridRenderer;
    private final SpriteBatch overlayBatch;
    private final BitmapFont overlayFont;
    private final Matrix4 overlayProjection;
    private final int undergroundDepth;
    private final float totalVerticalBlocks;
    private final MovingCube movingCube;
    private final Options options;
    
    // Cameras / viewports for each view mode
    private OrthographicCamera topDownCamera;
    private Viewport topDownViewport;
    private OrthographicCamera sideCamera;
    private Viewport sideViewport;
    private OrthographicCamera isoCamera;
    private Viewport isoViewport;
    private final float sideCameraPanSpeed = 200f; // pixels per second
    private static final float SIDE_VIEW_MIN_ZOOM = 0.5f;
    private static final float SIDE_VIEW_MAX_ZOOM = 3.0f;
    private static final float SIDE_VIEW_ZOOM_STEP = 0.15f;
    private final float isoTileWidth;
    private final float isoTileHeight;
    private final float isoBlockHeight;
    private float isoOriginX;
    private float isoOriginY;
    private float isoZoom = 1f;
    private ViewMode viewMode;
    private boolean keySlashPressed;
    private int sideViewSlice;
    private boolean keyMinusPressed;
    private boolean keyEqualsPressed;
    private boolean splitViewEnabled = true;
    private float splitViewRatio = 0.5f;
    private boolean keyMPressed;
    private boolean keyIPressed;
    
    private final float blockSize = 10f;
    
    // Current Z-level being viewed
    private int currentLayer;
    private boolean keyCommaPressed;
    private boolean keyPeriodPressed;
    private boolean keyLeftBracketPressed;
    private boolean keyRightBracketPressed;
    private ViewMode lastNonIsometricMode;
    private boolean splitViewBeforeIsometric;

    public DualViewScreen(WorldContext worldContext, Options options) {
        this.worldContext = worldContext;
        this.options = options;
        this.gridWorld = worldContext.getGridWorld();
        this.gridRenderer = new GridRenderer(blockSize);
        this.overlayBatch = new SpriteBatch();
        this.overlayFont = new BitmapFont();
        this.overlayFont.setColor(Color.WHITE);
    this.overlayProjection = new Matrix4();
        this.undergroundDepth = worldContext.getUndergroundDepth();
        this.totalVerticalBlocks = worldContext.getTotalVerticalBlocks();
        this.movingCube = worldContext.getMovingCube();
        this.isoTileWidth = blockSize;
        this.isoTileHeight = blockSize * 0.5f;
        this.isoBlockHeight = blockSize * 0.6f;
        
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
    sideCamera.zoom = SIDE_VIEW_MAX_ZOOM;
        sideCamera.update();
        clampSideCameraPosition();

        float isoWorldWidth = (gridWorld.getWidth() + gridWorld.getDepth()) * (isoTileWidth / 2f) + blockSize * 8f;
        float isoWorldHeight = (gridWorld.getWidth() + gridWorld.getDepth()) * (isoTileHeight / 2f)
            + gridWorld.getHeight() * isoBlockHeight + blockSize * 8f;
        isoCamera = new OrthographicCamera();
        isoViewport = new FitViewport(isoWorldWidth, isoWorldHeight, isoCamera);
        isoCamera.position.set(isoWorldWidth / 2f, isoWorldHeight / 2f, 0f);
        isoCamera.update();
        isoOriginX = isoWorldWidth / 2f;
        isoOriginY = gridWorld.getHeight() * isoBlockHeight
            + (gridWorld.getWidth() + gridWorld.getDepth()) * (isoTileHeight / 4f);
        
        viewMode = options.initialViewMode;
        keySlashPressed = false;
        keyMinusPressed = false;
        keyEqualsPressed = false;
    splitViewEnabled = options.allowSplitView;
        keyMPressed = false;
        keyIPressed = false;
        sideViewSlice = gridWorld.getDepth() / 2;
        
        currentLayer = 0;
        keyCommaPressed = false;
        keyPeriodPressed = false;
        keyLeftBracketPressed = false;
        keyRightBracketPressed = false;
        lastNonIsometricMode = viewMode;
        splitViewBeforeIsometric = splitViewEnabled;
    }
    
    private boolean isTopViewActive() {
        return splitViewEnabled || viewMode == ViewMode.TOP_DOWN || viewMode == ViewMode.ISOMETRIC;
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

    private void applyIsometricViewport(int x, int y, int width, int height) {
        isoViewport.setScreenBounds(x, y, Math.max(1, width), Math.max(1, height));
        isoViewport.apply();
    }

    @Override
    public void render(float delta) {
        // Handle input for view toggling and navigation
        handleViewToggle();
        handleIsometricToggle();
        handleSplitViewToggle();
        handleLayerInput();
        if (gridWorld.getDepth() > 0) {
            sideViewSlice = Math.max(0, Math.min(sideViewSlice, gridWorld.getDepth() - 1));
        }
        handleSideZoomInput();
        handleSideCameraPan(delta);
        handleIsoZoomInput();
        handleIsoCameraPan(delta);
        
        if (options.updatesSimulation) {
            worldContext.update(delta);
        }
        
        // Clear screen with light gray background
        Gdx.gl.glClearColor(0.9f, 0.9f, 0.9f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        if (viewMode == ViewMode.ISOMETRIC) {
            renderIsometricFullView();
        } else if (splitViewEnabled) {
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
        if (!options.allowViewToggle) {
            keySlashPressed = false;
            return;
        }
        if (viewMode == ViewMode.ISOMETRIC) {
            // Let the dedicated handler manage exiting isometric mode.
            keySlashPressed = false;
            return;
        }
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

    private void handleIsometricToggle() {
        if (Gdx.input.isKeyPressed(Input.Keys.I)) {
            if (!keyIPressed) {
                if (viewMode == ViewMode.ISOMETRIC) {
                    viewMode = lastNonIsometricMode;
                    if (options.allowSplitView) {
                        splitViewEnabled = splitViewBeforeIsometric;
                    }
                    Gdx.app.log("DualViewScreen", "Isometric view disabled");
                } else {
                    lastNonIsometricMode = viewMode;
                    splitViewBeforeIsometric = splitViewEnabled;
                    viewMode = ViewMode.ISOMETRIC;
                    splitViewEnabled = false;
                    Gdx.app.log("DualViewScreen", "Isometric view enabled");
                }
            }
            keyIPressed = true;
        } else {
            keyIPressed = false;
        }
    }

    private void handleSplitViewToggle() {
        if (!options.allowSplitView) {
            splitViewEnabled = false;
            keyMPressed = false;
            return;
        }
        if (viewMode == ViewMode.ISOMETRIC) {
            splitViewEnabled = false;
            keyMPressed = false;
            return;
        }
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
    boolean topActive = isTopViewActive();
    boolean sideActive = isSideViewActive() || viewMode == ViewMode.ISOMETRIC;

        if (Gdx.input.isKeyPressed(Input.Keys.COMMA)) {
            if (!keyCommaPressed && topActive) {
                int nextLayer = Math.max(-undergroundDepth, currentLayer - 1);
                if (nextLayer != currentLayer) {
                    currentLayer = nextLayer;
                    String levelDesc = currentLayer < 0 ? "Underground " + (-currentLayer) : "Layer " + currentLayer;
                    Gdx.app.log("DualViewScreen", levelDesc);
                }
            }
            keyCommaPressed = true;
        } else {
            keyCommaPressed = false;
        }

        if (Gdx.input.isKeyPressed(Input.Keys.PERIOD)) {
            if (!keyPeriodPressed && topActive && currentLayer < gridWorld.getHeight() - 1) {
                currentLayer++;
                String levelDesc = currentLayer < 0 ? "Underground " + (-currentLayer) : "Layer " + currentLayer;
                Gdx.app.log("DualViewScreen", levelDesc);
            }
            keyPeriodPressed = true;
        } else {
            keyPeriodPressed = false;
        }

        if (Gdx.input.isKeyPressed(Input.Keys.LEFT_BRACKET)) {
            if (!keyLeftBracketPressed && sideActive) {
                int nextSlice = Math.max(0, sideViewSlice - 1);
                if (nextSlice != sideViewSlice) {
                    sideViewSlice = nextSlice;
                    Gdx.app.log("DualViewScreen", "Side view slice Y=" + sideViewSlice);
                }
            }
            keyLeftBracketPressed = true;
        } else {
            keyLeftBracketPressed = false;
        }

        if (Gdx.input.isKeyPressed(Input.Keys.RIGHT_BRACKET)) {
            if (!keyRightBracketPressed && sideActive) {
                int nextSlice = Math.min(gridWorld.getDepth() - 1, sideViewSlice + 1);
                if (nextSlice != sideViewSlice) {
                    sideViewSlice = nextSlice;
                    Gdx.app.log("DualViewScreen", "Side view slice Y=" + sideViewSlice);
                }
            }
            keyRightBracketPressed = true;
        } else {
            keyRightBracketPressed = false;
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

    private void handleIsoZoomInput() {
        if (viewMode != ViewMode.ISOMETRIC) {
            return;
        }
        boolean updated = false;
        if (Gdx.input.isKeyPressed(Input.Keys.MINUS)) {
            isoZoom = Math.min(3f, isoZoom + 0.1f);
            updated = true;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.EQUALS)) {
            isoZoom = Math.max(0.3f, isoZoom - 0.1f);
            updated = true;
        }
        if (updated) {
            isoCamera.zoom = isoZoom;
            isoCamera.update();
        }
    }

    private void handleIsoCameraPan(float delta) {
        if (viewMode != ViewMode.ISOMETRIC) {
            return;
        }
        float panSpeed = 300f * delta;
        float moveX = 0f;
        float moveY = 0f;
        if (Gdx.input.isKeyPressed(Input.Keys.A)) {
            moveX -= panSpeed;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.D)) {
            moveX += panSpeed;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.S)) {
            moveY -= panSpeed;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.W)) {
            moveY += panSpeed;
        }
        if (moveX == 0f && moveY == 0f) {
            return;
        }
        isoCamera.position.x += moveX;
        isoCamera.position.y += moveY;
        isoCamera.update();
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

        renderSideSliceGuide();
    }

    private void renderSideScrollerFullView() {
        applySideViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        renderSideScrollerViewContents();
    }

    private void renderIsometricFullView() {
        applyIsometricViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        renderIsometricViewContents();
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
        renderTopLayerGuide();
    }

    private void renderIsometricViewContents() {
        ShapeRenderer sr = gridRenderer.getShapeRenderer();
        sr.setProjectionMatrix(isoCamera.combined);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        sr.begin(ShapeRenderer.ShapeType.Filled);
        int width = gridWorld.getWidth();
        int depth = gridWorld.getDepth();
        int maxZ = gridWorld.getHeight() - 1;
        int minZ = -undergroundDepth;

        for (int z = minZ; z <= maxZ; z++) {
            boolean focusedLayer = (z == currentLayer);
            float layerOpacity = focusedLayer ? 1f : 0.5f;
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < depth; y++) {
                    GridWorld.BlockState block = gridWorld.getBlock(x, y, z);
                    if (block == GridWorld.BlockState.AIR) {
                        continue;
                    }
                    Color baseColor = getBlockColor(block);
                    float r = baseColor.r;
                    float g = baseColor.g;
                    float b = baseColor.b;
                    if (!focusedLayer) {
                        float gray = (r + g + b) / 3f;
                        float emphasis = 0.6f;
                        r = MathUtils.lerp(gray, r, emphasis);
                        g = MathUtils.lerp(gray, g, emphasis);
                        b = MathUtils.lerp(gray, b, emphasis);
                    }
                    sr.setColor(r, g, b, layerOpacity);
                    drawIsoTile(sr, x, y, z, block);
                }
            }
        }
        if (movingCube != null) {
            float cubeZ = movingCube.getZ();
            if (cubeZ >= minZ && cubeZ <= maxZ) {
                sr.setColor(Color.RED);
                drawIsoCubeMarker(sr, movingCube.getX(), movingCube.getY(), cubeZ);
            }
        }
        sr.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    private void renderTopLayerGuide() {
        if (!isSideViewActive()) {
            return;
        }
        float guideZ = currentLayer;
        float minZ = -undergroundDepth;
        float maxZ = gridWorld.getHeight() - 1;
        if (guideZ < minZ || guideZ > maxZ) {
            return;
        }
        float screenZ = (guideZ + undergroundDepth) * blockSize;
        ShapeRenderer sr = gridRenderer.getShapeRenderer();
        sr.begin(ShapeRenderer.ShapeType.Filled);
        if (guideZ >= 0) {
            sr.setColor(0.1f, 0.4f, 1f, 1f);
        } else {
            sr.setColor(0.1f, 0.2f, 0.8f, 0.8f);
        }
        float worldWidthPixels = gridWorld.getWidth() * blockSize;
        sr.rect(0, screenZ - 1f, worldWidthPixels, 2f);
        sr.end();
    }

    private void renderSplitViews() {
        int screenWidth = Gdx.graphics.getWidth();
        int screenHeight = Gdx.graphics.getHeight();
        int topWidth = MathUtils.clamp(Math.round(screenWidth * splitViewRatio), 1, screenWidth - 1);
        int sideWidth = screenWidth - topWidth;

        // Top-down view occupies the left slice
        applyTopDownViewport(0, 0, topWidth, screenHeight);
        renderTopDownViewContents();

        // Side view occupies the right slice
        applySideViewport(topWidth, 0, sideWidth, screenHeight);
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

    private void renderSideSliceGuide() {
        if (!isTopViewActive()) {
            return;
        }
        int guideY = sideViewSlice;
        if (guideY < 0 || guideY >= gridWorld.getDepth()) {
            return;
        }
        float screenY = guideY * blockSize;
        ShapeRenderer sr = gridRenderer.getShapeRenderer();
        sr.begin(ShapeRenderer.ShapeType.Filled);
        sr.setColor(0.1f, 0.4f, 1f, 0.9f);
        float worldWidthPixels = gridWorld.getWidth() * blockSize;
        sr.rect(0, screenY - 1f, worldWidthPixels, 2f);
        sr.end();
    }

    private void drawIsoTile(ShapeRenderer sr, int x, int y, int z, GridWorld.BlockState block) {
        float isoX = (y - x) * (isoTileWidth / 2f);
        float isoY = (x + y) * (isoTileHeight / 2f) + (z * isoBlockHeight);
        float centerX = isoOriginX + isoX;
        float centerY = isoOriginY + isoY;
        float halfW = isoTileWidth / 2f;
        float halfH = isoTileHeight / 2f;

        // Top diamond (two triangles for isometric tile)
        sr.triangle(centerX, centerY + halfH, centerX - halfW, centerY, centerX + halfW, centerY);
        sr.triangle(centerX, centerY - halfH, centerX - halfW, centerY, centerX + halfW, centerY);

        if (block == GridWorld.BlockState.CASTLE_WHITE_STAIR || block == GridWorld.BlockState.CASTLE_BLACK_STAIR) {
            float accentHeight = isoBlockHeight * 0.4f;
            float accentWidth = halfW * 0.6f;
            sr.rectLine(centerX - accentWidth, centerY - halfH * 0.2f,
                centerX + accentWidth, centerY - halfH * 0.2f, 1.2f);
            sr.rect(centerX - accentWidth * 0.2f, centerY - halfH * 0.2f,
                accentWidth * 0.4f, accentHeight);
        }
    }

    private void drawIsoCubeMarker(ShapeRenderer sr, float x, float y, float z) {
        float isoX = (y - x) * (isoTileWidth / 2f);
        float isoY = (x + y) * (isoTileHeight / 2f) + (z * isoBlockHeight);
        float centerX = isoOriginX + isoX;
        float centerY = isoOriginY + isoY + isoBlockHeight * 0.2f;
        float halfW = isoTileWidth * 0.25f;
        float halfH = isoTileHeight * 0.25f;
        float bodyHeight = isoBlockHeight * 0.4f;

        // Top diamond
        sr.triangle(centerX, centerY + halfH, centerX - halfW, centerY, centerX + halfW, centerY);
        sr.triangle(centerX, centerY - halfH, centerX - halfW, centerY, centerX + halfW, centerY);

        // Front face
        sr.triangle(centerX - halfW, centerY, centerX - halfW, centerY - bodyHeight, centerX, centerY - halfH);
        sr.triangle(centerX + halfW, centerY, centerX + halfW, centerY - bodyHeight, centerX, centerY - halfH);
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
            case CASTLE_WHITE_STAIR:
                return new Color(0.98f, 0.78f, 0.35f, 1);
            case CASTLE_BLACK_STAIR:
                return new Color(0.65f, 0.4f, 0.2f, 1);
            default:
                return Color.WHITE;
        }
    }
    
    private void renderOverlay() {
        // Render a translucent bar for overlay text (placeholder for font rendering)
        ShapeRenderer sr = gridRenderer.getShapeRenderer();
        overlayProjection.setToOrtho2D(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        sr.setProjectionMatrix(overlayProjection);
        sr.begin(ShapeRenderer.ShapeType.Filled);
        sr.setColor(0, 0, 0, 0.7f);
        sr.rect(10, Gdx.graphics.getHeight() - 30, 560, 25);
        sr.end();
        
        String labelPrefix = (options.overlayLabel == null || options.overlayLabel.isEmpty())
            ? ""
            : "[" + options.overlayLabel + "] ";
        String info;
        if (viewMode == ViewMode.ISOMETRIC) {
            String levelDesc = currentLayer < 0 ? "Underground " + (-currentLayer) : "Layer " + currentLayer;
            StringBuilder builder = new StringBuilder(labelPrefix)
                .append("Isometric view: ")
                .append(levelDesc)
                .append(", side slice Y=")
                .append(sideViewSlice)
                .append("  (comma/period layers, '['/']' slices, WASD pan, -/= zoom, 'i' exit)");
            info = builder.toString();
        } else if (splitViewEnabled) {
            String topDesc = currentLayer < 0 ? "Underground " + (-currentLayer) : "Layer " + currentLayer;
            StringBuilder builder = new StringBuilder(labelPrefix)
                .append("Split: Top ")
                .append(topDesc)
                .append(", Side Y=")
                .append(sideViewSlice)
                .append("  (comma/period = top layers, '['/']' = side slices, arrows pan, -/= zoom");
            if (options.allowViewToggle) {
                builder.append(", '/' swaps focus");
            }
            if (options.allowSplitView) {
                builder.append(", 'm' toggle");
            }
            builder.append(", 'i' iso view");
            builder.append(')');
            info = builder.toString();
        } else if (viewMode == ViewMode.TOP_DOWN) {
            String levelDesc = currentLayer < 0 ? "Underground " + (-currentLayer) : "Layer " + currentLayer;
            StringBuilder builder = new StringBuilder(labelPrefix)
                .append("Top view: ")
                .append(levelDesc)
                .append("  (comma/period change layers");
            if (options.allowViewToggle || options.allowSplitView) {
                builder.append(", '['/']' side slices");
            }
            if (options.allowViewToggle) {
                builder.append(", '/' for side view");
            }
            if (options.allowSplitView) {
                builder.append(", 'm' split");
            }
            builder.append(", 'i' iso view");
            builder.append(')');
            info = builder.toString();
        } else {
            StringBuilder builder = new StringBuilder(labelPrefix)
                .append("Side view slice Y=")
                .append(sideViewSlice)
                .append("  ('['/']' = slice, arrows pan, -/= zoom");
            if (options.allowViewToggle) {
                builder.append(", '/' for top view");
            }
            if (options.allowSplitView) {
                builder.append(", 'm' split");
            }
            builder.append(", 'i' iso view");
            builder.append(')');
            info = builder.toString();
        }
    overlayBatch.setProjectionMatrix(overlayProjection);
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
        isoViewport.update(width, height, false);
        clampSideCameraPosition();
    }

    @Override
    public void show() {
        StringBuilder builder = new StringBuilder("View ready: ")
            .append(viewMode == ViewMode.TOP_DOWN ? "top-down" : "side")
            .append(" window.");
        if (options.allowViewToggle) {
            builder.append(" Press '/' to swap modes.");
        }
        if (options.allowSplitView) {
            builder.append(" Press 'm' for split view.");
        }
        builder.append(" Press 'i' for isometric view.");
        Gdx.app.log("DualViewScreen", builder.toString());
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
