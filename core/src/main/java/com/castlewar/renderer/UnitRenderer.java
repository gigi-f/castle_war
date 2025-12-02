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
            // LibGDX's createBox takes (width, height, depth) == (X, Y, Z)
            Model model = builder.createBox(MODEL_WIDTH, MODEL_LENGTH, MODEL_HEIGHT, material, attrs);
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

    private Pixmap generateTexture(UnitType type, Team team) {
        Pixmap pixmap = new Pixmap(64, 64, Pixmap.Format.RGBA8888);
        Color robeColor = type == UnitType.ASSASSIN ? new Color(0.18f, 0.18f, 0.2f, 1f) : new Color(0.7f, 0.7f, 0.72f, 1f);
        Color accentColor = team == Team.WHITE ? new Color(0.2f, 0.6f, 1f, 1f) : new Color(1f, 0.25f, 0.25f, 1f);
        Color hatColor = team == Team.WHITE ? new Color(0.95f, 0.85f, 0.1f, 1f) : new Color(0.95f, 0.3f, 0.8f, 1f);

        pixmap.setColor(robeColor);
        pixmap.fill();

        switch (type) {
            case KING:
                drawKingTexture(pixmap, accentColor, hatColor);
                break;
            case GUARD:
                drawGuardTexture(pixmap, accentColor, hatColor);
                break;
            case ASSASSIN:
                drawAssassinTexture(pixmap, accentColor, hatColor);
                break;
            case INFANTRY:
                drawInfantryTexture(pixmap, accentColor, hatColor);
                break;
            case ARCHER:
                drawArcherTexture(pixmap, accentColor, hatColor);
                break;
            case CAVALRY:
                drawCavalryTexture(pixmap, accentColor, hatColor);
                break;
            case TREBUCHET:
                drawTrebuchetTexture(pixmap, accentColor, hatColor);
                break;
            case BATTERING_RAM:
                drawBatteringRamTexture(pixmap, accentColor, hatColor);
                break;
            default:
                drawGenericTexture(pixmap, accentColor, hatColor);
        }
        return pixmap;
    }

    private void drawKingTexture(Pixmap pixmap, Color jewelColor, Color hatColor) {
        // Robe trim
        pixmap.setColor(new Color(0.4f, 0.1f, 0.05f, 1f));
        pixmap.fillRectangle(0, 0, 64, 20);
        pixmap.fillRectangle(0, 44, 64, 20);

        // Belt
        pixmap.setColor(new Color(0.25f, 0.2f, 0.15f, 1f));
        pixmap.fillRectangle(0, 30, 64, 6);

        // Crown base
        pixmap.setColor(hatColor);
        pixmap.fillRectangle(0, 48, 64, 16);
        pixmap.setColor(Color.GOLD);
        pixmap.fillRectangle(0, 56, 64, 8);

        // Crown spikes / jewels
        pixmap.setColor(hatColor);
        for (int i = 0; i < 4; i++) {
            int start = 4 + i * 14;
            pixmap.fillRectangle(start, 56, 6, 8);
        }
        pixmap.setColor(jewelColor);
        pixmap.fillCircle(32, 60, 4);

        // Face panel (skin tone on top-front)
        pixmap.setColor(new Color(0.93f, 0.84f, 0.7f, 1f));
        pixmap.fillRectangle(20, 32, 24, 16);
    }

    private void drawGuardTexture(Pixmap pixmap, Color plumeColor, Color hatColor) {
        // Armor panels
        pixmap.setColor(new Color(0.8f, 0.8f, 0.85f, 1f));
        pixmap.fillRectangle(0, 12, 64, 40);

        // Chest plate shading
        pixmap.setColor(new Color(0.6f, 0.6f, 0.65f, 1f));
        pixmap.fillRectangle(10, 18, 44, 28);

        // Belt and straps
        pixmap.setColor(new Color(0.2f, 0.2f, 0.25f, 1f));
        pixmap.fillRectangle(0, 28, 64, 4);
        pixmap.fillRectangle(28, 12, 8, 40);

        // Helmet highlight
        pixmap.setColor(new Color(0.9f, 0.9f, 0.95f, 1f));
        pixmap.fillRectangle(0, 44, 64, 8);

        // Plume (vibrant hat)
        pixmap.setColor(plumeColor);
        pixmap.fillRectangle(4, 52, 56, 12);
        pixmap.setColor(hatColor);
        pixmap.fillRectangle(26, 52, 12, 12);

        // Face panel (skin tone on top-front)
        pixmap.setColor(new Color(0.93f, 0.84f, 0.7f, 1f));
        pixmap.fillRectangle(20, 32, 24, 16);
    }

    private void drawAssassinTexture(Pixmap pixmap, Color accentColor, Color hatColor) {
        // Hood shadow
        pixmap.setColor(new Color(0.08f, 0.08f, 0.1f, 1f));
        pixmap.fillRectangle(0, 36, 64, 28);

        // Hood trim as vibrant hat band
        pixmap.setColor(hatColor);
        pixmap.fillRectangle(0, 52, 64, 8);
        pixmap.setColor(accentColor);
        pixmap.fillRectangle(16, 48, 32, 6);

        // Mask slit
        pixmap.setColor(new Color(0.2f, 0, 0, 1f));
        pixmap.fillRectangle(18, 44, 28, 4);

        // Chest daggers
        pixmap.setColor(accentColor);
        pixmap.fillRectangle(10, 20, 8, 24);
        pixmap.fillRectangle(46, 20, 8, 24);

        // Face panel (skin tone on top-front, showing through mask)
        pixmap.setColor(new Color(0.93f, 0.84f, 0.7f, 1f));
        pixmap.fillRectangle(20, 32, 24, 16);
    }

    private void drawGenericTexture(Pixmap pixmap, Color accentColor, Color hatColor) {
        pixmap.setColor(accentColor);
        pixmap.fillRectangle(0, 48, 64, 12);
        pixmap.setColor(hatColor);
        pixmap.fillRectangle(20, 52, 24, 12);
    }
    
    private void drawInfantryTexture(Pixmap pixmap, Color accentColor, Color hatColor) {
        // Tunic/gambeson base (already filled with robe color)
        
        // Chainmail vest
        pixmap.setColor(new Color(0.5f, 0.5f, 0.55f, 1f));
        pixmap.fillRectangle(8, 16, 48, 28);
        
        // Chainmail pattern (dots)
        pixmap.setColor(new Color(0.65f, 0.65f, 0.7f, 1f));
        for (int row = 0; row < 5; row++) {
            for (int col = 0; col < 8; col++) {
                int x = 12 + col * 5 + (row % 2) * 2;
                int y = 18 + row * 5;
                pixmap.fillCircle(x, y, 1);
            }
        }
        
        // Belt
        pixmap.setColor(new Color(0.3f, 0.2f, 0.1f, 1f));
        pixmap.fillRectangle(0, 30, 64, 5);
        
        // Belt buckle
        pixmap.setColor(accentColor);
        pixmap.fillRectangle(28, 30, 8, 5);
        
        // Simple helmet
        pixmap.setColor(new Color(0.6f, 0.6f, 0.65f, 1f));
        pixmap.fillRectangle(4, 48, 56, 16);
        
        // Helmet nose guard
        pixmap.setColor(new Color(0.5f, 0.5f, 0.55f, 1f));
        pixmap.fillRectangle(28, 44, 8, 16);
        
        // Helmet band with team color
        pixmap.setColor(hatColor);
        pixmap.fillRectangle(0, 56, 64, 4);
        
        // Face panel (skin tone)
        pixmap.setColor(new Color(0.93f, 0.84f, 0.7f, 1f));
        pixmap.fillRectangle(20, 36, 24, 12);
    }
    
    private void drawArcherTexture(Pixmap pixmap, Color accentColor, Color hatColor) {
        // Leather tunic base (greenish-brown for camouflage)
        pixmap.setColor(new Color(0.35f, 0.42f, 0.28f, 1f));
        pixmap.fillRectangle(0, 0, 64, 48);
        
        // Leather armor panels
        pixmap.setColor(new Color(0.45f, 0.35f, 0.25f, 1f));
        pixmap.fillRectangle(8, 14, 48, 24);
        
        // Leather stitching pattern
        pixmap.setColor(new Color(0.3f, 0.22f, 0.15f, 1f));
        for (int i = 0; i < 6; i++) {
            int x = 12 + i * 7;
            pixmap.drawLine(x, 16, x, 34);
        }
        
        // Quiver strap (diagonal across chest)
        pixmap.setColor(new Color(0.5f, 0.4f, 0.3f, 1f));
        for (int i = 0; i < 8; i++) {
            pixmap.fillRectangle(6 + i * 6, 18 + i * 2, 8, 4);
        }
        
        // Belt
        pixmap.setColor(new Color(0.35f, 0.28f, 0.18f, 1f));
        pixmap.fillRectangle(0, 32, 64, 5);
        
        // Belt buckle
        pixmap.setColor(accentColor);
        pixmap.fillRectangle(28, 32, 8, 5);
        
        // Hood (pulled back)
        pixmap.setColor(new Color(0.3f, 0.35f, 0.25f, 1f));
        pixmap.fillRectangle(0, 48, 64, 16);
        
        // Hood trim with team color
        pixmap.setColor(hatColor);
        pixmap.fillRectangle(0, 56, 64, 8);
        
        // Feather accent on hood
        pixmap.setColor(accentColor);
        pixmap.fillRectangle(48, 52, 4, 12);
        pixmap.fillRectangle(52, 54, 3, 8);
        
        // Face panel (skin tone)
        pixmap.setColor(new Color(0.93f, 0.84f, 0.7f, 1f));
        pixmap.fillRectangle(20, 40, 24, 12);
    }
    
    private void drawCavalryTexture(Pixmap pixmap, Color accentColor, Color hatColor) {
        // Horse body (lower section - brown/chestnut)
        pixmap.setColor(new Color(0.55f, 0.35f, 0.2f, 1f));
        pixmap.fillRectangle(0, 0, 64, 28);
        
        // Horse mane
        pixmap.setColor(new Color(0.2f, 0.15f, 0.1f, 1f));
        pixmap.fillRectangle(0, 20, 64, 8);
        
        // Saddle
        pixmap.setColor(new Color(0.3f, 0.15f, 0.05f, 1f));
        pixmap.fillRectangle(16, 22, 32, 12);
        
        // Saddle trim with team color
        pixmap.setColor(accentColor);
        pixmap.fillRectangle(16, 22, 32, 3);
        pixmap.fillRectangle(16, 31, 32, 3);
        
        // Rider armor
        pixmap.setColor(new Color(0.6f, 0.6f, 0.65f, 1f));
        pixmap.fillRectangle(8, 34, 48, 20);
        
        // Chest plate detail
        pixmap.setColor(new Color(0.5f, 0.5f, 0.55f, 1f));
        pixmap.fillRectangle(18, 36, 28, 16);
        
        // Helmet
        pixmap.setColor(new Color(0.65f, 0.65f, 0.7f, 1f));
        pixmap.fillRectangle(12, 54, 40, 10);
        
        // Helmet plume with team color
        pixmap.setColor(hatColor);
        pixmap.fillRectangle(26, 56, 12, 8);
        
        // Visor slit
        pixmap.setColor(new Color(0.1f, 0.1f, 0.1f, 1f));
        pixmap.fillRectangle(20, 52, 24, 3);
        
        // Lance (diagonal across)
        pixmap.setColor(new Color(0.5f, 0.35f, 0.2f, 1f));
        for (int i = 0; i < 6; i++) {
            pixmap.fillRectangle(4 + i * 8, 38 + i * 3, 6, 3);
        }
        
        // Lance tip
        pixmap.setColor(accentColor);
        pixmap.fillRectangle(52, 56, 8, 4);
    }

    private void drawTrebuchetTexture(Pixmap pixmap, Color accentColor, Color hatColor) {
        // Base platform (wooden)
        pixmap.setColor(new Color(0.5f, 0.35f, 0.2f, 1f));
        pixmap.fillRectangle(0, 0, 64, 18);
        
        // Platform planks detail
        pixmap.setColor(new Color(0.4f, 0.28f, 0.15f, 1f));
        for (int i = 0; i < 4; i++) {
            pixmap.fillRectangle(0, 4 + i * 4, 64, 1);
        }
        
        // Wheel (left)
        pixmap.setColor(new Color(0.35f, 0.25f, 0.15f, 1f));
        pixmap.fillRectangle(6, 2, 12, 12);
        pixmap.setColor(new Color(0.2f, 0.15f, 0.1f, 1f));
        pixmap.fillRectangle(10, 6, 4, 4);
        
        // Wheel (right)
        pixmap.setColor(new Color(0.35f, 0.25f, 0.15f, 1f));
        pixmap.fillRectangle(46, 2, 12, 12);
        pixmap.setColor(new Color(0.2f, 0.15f, 0.1f, 1f));
        pixmap.fillRectangle(50, 6, 4, 4);
        
        // Main beam frame (vertical supports)
        pixmap.setColor(new Color(0.45f, 0.32f, 0.18f, 1f));
        pixmap.fillRectangle(12, 14, 8, 40);
        pixmap.fillRectangle(44, 14, 8, 40);
        
        // Crossbeam
        pixmap.setColor(new Color(0.5f, 0.35f, 0.2f, 1f));
        pixmap.fillRectangle(12, 48, 40, 6);
        
        // Pivot axle
        pixmap.setColor(new Color(0.3f, 0.3f, 0.35f, 1f));
        pixmap.fillRectangle(28, 30, 8, 8);
        
        // Throwing arm
        pixmap.setColor(new Color(0.55f, 0.4f, 0.25f, 1f));
        pixmap.fillRectangle(4, 32, 56, 6);
        
        // Counterweight (heavy)
        pixmap.setColor(new Color(0.3f, 0.3f, 0.35f, 1f));
        pixmap.fillRectangle(2, 22, 14, 12);
        
        // Sling (at end of arm)
        pixmap.setColor(new Color(0.6f, 0.5f, 0.35f, 1f));
        pixmap.fillRectangle(52, 28, 10, 8);
        
        // Team-colored banner
        pixmap.setColor(hatColor);
        pixmap.fillRectangle(26, 52, 12, 10);
        
        // Banner pole
        pixmap.setColor(new Color(0.4f, 0.3f, 0.2f, 1f));
        pixmap.fillRectangle(30, 50, 4, 14);
        
        // Metal fittings (team accent)
        pixmap.setColor(accentColor);
        pixmap.fillRectangle(10, 48, 4, 6);
        pixmap.fillRectangle(50, 48, 4, 6);
    }

    private void drawBatteringRamTexture(Pixmap pixmap, Color accentColor, Color hatColor) {
        // Base chassis (wooden)
        pixmap.setColor(new Color(0.5f, 0.35f, 0.2f, 1f));
        pixmap.fillRectangle(0, 0, 64, 20);
        
        // Chassis planks
        pixmap.setColor(new Color(0.4f, 0.28f, 0.15f, 1f));
        for (int i = 0; i < 5; i++) {
            pixmap.fillRectangle(0, 4 + i * 4, 64, 1);
        }
        
        // Wheels (4 wheels)
        pixmap.setColor(new Color(0.35f, 0.25f, 0.15f, 1f));
        pixmap.fillRectangle(4, 2, 10, 10);
        pixmap.fillRectangle(18, 2, 10, 10);
        pixmap.fillRectangle(36, 2, 10, 10);
        pixmap.fillRectangle(50, 2, 10, 10);
        
        // Wheel hubs
        pixmap.setColor(new Color(0.2f, 0.15f, 0.1f, 1f));
        pixmap.fillRectangle(7, 5, 4, 4);
        pixmap.fillRectangle(21, 5, 4, 4);
        pixmap.fillRectangle(39, 5, 4, 4);
        pixmap.fillRectangle(53, 5, 4, 4);
        
        // Protective roof (shed-like)
        pixmap.setColor(new Color(0.4f, 0.3f, 0.2f, 1f));
        pixmap.fillRectangle(4, 32, 56, 20);
        
        // Roof slats
        pixmap.setColor(new Color(0.35f, 0.25f, 0.15f, 1f));
        for (int i = 0; i < 4; i++) {
            pixmap.fillRectangle(4, 34 + i * 5, 56, 2);
        }
        
        // Support posts
        pixmap.setColor(new Color(0.45f, 0.32f, 0.18f, 1f));
        pixmap.fillRectangle(6, 18, 6, 16);
        pixmap.fillRectangle(52, 18, 6, 16);
        
        // Ram beam (the battering ram itself - metal tipped)
        pixmap.setColor(new Color(0.55f, 0.4f, 0.25f, 1f));
        pixmap.fillRectangle(12, 24, 40, 8);
        
        // Ram head (iron)
        pixmap.setColor(new Color(0.4f, 0.4f, 0.45f, 1f));
        pixmap.fillRectangle(48, 22, 12, 12);
        
        // Ram head spikes
        pixmap.setColor(new Color(0.3f, 0.3f, 0.35f, 1f));
        pixmap.fillRectangle(58, 24, 4, 3);
        pixmap.fillRectangle(58, 29, 4, 3);
        
        // Team banner on roof
        pixmap.setColor(hatColor);
        pixmap.fillRectangle(26, 52, 12, 10);
        
        // Banner pole
        pixmap.setColor(new Color(0.4f, 0.3f, 0.2f, 1f));
        pixmap.fillRectangle(30, 50, 4, 14);
        
        // Team-colored trim on roof
        pixmap.setColor(accentColor);
        pixmap.fillRectangle(4, 50, 56, 2);
        
        // Ropes/chains for swinging
        pixmap.setColor(new Color(0.6f, 0.55f, 0.4f, 1f));
        pixmap.fillRectangle(16, 32, 2, 8);
        pixmap.fillRectangle(46, 32, 2, 8);
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
