package com.castlewar.entity;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.castlewar.ai.AiContext;
import com.castlewar.ai.archer.ArcherBTAgent;
import com.castlewar.ai.archer.ArcherState;
import com.castlewar.simulation.WorldContext;
import com.castlewar.world.GridWorld;

import java.util.List;

/**
 * Archer unit - ranged attacker with kiting capabilities.
 * <p>
 * Archers are glass cannons:
 * <ul>
 *   <li>Low health (40 HP)</li>
 *   <li>High ranged damage (20)</li>
 *   <li>Long attack range (12 units)</li>
 *   <li>Can kite enemies (attack while retreating)</li>
 *   <li>Prefer high ground and cover</li>
 * </ul>
 * 
 * <h2>Combat Behavior</h2>
 * Archers prioritize positioning:
 * <ul>
 *   <li>Elevation bonus: +15% damage from high ground</li>
 *   <li>Cover bonus: +25% defense when behind obstacles</li>
 *   <li>Kiting: Maintains minimum distance from melee threats</li>
 *   <li>Vulnerable: Takes +50% damage from melee attacks</li>
 * </ul>
 */
public class Archer extends Unit {
    
    // ═══════════════════════════════════════════════════════════════════════════
    // CONSTANTS
    // ═══════════════════════════════════════════════════════════════════════════
    
    private static final String[] FIRST_NAMES = {
        "Robin", "William", "Edward", "Thomas", "John", 
        "Richard", "Henry", "Geoffrey", "Simon", "Hugh"
    };
    
    private static final String[] SURNAMES = {
        "Longbow", "Swiftarrow", "Trueflight", "Sharpshot", "Hawkeye",
        "Fletcher", "Bowstring", "Quickdraw", "Keeneye", "Surehit"
    };
    
    /** Base health for archer (low - glass cannon) */
    private static final float BASE_HP = 40f;
    
    /** Base stamina for archer */
    private static final float BASE_STAMINA = 50f;
    
    /** Base ranged damage */
    private static final float BASE_DAMAGE = 20f;
    
    /** Maximum attack range in units */
    private static final float MAX_ATTACK_RANGE = 12.0f;
    
    /** Minimum safe distance from melee enemies */
    public static final float MIN_SAFE_DISTANCE = 5.0f;
    
    /** Attack cooldown in seconds (slower than melee) */
    private static final float ATTACK_COOLDOWN = 1.8f;
    
    /** Movement speed in units/second */
    private static final float MOVE_SPEED = 5.5f;
    
    /** Kiting speed multiplier (faster when retreating) */
    private static final float KITE_SPEED_MULTIPLIER = 1.3f;
    
    /** Damage bonus from high ground */
    private static final float ELEVATION_DAMAGE_BONUS = 1.15f;
    
    /** Defense bonus when in cover */
    private static final float COVER_DEFENSE_BONUS = 1.25f;
    
    /** Extra damage taken from melee attacks */
    private static final float MELEE_VULNERABILITY = 1.5f;
    
    /** Range to detect cover positions */
    public static final float COVER_DETECTION_RANGE = 8f;
    
    /** Ideal patrol radius for archer positions */
    private static final float PATROL_RADIUS = 10f;
    
    // ═══════════════════════════════════════════════════════════════════════════
    // INSTANCE FIELDS
    // ═══════════════════════════════════════════════════════════════════════════
    
    private final ArcherBTAgent aiAgent;
    private final float baseDamage;
    private ArcherState currentState = ArcherState.IDLE;
    private boolean isKiting = false;
    private boolean inCover = false;
    private boolean hasElevationAdvantage = false;
    private int arrowCount = 30;
    private transient float aiDeltaSnapshot;
    
    // ═══════════════════════════════════════════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Creates a new Archer unit.
     * 
     * @param x            X position
     * @param y            Y position  
     * @param z            Z position
     * @param team         Team affiliation
     * @param worldContext Access to world state
     */
    public Archer(float x, float y, float z, Team team, WorldContext worldContext) {
        super(x, y, z, team, generateName(), BASE_HP, BASE_STAMINA);
        this.baseDamage = BASE_DAMAGE;
        this.attackRange = MAX_ATTACK_RANGE;
        this.attackCooldown = ATTACK_COOLDOWN;
        this.aiAgent = new ArcherBTAgent(this, new AiContext(worldContext));
    }
    
