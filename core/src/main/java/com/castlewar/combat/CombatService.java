package com.castlewar.combat;

import com.badlogic.gdx.math.Vector3;
import com.castlewar.combat.DamageType.ArmorType;
import com.castlewar.entity.Entity;
import com.castlewar.entity.Unit;

import java.util.List;

/**
 * Centralized combat service for damage calculation, poise/stagger, and combat events.
 * 
 * <h2>Poise System</h2>
 * Each unit has a poise value representing stagger resistance:
 * <ul>
 *   <li>Attacks deal both HP damage and poise damage</li>
 *   <li>When poise breaks (reaches 0), unit is staggered</li>
 *   <li>Staggered units cannot act and take bonus damage</li>
 *   <li>Poise regenerates over time when not being attacked</li>
 * </ul>
 * 
 * <h2>Combat Flow</h2>
 * <pre>
 * 1. Attacker initiates attack
 * 2. CombatService.calculateDamage() computes damage with modifiers
 * 3. CombatService.applyDamage() deals damage and poise damage
 * 4. If poise breaks, target enters stagger state
 * 5. CombatEvent is broadcast for UI/audio feedback
 * </pre>
 */
public class CombatService {
    
    // ═══════════════════════════════════════════════════════════════════════════
    // CONSTANTS
    // ═══════════════════════════════════════════════════════════════════════════
    
    /** Poise damage as percentage of HP damage */
    private static final float POISE_DAMAGE_RATIO = 0.5f;
    
    /** Damage multiplier when attacking staggered target */
    private static final float STAGGER_DAMAGE_BONUS = 1.5f;
    
    /** Poise regeneration per second (when not attacked) */
    private static final float POISE_REGEN_RATE = 10f;
    
    /** Time after being hit before poise starts regenerating */
    private static final float POISE_REGEN_DELAY = 2f;
    
    /** Stagger duration in seconds */
    private static final float STAGGER_DURATION = 1.5f;
    
    /** Backstab damage multiplier (from behind) */
    private static final float BACKSTAB_MULTIPLIER = 2.5f;
    
    /** Angle threshold for backstab (degrees from directly behind) */
    private static final float BACKSTAB_ANGLE = 60f;
    
    /** Charge damage multiplier for cavalry */
    private static final float CHARGE_MULTIPLIER = 3f;
    
    // ═══════════════════════════════════════════════════════════════════════════
    // SINGLETON
    // ═══════════════════════════════════════════════════════════════════════════
    
    private static CombatService instance;
    
    public static CombatService getInstance() {
        if (instance == null) {
            instance = new CombatService();
        }
        return instance;
    }
    
    private CombatService() {}
    
    // ═══════════════════════════════════════════════════════════════════════════
    // DAMAGE CALCULATION
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Calculates final damage after applying all modifiers.
     * 
     * @param baseDamage   Raw damage value
     * @param damageType   Type of damage being dealt
     * @param armorType    Target's armor type
     * @param attacker     Attacking unit (for position-based bonuses)
     * @param target       Target unit
     * @param isCharging   Whether attacker is in charge state (cavalry)
     * @return Final damage value after modifiers
     */
    public float calculateDamage(float baseDamage, DamageType damageType, ArmorType armorType,
                                  Unit attacker, Unit target, boolean isCharging) {
        float damage = baseDamage;
        
        // Apply damage type modifier
        damage *= damageType.getMultiplier(armorType);
        
        // Apply backstab bonus if attacking from behind
        if (isBackstab(attacker, target)) {
            damage *= BACKSTAB_MULTIPLIER;
        }
        
        // Apply charge bonus
        if (isCharging) {
            damage *= CHARGE_MULTIPLIER;
        }
        
        // Apply stagger bonus
        if (isStaggered(target)) {
            damage *= STAGGER_DAMAGE_BONUS;
        }
        
        return damage;
    }
    
