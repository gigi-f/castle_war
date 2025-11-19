package com.castlewar.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.FitViewport;
import java.util.List;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.castlewar.entity.Assassin;
import com.castlewar.entity.Entity;
import com.castlewar.entity.Guard;
import com.castlewar.entity.King;
import com.castlewar.entity.Player;
import com.castlewar.entity.Team;
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
        ISOMETRIC,
        FIRST_PERSON
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
    private final GlyphLayout glyphLayout;
    private final Matrix4 overlayProjection;
    private final int undergroundDepth;
    private final float totalVerticalBlocks;
    private final Options options;
    
    // Cameras / viewports for each view mode
    private OrthographicCamera topDownCamera;
    private Viewport topDownViewport;
    private OrthographicCamera sideCamera;
    private Viewport sideViewport;
    private OrthographicCamera isoCamera;
    private Viewport isoViewport;
    private PerspectiveCamera fpsCamera;
    private Player player;
    private final float sideCameraPanSpeed = 200f; // pixels per second
    private static final float SIDE_VIEW_MIN_ZOOM = 0.5f;
    private static final float SIDE_VIEW_MAX_ZOOM = 3.0f;
    private static final float SIDE_VIEW_ZOOM_STEP = 0.15f;
    private final float isoTileWidth;
    private final float isoTileHeight;
    private final float isoBlockHeight;
    private float isoOriginX;
    private float isoOriginY;
    private float isoZoom = 5f;
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
    private boolean compassVisible = true;
    private boolean keyCPressed;
    private float isoRotationDegrees = 0f;
    private int lastMouseX;
    private boolean xRayEnabled = true;
    private boolean keyXPressed;
    private Entity selectedUnit;
    private Entity focusedUnit;
    private boolean keyTabPressed;
    private boolean keyEnterPressed;
    private boolean mouseClicked;

    public DualViewScreen(WorldContext worldContext, Options options) {
        this.worldContext = worldContext;
        this.options = options;
        this.gridWorld = worldContext.getGridWorld();
        this.gridRenderer = new GridRenderer(blockSize);
        this.overlayBatch = new SpriteBatch();
        
        // Generate high-res font
        FreeTypeFontGenerator generator = new FreeTypeFontGenerator(Gdx.files.internal("AovelSansRounded-rdDL.ttf"));
        FreeTypeFontGenerator.FreeTypeFontParameter parameter = new FreeTypeFontGenerator.FreeTypeFontParameter();
        parameter.size = 20; // Large base size for crisp text
        parameter.color = Color.WHITE;
        parameter.minFilter = com.badlogic.gdx.graphics.Texture.TextureFilter.Linear;
        parameter.magFilter = com.badlogic.gdx.graphics.Texture.TextureFilter.Linear;
        this.overlayFont = generator.generateFont(parameter);
        generator.dispose();
        
        this.glyphLayout = new GlyphLayout();
        this.overlayProjection = new Matrix4();
        this.undergroundDepth = worldContext.getUndergroundDepth();
        this.totalVerticalBlocks = worldContext.getTotalVerticalBlocks();
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
        isoViewport = new FitViewport(100 * isoZoom, 100 * isoZoom * (Gdx.graphics.getHeight() / (float)Gdx.graphics.getWidth()), isoCamera);
        isoViewport.update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        isoCamera.position.set(0, 0, 0);
        
        // FPS Camera
        fpsCamera = new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        fpsCamera.near = 0.1f;
        fpsCamera.far = 300f;
        fpsCamera.up.set(0, 0, 1); // Z is up
        isoOriginX = 0;
        isoOriginY = 0;
        
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
    handleCompassToggle();
        
        if (options.updatesSimulation) {
            worldContext.update(delta);
        }
        
        // Clear screen with light gray background
        Gdx.gl.glClearColor(0.9f, 0.9f, 0.9f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        if (Gdx.input.isKeyJustPressed(Input.Keys.F)) {
            if (viewMode == ViewMode.FIRST_PERSON) {
                viewMode = ViewMode.TOP_DOWN; // Exit FPS
                Gdx.input.setCursorCatched(false);
            } else {
                viewMode = ViewMode.FIRST_PERSON;
                Gdx.input.setCursorCatched(true);
                // Spawn player if needed
                if (player == null) {
                    float startX = gridWorld.getWidth() / 2f;
                    float startY = gridWorld.getDepth() / 2f;
                    float startZ = gridWorld.getHeight() + 5; // Drop from sky
                    player = new Player(startX, startY, startZ, Team.WHITE, fpsCamera);
                    worldContext.getEntities().add(player);
                }
            }
        }

        if (viewMode == ViewMode.ISOMETRIC) {
        renderIsometricFullView();
    } else if (viewMode == ViewMode.FIRST_PERSON) {
        if (player != null) {
            player.update(Gdx.graphics.getDeltaTime(), gridWorld);
        }
        renderFirstPerson();
    } else if (splitViewEnabled) {
        renderSplitViews();
    } else if (viewMode == ViewMode.TOP_DOWN) {
        renderTopDownFullView();
    } else if (viewMode == ViewMode.SIDE_SCROLLER) {
        renderSideScrollerFullView();
    }
        
        // Display view info overlay/logging
        renderOverlay();
        
        // Display view info overlay/logging
        renderOverlay();
        
        handleIsoRotationInput();
        handleXRayToggle();
        handleUnitSelection();
        handleAccessibilityInput();
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
            if (!keyMinusPressed) {
                isoZoom = Math.min(30f, isoZoom + 0.5f); // Zoom out
                updated = true;
            }
            keyMinusPressed = true;
        } else {
            keyMinusPressed = false;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.EQUALS)) {
            if (!keyEqualsPressed) {
                isoZoom = Math.max(0.1f, isoZoom - 0.5f); // Zoom in
                updated = true;
            }
            keyEqualsPressed = true;
        } else {
            keyEqualsPressed = false;
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

    private void handleIsoRotationInput() {
        if (viewMode != ViewMode.ISOMETRIC) {
            return;
        }
        if (Gdx.input.isButtonPressed(Input.Buttons.LEFT)) {
            int currentMouseX = Gdx.input.getX();
            if (lastMouseX != 0) { // Skip first frame of press to avoid jump
                float deltaX = currentMouseX - lastMouseX;
                isoRotationDegrees -= deltaX * 0.5f; // Adjust sensitivity as needed
            }
            lastMouseX = currentMouseX;
        } else {
            lastMouseX = 0;
        }
    }

    private void handleCompassToggle() {
        if (Gdx.input.isKeyPressed(Input.Keys.C)) {
            if (!keyCPressed) {
                compassVisible = !compassVisible;
                Gdx.app.log("DualViewScreen", compassVisible ? "Compass HUD shown" : "Compass HUD hidden");
            }
            keyCPressed = true;
        } else {
            keyCPressed = false;
        }
    }

    private void handleXRayToggle() {
        if (Gdx.input.isKeyPressed(Input.Keys.X)) {
            if (!keyXPressed) {
                xRayEnabled = !xRayEnabled;
                Gdx.app.log("DualViewScreen", xRayEnabled ? "X-Ray Mode ON" : "X-Ray Mode OFF");
            }
            keyXPressed = true;
        } else {
            keyXPressed = false;
        }
    }

    private void handleAccessibilityInput() {
        // TAB to cycle focus
        if (Gdx.input.isKeyJustPressed(Input.Keys.TAB)) {
            List<Entity> entities = worldContext.getEntities();
            if (!entities.isEmpty()) {
                int currentIndex = -1;
                if (focusedUnit != null) {
                    currentIndex = entities.indexOf(focusedUnit);
                }
                
                int nextIndex = (currentIndex + 1) % entities.size();
                focusedUnit = entities.get(nextIndex);
                String unitName = "Entity";
                if (focusedUnit instanceof King) unitName = ((King)focusedUnit).getName();
                else if (focusedUnit instanceof Guard) unitName = ((Guard)focusedUnit).getName();
                else if (focusedUnit instanceof Assassin) unitName = ((Assassin)focusedUnit).getName();
                Gdx.app.log("DualViewScreen", "Focused: " + unitName);
            }
        }
        
        // ENTER to select focused unit
        if (Gdx.input.isKeyJustPressed(Input.Keys.ENTER)) {
            if (focusedUnit != null) {
                selectedUnit = focusedUnit;
                String unitName = "Entity";
                if (selectedUnit instanceof King) unitName = ((King)selectedUnit).getName();
                else if (selectedUnit instanceof Guard) unitName = ((Guard)selectedUnit).getName();
                else if (selectedUnit instanceof Assassin) unitName = ((Assassin)selectedUnit).getName();
                Gdx.app.log("DualViewScreen", "Selected via Keyboard: " + unitName);
            }
        }
    }

    private void handleUnitSelection() {
        if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
            int mouseX = Gdx.input.getX();
            int mouseY = Gdx.input.getY();
            
            // Check for unit clicks based on current view
            Entity clicked = null;
            
            if (viewMode == ViewMode.ISOMETRIC) {
                clicked = getUnitAtIso(mouseX, mouseY);
            } else if (splitViewEnabled) {
                // Check top viewport
                if (mouseX <= Gdx.graphics.getWidth() * splitViewRatio) {
                    clicked = getUnitAtTopDown(mouseX, mouseY, topDownViewport);
                } else {
                    clicked = getUnitAtSide(mouseX, mouseY, sideViewport);
                }
            } else if (viewMode == ViewMode.TOP_DOWN) {
                clicked = getUnitAtTopDown(mouseX, mouseY, topDownViewport);
            } else {
                clicked = getUnitAtSide(mouseX, mouseY, sideViewport);
            }
            
            if (clicked != null) {
                selectedUnit = clicked;
                String unitName = "Entity";
                if (clicked instanceof King) unitName = ((King)clicked).getName();
                else if (clicked instanceof Guard) unitName = ((Guard)clicked).getName();
                else if (clicked instanceof Assassin) unitName = ((Assassin)clicked).getName();
                Gdx.app.log("DualViewScreen", "Selected: " + unitName);
            } else {
                // Deselect if clicking empty space (optional, maybe keep selection?)
                // selectedUnit = null; 
            }
        }
    }

    private Entity getUnitAtTopDown(int screenX, int screenY, Viewport viewport) {
        Vector3 worldCoords = viewport.unproject(new Vector3(screenX, screenY, 0));
        float wx = worldCoords.x / blockSize;
        float wy = worldCoords.y / blockSize;
        
        for (Entity entity : worldContext.getEntities()) {
            // Check if visible first
            boolean visible = xRayEnabled || ((int) entity.getZ() <= currentLayer && entity.getZ() >= 0);
            if (!visible) continue;

            if (Math.abs(entity.getX() - wx) < 0.5f && Math.abs(entity.getY() - wy) < 0.5f) {
                return entity;
            }
        }
        return null;
    }

    private Entity getUnitAtSide(int screenX, int screenY, Viewport viewport) {
        Vector3 worldCoords = viewport.unproject(new Vector3(screenX, screenY, 0));
        float wx = worldCoords.x / blockSize;
        float wz = (worldCoords.y / blockSize) - undergroundDepth;
        
        for (Entity entity : worldContext.getEntities()) {
             // Check visibility
            if (!xRayEnabled && Math.round(entity.getY()) != sideViewSlice) continue;
            
            if (Math.abs(entity.getX() - wx) < 0.5f && Math.abs(entity.getZ() - wz) < 0.5f) {
                return entity;
            }
        }
        return null;
    }

    private Entity getUnitAtIso(int screenX, int screenY) {
        // Simple screen-space distance check against projected positions
        float centerX = gridWorld.getWidth() / 2f;
        float centerY = gridWorld.getDepth() / 2f;
        
        float minDist = 40f; // Pixel threshold
        Entity bestMatch = null;
        
        for (Entity entity : worldContext.getEntities()) {
             // Check visibility (iso shows everything usually, maybe layer filter?)
             boolean visible = xRayEnabled || ((int) entity.getZ() <= currentLayer && entity.getZ() >= 0);
             if (!visible) continue;

            float[] screenPos = projectIsoPoint(entity.getX() + 0.5f, entity.getY() + 0.5f, entity.getZ(), centerX, centerY);
            // projectIsoPoint returns world coords relative to isoOrigin, need to project to screen
            Vector3 projected = isoCamera.project(new Vector3(screenPos[0], screenPos[1], 0));
            
            float dist = Vector2.dst(screenX, Gdx.graphics.getHeight() - screenY, projected.x, projected.y);
            if (dist < minDist) {
                minDist = dist;
                bestMatch = entity;
            }
        }
        return bestMatch;
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
        
        // Render entities
        for (Entity entity : worldContext.getEntities()) {
            boolean visible = xRayEnabled || ((int) entity.getZ() <= currentLayer && entity.getZ() >= 0);
            if (visible) {
                renderEntityTopDown(entity);
            }
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
        
        // Render entities
        for (Entity entity : worldContext.getEntities()) {
            renderEntitySide(entity);
        }

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
        
        // Render entities
        for (Entity entity : worldContext.getEntities()) {
            float ez = entity.getZ();
            if (ez >= minZ && ez <= maxZ) {
                renderEntityIso(sr, entity);
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
        // Calculate the 4 corners of the tile in world space relative to center
        float centerX = gridWorld.getWidth() / 2f;
        float centerY = gridWorld.getDepth() / 2f;
        
        // Corners: (x,y), (x+1,y), (x+1,y+1), (x,y+1)
        float[] c1 = projectIsoPoint(x, y, z, centerX, centerY);
        float[] c2 = projectIsoPoint(x + 1, y, z, centerX, centerY);
        float[] c3 = projectIsoPoint(x + 1, y + 1, z, centerX, centerY);
        float[] c4 = projectIsoPoint(x, y + 1, z, centerX, centerY);
        
        // Draw two triangles to form the quad
        sr.triangle(c1[0], c1[1], c2[0], c2[1], c3[0], c3[1]);
        sr.triangle(c1[0], c1[1], c3[0], c3[1], c4[0], c4[1]);

        if (block == GridWorld.BlockState.CASTLE_WHITE_STAIR || block == GridWorld.BlockState.CASTLE_BLACK_STAIR) {
            // Simplified stair marker for rotated view - just a small centered quad
            float[] center = projectIsoPoint(x + 0.5f, y + 0.5f, z, centerX, centerY);
            float size = isoTileWidth * 0.3f;
            sr.rect(center[0] - size/2, center[1] - size/2, size, size);
        }
    }

    private float[] projectIsoPoint(float x, float y, float z, float centerX, float centerY) {
        // 1. Translate to center
        float relX = x - centerX;
        float relY = y - centerY;
        
        // 2. Rotate
        float rad = isoRotationDegrees * MathUtils.degreesToRadians;
        float cos = MathUtils.cos(rad);
        float sin = MathUtils.sin(rad);
        float rotX = relX * cos - relY * sin;
        float rotY = relX * sin + relY * cos;
        
        // 3. Translate back (optional, but we want to rotate around center)
        // Actually for iso projection we usually want 0,0 to be the anchor.
        // Let's keep it relative to center for the projection.
        
        // 4. Isometric projection
        // Standard iso: x = (x - y), y = (x + y) / 2
        // But we need to scale by tile size.
        // isoX = (rotX - rotY) * (isoTileWidth / 2f)
        // isoY = (rotX + rotY) * (isoTileHeight / 2f)
        // Note: Our original code had isoX = -(y-x) which is (x-y).
        
        float isoX = (rotX - rotY) * (isoTileWidth / 2f);
        float isoY = (rotX + rotY) * (isoTileHeight / 2f) + (z * isoBlockHeight);
        
        return new float[] { isoOriginX + isoX, isoOriginY + isoY };
    }


    
    private Color getBlockColor(GridWorld.BlockState block) {
        return gridRenderer.getBlockColor(block);
    }
    
    private void renderOverlay() {
        int screenWidth = Gdx.graphics.getWidth();
        int screenHeight = Gdx.graphics.getHeight();
        
        // Reset viewport to full screen to avoid squashed UI in split view
        Gdx.gl.glViewport(0, 0, screenWidth, screenHeight);
        
        // Update projection matrix for overlay
        overlayProjection.setToOrtho2D(0, 0, screenWidth, screenHeight);
        
        renderCompassHud();
        
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
                .append("  (comma/period layers, '['/']' slices, WASD pan, -/= zoom, 'c' compass, 'x' xray, 'i' exit)");
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
            builder.append(", 'c' compass, 'x' xray, 'i' iso view");
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
            builder.append(", 'c' compass, 'x' xray, 'i' iso view");
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
            builder.append(", 'c' compass, 'x' xray, 'i' iso view");
            builder.append(')');
            info = builder.toString();
        }

        // HUD Configuration
        // Base scale on 1080p reference. 
        // Since base font is 28px, scale=1 at 1080p is good.
        float scale = Math.max(0.8f, screenHeight / 1080f); 
        overlayFont.getData().setScale(scale);
        
        float padding = 20f * scale;
        float maxWidth = screenWidth * 0.8f; // Max width 80% of screen
        
        // Measure text
        glyphLayout.setText(overlayFont, info, Color.WHITE, maxWidth, Align.left, true);
        float textWidth = glyphLayout.width;
        float textHeight = glyphLayout.height;
        
        float bubbleWidth = textWidth + padding * 2;
        float bubbleHeight = textHeight + padding * 2;
        
        // Position bubble at bottom center, slightly up
        float bubbleX = (screenWidth - bubbleWidth) / 2f;
        float bubbleY = 20f; // Margin from bottom
        
        // Draw Bubble
        ShapeRenderer sr = gridRenderer.getShapeRenderer();
        overlayProjection.setToOrtho2D(0, 0, screenWidth, screenHeight);
        sr.setProjectionMatrix(overlayProjection);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        sr.begin(ShapeRenderer.ShapeType.Filled);
        drawBubble(sr, bubbleX, bubbleY, bubbleWidth, bubbleHeight, new Color(0.1f, 0.1f, 0.15f, 0.85f));
        sr.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
        
        // Draw Text
        overlayBatch.setProjectionMatrix(overlayProjection);
        overlayBatch.begin();
        overlayFont.draw(overlayBatch, glyphLayout, bubbleX + padding, bubbleY + bubbleHeight - padding);
        overlayBatch.end();
        
        // Reset scale
        overlayFont.getData().setScale(1f);
        
        if (selectedUnit != null) {
            renderUnitModal(screenWidth, screenHeight, scale);
        }
    }

    private void renderUnitModal(float screenWidth, float screenHeight, float scale) {
        float modalWidth = 300f * scale;
        float modalHeight = 150f * scale;
        float modalX = 20f * scale;
        float modalY = screenHeight - modalHeight - 20f * scale;
        
        ShapeRenderer sr = gridRenderer.getShapeRenderer();
        overlayProjection.setToOrtho2D(0, 0, screenWidth, screenHeight);
        sr.setProjectionMatrix(overlayProjection);
        
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        sr.begin(ShapeRenderer.ShapeType.Filled);
        
        // Background Bubble
        drawBubble(sr, modalX, modalY, modalWidth, modalHeight, new Color(0.1f, 0.1f, 0.15f, 0.9f));
        
        // Headshot Background
        float headshotSize = 80f * scale;
        float headshotX = modalX + 20f * scale;
        float headshotY = modalY + (modalHeight - headshotSize) / 2f;
        sr.setColor(0.2f, 0.2f, 0.25f, 1f);
        sr.rect(headshotX, headshotY, headshotSize, headshotSize);
        
        // Procedural Headshot (Pixel Art Style)
        if (selectedUnit instanceof King) {
            King king = (King) selectedUnit;
            boolean isWhite = king.getTeam() == Team.WHITE;
            
            // Face
            sr.setColor(0.9f, 0.8f, 0.7f, 1f); // Skin
            sr.rect(headshotX + headshotSize*0.2f, headshotY + headshotSize*0.2f, headshotSize*0.6f, headshotSize*0.6f);
            
            // Beard
            sr.setColor(isWhite ? Color.WHITE : Color.BLACK);
            sr.rect(headshotX + headshotSize*0.2f, headshotY + headshotSize*0.2f, headshotSize*0.6f, headshotSize*0.3f);
            
            // Crown
            sr.setColor(isWhite ? Color.GOLD : new Color(0.4f, 0.4f, 0.4f, 1f));
            sr.rect(headshotX + headshotSize*0.2f, headshotY + headshotSize*0.7f, headshotSize*0.6f, headshotSize*0.2f);
            // Crown spikes
            sr.triangle(
                headshotX + headshotSize*0.2f, headshotY + headshotSize*0.9f,
                headshotX + headshotSize*0.3f, headshotY + headshotSize*0.9f,
                headshotX + headshotSize*0.25f, headshotY + headshotSize*1.0f
            );
             sr.triangle(
                headshotX + headshotSize*0.45f, headshotY + headshotSize*0.9f,
                headshotX + headshotSize*0.55f, headshotY + headshotSize*0.9f,
                headshotX + headshotSize*0.5f, headshotY + headshotSize*1.0f
            );
             sr.triangle(
                headshotX + headshotSize*0.7f, headshotY + headshotSize*0.9f,
                headshotX + headshotSize*0.8f, headshotY + headshotSize*0.9f,
                headshotX + headshotSize*0.75f, headshotY + headshotSize*1.0f
            );
            
            sr.rect(headshotX + headshotSize*0.35f, headshotY + headshotSize*0.55f, headshotSize*0.1f, headshotSize*0.1f);
            sr.rect(headshotX + headshotSize*0.55f, headshotY + headshotSize*0.55f, headshotSize*0.1f, headshotSize*0.1f);
        } else if (selectedUnit instanceof Assassin) {
            Assassin assassin = (Assassin) selectedUnit;
            boolean isWhite = assassin.getTeam() == Team.WHITE;
            
            // Hood (Dark Team Color)
            sr.setColor(isWhite ? new Color(0.8f, 0.8f, 0.9f, 1f) : new Color(0.2f, 0.1f, 0.1f, 1f));
            // Hood shape
            sr.triangle(
                headshotX + headshotSize*0.5f, headshotY + headshotSize*0.9f,
                headshotX + headshotSize*0.2f, headshotY + headshotSize*0.2f,
                headshotX + headshotSize*0.8f, headshotY + headshotSize*0.2f
            );
            sr.rect(headshotX + headshotSize*0.2f, headshotY + headshotSize*0.2f, headshotSize*0.6f, headshotSize*0.4f);
            
            // Face (Shadowed)
            sr.setColor(0.1f, 0.1f, 0.1f, 1f);
            sr.rect(headshotX + headshotSize*0.3f, headshotY + headshotSize*0.3f, headshotSize*0.4f, headshotSize*0.3f);
            
            // Eyes (Glowing Red)
            sr.setColor(1f, 0f, 0f, 1f);
            sr.rect(headshotX + headshotSize*0.35f, headshotY + headshotSize*0.45f, headshotSize*0.1f, headshotSize*0.05f);
            sr.rect(headshotX + headshotSize*0.55f, headshotY + headshotSize*0.45f, headshotSize*0.1f, headshotSize*0.05f);
        }
        else if (selectedUnit instanceof Guard) {
            Guard guard = (Guard) selectedUnit;
            boolean isWhite = guard.getTeam() == Team.WHITE;
            
            // Helmet (Gray/Silver)
            sr.setColor(0.6f, 0.6f, 0.65f, 1f);
            sr.rect(headshotX + headshotSize*0.2f, headshotY + headshotSize*0.2f, headshotSize*0.6f, headshotSize*0.7f);
            
            // Visor slit
            sr.setColor(0.1f, 0.1f, 0.1f, 1f);
            sr.rect(headshotX + headshotSize*0.3f, headshotY + headshotSize*0.6f, headshotSize*0.4f, headshotSize*0.1f);
            
            // Plume (Team Color)
            sr.setColor(isWhite ? Color.CYAN : Color.RED); // Distinguish plume
            sr.triangle(
                headshotX + headshotSize*0.5f, headshotY + headshotSize*0.9f,
                headshotX + headshotSize*0.3f, headshotY + headshotSize*1.0f,
                headshotX + headshotSize*0.7f, headshotY + headshotSize*1.0f
            );
        }
        
        sr.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
        
        // Text Stats
        overlayBatch.setProjectionMatrix(overlayProjection);
        overlayBatch.begin();
        overlayFont.getData().setScale(scale * 0.8f); // Slightly smaller for stats
        
        float textX = headshotX + headshotSize + 20f * scale;
        float textY = modalY + modalHeight - 30f * scale;
        float lineHeight = 25f * scale;
        
        if (selectedUnit instanceof King) {
            King king = (King) selectedUnit;
            overlayFont.draw(overlayBatch, king.getName(), textX, textY);
            overlayFont.draw(overlayBatch, "HP: " + (int)king.getHp() + "/" + (int)king.getMaxHp(), textX, textY - lineHeight);
            overlayFont.draw(overlayBatch, "Team: " + king.getTeam(), textX, textY - lineHeight * 2);
        } else if (selectedUnit instanceof Guard) {
            Guard guard = (Guard) selectedUnit;
            overlayFont.draw(overlayBatch, guard.getName(), textX, textY);
            overlayFont.draw(overlayBatch, "HP: " + (int)guard.getHp() + "/" + (int)guard.getMaxHp(), textX, textY - lineHeight);
            overlayFont.draw(overlayBatch, "Type: " + guard.getType(), textX, textY - lineHeight * 2);
        } else if (selectedUnit instanceof Assassin) {
            Assassin assassin = (Assassin) selectedUnit;
            overlayFont.draw(overlayBatch, assassin.getName(), textX, textY);
            overlayFont.draw(overlayBatch, "HP: " + (int)assassin.getHp() + "/" + (int)assassin.getMaxHp(), textX, textY - lineHeight);
            overlayFont.draw(overlayBatch, "Role: Assassin", textX, textY - lineHeight * 2);
        } else {
            overlayFont.draw(overlayBatch, "Unknown Unit", textX, textY);
        }
        
        overlayBatch.end();
        overlayFont.getData().setScale(1f); // Reset
    }

    private void drawBubble(ShapeRenderer sr, float x, float y, float width, float height, Color color) {
        sr.setColor(color);
        float radius = Math.min(width, height) * 0.2f; // 20% rounding
        
        // Center rect
        sr.rect(x + radius, y + radius, width - 2*radius, height - 2*radius);
        
        // Side rects
        sr.rect(x + radius, y, width - 2*radius, radius);
        sr.rect(x + radius, y + height - radius, width - 2*radius, radius);
        
        // Vertical rects
        sr.rect(x, y + radius, radius, height - 2*radius);
        sr.rect(x + width - radius, y + radius, radius, height - 2*radius);
        
        // Corners
        sr.arc(x + radius, y + radius, radius, 180f, 90f);
        sr.arc(x + width - radius, y + radius, radius, 270f, 90f);
        sr.arc(x + width - radius, y + height - radius, radius, 0f, 90f);
        sr.arc(x + radius, y + height - radius, radius, 90f, 90f);
    }

    private void renderCompassHud() {
        if (!compassVisible) {
            return;
        }
        // In side view (without split), North is "into" the screen, so a 2D compass is confusing.
        if (viewMode == ViewMode.SIDE_SCROLLER && !splitViewEnabled) {
            return;
        }
        float screenWidth = Gdx.graphics.getWidth();
        float screenHeight = Gdx.graphics.getHeight();
        float compassSize = 80f;
        float radius = compassSize / 2f;
        float rightMargin = 25f;
        float topMargin = 80f;
        float centerX = screenWidth - rightMargin - radius;
        float centerY = screenHeight - topMargin - radius;
        
        float rotation = getCompassRotationDegreesClockwise();
        
        ShapeRenderer sr = gridRenderer.getShapeRenderer();
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        sr.setProjectionMatrix(overlayProjection);
        sr.begin(ShapeRenderer.ShapeType.Filled);
        
        // 1. Rim (Dark Gray)
        sr.setColor(0.2f, 0.2f, 0.2f, 0.9f);
        sr.circle(centerX, centerY, radius + 4f, 50);
        
        // 2. Bezel (Lighter Gray gradient simulation)
        sr.setColor(0.4f, 0.4f, 0.4f, 0.9f);
        sr.circle(centerX, centerY, radius, 50);
        sr.setColor(0.3f, 0.3f, 0.3f, 0.9f);
        sr.circle(centerX, centerY, radius - 2f, 50);
        
        // 3. Face (White/Off-white)
        sr.setColor(0.95f, 0.95f, 0.92f, 0.95f);
        sr.circle(centerX, centerY, radius - 6f, 40);
        
        // 4. Ticks
        sr.setColor(0.1f, 0.1f, 0.1f, 0.8f);
        for (int i = 0; i < 4; i++) {
            float angle = i * 90f + rotation;
            float r1 = radius - 6f;
            float r2 = radius - 12f;
            float tickX1 = centerX + MathUtils.sinDeg(angle) * r1;
            float tickY1 = centerY + MathUtils.cosDeg(angle) * r1;
            float tickX2 = centerX + MathUtils.sinDeg(angle) * r2;
            float tickY2 = centerY + MathUtils.cosDeg(angle) * r2;
            sr.rectLine(tickX1, tickY1, tickX2, tickY2, 2f);
        }
        
        // 5. Needle (3D style)
        float needleLen = radius - 10f;
        float needleWidth = 8f;
        float rad = -rotation * MathUtils.degreesToRadians; // Counter-rotate for needle
        float cos = MathUtils.cos(rad);
        float sin = MathUtils.sin(rad);
        
        // North half (Red)
        // Left side (darker)
        sr.setColor(0.7f, 0.1f, 0.1f, 1f);
        sr.triangle(
            centerX, centerY,
            centerX - needleWidth/2 * cos, centerY - needleWidth/2 * sin,
            centerX + needleLen * sin, centerY - needleLen * cos // Tip
        );
        // Right side (lighter)
        sr.setColor(0.9f, 0.2f, 0.2f, 1f);
        sr.triangle(
            centerX, centerY,
            centerX + needleWidth/2 * cos, centerY + needleWidth/2 * sin,
            centerX + needleLen * sin, centerY - needleLen * cos // Tip
        );
        
        // South half (White/Gray)
        // Left side (darker)
        sr.setColor(0.7f, 0.7f, 0.7f, 1f);
        sr.triangle(
            centerX, centerY,
            centerX - needleWidth/2 * cos, centerY - needleWidth/2 * sin,
            centerX - needleLen * sin, centerY + needleLen * cos // Tip
        );
        // Right side (lighter)
        sr.setColor(0.9f, 0.9f, 0.9f, 1f);
        sr.triangle(
            centerX, centerY,
            centerX + needleWidth/2 * cos, centerY + needleWidth/2 * sin,
            centerX - needleLen * sin, centerY + needleLen * cos // Tip
        );
        
        // Center cap
        sr.setColor(0.2f, 0.2f, 0.2f, 1f);
        sr.circle(centerX, centerY, 4f, 16);
        
        sr.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        // Labels (N, E, S, W) - Rotate with compass
        overlayBatch.setProjectionMatrix(overlayProjection);
        overlayBatch.begin();
        drawCompassLabel("N", 0, rotation, centerX, centerY, radius - 20f);
        drawCompassLabel("E", 90, rotation, centerX, centerY, radius - 20f);
        drawCompassLabel("S", 180, rotation, centerX, centerY, radius - 20f);
        drawCompassLabel("W", 270, rotation, centerX, centerY, radius - 20f);
        overlayBatch.end();
    }

    private void drawCompassLabel(String text, float angleOffset, float rotation, float cx, float cy, float dist) {
        float angle = angleOffset + rotation;
        float x = cx + MathUtils.sinDeg(angle) * dist - 4f;
        float y = cy + MathUtils.cosDeg(angle) * dist + 5f;
        overlayFont.draw(overlayBatch, text, x, y);
    }

    private float getCompassRotationDegreesClockwise() {
        if (viewMode == ViewMode.ISOMETRIC) {
            // Base isometric rotation is -45 degrees (North is top-left)
            // Add the user's manual rotation
            return -45f - isoRotationDegrees;
        }
        return 0f;
    }
    
    private void renderEntityTopDown(Entity entity) {
        float screenX = entity.getX() * blockSize;
        float screenY = entity.getY() * blockSize;
        float size = blockSize * 0.8f;
        float offset = (blockSize - size) / 2f;

        ShapeRenderer sr = gridRenderer.getShapeRenderer();
        sr.begin(ShapeRenderer.ShapeType.Filled);
        
        // Draw focus outline if applicable
        if (entity == focusedUnit) {
            sr.setColor(Color.CYAN);
            float outlineSize = size + 4f;
            float outlineOffset = (blockSize - outlineSize) / 2f;
            if (entity instanceof King) {
                sr.rect(screenX + outlineOffset, screenY + outlineOffset, outlineSize, outlineSize);
            } else {
                sr.circle(screenX + blockSize/2, screenY + blockSize/2, outlineSize/2);
            }
        }
        
        if (entity instanceof King) {
            // Draw King icon (Chess piece style)
            Color teamColor = entity.getTeam() == Team.WHITE ? Color.WHITE : Color.BLACK;
            sr.setColor(teamColor);
            
            // Base
            sr.rect(screenX + offset, screenY + offset, size, size * 0.2f);
            // Body
            sr.triangle(
                screenX + offset + size * 0.2f, screenY + offset + size * 0.2f,
                screenX + offset + size * 0.8f, screenY + offset + size * 0.2f,
                screenX + offset + size * 0.5f, screenY + offset + size * 0.8f
            );
            // Cross
            sr.rectLine(
                screenX + offset + size * 0.5f, screenY + offset + size * 0.7f,
                screenX + offset + size * 0.5f, screenY + offset + size,
                2f
            );
            sr.rectLine(
                screenX + offset + size * 0.3f, screenY + offset + size * 0.85f,
                screenX + offset + size * 0.7f, screenY + offset + size * 0.85f,
                2f
            );
        } else if (entity instanceof Guard) {
            // Guard icon (Shield)
            Color teamColor = entity.getTeam() == Team.WHITE ? Color.LIGHT_GRAY : Color.DARK_GRAY;
            sr.setColor(teamColor);
            
            // Shield shape
            sr.rect(screenX + offset + size*0.2f, screenY + offset + size*0.4f, size*0.6f, size*0.5f);
            sr.triangle(
                screenX + offset + size*0.2f, screenY + offset + size*0.4f,
                screenX + offset + size*0.8f, screenY + offset + size*0.4f,
                screenX + offset + size*0.5f, screenY + offset + size*0.1f
            );
            
            // Inner detail
            sr.setColor(entity.getTeam() == Team.WHITE ? Color.WHITE : Color.BLACK);
            sr.rect(screenX + offset + size*0.4f, screenY + offset + size*0.3f, size*0.2f, size*0.4f);
        } else if (entity instanceof Assassin) {
            // Assassin icon (Dagger/Triangle)
            Color teamColor = entity.getTeam() == Team.WHITE ? Color.WHITE : Color.BLACK;
            sr.setColor(teamColor);
            
            // Dagger blade
            sr.triangle(
                screenX + offset + size*0.5f, screenY + offset + size*0.9f,
                screenX + offset + size*0.3f, screenY + offset + size*0.4f,
                screenX + offset + size*0.7f, screenY + offset + size*0.4f
            );
            // Handle
            sr.setColor(Color.BROWN);
            sr.rect(screenX + offset + size*0.45f, screenY + offset + size*0.1f, size*0.1f, size*0.3f);
            sr.rect(screenX + offset + size*0.3f, screenY + offset + size*0.3f, size*0.4f, size*0.1f);
        } else {
            // Generic entity
            sr.setColor(Color.MAGENTA);
            sr.circle(screenX + blockSize/2, screenY + blockSize/2, size/2);
        }
        sr.end();
    }

    private void renderEntityIso(ShapeRenderer sr, Entity entity) {
        if (entity instanceof King) {
            Color teamColor = entity.getTeam() == Team.WHITE ? Color.WHITE : new Color(0.2f, 0.2f, 0.2f, 1f);
            sr.setColor(teamColor);
            
            float x = entity.getX();
            float y = entity.getY();
            float z = entity.getZ();
            float centerX = gridWorld.getWidth() / 2f;
            float centerY = gridWorld.getDepth() / 2f;
            
            // Project base position
            float[] base = projectIsoPoint(x + 0.5f, y + 0.5f, z, centerX, centerY);
            float bx = base[0];
            float by = base[1];
            float scale = isoTileWidth * 0.8f;
            
            // Draw focus outline
            if (entity == focusedUnit) {
                sr.setColor(Color.CYAN);
                // Draw a diamond outline on the ground
                float[] t1 = projectIsoPoint(x, y, z, centerX, centerY);
                float[] t2 = projectIsoPoint(x + 1, y, z, centerX, centerY);
                float[] t3 = projectIsoPoint(x + 1, y + 1, z, centerX, centerY);
                float[] t4 = projectIsoPoint(x, y + 1, z, centerX, centerY);
                
                // Draw slightly larger diamond or just thick lines
                float margin = 2f;
                sr.rectLine(t1[0], t1[1]-margin, t2[0], t2[1]-margin, 2f);
                sr.rectLine(t2[0], t2[1]-margin, t3[0], t3[1]-margin, 2f);
                sr.rectLine(t3[0], t3[1]-margin, t4[0], t4[1]-margin, 2f);
                sr.rectLine(t4[0], t4[1]-margin, t1[0], t1[1]-margin, 2f);
            }
            
            // Draw simple 3D-ish King
            // Base
            sr.rect(bx - scale*0.3f, by, scale*0.6f, scale*0.1f);
            // Body
            sr.triangle(
                bx - scale*0.2f, by + scale*0.1f,
                bx + scale*0.2f, by + scale*0.1f,
                bx, by + scale*0.6f
            );
            // Cross
            sr.rectLine(bx, by + scale*0.5f, bx, by + scale*0.9f, 2f);
            sr.rectLine(bx - scale*0.15f, by + scale*0.75f, bx + scale*0.15f, by + scale*0.75f, 2f);
        } else if (entity instanceof Guard) {
            Color teamColor = entity.getTeam() == Team.WHITE ? Color.LIGHT_GRAY : Color.DARK_GRAY;
            sr.setColor(teamColor);
            
            float x = entity.getX();
            float y = entity.getY();
            float z = entity.getZ();
            float centerX = gridWorld.getWidth() / 2f;
            float centerY = gridWorld.getDepth() / 2f;
            
            // Project base position
            float[] base = projectIsoPoint(x + 0.5f, y + 0.5f, z, centerX, centerY);
            float bx = base[0];
            float by = base[1];
            float scale = isoTileWidth * 0.8f;
            
            // Draw focus outline
            if (entity == focusedUnit) {
                sr.setColor(Color.CYAN);
                float[] t1 = projectIsoPoint(x, y, z, centerX, centerY);
                float[] t2 = projectIsoPoint(x + 1, y, z, centerX, centerY);
                float[] t3 = projectIsoPoint(x + 1, y + 1, z, centerX, centerY);
                float[] t4 = projectIsoPoint(x, y + 1, z, centerX, centerY);
                float margin = 2f;
                sr.rectLine(t1[0], t1[1]-margin, t2[0], t2[1]-margin, 2f);
                sr.rectLine(t2[0], t2[1]-margin, t3[0], t3[1]-margin, 2f);
                sr.rectLine(t3[0], t3[1]-margin, t4[0], t4[1]-margin, 2f);
                sr.rectLine(t4[0], t4[1]-margin, t1[0], t1[1]-margin, 2f);
            }
            
            // Draw Guard (Blocky with helmet)
            // Body
            sr.rect(bx - scale*0.25f, by, scale*0.5f, scale*0.5f);
            // Helmet
            sr.setColor(0.6f, 0.6f, 0.65f, 1f);
            sr.rect(bx - scale*0.2f, by + scale*0.5f, scale*0.4f, scale*0.3f);
        } else if (entity instanceof Assassin) {
            Color teamColor = entity.getTeam() == Team.WHITE ? Color.WHITE : Color.BLACK;
            sr.setColor(teamColor);
            
            float x = entity.getX();
            float y = entity.getY();
            float z = entity.getZ();
            float centerX = gridWorld.getWidth() / 2f;
            float centerY = gridWorld.getDepth() / 2f;
            
            // Project base position
            float[] base = projectIsoPoint(x + 0.5f, y + 0.5f, z, centerX, centerY);
            float bx = base[0];
            float by = base[1];
            float scale = isoTileWidth * 0.8f;
            
            // Draw focus outline
            if (entity == focusedUnit) {
                sr.setColor(Color.CYAN);
                float[] t1 = projectIsoPoint(x, y, z, centerX, centerY);
                float[] t2 = projectIsoPoint(x + 1, y, z, centerX, centerY);
                float[] t3 = projectIsoPoint(x + 1, y + 1, z, centerX, centerY);
                float[] t4 = projectIsoPoint(x, y + 1, z, centerX, centerY);
                float margin = 2f;
                sr.rectLine(t1[0], t1[1]-margin, t2[0], t2[1]-margin, 2f);
                sr.rectLine(t2[0], t2[1]-margin, t3[0], t3[1]-margin, 2f);
                sr.rectLine(t3[0], t3[1]-margin, t4[0], t4[1]-margin, 2f);
                sr.rectLine(t4[0], t4[1]-margin, t1[0], t1[1]-margin, 2f);
            }
            
            // Draw Assassin (Hooded, crouching)
            // Body (Lower)
            sr.triangle(
                bx - scale*0.3f, by,
                bx + scale*0.3f, by,
                bx, by + scale*0.5f
            );
            // Hood/Head
            sr.setColor(teamColor);
            sr.triangle(
                bx - scale*0.2f, by + scale*0.4f,
                bx + scale*0.2f, by + scale*0.4f,
                bx, by + scale*0.8f
            );
        }
    }

    private void renderEntitySide(Entity entity) {
        if (!xRayEnabled && Math.round(entity.getY()) != sideViewSlice) {
            return;
        }
        float z = entity.getZ();
        if (z < -undergroundDepth || z >= gridWorld.getHeight()) {
            return;
        }
        float screenX = entity.getX() * blockSize;
        float screenZ = (z + undergroundDepth) * blockSize;
        float size = blockSize * 0.8f;
        float offset = (blockSize - size) / 2f;

        ShapeRenderer sr = gridRenderer.getShapeRenderer();
        sr.begin(ShapeRenderer.ShapeType.Filled);
        
        // Draw focus outline
        if (entity == focusedUnit) {
            sr.setColor(Color.CYAN);
            sr.rect(screenX - 2, screenZ - 2, size + 4, size + 4);
        }
        
        if (entity instanceof King) {
            // Draw King icon (Side view profile)
            Color teamColor = entity.getTeam() == Team.WHITE ? Color.WHITE : Color.BLACK;
            sr.setColor(teamColor);
            sr.rect(screenX + offset, screenZ + offset, size, size);
            // Crown
            sr.setColor(Color.GOLD);
            sr.rect(screenX + offset, screenZ + offset + size*0.8f, size, size*0.2f);
        } else if (entity instanceof Guard) {
            // Draw Guard icon (Side view)
            Color teamColor = entity.getTeam() == Team.WHITE ? Color.LIGHT_GRAY : Color.DARK_GRAY;
            sr.setColor(teamColor);
            sr.rect(screenX + offset, screenZ + offset, size, size);
            // Helmet visor
            sr.setColor(Color.BLACK);
            sr.rect(screenX + offset + size*0.6f, screenZ + offset + size*0.6f, size*0.4f, size*0.1f);
        } else if (entity instanceof Assassin) {
            // Draw Assassin icon (Side view - Crouching)
            Color teamColor = entity.getTeam() == Team.WHITE ? Color.WHITE : Color.BLACK;
            sr.setColor(teamColor);
            // Low body
            sr.rect(screenX + offset, screenZ + offset, size, size*0.6f);
            // Hood
            sr.triangle(
                screenX + offset, screenZ + offset + size*0.6f,
                screenX + offset + size, screenZ + offset + size*0.6f,
                screenX + offset + size*0.5f, screenZ + offset + size
            );
        } else {
            sr.setColor(Color.MAGENTA);
            sr.circle(screenX + blockSize/2, screenZ + blockSize/2, size/2);
        }
        sr.end();
    }

    private void renderFirstPerson() {
        // Set fog color (match background)
        Color fogColor = new Color(0.9f, 0.9f, 0.9f, 1f);
        Gdx.gl.glClearColor(fogColor.r, fogColor.g, fogColor.b, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        
        fpsCamera.far = 100f; // Increase draw distance
        fpsCamera.update();

        ShapeRenderer sr = gridRenderer.getShapeRenderer();
        sr.setProjectionMatrix(fpsCamera.combined);
        sr.begin(ShapeRenderer.ShapeType.Filled);
        
        // Draw visible blocks around player
        int range = 80; // Increased range
        int px = (int)fpsCamera.position.x;
        int py = (int)fpsCamera.position.y;
        int pz = (int)fpsCamera.position.z;
        
        int minX = Math.max(0, px - range);
        int maxX = Math.min(gridWorld.getWidth(), px + range);
        int minY = Math.max(0, py - range);
        int maxY = Math.min(gridWorld.getDepth(), py + range);
        int minZ = Math.max(0, pz - range);
        int maxZ = Math.min(gridWorld.getHeight(), pz + range);
        
        float fogStart = 20f;
        float fogEnd = 80f;

        for (int x = minX; x < maxX; x++) {
            for (int y = minY; y < maxY; y++) {
                for (int z = minZ; z < maxZ; z++) {
                    GridWorld.BlockState block = gridWorld.getBlock(x, y, z);
                    if (block != GridWorld.BlockState.AIR) {
                        float dist = Vector3.dst(px, py, pz, x + 0.5f, y + 0.5f, z + 0.5f);
                        
                        // Occlusion fix: Don't render blocks too close to camera
                        if (dist < 1.1f) continue;

                        Color color = gridRenderer.getBlockColor(block).cpy();
                        
                        // Apply fog
                        float fogFactor = MathUtils.clamp((dist - fogStart) / (fogEnd - fogStart), 0f, 1f);
                        color.lerp(fogColor, fogFactor);
                        
                        sr.setColor(color);
                        // Draw box (x, y, z is corner)
                        sr.box(x, y, z, 1, 1, 1);
                    }
                }
            }
        }
        
        // Draw entities
        for (Entity entity : worldContext.getEntities()) {
            if (entity == player) continue;
            
            float dist = Vector3.dst(px, py, pz, entity.getX(), entity.getY(), entity.getZ());
            Color color = (entity.getTeam() == Team.WHITE ? Color.WHITE : Color.BLACK).cpy();
            
            // Apply fog
            float fogFactor = MathUtils.clamp((dist - fogStart) / (fogEnd - fogStart), 0f, 1f);
            color.lerp(fogColor, fogFactor);
            
            sr.setColor(color);
            sr.box(entity.getX(), entity.getY(), entity.getZ(), 0.6f, 0.6f, 1.8f);
        }
        
        sr.end();
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
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
        builder.append(" Press 'c' to toggle the compass HUD.");
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
