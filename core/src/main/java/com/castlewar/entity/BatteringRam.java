package com.castlewar.entity;

import com.badlogic.gdx.math.MathUtils;
import com.castlewar.ai.AiContext;
import com.castlewar.ai.siege.BatteringRamBTAgent;
import com.castlewar.ai.siege.BatteringRamState;
import com.castlewar.simulation.WorldContext;
import com.castlewar.world.GridWorld;

/**
 * Battering Ram siege engine - specialized for destroying gates and walls.
 * 
 * Characteristics:
 * - Slow movement, requires crew to push
 * - High structural damage per hit
 * - Requires windup time before strike
 * - Protected by roof (takes reduced damage from above)
 * - Vulnerable from sides
 */
public class BatteringRam extends Unit {
    
    // ═══════════════════════════════════════════════════════════════════════════
    // CONSTANTS
    // ═══════════════════════════════════════════════════════════════════════════
    
    private static final String[] NAMES = {
        "Gatebreaker", "Wallcrusher", "Doombringer", "Siegemaster", "Ironfist",
        "Thunderstrike", "Demolisher", "Fortress Bane", "Stonerender", "War Hammer"
    };
    
    private static final String[] TITLES = {
        "The Unstoppable", "The Heavy", "The Relentless", "The Ancient", "The Dread"
    };
    
    /** Base structural damage per hit */
    public static final float STRIKE_DAMAGE = 200f;
    
    /** Attack range (must be close to gate) */
    public static final float STRIKE_RANGE = 3f;
    
    /** Time to wind up before strike (seconds) */
    public static final float WINDUP_TIME = 2f;
    
    /** Time between strikes (recovery) */
    public static final float STRIKE_COOLDOWN = 4f;
    
    /** Movement speed (slow) */
    public static final float MOVE_SPEED = 1.0f;
    
    /** Base health for battering ram */
    private static final float BASE_HP = 250f;
    
    /** Base stamina (crew fatigue) */
    private static final float BASE_STAMINA = 150f;
    
    /** Damage reduction from above (roof protection) */
    public static final float ROOF_ARMOR = 0.5f;
    
    /** Crew required to operate effectively */
    public static final int CREW_SIZE = 4;
    
    // ═══════════════════════════════════════════════════════════════════════════
    // INSTANCE STATE
    // ═══════════════════════════════════════════════════════════════════════════
    
    private final BatteringRamBTAgent aiAgent;
    private BatteringRamState state = BatteringRamState.IDLE;
    private float windupProgress = 0f;
    private float strikeCooldown = 0f;
    private int gateHitsRemaining = 5; // Gates take multiple hits to break
    private transient float aiDeltaSnapshot;
    
    // ═══════════════════════════════════════════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════════════════════════════════════════
    
    public BatteringRam(float x, float y, float z, Team team, WorldContext worldContext) {
        super(x, y, z, team, generateName(), BASE_HP, BASE_STAMINA);
        this.attackDamage = STRIKE_DAMAGE;
        this.attackRange = STRIKE_RANGE;
        this.attackCooldown = STRIKE_COOLDOWN;
        this.aiAgent = new BatteringRamBTAgent(this, new AiContext(worldContext));
    }
    
    /**
     * Generates a random battering ram name.
     */
    private static String generateName() {
        return NAMES[MathUtils.random(NAMES.length - 1)] + " " +
               TITLES[MathUtils.random(TITLES.length - 1)];
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
        
        // Handle windup progress
        if (state == BatteringRamState.WINDUP) {
            windupProgress += delta;
        }
        
        // Handle strike cooldown
        if (strikeCooldown > 0) {
            strikeCooldown -= delta;
        }
        
        // Run AI
        aiAgent.update(delta, aiAgent.getContext());
        
        super.applyPhysics(delta, world);
    }
    
    /**
     * Override takeDamage to apply roof armor from above.
     */
    @Override
    public void takeDamage(float amount) {
        // TODO: Check if damage is from above (archers, projectiles)
        // For now, apply standard damage
        super.takeDamage(amount);
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // COMBAT ACTIONS
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Begin winding up for a strike.
     */
    public void startWindup() {
        if (canStrike()) {
            windupProgress = 0f;
            state = BatteringRamState.WINDUP;
        }
    }
    
    /**
     * Execute the strike if fully wound up.
     * Returns true if strike was successful.
     */
    public boolean strike() {
        if (!isWindupComplete() || !canStrike()) {
            return false;
        }
        
        // Execute strike
        windupProgress = 0f;
        strikeCooldown = STRIKE_COOLDOWN;
        state = BatteringRamState.STRIKE;
        
        // Damage would be applied to gate/wall in the BT agent
        return true;
    }
    
    /**
     * Check if the ram can begin a strike (not on cooldown).
     */
    public boolean canStrike() {
        return strikeCooldown <= 0;
    }
    
    /**
     * Check if windup is complete.
     */
    public boolean isWindupComplete() {
        return windupProgress >= WINDUP_TIME;
    }
    
    /**
     * Get windup progress as 0-1 fraction.
     */
    public float getWindupProgress() {
        return windupProgress / WINDUP_TIME;
    }
    
    /**
     * Get cooldown progress as 0-1 fraction (1 = ready).
     */
    public float getCooldownProgress() {
        return strikeCooldown <= 0 ? 1f : 1f - (strikeCooldown / STRIKE_COOLDOWN);
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // GETTERS
    // ═══════════════════════════════════════════════════════════════════════════
    
    public BatteringRamState getState() {
        return state;
    }
    
    public void setState(BatteringRamState state) {
        this.state = state;
    }
    
    public int getGateHitsRemaining() {
        return gateHitsRemaining;
    }
    
    public void decrementGateHits() {
        if (gateHitsRemaining > 0) {
            gateHitsRemaining--;
        }
    }
    
    public float getMoveSpeed() {
        return MOVE_SPEED;
    }
    
    public float getStrikeDamage() {
        return STRIKE_DAMAGE;
    }
}
