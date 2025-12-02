package com.castlewar.entity;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.castlewar.ai.AiContext;
import com.castlewar.ai.cavalry.CavalryBTAgent;
import com.castlewar.ai.cavalry.CavalryState;
import com.castlewar.simulation.WorldContext;
import com.castlewar.world.GridWorld;

import java.util.List;

/**
 * Cavalry unit - mounted warrior with charge attacks.
 * <p>
 * Cavalry are highly mobile shock troops:
 * <ul>
 *   <li>High health (80 HP unit + 60 HP horse = 140 total effective)</li>
 *   <li>High damage on charge (35)</li>
 *   <li>Very fast movement (12 units/sec at full gallop)</li>
 *   <li>Devastating charge attacks with knockback</li>
 *   <li>Weaker in prolonged melee combat</li>
 * </ul>
 * 
 * <h2>Charge Mechanics</h2>
 * <ul>
 *   <li>Charge damage scales with speed: base + (speed * 2)</li>
 *   <li>Requires 10 units of run-up distance</li>
 *   <li>3 second cooldown between charges</li>
 *   <li>Knockback on impact</li>
 *   <li>Must wheel around after charge (2 seconds)</li>
 * </ul>
 * 
 * <h2>Horse Mechanics</h2>
 * <ul>
 *   <li>Horse has separate HP pool (60)</li>
 *   <li>If horse dies, rider is dismounted</li>
 *   <li>Dismounted cavalry fights as weakened infantry</li>
 * </ul>
 */
public class Cavalry extends Unit {
    
    // ═══════════════════════════════════════════════════════════════════════════
    // CONSTANTS
    // ═══════════════════════════════════════════════════════════════════════════
    
    private static final String[] FIRST_NAMES = {
        "Roland", "Percival", "Lancelot", "Galahad", "Gawain",
        "Arthur", "Tristan", "Bors", "Ector", "Kay"
    };
    
    private static final String[] SURNAMES = {
        "Ironhorse", "Swiftlance", "Thunderhoof", "Steelsteed", "Nightrider",
        "Charger", "Destrier", "Courser", "Paladin", "Warhorse"
    };
    
    /** Base health for rider */
    private static final float BASE_HP = 80f;
    
    /** Health for horse */
    private static final float HORSE_HP = 60f;
    
    /** Base stamina */
    private static final float BASE_STAMINA = 60f;
    
    /** Base melee damage */
    private static final float BASE_DAMAGE = 18f;
    
    /** Charge damage (before speed bonus) */
    private static final float CHARGE_DAMAGE = 35f;
    
    /** Attack range while mounted */
    private static final float MOUNTED_ATTACK_RANGE = 3.0f;
    
    /** Attack range while dismounted */
    private static final float DISMOUNTED_ATTACK_RANGE = 2.0f;
    
    /** Attack cooldown */
    private static final float ATTACK_COOLDOWN = 1.2f;
    
    /** Patrol speed (trotting) */
    private static final float TROT_SPEED = 6f;
    
    /** Combat movement speed */
    private static final float CANTER_SPEED = 9f;
    
    /** Full charge speed */
    private static final float GALLOP_SPEED = 14f;
    
    /** Dismounted movement speed */
    private static final float DISMOUNTED_SPEED = 4f;
    
    /** Minimum distance needed to initiate charge */
    public static final float MIN_CHARGE_DISTANCE = 10f;
    
    /** Cooldown between charges */
    private static final float CHARGE_COOLDOWN = 3f;
    
    /** Time to wheel around after charge */
    private static final float WHEEL_TIME = 2f;
    
    /** Patrol radius for cavalry */
    private static final float PATROL_RADIUS = 15f;
    
    // ═══════════════════════════════════════════════════════════════════════════
    // INSTANCE FIELDS
    // ═══════════════════════════════════════════════════════════════════════════
    
    private final CavalryBTAgent aiAgent;
    private final float baseDamage;
    private CavalryState currentState = CavalryState.IDLE;
    
