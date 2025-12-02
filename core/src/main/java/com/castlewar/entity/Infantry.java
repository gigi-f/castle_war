package com.castlewar.entity;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.castlewar.ai.AiContext;
import com.castlewar.ai.infantry.InfantryBTAgent;
import com.castlewar.ai.infantry.InfantryState;
import com.castlewar.simulation.WorldContext;
import com.castlewar.world.GridWorld;

import java.util.List;

/**
 * Infantry unit - basic melee foot soldier.
 * <p>
 * Infantry are the backbone of an army:
 * <ul>
 *   <li>Moderate health (60 HP)</li>
 *   <li>Moderate damage (15)</li>
 *   <li>Formation-based movement and combat</li>
 *   <li>Bonus when near allies (morale)</li>
 *   <li>Can form shield walls for defense</li>
 * </ul>
 * 
 * <h2>Formation Behavior</h2>
 * Infantry try to maintain spacing with nearby allies:
 * <ul>
 *   <li>Ideal spacing: 2 units apart</li>
 *   <li>Formation bonus: +20% damage when 2+ allies nearby</li>
 *   <li>Morale penalty: -30% damage when isolated</li>
 * </ul>
 */
public class Infantry extends Unit {
    
    // ═══════════════════════════════════════════════════════════════════════════
    // CONSTANTS
    // ═══════════════════════════════════════════════════════════════════════════
    
    private static final String[] FIRST_NAMES = {
        "Marcus", "Lucius", "Gaius", "Titus", "Quintus", 
        "Decimus", "Publius", "Gnaeus", "Aulus", "Manius"
    };
    
    private static final String[] SURNAMES = {
        "Ironshield", "Steelhelm", "Strongarm", "Battleborn", "Warfoot",
        "Shieldbreaker", "Spearpoint", "Swordhand", "Stoneguard", "Braveheart"
    };
    
    /** Base health for infantry */
    private static final float BASE_HP = 60f;
    
    /** Base stamina for infantry */
    private static final float BASE_STAMINA = 40f;
    
    /** Base melee damage */
    private static final float BASE_DAMAGE = 15f;
    
    /** Attack range in units */
    private static final float ATTACK_RANGE = 2.0f;
    
    /** Attack cooldown in seconds */
    private static final float ATTACK_COOLDOWN = 1.0f;
    
    /** Ideal spacing between infantry in formation */
    public static final float FORMATION_SPACING = 2.5f;
    
    /** Range to detect allies for formation bonus */
    public static final float ALLY_DETECTION_RANGE = 6f;
    
    /** Minimum allies needed for formation bonus */
    public static final int MIN_ALLIES_FOR_BONUS = 2;
    
    /** Damage multiplier when in formation */
    private static final float FORMATION_DAMAGE_BONUS = 1.2f;
    
    /** Damage multiplier when isolated */
    private static final float ISOLATED_DAMAGE_PENALTY = 0.7f;
    
    /** Movement speed in units/second */
    private static final float MOVE_SPEED = 4.5f;
    
    /** Charge speed multiplier */
    private static final float CHARGE_SPEED_MULTIPLIER = 1.6f;
    
    // ═══════════════════════════════════════════════════════════════════════════
    // INSTANCE FIELDS
    // ═══════════════════════════════════════════════════════════════════════════
    
    private final InfantryBTAgent aiAgent;
    private final float baseDamage;
    private int nearbyAllyCount = 0;
    private boolean isCharging = false;
    private float chargeTimer = 0f;
    private transient float aiDeltaSnapshot;
    
    // ═══════════════════════════════════════════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Creates a new Infantry unit.
     * 
     * @param x            X position
     * @param y            Y position  
     * @param z            Z position
     * @param team         Team affiliation
     * @param worldContext Access to world state
     */
    public Infantry(float x, float y, float z, Team team, WorldContext worldContext) {
        super(x, y, z, team, generateName(), BASE_HP, BASE_STAMINA);
        this.baseDamage = BASE_DAMAGE;
        this.attackRange = ATTACK_RANGE;
        this.attackCooldown = ATTACK_COOLDOWN;
        this.aiAgent = new InfantryBTAgent(this, new AiContext(worldContext));
    }
    
