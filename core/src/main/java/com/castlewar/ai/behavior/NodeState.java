package com.castlewar.ai.behavior;

/**
 * Represents the execution state of a behavior tree node.
 * <p>
 * Behavior trees evaluate nodes each tick, and each node returns one of these states:
 * <ul>
 *   <li>{@link #SUCCESS} - The node completed successfully</li>
 *   <li>{@link #FAILURE} - The node failed to complete its task</li>
 *   <li>{@link #RUNNING} - The node is still executing (will be ticked again)</li>
 * </ul>
 */
public enum NodeState {
    /**
     * The node completed its task successfully.
     * For conditions, this means the condition is true.
     * For tasks, this means the action completed.
     */
    SUCCESS,
    
    /**
     * The node failed to complete its task.
     * For conditions, this means the condition is false.
     * For tasks, this means the action could not be performed.
     */
    FAILURE,
    
    /**
     * The node is still executing and needs more ticks.
     * The behavior tree will continue ticking this node until it returns
     * SUCCESS or FAILURE.
     */
    RUNNING
}