    /** Current horse health */
    private float horseHp;
    
    /** Whether still mounted */
    private boolean mounted = true;
    
    /** Whether currently charging */
    private boolean charging = false;
    
    /** Current charge speed */
    private float chargeSpeed = 0f;
    
    /** Timer for charge cooldown */
    private float chargeCooldownTimer = 0f;
    
    /** Timer for wheeling after charge */
    private float wheelTimer = 0f;
    
    /** Direction of charge */
    private final Vector3 chargeDirection = new Vector3();
    
    /** Target of current charge */
    private Unit chargeTarget = null;
    
    private transient float aiDeltaSnapshot;
    
    // ═══════════════════════════════════════════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Creates a new Cavalry unit.
     * 
     * @param x            X position
     * @param y            Y position
     * @param z            Z position
     * @param team         Team affiliation
     * @param worldContext Access to world state
     */
    public Cavalry(float x, float y, float z, Team team, WorldContext worldContext) {
        super(x, y, z, team, generateName(), BASE_HP, BASE_STAMINA);
        this.baseDamage = BASE_DAMAGE;
        this.attackRange = MOUNTED_ATTACK_RANGE;
        this.attackCooldown = ATTACK_COOLDOWN;
        this.horseHp = HORSE_HP;
        this.aiAgent = new CavalryBTAgent(this, new AiContext(worldContext));
    }
    
