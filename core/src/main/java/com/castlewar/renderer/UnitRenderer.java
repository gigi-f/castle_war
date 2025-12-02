package com.castlewar.renderer;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import com.castlewar.entity.Archer;
import com.castlewar.entity.Assassin;
import com.castlewar.entity.Cavalry;
import com.castlewar.entity.Entity;
import com.castlewar.entity.Guard;
import com.castlewar.entity.Infantry;
import com.castlewar.entity.King;
import com.castlewar.entity.Trebuchet;
import com.castlewar.entity.BatteringRam;
import com.castlewar.entity.Unit;
import com.castlewar.entity.Team;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders thicker voxel-inspired unit meshes with procedurally generated textures.
 * Textures lean on neutral clothing with bold, team-colored headwear for readability.
 */
public class UnitRenderer implements Disposable {
    private static final float MODEL_WIDTH = 1.2f;   // X axis (east/west)
    private static final float MODEL_LENGTH = 1.2f;  // Y axis (north/south)
    private static final float MODEL_HEIGHT = 3.4f;  // Z axis (vertical)

    private static final Color BASE_TINT = new Color(Color.WHITE);
    private static final Color FLASH_TINT = new Color(1f, 0.1f, 0.05f, 1f);
    private static final Color FLASH_EMISSIVE = new Color(1.5f, 0.5f, 0.3f, 1f);
    private static final float CORPSE_SINK = 0.15f;

    private final ModelBatch modelBatch = new ModelBatch();
    private final Map<ModelKey, Model> models = new EnumMap<>(ModelKey.class);
    private final IdentityHashMap<Entity, ModelInstance> activeInstances = new IdentityHashMap<>();
    private final IdentityHashMap<Entity, ModelInstance> glowInstances = new IdentityHashMap<>();
    private final List<Texture> allocatedTextures = new ArrayList<>();
    private final Color workingColor = new Color(Color.WHITE);
    private final Color glowWorkingColor = new Color(1f, 0.1f, 0.05f, 0.8f);
    private final Vector3 workingVector = new Vector3();
    private final Quaternion workingRotation = new Quaternion();
    private Model glowModel;

    private enum UnitType { KING, GUARD, ASSASSIN, INFANTRY, ARCHER, CAVALRY, TREBUCHET, BATTERING_RAM, OTHER }

    private enum ModelKey {
        KING_WHITE(UnitType.KING, Team.WHITE),
        KING_BLACK(UnitType.KING, Team.BLACK),
        GUARD_WHITE(UnitType.GUARD, Team.WHITE),
        GUARD_BLACK(UnitType.GUARD, Team.BLACK),
        ASSASSIN_WHITE(UnitType.ASSASSIN, Team.WHITE),
        ASSASSIN_BLACK(UnitType.ASSASSIN, Team.BLACK),
        INFANTRY_WHITE(UnitType.INFANTRY, Team.WHITE),
        INFANTRY_BLACK(UnitType.INFANTRY, Team.BLACK),
        ARCHER_WHITE(UnitType.ARCHER, Team.WHITE),
        ARCHER_BLACK(UnitType.ARCHER, Team.BLACK),
        CAVALRY_WHITE(UnitType.CAVALRY, Team.WHITE),
        CAVALRY_BLACK(UnitType.CAVALRY, Team.BLACK),
        TREBUCHET_WHITE(UnitType.TREBUCHET, Team.WHITE),
        TREBUCHET_BLACK(UnitType.TREBUCHET, Team.BLACK),
        BATTERING_RAM_WHITE(UnitType.BATTERING_RAM, Team.WHITE),
        BATTERING_RAM_BLACK(UnitType.BATTERING_RAM, Team.BLACK),
        OTHER_WHITE(UnitType.OTHER, Team.WHITE),
        OTHER_BLACK(UnitType.OTHER, Team.BLACK);

        final UnitType type;
        final Team team;

        ModelKey(UnitType type, Team team) {
            this.type = type;
            this.team = team;
        }

        static ModelKey of(UnitType type, Team team) {
            for (ModelKey key : values()) {
                if (key.type == type && key.team == team) {
                    return key;
                }
            }
            // Fallback to generic entry for the requested team
            return team == Team.WHITE ? OTHER_WHITE : OTHER_BLACK;
        }
    }

