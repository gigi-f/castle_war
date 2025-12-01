package com.castlewar.ai.behavior.leaf.conditions;

import com.castlewar.ai.behavior.leaf.ConditionNode;
import com.castlewar.ai.blackboard.Blackboard;
import com.castlewar.ai.blackboard.BlackboardKey;
import com.castlewar.entity.Unit;

/**
 * Condition that checks if unit health is below a threshold.
 * <p>
 * Returns SUCCESS if health percentage is at or below threshold.
 * 
 * <h2>Blackboard Keys</h2>
 * <ul>
 *   <li>Input: {@link BlackboardKey#SELF_UNIT} - The unit to check</li>
 * </ul>
 */
public class IsLowHealth extends ConditionNode {
    
    /**
     * Creates condition with 30% health threshold.
     */
    public IsLowHealth() {
        this(0.3f);
    }
    
    /**
     * Creates condition with custom threshold.
     * 
     * @param threshold Health percentage (0.0 to 1.0)
     */
    public IsLowHealth(float threshold) {
        super("isLowHealth", bb -> {
            Unit unit = bb.get(BlackboardKey.SELF_UNIT, Unit.class);
            if (unit == null) {
                return false;
            }
            float healthPercent = unit.getHp() / unit.getMaxHp();
            return healthPercent <= threshold;
        });
    }
    
    /**
     * Factory for standard low health check (30%).
     */
    public static IsLowHealth standard() {
        return new IsLowHealth(0.3f);
    }
    
    /**
     * Factory for critical health check (10%).
     */
    public static IsLowHealth critical() {
        return new IsLowHealth(0.1f);
    }
    
    /**
     * Factory for half health check (50%).
     */
    public static IsLowHealth half() {
        return new IsLowHealth(0.5f);
    }
}
