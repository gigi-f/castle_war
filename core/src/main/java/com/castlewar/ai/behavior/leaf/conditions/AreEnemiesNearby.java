package com.castlewar.ai.behavior.leaf.conditions;

import com.castlewar.ai.behavior.leaf.ConditionNode;
import com.castlewar.ai.blackboard.BlackboardKey;
import com.castlewar.entity.Entity;
import com.castlewar.entity.Unit;

import java.util.List;

/**
 * Condition that checks if enemies are within detection range.
 * <p>
 * Returns SUCCESS if any enemy is detected in the threat list.
 * 
 * <h2>Blackboard Keys</h2>
 * <ul>
 *   <li>Input: {@link BlackboardKey#THREAT_ENEMIES_IN_RANGE} - List of detected enemies</li>
 * </ul>
 */
public class AreEnemiesNearby extends ConditionNode {
    
    /**
     * Creates condition that triggers on any enemy.
     */
    public AreEnemiesNearby() {
        this(1);
    }
    
    /**
     * Creates condition requiring minimum enemy count.
     * 
     * @param minEnemies Minimum enemies required
     */
    public AreEnemiesNearby(int minEnemies) {
        super("areEnemiesNearby", bb -> {
            List<Entity> enemies = bb.getList(BlackboardKey.THREAT_ENEMIES_IN_RANGE);
            
            if (enemies == null || enemies.isEmpty()) {
                return false;
            }
            
            int requiredCount = Math.max(1, minEnemies);
            
            // Count alive enemies
            int aliveCount = 0;
            for (Entity enemy : enemies) {
                boolean alive = !(enemy instanceof Unit) || !((Unit) enemy).isDead();
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
     * Factory for any enemy detection.
     */
    public static AreEnemiesNearby any() {
        return new AreEnemiesNearby(1);
    }
    
    /**
     * Factory for multiple enemies (ambush detection).
     */
    public static AreEnemiesNearby multiple() {
        return new AreEnemiesNearby(3);
    }
    
    /**
     * Factory for overwhelming force detection.
     */
    public static AreEnemiesNearby overwhelming() {
        return new AreEnemiesNearby(5);
    }
}