    public UnitRenderer() {
        buildModels();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // VEHICLE-SPECIFIC DIMENSIONS
    // ═══════════════════════════════════════════════════════════════════════════
    
    // Cavalry (horse + rider) - wider and longer than humanoid
    private static final float CAVALRY_WIDTH = 1.6f;
    private static final float CAVALRY_LENGTH = 2.8f;
    private static final float CAVALRY_HEIGHT = 3.8f;
    
    // Trebuchet - large siege engine
    private static final float TREBUCHET_WIDTH = 3.5f;
    private static final float TREBUCHET_LENGTH = 4.5f;
    private static final float TREBUCHET_HEIGHT = 5.0f;
    
    // Battering Ram - long and low
    private static final float RAM_WIDTH = 2.5f;
    private static final float RAM_LENGTH = 6.0f;
    private static final float RAM_HEIGHT = 3.2f;

    private void buildModels() {
        ModelBuilder builder = new ModelBuilder();
        for (ModelKey key : ModelKey.values()) {
            Pixmap pixmap = generateTexture(key.type, key.team);
            Texture texture = new Texture(pixmap);
            texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
            pixmap.dispose();
            allocatedTextures.add(texture);

            Material material = new Material(
                ColorAttribute.createDiffuse(BASE_TINT),
                ColorAttribute.createEmissive(Color.BLACK),
                TextureAttribute.createDiffuse(texture)
            );
            long attrs = VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal | VertexAttributes.Usage.TextureCoordinates;
            
            // Create different model shapes based on unit type
            Model model = createModelForType(builder, key.type, material, attrs);
            models.put(key, model);
        }

        BlendingAttribute glowBlend = new BlendingAttribute();
        glowBlend.blended = true;
        glowBlend.sourceFunction = GL20.GL_SRC_ALPHA;
        glowBlend.destFunction = GL20.GL_ONE;
        glowBlend.opacity = 1f;
        Material glowMaterial = new Material(
            ColorAttribute.createDiffuse(glowWorkingColor),
            glowBlend
        );
        long glowAttrs = VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal;
        glowModel = builder.createSphere(MODEL_WIDTH * 2.4f, MODEL_LENGTH * 2.4f, MODEL_HEIGHT * 1.4f, 16, 16, glowMaterial, glowAttrs);
    }
    
    /**
     * Creates a model with appropriate shape for the unit type.
     * Vehicles get distinctive shapes; humanoids get standard box.
     */
    private Model createModelForType(ModelBuilder builder, UnitType type, Material material, long attrs) {
        return switch (type) {
            case CAVALRY -> builder.createBox(CAVALRY_WIDTH, CAVALRY_LENGTH, CAVALRY_HEIGHT, material, attrs);
            case TREBUCHET -> builder.createBox(TREBUCHET_WIDTH, TREBUCHET_LENGTH, TREBUCHET_HEIGHT, material, attrs);
            case BATTERING_RAM -> builder.createBox(RAM_WIDTH, RAM_LENGTH, RAM_HEIGHT, material, attrs);
            default -> builder.createBox(MODEL_WIDTH, MODEL_LENGTH, MODEL_HEIGHT, material, attrs);
        };
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HAT COLORS BY ROLE (same for both teams)
    // ═══════════════════════════════════════════════════════════════════════════
    private static final Color HAT_KING = new Color(1f, 0.84f, 0f, 1f);           // Gold
    private static final Color HAT_GUARD = new Color(0.9f, 0.1f, 0.1f, 1f);       // Red
    private static final Color HAT_ASSASSIN = new Color(0.5f, 0f, 0.5f, 1f);      // Purple
    private static final Color HAT_INFANTRY = new Color(0.2f, 0.6f, 0.2f, 1f);    // Green
    private static final Color HAT_ARCHER = new Color(0f, 0.6f, 0.8f, 1f);        // Cyan/Teal
    private static final Color HAT_CAVALRY = new Color(1f, 0.5f, 0f, 1f);         // Orange
    private static final Color HAT_TREBUCHET = new Color(0.4f, 0.26f, 0.13f, 1f); // Brown
    private static final Color HAT_BATTERING_RAM = new Color(0.3f, 0.3f, 0.3f, 1f); // Dark Gray
    private static final Color HAT_OTHER = new Color(0.5f, 0.5f, 0.5f, 1f);       // Gray

    private Pixmap generateTexture(UnitType type, Team team) {
        Pixmap pixmap = new Pixmap(64, 64, Pixmap.Format.RGBA8888);
        
        // Team color: WHITE team = white body, BLACK team = black body
        Color bodyColor = team == Team.WHITE 
            ? new Color(0.95f, 0.95f, 0.95f, 1f)  // Off-white
            : new Color(0.1f, 0.1f, 0.1f, 1f);   // Near-black
        
        // Get hat color based on role
        Color hatColor = getHatColorForType(type);
        
        // Fill body with team color
        pixmap.setColor(bodyColor);
        pixmap.fill();
        
        // Draw hat on top portion (top 1/4 of texture)
        drawSimpleHat(pixmap, hatColor, type);
        
        return pixmap;
    }
    
    /**
     * Returns the hat color for a given unit type.
     * Hat colors are role-based and the same for both teams.
     */
    private Color getHatColorForType(UnitType type) {
        return switch (type) {
            case KING -> HAT_KING;
            case GUARD -> HAT_GUARD;
            case ASSASSIN -> HAT_ASSASSIN;
            case INFANTRY -> HAT_INFANTRY;
            case ARCHER -> HAT_ARCHER;
            case CAVALRY -> HAT_CAVALRY;
            case TREBUCHET -> HAT_TREBUCHET;
            case BATTERING_RAM -> HAT_BATTERING_RAM;
            default -> HAT_OTHER;
        };
    }
    
    /**
     * Draws a simple hat/marker on the top portion of the texture.
     * Different shapes for different unit types for visual variety.
     */
    private void drawSimpleHat(Pixmap pixmap, Color hatColor, UnitType type) {
        pixmap.setColor(hatColor);
        
        switch (type) {
            case KING -> {
                // Crown shape - tall with points
                pixmap.fillRectangle(8, 48, 48, 16);  // Base
                // Crown points
                pixmap.fillRectangle(8, 56, 8, 8);
                pixmap.fillRectangle(24, 56, 16, 8);
                pixmap.fillRectangle(48, 56, 8, 8);
            }
            case GUARD -> {
                // Helmet with plume
                pixmap.fillRectangle(4, 48, 56, 12);
                // Plume on top
                pixmap.fillRectangle(24, 56, 16, 8);
            }
            case ASSASSIN -> {
                // Hood shape - pointed
                pixmap.fillRectangle(12, 48, 40, 10);
                // Hood point
                pixmap.fillRectangle(26, 54, 12, 10);
            }
            case INFANTRY -> {
                // Simple helmet - rounded top
                pixmap.fillRectangle(8, 48, 48, 14);
                pixmap.fillRectangle(16, 58, 32, 6);
            }
            case ARCHER -> {
                // Hood/cap with feather accent
                pixmap.fillRectangle(8, 48, 48, 12);
                // Feather
                pixmap.fillRectangle(48, 52, 8, 12);
            }
            case CAVALRY -> {
                // Cavalry helmet with crest
                pixmap.fillRectangle(4, 46, 56, 14);
                // Crest
                pixmap.fillRectangle(26, 56, 12, 8);
            }
            case TREBUCHET, BATTERING_RAM -> {
                // Banner/flag marker for vehicles
                pixmap.fillRectangle(24, 48, 16, 16);
                // Flag pole
                pixmap.setColor(new Color(0.4f, 0.3f, 0.2f, 1f));
                pixmap.fillRectangle(30, 40, 4, 24);
                pixmap.setColor(hatColor);
            }
            default -> {
                // Simple band
                pixmap.fillRectangle(0, 52, 64, 12);
            }
        }
    }

    public void render(Camera camera, List<Entity> entities) {
        activeInstances.keySet().retainAll(entities);
        glowInstances.keySet().retainAll(entities);
        modelBatch.begin(camera);
        for (Entity entity : entities) {
            if (!(entity instanceof Unit)) {
                continue;
            }
            Unit unit = (Unit) entity;
            ModelInstance instance = activeInstances.get(entity);
            if (instance == null) {
                Model model = models.get(ModelKey.of(getUnitType(entity), entity.getTeam()));
                if (model == null) continue;
                instance = new ModelInstance(model);
                for (int i = 0; i < instance.materials.size; i++) {
                    instance.materials.set(i, new Material(instance.materials.get(i)));
                }
                activeInstances.put(entity, instance);
            }
            updateTransform(unit, instance);
            applyTint(unit, instance);
            modelBatch.render(instance);

            float flash = unit.getDamageFlashAlpha();
            if (flash > 0.05f) {
                ModelInstance glowInstance = glowInstances.get(entity);
                if (glowInstance == null && glowModel != null) {
                    glowInstance = new ModelInstance(glowModel);
                    for (int i = 0; i < glowInstance.materials.size; i++) {
                        glowInstance.materials.set(i, new Material(glowInstance.materials.get(i)));
                    }
                    glowInstances.put(entity, glowInstance);
                }
                if (glowInstance != null) {
                    renderGlow(unit, flash, glowInstance);
                    modelBatch.render(glowInstance);
                }
            }
        }
        modelBatch.end();
    }

    private void renderGlow(Unit unit, float flash, ModelInstance glowInstance) {
        float intensity = MathUtils.clamp(flash, 0f, 1f);
        float radiusScale = MathUtils.lerp(1.1f, 1.6f, intensity);
        glowInstance.transform.idt();
        glowInstance.transform.translate(unit.getX(), unit.getY(), unit.getZ() + (MODEL_HEIGHT * 0.4f));
        glowInstance.transform.scale(radiusScale, radiusScale, radiusScale * 0.8f);

        for (Material material : glowInstance.materials) {
            ColorAttribute diffuse = (ColorAttribute) material.get(ColorAttribute.Diffuse);
            if (diffuse != null) {
                diffuse.color.set(glowWorkingColor);
                diffuse.color.a = intensity * 0.85f;
            }
            BlendingAttribute blend = (BlendingAttribute) material.get(BlendingAttribute.Type);
            if (blend != null) {
                blend.opacity = intensity;
            }
        }
    }

    private void updateTransform(Unit unit, ModelInstance instance) {
        instance.transform.idt();
        if (unit.isCorpse()) {
            Vector3 forward = unit.getCorpseForward(workingVector).nor();
            if (forward.isZero(0.001f)) {
                forward.set(0, 1, 0);
            }
            workingRotation.setFromCross(Vector3.Z, forward);
            float sink = MathUtils.clamp(unit.getCorpseTimer() / unit.getCorpseLifetime(), 0f, 1f) * CORPSE_SINK;
            instance.transform.rotate(workingRotation);
            instance.transform.setTranslation(unit.getX(), unit.getY(), unit.getZ() + (MODEL_WIDTH * 0.5f) - sink);
        } else {
            // Rotate based on unit facing direction (add 180 degrees to flip face to front)
            Vector3 facing = unit.getFacing();
            // Start with 180-degree rotation around Z to put face on front
            workingRotation.setEulerAngles(0, 0, 180);
            instance.transform.rotate(workingRotation);
            
            if (!facing.isZero(0.001f)) {
                // Then rotate to face the direction of movement
                workingRotation.setFromCross(new Vector3(1, 0, 0), facing);
                instance.transform.rotate(workingRotation);
            }
            instance.transform.setTranslation(unit.getX(), unit.getY(), unit.getZ() + MODEL_HEIGHT / 2f);
        }
    }

    private void applyTint(Unit unit, ModelInstance instance) {
        float flash = unit.getDamageFlashAlpha();
        float corpseFade = unit.isCorpse()
            ? MathUtils.clamp(1f - (unit.getCorpseTimer() / unit.getCorpseLifetime()), 0.1f, 1f)
            : 1f;

        float flashWeight = flash > 0.01f ? MathUtils.clamp(flash * 2.0f, 0f, 1f) : 0f;

        for (Material material : instance.materials) {
            ColorAttribute diffuse = (ColorAttribute) material.get(ColorAttribute.Diffuse);
            ColorAttribute emissive = (ColorAttribute) material.get(ColorAttribute.Emissive);
            if (diffuse != null) {
                if (flashWeight > 0f) {
                    workingColor.set(BASE_TINT).lerp(FLASH_TINT, flashWeight);
                } else {
                    workingColor.set(BASE_TINT);
                }
                workingColor.mul(corpseFade);
                diffuse.color.set(workingColor);
            }
            if (emissive != null) {
                if (flashWeight > 0f) {
                    emissive.color.set(FLASH_EMISSIVE).mul(flashWeight);
                } else {
                    emissive.color.set(Color.BLACK);
                }
            }
        }
    }

    private UnitType getUnitType(Entity entity) {
        if (entity instanceof King) return UnitType.KING;
        if (entity instanceof Guard) return UnitType.GUARD;
        if (entity instanceof Assassin) return UnitType.ASSASSIN;
        if (entity instanceof Infantry) return UnitType.INFANTRY;
        if (entity instanceof Archer) return UnitType.ARCHER;
        if (entity instanceof Cavalry) return UnitType.CAVALRY;
        if (entity instanceof Trebuchet) return UnitType.TREBUCHET;
        if (entity instanceof BatteringRam) return UnitType.BATTERING_RAM;
        return UnitType.OTHER;
    }

    @Override
    public void dispose() {
        modelBatch.dispose();
        for (Model model : models.values()) {
            model.dispose();
        }
        models.clear();
        if (glowModel != null) {
            glowModel.dispose();
            glowModel = null;
        }
        for (Texture texture : allocatedTextures) {
            texture.dispose();
        }
        allocatedTextures.clear();
        activeInstances.clear();
        glowInstances.clear();
    }
}
