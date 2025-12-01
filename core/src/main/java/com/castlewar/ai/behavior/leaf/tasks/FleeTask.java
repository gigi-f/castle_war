package com.castlewar.ai.behavior.leaf.tasks;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.castlewar.ai.behavior.NodeState;
import com.castlewar.ai.behavior.leaf.TaskNode;
import com.castlewar.ai.blackboard.Blackboard;
import com.castlewar.ai.blackboard.BlackboardKey;
import com.castlewar.entity.Unit;

/**
 * Task that makes the unit flee from threats.
 * <p>
 * Calculates a direction away from the threat and moves there.
 * Returns RUNNING while fleeing, SUCCESS when safe distance reached.
 * 
 * <h2>Blackboard Keys</h2>
 * <ul>
 *   <li>Input: {@link BlackboardKey#COMBAT_CURRENT_TARGET} - Threat to flee from</li>
 *   <li>Input: {@link BlackboardKey#PERCEPTION_LAST_KNOWN_THREAT_POS} - Alternate threat pos</li>
 *   <li>Output: {@link BlackboardKey#MOVEMENT_FLEE_DESTINATION} - Calculated flee point</li>
 * </ul>
 */
public class FleeTask extends TaskNode {
    
    /** Distance to consider safe */
    private final float safeDistance;
    
    /** How far to flee in one calculation */
    private final float fleeDistance;
    
    /** Cached unit reference */
    private Unit unit;
    
    /** Calculated flee destination */
    private final Vector3 fleeTarget = new Vector3();
    
    /**
     * Creates a flee task with default settings.
     */
    public FleeTask() {
        this("flee", 15f, 10f);
    }
    
    /**
     * Creates a flee task with custom distances.
     * 
     * @param name         Debug name
     * @param safeDistance Distance to consider safe
     * @param fleeDistance How far to flee
     */
    public FleeTask(String name, float safeDistance, float fleeDistance) {
        super(name);
        this.safeDistance = safeDistance;
        this.fleeDistance = fleeDistance;
    }
    
    @Override
    protected void onEnter(Blackboard blackboard) {
        unit = blackboard.get(BlackboardKey.SELF_UNIT, Unit.class);
        calculateFleeTarget(blackboard);
    }
    
    @Override
    protected NodeState execute(Blackboard blackboard) {
        if (unit == null || unit.isDead()) {
            return NodeState.FAILURE;
        }
        
        // Get threat position
        Vector3 threatPos = getThreatPosition(blackboard);
        if (threatPos == null) {
            // No threat, fleeing succeeded
            return NodeState.SUCCESS;
        }
        
        // Check if we're safe
        float distToThreat = unit.getPosition().dst(threatPos);
        if (distToThreat >= safeDistance) {
            unit.setTargetPosition(null);
            return NodeState.SUCCESS;
        }
        
        // Recalculate flee target periodically
        if (unit.getPosition().dst(fleeTarget) < 2f) {
            calculateFleeTarget(blackboard);
        }
        
        // Move away from threat
        unit.setTargetPosition(fleeTarget);
        
        Vector3 direction = new Vector3(fleeTarget).sub(unit.getPosition()).nor();
        float speed = 5f; // Flee speed
        unit.getVelocity().x = direction.x * speed;
        unit.getVelocity().y = direction.y * speed;
        
        return NodeState.RUNNING;
    }
    
    @Override
    protected void onExit(Blackboard blackboard) {
        if (unit != null) {
            unit.setTargetPosition(null);
        }
    }
    
    /**
     * Gets the threat position from blackboard.
     */
    private Vector3 getThreatPosition(Blackboard blackboard) {
        // Try current target first
        Object target = blackboard.get(BlackboardKey.COMBAT_CURRENT_TARGET);
        if (target instanceof Unit && !((Unit) target).isDead()) {
            return ((Unit) target).getPosition();
        }
        
        // Fall back to last known position
        return blackboard.getVector3(BlackboardKey.PERCEPTION_LAST_KNOWN_THREAT_POS);
    }
    
    /**
     * Calculates a flee destination away from threats.
     */
    private void calculateFleeTarget(Blackboard blackboard) {
        Vector3 threatPos = getThreatPosition(blackboard);
        if (threatPos == null || unit == null) {
            return;
        }
        
        Vector3 pos = unit.getPosition();
        
        // Direction away from threat
        Vector3 awayDir = new Vector3(pos).sub(threatPos);
        awayDir.z = 0;
        
        if (awayDir.len2() < 0.01f) {
            // On top of threat, pick random direction
            float angle = MathUtils.random(360f);
            awayDir.set(MathUtils.cosDeg(angle), MathUtils.sinDeg(angle), 0);
        } else {
            awayDir.nor();
        }
        
        // Add some randomness to avoid predictable fleeing
        float randomAngle = MathUtils.random(-30f, 30f);
        awayDir.rotate(Vector3.Z, randomAngle);
        
        // Calculate target position
        fleeTarget.set(pos).add(awayDir.scl(fleeDistance));
        blackboard.setVector3(BlackboardKey.MOVEMENT_FLEE_DESTINATION, fleeTarget);
    }
}
