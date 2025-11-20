package com.castlewar.renderer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.castlewar.world.GridWorld;
import com.castlewar.world.GridWorld.BlockState;

import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders a 2D grid world using textures, and supports 3D rendering for FPS mode.
 */
public class GridRenderer {
    private final SpriteBatch batch;
    private final ShapeRenderer shapeRenderer;
    private final ModelBatch modelBatch;
    private final float blockSize;
    
    private Texture stoneTexture;
    private Texture woodTexture;
    private Texture grassTexture;
    private Texture dirtTexture;
    private Texture waterTexture;
    private Texture whiteWallTexture;
    private Texture blackWallTexture;
    private Texture whiteFloorTexture;
    private Texture blackFloorTexture;
    private Texture doorTexture;
    private Texture windowTexture;
    private Texture defaultTexture;
    private Texture cliffTexture;

    private Environment environment;
    private Map<BlockState, List<Model>> blockModels;
    private Map<BlockState, List<ModelInstance>> blockInstances;

    public GridRenderer(float blockSize) {
        this.batch = new SpriteBatch();
        this.shapeRenderer = new ShapeRenderer();
        this.modelBatch = new ModelBatch();
        this.blockSize = blockSize;
        
        loadTextures();
        create3DModels();
    }
    
    private void loadTextures() {
        // Load generated textures
        try {
            stoneTexture = new Texture(Gdx.files.internal("textures/stone_wall.png"));
            woodTexture = new Texture(Gdx.files.internal("textures/wooden_stairs.png"));
            grassTexture = new Texture(Gdx.files.internal("textures/grass.png"));
            cliffTexture = new Texture(Gdx.files.internal("textures/cliff_rock.png"));
            
            // Set wrap to Repeat for tiling
            stoneTexture.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
            woodTexture.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
            grassTexture.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
            cliffTexture.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
            
            // Set filter to Nearest for pixel art look
            stoneTexture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
            woodTexture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
            grassTexture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
            cliffTexture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
            
        } catch (Exception e) {
            Gdx.app.error("GridRenderer", "Failed to load textures", e);
        }

        // Create procedural textures for others
        dirtTexture = createColorTexture(new Color(0.6f, 0.4f, 0.2f, 1));
        waterTexture = createColorTexture(new Color(0.2f, 0.4f, 0.8f, 0.8f));
        
        if (stoneTexture == null) stoneTexture = createColorTexture(Color.GRAY);
        if (woodTexture == null) woodTexture = createColorTexture(Color.BROWN);
        if (grassTexture == null) grassTexture = createColorTexture(Color.GREEN);
        if (cliffTexture == null) cliffTexture = createColorTexture(Color.DARK_GRAY);

        whiteWallTexture = stoneTexture; 
        blackWallTexture = stoneTexture; 
        
        whiteFloorTexture = createColorTexture(new Color(0.85f, 0.85f, 0.78f, 1));
        blackFloorTexture = createColorTexture(new Color(0.25f, 0.25f, 0.25f, 1));
        
        doorTexture = createColorTexture(new Color(0.4f, 0.25f, 0.1f, 1));
        windowTexture = createColorTexture(new Color(0.6f, 0.8f, 1.0f, 0.6f));
        defaultTexture = createColorTexture(Color.WHITE);
    }

    private Texture createColorTexture(Color color) {
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(color);
        pixmap.fill();
        Texture texture = new Texture(pixmap);
        pixmap.dispose();
        return texture;
    }

