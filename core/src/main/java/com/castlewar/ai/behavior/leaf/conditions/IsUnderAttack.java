package com.castlewar.ai.behavior.leaf.conditions;

import com.castlewar.ai.behavior.leaf.ConditionNode;
import com.castlewar.ai.blackboard.BlackboardKey;

/**
 * Condition that checks if unit is currently under attack.
 * <p>
 * Returns SUCCESS if the "under attack" flag is set and recent.
 * 
 * <h2>Blackboard Keys</h2>
 * <ul>
 *   <li>Input: {@link BlackboardKey#THREAT_UNDER_ATTACK} - Under attack flag</li>
 *   <li>Input: {@link BlackboardKey#THREAT_LAST_DAMAGE_TIME} - Time of last damage (optional)</li>
 * </ul>
 */
public class IsUnderAttack extends ConditionNode {
    
    /**
     * Creates condition checking only the flag.
     */
    public IsUnderAttack() {
        this(Float.MAX_VALUE);
    }
    
    /**
     * Creates condition with recency requirement.
     * 
     * @param recentThreshold Seconds since last damage
     */
    public IsUnderAttack(float recentThreshold) {
        super("isUnderAttack", bb -> {
            boolean underAttack = bb.getBool(BlackboardKey.THREAT_UNDER_ATTACK);
            
            if (!underAttack) {
                return false;
            }
            
            // Check recency if threshold is set
            if (recentThreshold < Float.MAX_VALUE) {
                float lastDamageTime = bb.getFloat(BlackboardKey.THREAT_LAST_DAMAGE_TIME);
                float currentTime = bb.getFloat(BlackboardKey.CUSTOM_2); // CURRENT_TIME_KEY
                
                if (currentTime - lastDamageTime > recentThreshold) {
                    // Attack was too long ago - clear flag
                    bb.setBool(BlackboardKey.THREAT_UNDER_ATTACK, false);
                    return false;
                }
            }
            
            return true;
        });
    }
    
    /**
     * Factory for immediate check.
     */
    public static IsUnderAttack now() {
        return new IsUnderAttack();
    }
    
    /**
     * Factory for recent attack (within 2 seconds).
     */
    public static IsUnderAttack recent() {
        return new IsUnderAttack(2f);
    }
    
    /**
     * Factory for combat mode (within 5 seconds).
     */
    public static IsUnderAttack inCombat() {
        return new IsUnderAttack(5f);
    }
}