    /**
     * Generates a random infantry name.
     */
    private static String generateName() {
        return FIRST_NAMES[MathUtils.random(FIRST_NAMES.length - 1)] + " " +
               SURNAMES[MathUtils.random(SURNAMES.length - 1)];
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
        
        // Update charge timer
        if (isCharging && chargeTimer > 0) {
            chargeTimer -= delta;
            if (chargeTimer <= 0) {
                isCharging = false;
            }
        }
        
        // Run AI
        aiAgent.update(delta, aiAgent.getContext());
        
        super.applyPhysics(delta, world);
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // COMBAT
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Calculates effective damage based on formation status.
     */
    public float getEffectiveDamage() {
        if (nearbyAllyCount >= MIN_ALLIES_FOR_BONUS) {
            return baseDamage * FORMATION_DAMAGE_BONUS;
        } else if (nearbyAllyCount == 0) {
            return baseDamage * ISOLATED_DAMAGE_PENALTY;
        }
        return baseDamage;
    }
    
    /**
     * Updates the count of nearby allied infantry.
     * 
     * @param allies List of nearby allies
     */
    public void updateNearbyAllies(List<Entity> allies) {
        this.nearbyAllyCount = 0;
        for (Entity e : allies) {
            if (e instanceof Infantry && e.getTeam() == team && e != this) {
                if (!((Infantry) e).isDead()) {
                    float dist = position.dst(e.getPosition());
                    if (dist <= ALLY_DETECTION_RANGE) {
                        nearbyAllyCount++;
                    }
                }
            }
        }
    }
    
    /**
     * Initiates a charge attack.
     * 
     * @param duration How long the charge lasts
     */
    public void startCharge(float duration) {
        isCharging = true;
        chargeTimer = duration;
    }
    
    /**
     * Gets current movement speed, accounting for charge.
     */
    public float getMoveSpeed() {
        return isCharging ? MOVE_SPEED * CHARGE_SPEED_MULTIPLIER : MOVE_SPEED;
    }
    
    @Override
    protected float getKnockbackStrengthAgainst(Unit target) {
        // Extra knockback when charging
        return isCharging ? 12f : 6f;
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // FORMATION HELPERS
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Checks if this infantry is in a valid formation (enough allies nearby).
     */
    public boolean isInFormation() {
        return nearbyAllyCount >= MIN_ALLIES_FOR_BONUS;
    }
    
    /**
     * Gets the ideal position offset for formation with given index.
     * 
     * @param formationIndex Index in formation (0 = leader)
     * @param formationSize  Total units in formation
     * @return Offset from formation center
     */
    public static Vector3 getFormationOffset(int formationIndex, int formationSize) {
        // Simple line formation for now
        int row = formationIndex / 5;
        int col = formationIndex % 5;
        
        float offsetX = (col - 2) * FORMATION_SPACING;
        float offsetY = -row * FORMATION_SPACING;
        
        return new Vector3(offsetX, offsetY, 0);
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // ENEMY DETECTION
    // ═══════════════════════════════════════════════════════════════════════════
    
    @Override
    public void scanForEnemies(List<Entity> entities, GridWorld world) {
        float closestDist = 12f; // Vision range
        Unit closest = null;
        
        for (Entity e : entities) {
            if (e instanceof Unit && e.getTeam() != this.team && !((Unit) e).isDead()) {
                float d = position.dst(e.getPosition());
                if (d < closestDist) {
                    // Check FOV (wider than guards - infantry are trained to watch flanks)
                    Vector3 toEnemy = new Vector3(e.getPosition()).sub(position);
                    toEnemy.z = 0;
                    toEnemy.nor();
                    
                    float dot = facing.dot(toEnemy);
                    // 150 degree FOV
                    if (dot > 0.25f || d < 3f) { // Or very close
                        if (world.hasLineOfSight(position.x, position.y, position.z + 1.5f,
                                                e.getX(), e.getY(), e.getZ() + 1.0f)) {
                            closestDist = d;
                            closest = (Unit) e;
                        }
                    }
                }
            }
        }
        setTargetEnemy(closest);
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // ACCESSORS
    // ═══════════════════════════════════════════════════════════════════════════
    
    public InfantryBTAgent getAiAgent() {
        return aiAgent;
    }
    
    public float getAiDeltaSnapshot() {
        return aiDeltaSnapshot;
    }
    
    public int getNearbyAllyCount() {
        return nearbyAllyCount;
    }
    
    public boolean isCharging() {
        return isCharging;
    }
    
    public float getAttackRange() {
        return attackRange;
    }
    
    public float getBaseDamage() {
        return baseDamage;
    }
    
    /**
     * Changes the AI state (for animation purposes).
     */
    public void changeState(InfantryState nextState) {
        aiAgent.changeState(nextState);
    }
    
    /**
     * Gets current AI state.
     */
    public InfantryState getCurrentState() {
        return aiAgent.getCurrentState();
    }
}