    /**
     * Generates a random archer name.
     */
    private static String generateName() {
        String first = FIRST_NAMES[MathUtils.random(FIRST_NAMES.length - 1)];
        String last = SURNAMES[MathUtils.random(SURNAMES.length - 1)];
        return first + " " + last;
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
        
        // Store delta for AI access
        this.aiDeltaSnapshot = delta;
        
        // Update AI agent
        aiAgent.update(delta, aiAgent.getContext());
        
        // Check positional advantages
        updatePositionalAdvantages(world);
        
        // Apply physics (handles movement based on velocity)
        super.applyPhysics(delta, world);
    }
    
    /**
     * Updates elevation and cover status.
     */
    private void updatePositionalAdvantages(GridWorld world) {
        if (world == null) return;
        
        int x = (int) position.x;
        int y = (int) position.y;
        int z = (int) position.z;
        
        // Check for elevation advantage (standing on blocks)
        hasElevationAdvantage = z > 0;
        
        // Check for nearby cover (adjacent non-air blocks)
        inCover = false;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) continue;
                GridWorld.BlockState block = world.getBlock(x + dx, y + dy, z);
                if (block != GridWorld.BlockState.AIR && 
                    block != GridWorld.BlockState.WATER) {
                    inCover = true;
                    break;
                }
            }
            if (inCover) break;
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // RANGED COMBAT
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Attempts to fire an arrow at the target.
     * 
     * @param target The target to attack
     * @return true if arrow was fired, false if on cooldown or out of arrows
     */
    public boolean fireArrow(Unit target) {
        if (!canAttack() || arrowCount <= 0) return false;
        
        float distance = Vector3.dst(position.x, position.y, position.z,
                                      target.getPosition().x, target.getPosition().y, target.getPosition().z);
        
        if (distance > attackRange) return false;
        
        // Calculate damage with bonuses
        float damage = calculateDamage(target);
        target.takeDamage(damage);
        
        arrowCount--;
        attackTimer = attackCooldown;
        currentState = ArcherState.FIRING;
        
        return true;
    }
    
    /**
     * Calculates damage including positional bonuses.
     */
    private float calculateDamage(Unit target) {
        float damage = baseDamage;
        
        // Elevation bonus
        if (hasElevationAdvantage && position.z > target.getPosition().z) {
            damage *= ELEVATION_DAMAGE_BONUS;
        }
        
        return damage;
    }
    
    /**
     * Take damage with vulnerability modifiers.
     */
    public void takeDamageFrom(float damage, Unit attacker) {
        float modifiedDamage = damage;
        
        // Check if this is a melee attack (attacker close range)
        if (attacker != null) {
            float distance = Vector3.dst(position.x, position.y, position.z,
                                          attacker.getPosition().x, attacker.getPosition().y, attacker.getPosition().z);
            if (distance < 3.0f) {
                // Melee vulnerability
                modifiedDamage *= MELEE_VULNERABILITY;
                if (!inCover) {
                    // Even more damage if not in cover
                    modifiedDamage *= 1.2f;
                }
            }
        }
        
        // Apply cover defense bonus
        if (inCover) {
            modifiedDamage /= COVER_DEFENSE_BONUS;
        }
        
        super.takeDamage(modifiedDamage);
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // KITING & POSITIONING
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Checks if there's a melee threat nearby requiring kiting.
     */
    public boolean hasMeleeThreat() {
        List<Entity> entities = aiAgent.getContext().getEntities();
        if (entities == null) return false;
        
        for (Entity e : entities) {
            if (e instanceof Unit && e != this) {
                Unit unit = (Unit) e;
                if (unit.getTeam() != this.team && !unit.isDead()) {
                    float distance = Vector3.dst(position.x, position.y, position.z,
                                                  unit.getPosition().x, unit.getPosition().y, unit.getPosition().z);
                    // If enemy melee unit is within minimum safe distance
                    if (distance < MIN_SAFE_DISTANCE && unit.attackRange < 5.0f) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
    
    /**
     * Gets the direction to kite away from the nearest melee threat.
     */
    public Vector3 getKiteDirection() {
        List<Entity> entities = aiAgent.getContext().getEntities();
        if (entities == null) return new Vector3(0, 0, 0);
        
        Vector3 awayDirection = new Vector3();
        int threatCount = 0;
        
        for (Entity e : entities) {
            if (e instanceof Unit && e != this) {
                Unit unit = (Unit) e;
                if (unit.getTeam() != this.team && !unit.isDead()) {
                    float distance = Vector3.dst(position.x, position.y, position.z,
                                                  unit.getPosition().x, unit.getPosition().y, unit.getPosition().z);
                    if (distance < MIN_SAFE_DISTANCE * 1.5f && unit.attackRange < 5.0f) {
                        // Add direction away from this threat
                        Vector3 away = new Vector3(position).sub(unit.getPosition()).nor();
                        awayDirection.add(away);
                        threatCount++;
                    }
                }
            }
        }
        
        if (threatCount > 0) {
            awayDirection.scl(1f / threatCount).nor();
        }
        
        return awayDirection;
    }
    
    /**
     * Finds the nearest valid cover position.
     */
    public Vector3 findCoverPosition(GridWorld world) {
        if (world == null) return null;
        
        Vector3 bestCover = null;
        float bestScore = Float.MAX_VALUE;
        
        int searchRadius = (int) COVER_DETECTION_RANGE;
        int px = (int) position.x;
        int py = (int) position.y;
        int pz = (int) position.z;
        
        for (int dx = -searchRadius; dx <= searchRadius; dx++) {
            for (int dy = -searchRadius; dy <= searchRadius; dy++) {
                int x = px + dx;
                int y = py + dy;
                
                // Check if position is walkable
                GridWorld.BlockState groundBlock = world.getBlock(x, y, pz - 1);
                GridWorld.BlockState posBlock = world.getBlock(x, y, pz);
                
                if (posBlock == GridWorld.BlockState.AIR && 
                    groundBlock != GridWorld.BlockState.AIR &&
                    groundBlock != GridWorld.BlockState.WATER) {
                    
                    // Check if there's cover nearby
                    boolean hasCover = false;
                    for (int cx = -1; cx <= 1; cx++) {
                        for (int cy = -1; cy <= 1; cy++) {
                            if (cx == 0 && cy == 0) continue;
                            GridWorld.BlockState adjacent = world.getBlock(x + cx, y + cy, pz);
                            if (adjacent != GridWorld.BlockState.AIR) {
                                hasCover = true;
                                break;
                            }
                        }
                        if (hasCover) break;
                    }
                    
                    if (hasCover) {
                        float dist = Math.abs(dx) + Math.abs(dy);
                        if (dist < bestScore) {
                            bestScore = dist;
                            bestCover = new Vector3(x + 0.5f, y + 0.5f, pz);
                        }
                    }
                }
            }
        }
        
        return bestCover;
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // STATE & GETTERS
    // ═══════════════════════════════════════════════════════════════════════════
    
    public ArcherState getCurrentState() { return currentState; }
    public void setCurrentState(ArcherState state) { this.currentState = state; }
    
    public boolean isKiting() { return isKiting; }
    public void setKiting(boolean kiting) { this.isKiting = kiting; }
    
    public boolean isInCover() { return inCover; }
    public boolean hasElevationAdvantage() { return hasElevationAdvantage; }
    
    public int getArrowCount() { return arrowCount; }
    public void setArrowCount(int count) { this.arrowCount = count; }
    
    public float getPatrolRadius() { return PATROL_RADIUS; }
    public float getBaseDamage() { return baseDamage; }
    public float getMinSafeDistance() { return MIN_SAFE_DISTANCE; }
    public float getMoveSpeed() { return isKiting ? MOVE_SPEED * KITE_SPEED_MULTIPLIER : MOVE_SPEED; }
    
    public ArcherBTAgent getAiAgent() { return aiAgent; }
    
    @Override
    public String toString() {
        return String.format("Archer[%s, %s, HP:%.0f/%.0f, Arrows:%d, State:%s]",
            getName(), team, hp, maxHp, arrowCount, currentState);
    }
}
