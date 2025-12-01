package com.castlewar.ai.behavior.leaf.conditions;

import com.castlewar.ai.behavior.leaf.ConditionNode;
import com.castlewar.ai.blackboard.BlackboardKey;
import com.castlewar.entity.Entity;
import com.castlewar.entity.Unit;

/**
 * Condition that checks if the unit has a valid combat target.
 * <p>
 * Returns SUCCESS if target exists and is alive.
 * 
 * <h2>Blackboard Keys</h2>
 * <ul>
 *   <li>Input: {@link BlackboardKey#COMBAT_CURRENT_TARGET} - Current target</li>
 * </ul>
 */
public class HasTarget extends ConditionNode {
    
    public HasTarget() {
        super("hasTarget", bb -> {
            Entity target = bb.get(BlackboardKey.COMBAT_CURRENT_TARGET, Entity.class);
            
            if (target == null) {
                return false;
            }
            
            // Check if target is still alive (only Unit has isDead)
            if (target instanceof Unit && ((Unit) target).isDead()) {
                // Clear stale target
                bb.clear(BlackboardKey.COMBAT_CURRENT_TARGET);
                return false;
            }
            
            return true;
        });
    }
    
    /**
     * Factory method.
     */
    public static HasTarget create() {
        return new HasTarget();
    }
}
