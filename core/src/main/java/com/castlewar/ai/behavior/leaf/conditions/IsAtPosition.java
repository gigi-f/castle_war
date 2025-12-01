package com.castlewar.ai.behavior.leaf.conditions;

import com.badlogic.gdx.math.Vector3;
import com.castlewar.ai.behavior.leaf.ConditionNode;
import com.castlewar.ai.blackboard.BlackboardKey;
import com.castlewar.entity.Unit;

/**
 * Condition that checks if unit is at a specific position.
 * <p>
 * Returns SUCCESS if distance to target position is within threshold.
 * 
 * <h2>Blackboard Keys</h2>
 * <ul>
 *   <li>Input: {@link BlackboardKey#SELF_UNIT} - The unit</li>
 *   <li>Input: Configurable key for target position</li>
 * </ul>
 */
public class IsAtPosition extends ConditionNode {
    
    /**
     * Creates condition with movement target.
     */
    public IsAtPosition() {
        this(BlackboardKey.MOVEMENT_TARGET_POSITION, 1f);
    }
    
    /**
     * Creates condition with custom position key.
     * 
     * @param positionKey Key containing target position
     * @param threshold   Distance to consider "at position"
     */
    public IsAtPosition(BlackboardKey positionKey, float threshold) {
        super("isAtPosition", bb -> {
            Unit unit = bb.get(BlackboardKey.SELF_UNIT, Unit.class);
            Vector3 target = bb.get(positionKey, Vector3.class);
            
            if (unit == null || target == null) {
                return false;
            }
            
            float distance = unit.getPosition().dst(target);
            return distance <= threshold;
        });
    }
    
    /**
     * Factory for movement target.
     */
    public static IsAtPosition atMovementTarget() {
        return new IsAtPosition(BlackboardKey.MOVEMENT_TARGET_POSITION, 1f);
    }
    
    /**
     * Factory for current waypoint.
     */
    public static IsAtPosition atWaypoint() {
        return new IsAtPosition(BlackboardKey.MOVEMENT_CURRENT_WAYPOINT, 2f);
    }
    
    /**
     * Factory for home position.
     */
    public static IsAtPosition atHome() {
        return new IsAtPosition(BlackboardKey.MOVEMENT_HOME_POSITION, 3f);
    }
}
