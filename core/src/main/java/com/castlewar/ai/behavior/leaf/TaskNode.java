package com.castlewar.ai.behavior.leaf;

import com.castlewar.ai.behavior.BehaviorNode;
import com.castlewar.ai.behavior.NodeState;
import com.castlewar.ai.blackboard.Blackboard;

import java.util.function.Function;

/**
 * Leaf node that performs an action/task.
 * <p>
 * Tasks are the "doing" nodes in a behavior tree. They can:
 * <ul>
 *   <li>Complete instantly (return SUCCESS/FAILURE)</li>
 *   <li>Take multiple ticks (return RUNNING until done)</li>
 * </ul>
 * 
 * <h2>Usage</h2>
 * <pre>{@code
 * // Instant task (lambda)
 * TaskNode setTarget = new TaskNode("set-target", bb -> {
 *     Entity enemy = findNearestEnemy();
 *     bb.set(BlackboardKey.COMBAT_CURRENT_TARGET, enemy);
 *     return NodeState.SUCCESS;
 * });
 * 
 * // Multi-tick task (extend class)
 * public class MoveToTargetTask extends TaskNode {
 *     public MoveToTargetTask() {
 *         super("move-to-target");
 *     }
 *     
 *     @Override
 *     protected NodeState execute(Blackboard bb) {
 *         Vector3 target = bb.getVector3(BlackboardKey.MOVEMENT_CURRENT_WAYPOINT);
 *         if (arrivedAt(target)) return NodeState.SUCCESS;
 *         moveToward(target);
 *         return NodeState.RUNNING;
 *     }
 * }
 * }</pre>
 * 
 * <h2>Lifecycle</h2>
 * Override {@link #onEnter(Blackboard)} and {@link #onExit(Blackboard)} for setup/cleanup:
 * <pre>{@code
 * protected void onEnter(Blackboard bb) {
 *     startAnimation("attack");
 * }
 * 
 * protected void onExit(Blackboard bb) {
 *     stopAnimation("attack");
 * }
 * }</pre>
 * 
 * @see ConditionNode
 */
public class TaskNode extends BehaviorNode {
    
    /** Optional task function for simple tasks */
    private final Function<Blackboard, NodeState> taskFunction;
    
    /**
     * Creates a task node to be extended.
     * Subclasses should override {@link #execute(Blackboard)}.
     * 
     * @param name Debug name
     */
    public TaskNode(String name) {
        super(name);
        this.taskFunction = null;
    }
    
    /**
     * Creates a task node with a function.
     * 
     * @param name         Debug name
     * @param taskFunction Function that performs the task and returns the state
     */
    public TaskNode(String name, Function<Blackboard, NodeState> taskFunction) {
        super(name);
        this.taskFunction = taskFunction;
    }
    
    /**
     * Creates a task node with a function and auto-generated name.
     * 
     * @param taskFunction Function that performs the task and returns the state
     */
    public TaskNode(Function<Blackboard, NodeState> taskFunction) {
        super();
        this.taskFunction = taskFunction;
    }
    
    @Override
    protected NodeState execute(Blackboard blackboard) {
        if (taskFunction != null) {
            try {
                return taskFunction.apply(blackboard);
            } catch (Exception e) {
                // If task throws, treat as failure
                return NodeState.FAILURE;
            }
        }
        // Default implementation - subclasses should override
        return NodeState.SUCCESS;
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // FACTORY METHODS for common tasks
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Creates a task that succeeds immediately.
     * Useful for placeholder or debug purposes.
     * 
     * @param name Debug name
     * @return TaskNode that always returns SUCCESS
     */
    public static TaskNode success(String name) {
        return new TaskNode(name, bb -> NodeState.SUCCESS);
    }
    
    /**
     * Creates a task that fails immediately.
     * Useful for placeholder or debug purposes.
     * 
     * @param name Debug name
     * @return TaskNode that always returns FAILURE
     */
    public static TaskNode failure(String name) {
        return new TaskNode(name, bb -> NodeState.FAILURE);
    }
    
    /**
     * Creates a task that runs indefinitely.
     * Useful for testing or as a placeholder for async tasks.
     * 
     * @param name Debug name
     * @return TaskNode that always returns RUNNING
     */
    public static TaskNode running(String name) {
        return new TaskNode(name, bb -> NodeState.RUNNING);
    }
    
    /**
     * Creates a task that sets a blackboard value.
     * 
     * @param name  Debug name
     * @param key   Blackboard key
     * @param value Value to set
     * @return TaskNode that sets the value and returns SUCCESS
     */
    public static TaskNode setValue(String name, com.castlewar.ai.blackboard.BlackboardKey key, Object value) {
        return new TaskNode(name, bb -> {
            bb.set(key, value);
            return NodeState.SUCCESS;
        });
    }
    
    /**
     * Creates a task that clears a blackboard value.
     * 
     * @param name Debug name
     * @param key  Blackboard key to clear
     * @return TaskNode that removes the value and returns SUCCESS
     */
    public static TaskNode clearValue(String name, com.castlewar.ai.blackboard.BlackboardKey key) {
        return new TaskNode(name, bb -> {
            bb.remove(key);
            return NodeState.SUCCESS;
        });
    }
    
    /**
     * Creates a task that logs a message (for debugging).
     * 
     * @param message Message to log
     * @return TaskNode that logs and returns SUCCESS
     */
    public static TaskNode log(String message) {
        return new TaskNode("log", bb -> {
            System.out.println("[BT] " + message);
            return NodeState.SUCCESS;
        });
    }
    
    /**
     * Creates a task that waits for a specified duration.
     * Note: This is a simple implementation; for production use, consider
     * using a proper timer service.
     * 
     * @param name    Debug name
     * @param seconds Duration to wait
     * @return TaskNode that returns RUNNING for the duration, then SUCCESS
     */
    public static TaskNode wait(String name, float seconds) {
        return new WaitTask(name, seconds);
    }
    
    /**
     * Internal class for wait task with state.
     */
    private static class WaitTask extends TaskNode {
        private final float duration;
        private float elapsed;
        
        WaitTask(String name, float duration) {
            super(name);
            this.duration = duration;
            this.elapsed = 0f;
        }
        
        @Override
        protected void onEnter(Blackboard blackboard) {
            elapsed = 0f;
        }
        
        @Override
        protected NodeState execute(Blackboard blackboard) {
            // Note: In a real implementation, delta time would come from the blackboard
            // or a time service. For now, assume ~60 FPS.
            elapsed += 1f / 60f;
            
            if (elapsed >= duration) {
                return NodeState.SUCCESS;
            }
            return NodeState.RUNNING;
        }
    }
}
