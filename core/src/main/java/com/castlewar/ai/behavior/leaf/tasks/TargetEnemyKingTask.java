package com.castlewar.ai.behavior.leaf.tasks;

import com.badlogic.gdx.math.Vector3;
import com.castlewar.ai.behavior.NodeState;
import com.castlewar.ai.behavior.leaf.TaskNode;
import com.castlewar.ai.blackboard.Blackboard;
import com.castlewar.ai.blackboard.BlackboardKey;
import com.castlewar.entity.King;
import com.castlewar.entity.Unit;
import com.castlewar.simulation.TeamObjective;

/**
 * Task that sets the enemy king as the primary target.
 * <p>
 * This task supports the collective team goal of regicide - killing the enemy king.
 * It sets both the movement target and combat target to the enemy king's position.
 * 
 * <h2>Behavior</h2>
 * <ul>
 *   <li>Queries TeamObjective singleton for enemy king location</li>
 *   <li>Sets king as COMBAT_CURRENT_TARGET if alive</li>
 *   <li>Sets king position as MOVEMENT_TARGET_POSITION</li>
 *   <li>Returns SUCCESS if king is targeted, FAILURE if no king exists</li>
 * </ul>
 * 
 * <h2>Blackboard Keys</h2>
 * <ul>
 *   <li>Input: {@link BlackboardKey#SELF_UNIT} - The unit performing the action</li>
 *   <li>Output: {@link BlackboardKey#COMBAT_CURRENT_TARGET} - Set to enemy king</li>
 *   <li>Output: {@link BlackboardKey#MOVEMENT_TARGET_POSITION} - Set to king position</li>
 *   <li>Output: {@link BlackboardKey#GOAL_PRIMARY_TARGET} - Set to enemy king</li>
 * </ul>
 */
public class TargetEnemyKingTask extends TaskNode {
    
    /**
     * Creates a target enemy king task.
     */
    public TargetEnemyKingTask() {
        super("target-enemy-king");
    }
    
    /**
     * Creates a target enemy king task with custom name.
     * 
     * @param name Debug name for the task
     */
    public TargetEnemyKingTask(String name) {
        super(name);
    }
    
    /** Debug: Track if we've logged recently */
    private static long lastLogTime = 0;
    private static final long LOG_INTERVAL_MS = 5000; // Log every 5 seconds max
    
    @Override
    protected NodeState execute(Blackboard blackboard) {
        Unit unit = blackboard.get(BlackboardKey.SELF_UNIT, Unit.class);
        if (unit == null || unit.isDead() || unit.getTeam() == null) {
            return NodeState.FAILURE;
        }
        
        TeamObjective objective = TeamObjective.getInstance();
        King enemyKing = objective.getEnemyKing(unit.getTeam());
        
        // Debug logging (throttled)
        long now = System.currentTimeMillis();
        if (now - lastLogTime > LOG_INTERVAL_MS) {
            lastLogTime = now;
            System.out.println("[TargetEnemyKingTask] Unit " + unit.getName() + " (" + unit.getTeam() + 
                ") looking for enemy king. Found: " + (enemyKing != null ? enemyKing.getName() : "NULL"));
        }
        
        if (enemyKing == null || enemyKing.isDead()) {
            // No enemy king to target - victory!
            return NodeState.FAILURE;
        }
        
        // Set the enemy king as our primary target
        blackboard.set(BlackboardKey.GOAL_PRIMARY_TARGET, enemyKing);
        blackboard.set(BlackboardKey.COMBAT_CURRENT_TARGET, enemyKing);
        blackboard.setVector3(BlackboardKey.MOVEMENT_TARGET_POSITION, enemyKing.getPosition());
        
        // Also set target position on the unit directly for movement
        unit.setTargetPosition(enemyKing.getPosition());
        
        return NodeState.SUCCESS;
    }
    
    /**
     * Factory method for creating the task.
     * 
     * @return New TargetEnemyKingTask instance
     */
    public static TargetEnemyKingTask create() {
        return new TargetEnemyKingTask();
    }
    
    /**
     * Factory method with custom name.
     * 
     * @param name Debug name
     * @return New TargetEnemyKingTask instance
     */
    public static TargetEnemyKingTask create(String name) {
        return new TargetEnemyKingTask(name);
    }
}