    /**
     * Simplified damage calculation without positional bonuses.
     */
    public float calculateDamage(float baseDamage, DamageType damageType, ArmorType armorType) {
        return baseDamage * damageType.getMultiplier(armorType);
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // DAMAGE APPLICATION
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Applies damage to a target, including poise damage.
     * 
     * @param target      Unit receiving damage
     * @param damage      Amount of HP damage
     * @param poiseDamage Amount of poise damage (0 for no poise effect)
     * @return CombatResult with outcome details
     */
    public CombatResult applyDamage(Unit target, float damage, float poiseDamage) {
        boolean wasStaggered = isStaggered(target);
        boolean killed = false;
        boolean staggered = false;
        
        // Apply HP damage
        target.takeDamage(damage);
        killed = target.isDead();
        
        // Apply poise damage if target has poise system
        if (poiseDamage > 0 && !killed) {
            float currentPoise = getPoise(target);
            float newPoise = Math.max(0, currentPoise - poiseDamage);
            setPoise(target, newPoise);
            
            // Check for stagger
            if (!wasStaggered && newPoise <= 0) {
                applyStagger(target);
                staggered = true;
            }
            
            // Reset poise regen timer
            setPoiseRegenTimer(target, POISE_REGEN_DELAY);
        }
        
        return new CombatResult(damage, killed, staggered, wasStaggered);
    }
    
    /**
     * Applies damage with automatic poise damage calculation.
     */
    public CombatResult applyDamage(Unit target, float damage) {
        return applyDamage(target, damage, damage * POISE_DAMAGE_RATIO);
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // AOE DAMAGE
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Applies area-of-effect damage centered on a position.
     * Damage falls off with distance from center.
     * 
     * @param center     Center of the AoE
     * @param radius     Effect radius
     * @param baseDamage Maximum damage at center
     * @param damageType Type of damage
     * @param source     Entity that caused the AoE (excluded from damage)
     * @param entities   All entities to check
     * @return Number of entities hit
     */
    public int applyAoeDamage(Vector3 center, float radius, float baseDamage, 
                               DamageType damageType, Entity source, List<Entity> entities) {
        int hits = 0;
        
        for (Entity entity : entities) {
            if (entity == source) continue;
            if (!(entity instanceof Unit)) continue;
            
            Unit target = (Unit) entity;
            if (target.isDead()) continue;
            
            float distance = target.getPosition().dst(center);
            if (distance > radius) continue;
            
            // Calculate falloff (linear from center to edge)
            float falloff = 1f - (distance / radius);
            float damage = baseDamage * falloff * damageType.getGroupMultiplier();
            
            // Determine armor type (simplified)
            ArmorType armor = getArmorType(target);
            damage = calculateDamage(damage, damageType, armor);
            
            applyDamage(target, damage);
            hits++;
        }
        
        return hits;
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // POISE SYSTEM
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Updates poise regeneration for a unit.
     * Call this each frame.
     */
    public void updatePoise(Unit unit, float delta) {
        if (unit.isDead()) return;
        if (isStaggered(unit)) return;
        
        float regenTimer = getPoiseRegenTimer(unit);
        if (regenTimer > 0) {
            setPoiseRegenTimer(unit, regenTimer - delta);
            return;
        }
        
        float currentPoise = getPoise(unit);
        float maxPoise = getMaxPoise(unit);
        
        if (currentPoise < maxPoise) {
            float newPoise = Math.min(maxPoise, currentPoise + POISE_REGEN_RATE * delta);
            setPoise(unit, newPoise);
        }
    }
    
    /**
     * Updates stagger state for a unit.
     * Call this each frame.
     */
    public void updateStagger(Unit unit, float delta) {
        if (!isStaggered(unit)) return;
        
        float staggerTime = getStaggerTime(unit);
        staggerTime -= delta;
        
        if (staggerTime <= 0) {
            clearStagger(unit);
        } else {
            setStaggerTime(unit, staggerTime);
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // POSITION CHECKS
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Checks if attacker is behind target (for backstab).
     */
    public boolean isBackstab(Unit attacker, Unit target) {
        if (attacker == null || target == null) return false;
        
        // Get direction from target to attacker
        Vector3 toAttacker = new Vector3(attacker.getPosition()).sub(target.getPosition());
        toAttacker.z = 0;
        toAttacker.nor();
        
        // Get target's facing direction
        Vector3 facing = target.getFacing();
        
        // Calculate angle between facing and direction to attacker
        float dot = facing.dot(toAttacker);
        float angle = (float) Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, dot))));
        
        // Backstab if attacker is behind (angle > 180 - threshold)
        return angle > (180 - BACKSTAB_ANGLE);
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // UNIT PROPERTY HELPERS (using transient fields pattern)
    // ═══════════════════════════════════════════════════════════════════════════
    
    // These methods use a simple approach - in a full implementation,
    // units would have poise fields. For now, we use max HP as poise proxy.
    
    private float getPoise(Unit unit) {
        // Use HP ratio as proxy for poise in this simplified version
        return unit.getHp();
    }
    
    private void setPoise(Unit unit, float poise) {
        // In full implementation, unit would have a poise field
        // For now, this is a no-op as we use HP as proxy
    }
    
    private float getMaxPoise(Unit unit) {
        return unit.getMaxHp();
    }
    
    private float getPoiseRegenTimer(Unit unit) {
        // Simplified - would be stored on unit
        return 0f;
    }
    
    private void setPoiseRegenTimer(Unit unit, float time) {
        // Simplified - would be stored on unit
    }
    
    private boolean isStaggered(Unit unit) {
        // Simplified - check if HP is very low
        return unit.getHp() < unit.getMaxHp() * 0.1f;
    }
    
    private void applyStagger(Unit unit) {
        // In full implementation, set stagger state on unit
    }
    
    private float getStaggerTime(Unit unit) {
        return 0f;
    }
    
    private void setStaggerTime(Unit unit, float time) {
        // Would be stored on unit
    }
    
    private void clearStagger(Unit unit) {
        // Clear stagger state
    }
    
    /**
     * Gets the armor type for a unit based on its class.
     */
    public ArmorType getArmorType(Unit unit) {
        String className = unit.getClass().getSimpleName();
        return switch (className) {
            case "King", "Guard" -> ArmorType.HEAVY;
            case "Infantry" -> ArmorType.LIGHT;
            case "Cavalry" -> ArmorType.HEAVY;
            case "Archer", "Assassin" -> ArmorType.NONE;
            case "Trebuchet", "BatteringRam" -> ArmorType.STRUCTURE;
            default -> ArmorType.LIGHT;
        };
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // COMBAT RESULT
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Result of a combat action.
     */
    public record CombatResult(
        float damageDealt,
        boolean killed,
        boolean staggered,
        boolean wasAlreadyStaggered
    ) {
        public boolean wasEffective() {
            return damageDealt > 0 || staggered;
        }
    }
}