    /**
     * Generates a random cavalry name.
     */
    private static String generateName() {
        String first = FIRST_NAMES[MathUtils.random(FIRST_NAMES.length - 1)];
        String last = SURNAMES[MathUtils.random(SURNAMES.length - 1)];
        return "Sir " + first + " " + last;
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // UPDATE
    // ═══════════════════════════════════════════════════════════════════════════
    
    @Override
    public void update(float delta, GridWorld world) {
        checkEnvironment(world);
        if (!beginUpdate(delta, world)) {
            return;
        }
        
        if (isDead()) return;
        
        this.aiDeltaSnapshot = delta;
        
        // Update timers
        if (chargeCooldownTimer > 0) {
            chargeCooldownTimer -= delta;
        }
        if (wheelTimer > 0) {
            wheelTimer -= delta;
        }
        
        // Update AI
        aiAgent.update(delta, aiAgent.getContext());
        
        // Handle charging movement
        if (charging) {
            updateCharge(delta);
        }
        
        // Apply physics
        super.applyPhysics(delta, world);
    }
    
    /**
     * Updates charge state and movement.
     */
    private void updateCharge(float delta) {
        if (chargeTarget == null || chargeTarget.isDead()) {
            endCharge();
            return;
        }
        
        // Accelerate toward charge target
        Vector3 toTarget = new Vector3(chargeTarget.getPosition()).sub(position);
        float distance = toTarget.len();
        
        // Check for impact
        if (distance < attackRange) {
            // Deliver charge damage
            float damage = calculateChargeDamage();
            chargeTarget.takeDamage(damage);
            
            // Apply knockback to target
            Vector3 knockbackDir = new Vector3(chargeDirection).nor();
            chargeTarget.getVelocity().add(knockbackDir.scl(chargeSpeed * 0.5f));
            
            endCharge();
            return;
        }
        
        // Continue charging
        chargeDirection.set(toTarget).nor();
        velocity.set(chargeDirection).scl(chargeSpeed);
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // CHARGE MECHANICS
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Checks if cavalry can initiate a charge.
     */
    public boolean canCharge() {
        return mounted && 
               chargeCooldownTimer <= 0 && 
               wheelTimer <= 0 && 
               !charging;
    }
    
    /**
     * Initiates a charge toward a target.
     */
    public boolean startCharge(Unit target) {
        if (!canCharge()) return false;
        if (target == null || target.isDead()) return false;
        
        float distance = position.dst(target.getPosition());
        if (distance < MIN_CHARGE_DISTANCE) return false;
        
        charging = true;
        chargeTarget = target;
        chargeSpeed = GALLOP_SPEED;
        chargeDirection.set(target.getPosition()).sub(position).nor();
        currentState = CavalryState.CHARGING;
        
        return true;
    }
    
    /**
     * Ends the current charge.
     */
    public void endCharge() {
        charging = false;
        chargeTarget = null;
        chargeSpeed = 0f;
        chargeCooldownTimer = CHARGE_COOLDOWN;
        wheelTimer = WHEEL_TIME;
        currentState = CavalryState.WHEELING;
    }
    
    /**
     * Calculates charge damage based on current speed.
     */
    private float calculateChargeDamage() {
        // Base charge damage + speed bonus
        return CHARGE_DAMAGE + (chargeSpeed * 2f);
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // MOUNT MECHANICS
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Damages the horse. If horse dies, rider is dismounted.
     */
    public void damageHorse(float amount) {
        if (!mounted) return;
        
        horseHp -= amount;
        if (horseHp <= 0) {
            horseHp = 0;
            dismount();
        }
    }
    
    /**
     * Forces dismount (horse killed or terrain).
     */
    public void dismount() {
        if (!mounted) return;
        
        mounted = false;
        attackRange = DISMOUNTED_ATTACK_RANGE;
        currentState = CavalryState.DISMOUNTED;
        
        // End any ongoing charge
        if (charging) {
            endCharge();
        }
    }
    
    @Override
    public void takeDamage(float amount) {
        if (mounted) {
            // Split damage between rider and horse (60% rider, 40% horse)
            float riderDamage = amount * 0.6f;
            float horseDamage = amount * 0.4f;
            
            super.takeDamage(riderDamage);
            damageHorse(horseDamage);
        } else {
            super.takeDamage(amount);
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // MOVEMENT
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Gets the appropriate movement speed based on state.
     */
    public float getCurrentMoveSpeed() {
        if (!mounted) return DISMOUNTED_SPEED;
        if (charging) return GALLOP_SPEED;
        
        switch (currentState) {
            case CANTERING:
            case ENGAGING:
                return CANTER_SPEED;
            case TROTTING:
            default:
                return TROT_SPEED;
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // STATE & GETTERS
    // ═══════════════════════════════════════════════════════════════════════════
    
    public CavalryState getCurrentState() { return currentState; }
    public void setCurrentState(CavalryState state) { this.currentState = state; }
    
    public boolean isMounted() { return mounted; }
    public boolean isCharging() { return charging; }
    public boolean isWheeling() { return wheelTimer > 0; }
    
    public float getHorseHp() { return horseHp; }
    public float getHorseMaxHp() { return HORSE_HP; }
    public float getChargeCooldownTimer() { return chargeCooldownTimer; }
    public float getWheelTimer() { return wheelTimer; }
    
    public float getPatrolRadius() { return PATROL_RADIUS; }
    public float getBaseDamage() { return baseDamage; }
    public float getChargeDamage() { return CHARGE_DAMAGE; }
    public float getMinChargeDistance() { return MIN_CHARGE_DISTANCE; }
    
    public float getTrotSpeed() { return TROT_SPEED; }
    public float getCanterSpeed() { return CANTER_SPEED; }
    public float getGallopSpeed() { return GALLOP_SPEED; }
    
    public CavalryBTAgent getAiAgent() { return aiAgent; }
    
    /**
     * Gets total effective HP (rider + horse).
     */
    public float getTotalEffectiveHp() {
        return hp + (mounted ? horseHp : 0);
    }
    
    @Override
    public String toString() {
        return String.format("Cavalry[%s, %s, HP:%.0f/%.0f, Horse:%.0f/%.0f, State:%s, %s]",
            getName(), team, hp, maxHp, horseHp, HORSE_HP, currentState,
            mounted ? "Mounted" : "Dismounted");
    }
}