    private void create3DModels() {
        environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.4f, 0.4f, 0.4f, 1f));
        environment.add(new DirectionalLight().set(0.8f, 0.8f, 0.8f, -1f, -0.8f, -0.2f));
        
        // Add fog color for N64-style fog effect (light gray/white)
        environment.set(new ColorAttribute(ColorAttribute.Fog, 0.85f, 0.87f, 0.9f, 1f));

        blockModels = new HashMap<>();
        blockInstances = new HashMap<>();
        ModelBuilder modelBuilder = new ModelBuilder();

        for (BlockState state : BlockState.values()) {
            if (state == BlockState.AIR) continue;

            Texture texture = getTextureForBlock(state);
            Color color = getColorForBlock(state); 
            
            boolean useTiling = (state == BlockState.GRASS || 
                                 state == BlockState.STONE || 
                                 state == BlockState.CASTLE_WHITE || 
                                 state == BlockState.CASTLE_BLACK ||
                                 state == BlockState.MOUNTAIN_ROCK);

            List<Model> models = new ArrayList<>();
            List<ModelInstance> instances = new ArrayList<>();

            if (useTiling) {
                // Create 4 variations for 2x2 tiling
                // For castle walls, use larger scale (0.25) to make stones appear bigger
                // For grass, keep original scale (0.5)
                float scale = (state == BlockState.CASTLE_WHITE || state == BlockState.CASTLE_BLACK) ? 0.25f : 0.5f;
                float offset = (state == BlockState.CASTLE_WHITE || state == BlockState.CASTLE_BLACK) ? 0.25f : 0.5f;
                
                for (int i = 0; i < 4; i++) {
                    TextureAttribute attr = TextureAttribute.createDiffuse(texture);
                    attr.scaleU = scale;
                    attr.scaleV = scale;
                    attr.offsetU = (i % 2) * offset;
                    attr.offsetV = (i / 2) * offset;
                    
                    Material material = new Material(attr);
                    // Note: ColorAttribute might conflict or mix depending on shader. 
                    // For now, we rely on texture. If we need tint, we can try adding ColorAttribute.
                    // But standard shader multiplies them?
                    if (!color.equals(Color.WHITE)) {
                         // material.set(ColorAttribute.createDiffuse(color)); 
                    }

                    Model model = modelBuilder.createBox(1f, 1f, 1f, 
                        material,
                        VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal | VertexAttributes.Usage.TextureCoordinates);
                    models.add(model);
                    instances.add(new ModelInstance(model));
                }
            } else {
                // Standard 1x1
                Material material = new Material(TextureAttribute.createDiffuse(texture));
                Model model = modelBuilder.createBox(1f, 1f, 1f, 
                    material,
                    VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal | VertexAttributes.Usage.TextureCoordinates);
                models.add(model);
                instances.add(new ModelInstance(model));
            }
            
            blockModels.put(state, models);
            blockInstances.put(state, instances);
        }
    }

    private Texture getTextureForBlock(BlockState block) {
        switch (block) {
            case GRASS: return grassTexture;
            case DIRT: return dirtTexture;
            case STONE: return stoneTexture;
            case WATER: return waterTexture;
            case CASTLE_WHITE: return whiteWallTexture;
            case CASTLE_BLACK: return blackWallTexture;
            case CASTLE_WHITE_FLOOR: return whiteFloorTexture;
            case CASTLE_BLACK_FLOOR: return blackFloorTexture;
            case CASTLE_WHITE_STAIR: return woodTexture;
            case CASTLE_BLACK_STAIR: return woodTexture;
            case DOOR: return doorTexture;
            case WINDOW: return windowTexture;
            case MOUNTAIN_ROCK: return cliffTexture;
            default: return defaultTexture;
        }
    }
    
    private Color getColorForBlock(BlockState block) {
         switch (block) {
            case CASTLE_WHITE: return new Color(0.9f, 0.9f, 0.9f, 1f);
            case CASTLE_BLACK: return new Color(0.3f, 0.3f, 0.3f, 1f);
            case CASTLE_BLACK_STAIR: return new Color(0.6f, 0.5f, 0.4f, 1f);
            case MOUNTAIN_ROCK: return new Color(0.6f, 0.6f, 0.65f, 1f);
            default: return Color.WHITE;
        }
    }

    public void render3D(GridWorld world, Camera camera) {
        modelBatch.begin(camera);
        
        // Reduced draw distance for better performance
        int range = 150; // Draw distance
        float fogStart = range * 0.6f; // Fog starts at 60% of draw distance
        float fogEnd = range * 0.9f;   // Full fog at 90% of draw distance
        
        int px = (int)camera.position.x;
        int py = (int)camera.position.y;
        int pz = (int)camera.position.z;
        
        int minX = Math.max(0, px - range);
        int maxX = Math.min(world.getWidth(), px + range);
        int minY = Math.max(0, py - range);
        int maxY = Math.min(world.getDepth(), py + range);
        int minZ = Math.max(0, pz - range);
        int maxZ = Math.min(world.getHeight(), pz + range);

        // Render blocks in order from back to front for better fog effect
        for (int x = minX; x < maxX; x++) {
            for (int y = minY; y < maxY; y++) {
                for (int z = minZ; z < maxZ; z++) {
                    BlockState block = world.getBlock(x, y, z);
                    
                    // Skip AIR blocks (major optimization)
                    if (block == BlockState.AIR) continue;
                    
                    // Occlusion Culling: Skip blocks completely surrounded by opaque blocks
                    // This is critical for performance with large solid structures like mountains
                    if (world.isOpaque(world.getBlock(x + 1, y, z)) &&
                        world.isOpaque(world.getBlock(x - 1, y, z)) &&
                        world.isOpaque(world.getBlock(x, y + 1, z)) &&
                        world.isOpaque(world.getBlock(x, y - 1, z)) &&
                        world.isOpaque(world.getBlock(x, y, z + 1)) &&
                        world.isOpaque(world.getBlock(x, y, z - 1))) {
                        continue;
                    }
                    
                    // Calculate distance for fog and frustum culling
                    float dx = x + 0.5f - camera.position.x;
                    float dy = y + 0.5f - camera.position.y;
                    float dz = z + 0.5f - camera.position.z;
                    float distSquared = dx*dx + dy*dy + dz*dz;
                    
                    // Early distance cull (before expensive frustum check)
                    if (distSquared > range * range) continue;
                    
                    // Frustum culling - check if block is in camera view
                    // Using a simple sphere test (fast approximation)
                    if (!camera.frustum.sphereInFrustum(x + 0.5f, y + 0.5f, z + 0.5f, 0.87f)) {
                        continue;
                    }
                    
                    List<ModelInstance> instances = blockInstances.get(block);
                    if (instances != null && !instances.isEmpty()) {
                        ModelInstance instance;
                        
                        if (instances.size() == 4) {
                            // Select variant for 2x2 tiling
                            int idx = 0;
                            if (block == BlockState.GRASS) {
                                idx = (x % 2) + 2 * (y % 2);
                            } else {
                                idx = ((x + y) % 2) + 2 * (z % 2);
                            }
                            instance = instances.get(idx);
                        } else {
                            instance = instances.get(0);
                        }
                        
                        instance.transform.setToTranslation(x + 0.5f, y + 0.5f, z + 0.5f);
                        
                        // Apply N64-style fog by modifying environment for this block
                        // Note: This is simplified - ideally we'd use a shader for true per-pixel fog
                        float dist = (float)Math.sqrt(distSquared);
                        if (dist > fogStart) {
                            // Calculate fog factor (0 = no fog, 1 = full fog)
                            float fogFactor = Math.min(1.0f, (dist - fogStart) / (fogEnd - fogStart));
                            
                            // Skip rendering if completely fogged (optimization)
                            if (fogFactor >= 0.98f) continue;
                        }
                        
                        modelBatch.render(instance, environment);
                    }
                }
            }
        }
        
        modelBatch.end();
    }

    // ... (Existing 2D render methods: renderLayer, renderSideSlice, etc. can be removed or kept unused)
    // Since we reverted DualViewScreen, we can remove them or keep them as legacy.
    // I'll keep getBlockColor and getShapeRenderer as they are needed.

    public Color getBlockColor(BlockState block) {
        switch (block) {
            case GRASS: return new Color(0.3f, 0.7f, 0.2f, 1);
            case DIRT: return new Color(0.6f, 0.4f, 0.2f, 1);
            case STONE: return new Color(0.5f, 0.5f, 0.5f, 1);
            case WATER: return new Color(0.2f, 0.4f, 0.8f, 0.8f);
            case CASTLE_WHITE: return new Color(0.95f, 0.95f, 0.95f, 1);
            case CASTLE_BLACK: return new Color(0.15f, 0.15f, 0.15f, 1);
            case CASTLE_WHITE_FLOOR: return new Color(0.85f, 0.85f, 0.78f, 1);
            case CASTLE_BLACK_FLOOR: return new Color(0.25f, 0.25f, 0.25f, 1);
            case CASTLE_WHITE_STAIR: return new Color(0.98f, 0.78f, 0.35f, 1);
            case CASTLE_BLACK_STAIR: return new Color(0.65f, 0.4f, 0.2f, 1);
            case DOOR: return new Color(0.4f, 0.25f, 0.1f, 1);
            case WINDOW: return new Color(0.6f, 0.8f, 1.0f, 0.6f);
            case MOUNTAIN_ROCK: return new Color(0.4f, 0.4f, 0.45f, 1);
            default: return Color.WHITE;
        }
    }

    public ShapeRenderer getShapeRenderer() {
        return shapeRenderer;
    }
    
    public void dispose() {
        batch.dispose();
        shapeRenderer.dispose();
        modelBatch.dispose();
        
        if (stoneTexture != null) stoneTexture.dispose();
        if (woodTexture != null) woodTexture.dispose();
        if (grassTexture != null) grassTexture.dispose();
        if (dirtTexture != null) dirtTexture.dispose();
        if (waterTexture != null) waterTexture.dispose();
        if (whiteFloorTexture != null) whiteFloorTexture.dispose();
        if (blackFloorTexture != null) blackFloorTexture.dispose();
        if (doorTexture != null) doorTexture.dispose();
        if (windowTexture != null) windowTexture.dispose();
        if (defaultTexture != null) defaultTexture.dispose();
        if (cliffTexture != null) cliffTexture.dispose();
        
        for (List<Model> models : blockModels.values()) {
            for (Model model : models) {
                model.dispose();
            }
        }
    }
    
    // Removed renderLayer and renderSideSlice as they are no longer used by DualViewScreen
}
