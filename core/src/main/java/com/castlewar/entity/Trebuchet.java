package com.castlewar.entity;

import com.badlogic.gdx.math.MathUtils;
import com.castlewar.ai.AiContext;
import com.castlewar.ai.siege.TrebuchetBTAgent;
import com.castlewar.ai.siege.TrebuchetState;
import com.castlewar.simulation.WorldContext;
import com.castlewar.world.GridWorld;

/**
 * Trebuchet siege engine - long-range artillery for castle destruction.
 * 
 * Characteristics:
 * - Very slow movement, requires setup time
 * - Massive damage to structures and area effect
 * - Long reload time between shots
 * - Limited ammunition
 * - Vulnerable to melee attack
 */
public class Trebuchet extends Unit {
    
    // ═══════════════════════════════════════════════════════════════════════════
    // CONSTANTS
    // ═══════════════════════════════════════════════════════════════════════════
    
    private static final String[] NAMES = {
        "Thunder", "Earthshaker", "Stonebreaker", "Wallcrusher", "Desolator",
        "Goliath", "Titan", "Behemoth", "Devastator", "Obliterator"
    };
    
    private static final String[] ADJECTIVES = {
        "The Mighty", "The Terrible", "The Merciless", "The Grand", "The Iron"
    };
    
    /** Base siege damage (structure damage) */
    public static final float SIEGE_DAMAGE = 100f;
    
    /** Area of effect radius */
    public static final float SPLASH_RADIUS = 5f;
    
    /** Maximum firing range */
    public static final float MAX_RANGE = 40f;
    
    /** Minimum firing range (can't hit close targets) */
    public static final float MIN_RANGE = 10f;
    
    /** Time to reload between shots (seconds) */
    public static final float RELOAD_TIME = 8f;
    
    /** Time to aim before firing (seconds) */
    public static final float AIM_TIME = 2f;
    
    /** Maximum ammunition capacity */
    public static final int MAX_AMMO = 10;
    
    /** Movement speed (very slow) */
    public static final float MOVE_SPEED = 0.5f;
    
    /** Setup time before can fire after moving */
    public static final float SETUP_TIME = 3f;
    
    /** Base health for trebuchet */
    private static final float BASE_HP = 150f;
    
    /** Base stamina (crew fatigue) */
    private static final float BASE_STAMINA = 100f;
    
    // ═══════════════════════════════════════════════════════════════════════════
    // INSTANCE STATE
    // ═══════════════════════════════════════════════════════════════════════════
    
    private final TrebuchetBTAgent aiAgent;
    private TrebuchetState state = TrebuchetState.IDLE;
    private int ammo = MAX_AMMO;
    private float reloadProgress = 0f;
    private float aimProgress = 0f;
    private float setupProgress = 0f;
    private boolean isSetUp = true; // Starts set up
    private transient float aiDeltaSnapshot;
    
    // ═══════════════════════════════════════════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════════════════════════════════════════
    
    public Trebuchet(float x, float y, float z, Team team, WorldContext worldContext) {
        super(x, y, z, team, generateName(), BASE_HP, BASE_STAMINA);
        this.attackDamage = SIEGE_DAMAGE;
        this.attackRange = MAX_RANGE;
        this.attackCooldown = RELOAD_TIME;
        this.aiAgent = new TrebuchetBTAgent(this, new AiContext(worldContext));
    }
    
    /**
     * Generates a random trebuchet name.
     */
    private static String generateName() {
        return NAMES[MathUtils.random(NAMES.length - 1)] + " " +
               ADJECTIVES[MathUtils.random(ADJECTIVES.length - 1)];
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // UPDATE LOOP
    // ═══════════════════════════════════════════════════════════════════════════
    
    @Override
    public void update(float delta, GridWorld world) {
        checkEnvironment(world);
        if (!beginUpdate(delta, world)) {
            return;
        }
        
        this.aiDeltaSnapshot = delta;
        
        // Handle reload progress
        if (state == TrebuchetState.RELOAD && ammo < MAX_AMMO) {
            reloadProgress += delta;
            if (reloadProgress >= RELOAD_TIME) {
                reloadProgress = 0f;
                state = TrebuchetState.IDLE;
            }
        }
        
        // Handle aim progress
        if (state == TrebuchetState.AIM) {
            aimProgress += delta;
        }
        
        // Handle setup after moving
        if (!isSetUp && state != TrebuchetState.RELOCATE) {
            setupProgress += delta;
            if (setupProgress >= SETUP_TIME) {
                isSetUp = true;
                setupProgress = 0f;
            }
        }
        
        // Run AI
        aiAgent.update(delta, aiAgent.getContext());
        
        super.applyPhysics(delta, world);
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // COMBAT ACTIONS
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Fire at a target position. Returns true if fired successfully.
     */
    public boolean fire(float targetX, float targetY) {
        if (!canFire()) {
            return false;
        }
        
        // Check range
        float dx = targetX - position.x;
        float dy = targetY - position.y;
        float distance = (float) Math.sqrt(dx * dx + dy * dy);
        
        if (distance < MIN_RANGE || distance > MAX_RANGE) {
            return false;
        }
        
        // Fire the projectile
        ammo--;
        aimProgress = 0f;
        state = TrebuchetState.RELOAD;
        
        return true;
    }
    
    /**
     * Check if trebuchet can fire (set up, aimed, has ammo).
     */
    public boolean canFire() {
        return isSetUp && aimProgress >= AIM_TIME && ammo > 0;
    }
    
    /**
     * Check if target is in valid range (not too close, not too far).
     */
    public boolean isInRange(float targetX, float targetY) {
        float dx = targetX - position.x;
        float dy = targetY - position.y;
        float distance = (float) Math.sqrt(dx * dx + dy * dy);
        return distance >= MIN_RANGE && distance <= MAX_RANGE;
    }
    
    /**
     * Begin aiming at target. Resets aim progress.
     */
    public void startAiming() {
        if (isSetUp && ammo > 0) {
            aimProgress = 0f;
            state = TrebuchetState.AIM;
        }
    }
    
    /**
     * Check if fully aimed and ready to fire.
     */
    public boolean isAimed() {
        return aimProgress >= AIM_TIME;
    }
    
    /**
     * Begin relocating. Disables firing until set up again.
     */
    public void startRelocating() {
        isSetUp = false;
        setupProgress = 0f;
        state = TrebuchetState.RELOCATE;
    }
    
    /**
     * Stop moving and begin setup.
     */
    public void beginSetup() {
        if (!isSetUp) {
            setupProgress = 0f;
            state = TrebuchetState.IDLE;
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // GETTERS
    // ═══════════════════════════════════════════════════════════════════════════
    
    public TrebuchetState getState() {
        return state;
    }
    
    public void setState(TrebuchetState state) {
        this.state = state;
    }
    
    public int getAmmo() {
        return ammo;
    }
    
    public boolean hasAmmo() {
        return ammo > 0;
    }
    
    public boolean isSetUp() {
        return isSetUp;
    }
    
    public float getSetupProgress() {
        return setupProgress / SETUP_TIME;
    }
    
    public float getReloadProgress() {
        return reloadProgress / RELOAD_TIME;
    }
    
    public float getAimProgress() {
        return aimProgress / AIM_TIME;
    }
    
    public float getMinRange() {
        return MIN_RANGE;
    }
    
    public float getSplashRadius() {
        return SPLASH_RADIUS;
    }
    
    public float getMoveSpeed() {
        return MOVE_SPEED;
    }
}
