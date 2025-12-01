package com.castlewar.ai.behavior.leaf.tasks;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.castlewar.ai.behavior.NodeState;
import com.castlewar.ai.behavior.leaf.TaskNode;
import com.castlewar.ai.blackboard.Blackboard;
import com.castlewar.ai.blackboard.BlackboardKey;
import com.castlewar.entity.Unit;

import java.util.List;

/**
 * Task that makes the unit patrol between waypoints.
 * <p>
 * Cycles through patrol waypoints stored in blackboard.
 * Returns RUNNING continuously (patrol never "completes").
 * <p>
 * <b>Note:</b> All state (wait timer, waiting flag) is stored in the Blackboard
 * to survive node resets from reactive selectors.
 * 
 * <h2>Blackboard Keys</h2>
 * <ul>
 *   <li>Input: {@link BlackboardKey#MOVEMENT_PATROL_WAYPOINTS} - List of patrol points</li>
 *   <li>Input/Output: {@link BlackboardKey#MOVEMENT_PATROL_INDEX} - Current waypoint index</li>
 *   <li>Output: {@link BlackboardKey#MOVEMENT_CURRENT_WAYPOINT} - Current destination</li>
 *   <li>Internal: CUSTOM_FLOAT_1 - Wait timer</li>
 *   <li>Internal: CUSTOM_BOOL_1 - Waiting flag</li>
 * </ul>
 */
public class PatrolTask extends TaskNode {
    
    /** Distance to consider waypoint reached */
    private final float waypointReachDistance;
    
    /** Wait time at each waypoint */
    private final float waitTimeAtWaypoint;
    
    /**
     * Creates a patrol task with default settings.
     */
    public PatrolTask() {
        this("patrol", 2f, 1f);
    }
    
    /**
     * Creates a patrol task with custom settings.
     * 
     * @param name                  Debug name
     * @param waypointReachDistance Distance to consider reached
     * @param waitTimeAtWaypoint    Seconds to wait at each point
     */
    public PatrolTask(String name, float waypointReachDistance, float waitTimeAtWaypoint) {
        super(name);
        this.waypointReachDistance = waypointReachDistance;
        this.waitTimeAtWaypoint = waitTimeAtWaypoint;
    }
    
    @Override
    protected void onEnter(Blackboard blackboard) {
        // Initialize patrol index if not set
        if (!blackboard.has(BlackboardKey.MOVEMENT_PATROL_INDEX)) {
            blackboard.setInt(BlackboardKey.MOVEMENT_PATROL_INDEX, 0);
        }
        // Don't reset wait state here - it's stored in blackboard and survives resets
    }
    
    @Override
    protected NodeState execute(Blackboard blackboard) {
        Unit unit = blackboard.get(BlackboardKey.SELF_UNIT, Unit.class);
        if (unit == null || unit.isDead()) {
            return NodeState.FAILURE;
        }
        
        // Get waypoints
        List<Vector3> waypoints = blackboard.getList(BlackboardKey.MOVEMENT_PATROL_WAYPOINTS);
        if (waypoints == null || waypoints.isEmpty()) {
            // No waypoints - just idle
            return idleBehavior(unit, blackboard);
        }
        
        // Get wait state from blackboard (survives node resets)
        boolean waiting = blackboard.getBool(BlackboardKey.CUSTOM_BOOL_1);
        float waitTimer = blackboard.getFloat(BlackboardKey.CUSTOM_FLOAT_1);
        
        // Handle waiting at waypoint
        if (waiting) {
            waitTimer -= getDeltaTime(blackboard);
            if (waitTimer <= 0) {
                // Done waiting - advance to next waypoint
                blackboard.setBool(BlackboardKey.CUSTOM_BOOL_1, false);
                blackboard.setFloat(BlackboardKey.CUSTOM_FLOAT_1, 0f);
                advanceToNextWaypoint(blackboard, waypoints.size());
            } else {
                // Still waiting
                blackboard.setFloat(BlackboardKey.CUSTOM_FLOAT_1, waitTimer);
                unit.getVelocity().x = 0;
                unit.getVelocity().y = 0;
            }
            return NodeState.RUNNING;
        }
        
        // Get current waypoint
        int index = blackboard.getInt(BlackboardKey.MOVEMENT_PATROL_INDEX);
        if (index < 0 || index >= waypoints.size()) {
            index = 0;
            blackboard.setInt(BlackboardKey.MOVEMENT_PATROL_INDEX, 0);
        }
        
        Vector3 waypoint = waypoints.get(index);
        blackboard.setVector3(BlackboardKey.MOVEMENT_CURRENT_WAYPOINT, waypoint);
        
        // Check if reached (use 2D distance to ignore Z differences)
        float dx = unit.getPosition().x - waypoint.x;
        float dy = unit.getPosition().y - waypoint.y;
        float distance2D = (float) Math.sqrt(dx * dx + dy * dy);
        
        if (distance2D <= waypointReachDistance) {
            // Reached waypoint - start waiting
            blackboard.setBool(BlackboardKey.CUSTOM_BOOL_1, true);
            blackboard.setFloat(BlackboardKey.CUSTOM_FLOAT_1, waitTimeAtWaypoint);
            unit.setTargetPosition(null);
            unit.getVelocity().x = 0;
            unit.getVelocity().y = 0;
            return NodeState.RUNNING;
        }
        
        // Move toward waypoint (2D movement)
        unit.setTargetPosition(waypoint);
        float dirX = waypoint.x - unit.getPosition().x;
        float dirY = waypoint.y - unit.getPosition().y;
        float len = (float) Math.sqrt(dirX * dirX + dirY * dirY);
        if (len > 0.01f) {
            dirX /= len;
            dirY /= len;
        }
        
        float speed = 3f; // Patrol speed (slower than normal)
        unit.getVelocity().x = dirX * speed;
        unit.getVelocity().y = dirY * speed;
        
        return NodeState.RUNNING;
    }
    
    @Override
    protected void onExit(Blackboard blackboard) {
        Unit unit = blackboard.get(BlackboardKey.SELF_UNIT, Unit.class);
        if (unit != null) {
            unit.setTargetPosition(null);
        }
    }
    
    /**
     * Advances to the next waypoint in the list.
     */
    private void advanceToNextWaypoint(Blackboard blackboard, int waypointCount) {
        int current = blackboard.getInt(BlackboardKey.MOVEMENT_PATROL_INDEX);
        int next = (current + 1) % waypointCount;
        blackboard.setInt(BlackboardKey.MOVEMENT_PATROL_INDEX, next);
    }
    
    /**
     * Idle behavior when no waypoints defined.
     */
    private NodeState idleBehavior(Unit unit, Blackboard blackboard) {
        // Just stand still and look around occasionally
        unit.setTargetPosition(null);
        unit.getVelocity().x = 0;
        unit.getVelocity().y = 0;
        
        // Occasionally rotate facing
        if (MathUtils.random() < 0.01f) {
            float angle = MathUtils.random(-45f, 45f);
            unit.getFacing().rotate(Vector3.Z, angle);
        }
        
        return NodeState.RUNNING;
    }
    
    /**
     * Gets delta time from blackboard (with fallback).
     */
    private float getDeltaTime(Blackboard blackboard) {
        float dt = blackboard.getFloat(BlackboardKey.CUSTOM_1); // DELTA_TIME_KEY
        return dt > 0 ? dt : 1f / 60f;
    }
}
