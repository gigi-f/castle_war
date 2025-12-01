package com.castlewar.ai.behavior.leaf.conditions;

import com.castlewar.ai.behavior.leaf.ConditionNode;
import com.castlewar.ai.blackboard.BlackboardKey;
import com.castlewar.entity.Entity;
import com.castlewar.entity.Unit;

/**
 * Condition that checks if current target is within attack range.
 * <p>
 * Returns SUCCESS if distance to target <= attack range.
 * 
 * <h2>Blackboard Keys</h2>
 * <ul>
 *   <li>Input: {@link BlackboardKey#SELF_UNIT} - The attacking unit</li>
 *   <li>Input: {@link BlackboardKey#COMBAT_CURRENT_TARGET} - Target entity</li>
 *   <li>Input: {@link BlackboardKey#COMBAT_ATTACK_RANGE} - Attack range (optional, defaults to 1.5)</li>
 * </ul>
 */
public class IsTargetInRange extends ConditionNode {
    
    /**
     * Creates condition using blackboard attack range.
     */
    public IsTargetInRange() {
        this(-1f);
    }
    
    /**
     * Creates condition with explicit range.
     * 
     * @param rangeOverride Attack range override (-1 to use blackboard)
     */
    public IsTargetInRange(float rangeOverride) {
        super("isTargetInRange", bb -> {
            Unit unit = bb.get(BlackboardKey.SELF_UNIT, Unit.class);
            Entity target = bb.get(BlackboardKey.COMBAT_CURRENT_TARGET, Entity.class);
            
            if (unit == null || target == null) {
                return false;
            }
            
            // Check if target is dead (only Unit has isDead)
            if (target instanceof Unit && ((Unit) target).isDead()) {
                return false;
            }
            
            // Get attack range
            float range;
            if (rangeOverride > 0) {
                range = rangeOverride;
            } else {
                range = bb.getFloat(BlackboardKey.COMBAT_ATTACK_RANGE);
                if (range <= 0) {
                    range = 1.5f; // Default melee range
                }
            }
            
            // Check distance
            float distance = unit.getPosition().dst(target.getPosition());
            return distance <= range;
        });
    }
    
    /**
     * Factory for melee range (1.5 units).
     */
    public static IsTargetInRange melee() {
        return new IsTargetInRange(1.5f);
    }
    
    /**
     * Factory for short range (5 units).
     */
    public static IsTargetInRange shortRange() {
        return new IsTargetInRange(5f);
    }
    
    /**
     * Factory for ranged attacks (15 units).
     */
    public static IsTargetInRange ranged() {
        return new IsTargetInRange(15f);
    }
    
    /**
     * Factory using blackboard attack range.
     */
    public static IsTargetInRange fromBlackboard() {
        return new IsTargetInRange();
    }
}
