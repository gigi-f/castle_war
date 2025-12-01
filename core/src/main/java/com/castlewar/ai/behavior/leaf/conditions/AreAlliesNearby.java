package com.castlewar.ai.behavior.leaf.conditions;

import com.castlewar.ai.behavior.leaf.ConditionNode;
import com.castlewar.ai.blackboard.BlackboardKey;
import com.castlewar.entity.Entity;
import com.castlewar.entity.Unit;

import java.util.List;

/**
 * Condition that checks if friendly units are nearby.
 * <p>
 * Returns SUCCESS if allies are within detection range.
 * 
 * <h2>Blackboard Keys</h2>
 * <ul>
 *   <li>Input: {@link BlackboardKey#SOCIAL_NEARBY_ALLIES} - List of nearby allies</li>
 * </ul>
 */
public class AreAlliesNearby extends ConditionNode {
    
    /**
     * Creates condition requiring at least one ally.
     */
    public AreAlliesNearby() {
        this(1);
    }
    
    /**
     * Creates condition requiring minimum ally count.
     * 
     * @param minAllies Minimum allies required
     */
    public AreAlliesNearby(int minAllies) {
        super("areAlliesNearby", bb -> {
            List<Entity> allies = bb.getList(BlackboardKey.SOCIAL_NEARBY_ALLIES);
            
            if (allies == null || allies.isEmpty()) {
                return false;
            }
            
            int requiredCount = Math.max(1, minAllies);
            
            // Count alive allies
            int aliveCount = 0;
            for (Entity ally : allies) {
                boolean alive = !(ally instanceof Unit) || !((Unit) ally).isDead();
                if (alive) {
                    aliveCount++;
                    if (aliveCount >= requiredCount) {
                        return true;
                    }
                }
            }
            
            return false;
        });
    }
    
    /**
     * Factory for any ally nearby.
     */
    public static AreAlliesNearby any() {
        return new AreAlliesNearby(1);
    }
    
    /**
     * Factory for squad presence (3+ allies).
     */
    public static AreAlliesNearby squad() {
        return new AreAlliesNearby(3);
    }
    
    /**
     * Factory for formation strength (5+ allies).
     */
    public static AreAlliesNearby formation() {
        return new AreAlliesNearby(5);
    }
}
