package com.castlewar.ai.behavior.leaf.tasks;

import com.badlogic.gdx.math.Vector3;
import com.castlewar.ai.behavior.NodeState;
import com.castlewar.ai.behavior.leaf.TaskNode;
import com.castlewar.ai.blackboard.Blackboard;
import com.castlewar.ai.blackboard.BlackboardKey;
import com.castlewar.entity.Entity;
import com.castlewar.entity.Unit;

/**
 * Task that moves the unit toward a target position.
 * <p>
 * Reads target from blackboard key and updates unit's targetPosition.
 * Returns RUNNING while moving, SUCCESS when arrived.
 * 
 * <h2>Blackboard Keys</h2>
 * <ul>
 *   <li>Input: {@link BlackboardKey#MOVEMENT_CURRENT_WAYPOINT} - Target position</li>
 *   <li>Input: {@link BlackboardKey#SELF_POSITION} - Current position</li>
 *   <li>Output: {@link BlackboardKey#MOVEMENT_ARRIVED} - Set to true on arrival</li>
 * </ul>
 */
public class MoveToTargetTask extends TaskNode {
    
    /** Distance threshold for arrival */
    private final float arrivalDistance;
    
    /** Blackboard key for target position */
    private final BlackboardKey targetKey;
    
    /** Cached unit reference */
    private Unit unit;
    
    /**
     * Creates a move task with default settings.
     */
    public MoveToTargetTask() {
        this("move-to-target", BlackboardKey.MOVEMENT_CURRENT_WAYPOINT, 1.5f);
    }
    
    /**
     * Creates a move task targeting a specific blackboard key.
     * 
     * @param name           Debug name
     * @param targetKey      Blackboard key for target position
     * @param arrivalDistance Distance to consider arrived
     */
    public MoveToTargetTask(String name, BlackboardKey targetKey, float arrivalDistance) {
        super(name);
        this.targetKey = targetKey;
        this.arrivalDistance = arrivalDistance;
    }
    
    @Override
    protected void onEnter(Blackboard blackboard) {
        unit = blackboard.get(BlackboardKey.SELF_UNIT, Unit.class);
        blackboard.setBool(BlackboardKey.MOVEMENT_ARRIVED, false);
    }
    
    @Override
    protected NodeState execute(Blackboard blackboard) {
        if (unit == null || unit.isDead()) {
            return NodeState.FAILURE;
        }
        
        Vector3 target = blackboard.getVector3(targetKey);
        if (target == null) {
            return NodeState.FAILURE;
        }
        
        Vector3 currentPos = unit.getPosition();
        float distance = currentPos.dst(target);
        
        if (distance <= arrivalDistance) {
            // Arrived
            unit.setTargetPosition(null);
            blackboard.setBool(BlackboardKey.MOVEMENT_ARRIVED, true);
            return NodeState.SUCCESS;
        }
        
        // Set target position for unit movement
        unit.setTargetPosition(target);
        
        // Calculate velocity toward target
        Vector3 direction = new Vector3(target).sub(currentPos).nor();
        float speed = unit.getVelocity().len();
        if (speed < 0.1f) speed = 4f; // Default speed
        
        unit.getVelocity().x = direction.x * speed;
        unit.getVelocity().y = direction.y * speed;
        
        return NodeState.RUNNING;
    }
    
    @Override
    protected void onExit(Blackboard blackboard) {
        // Stop movement when task exits
        if (unit != null) {
            unit.setTargetPosition(null);
        }
    }
}
