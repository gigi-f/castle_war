package com.castlewar.ai.behavior;

import com.castlewar.ai.blackboard.Blackboard;

/**
 * Abstract base class for all behavior tree nodes.
 * <p>
 * Behavior tree nodes form a tree structure where:
 * <ul>
 *   <li>Composite nodes (Selector, Sequence, Parallel) have multiple children</li>
 *   <li>Decorator nodes have exactly one child and modify its behavior</li>
 *   <li>Leaf nodes (Condition, Task) have no children and perform actual work</li>
 * </ul>
 * 
 * <h2>Lifecycle</h2>
 * <ol>
 *   <li>{@link #onEnter(Blackboard)} - Called once when node starts executing</li>
 *   <li>{@link #execute(Blackboard)} - Called each tick while RUNNING</li>
 *   <li>{@link #onExit(Blackboard)} - Called once when node finishes (SUCCESS/FAILURE)</li>
 * </ol>
 * 
 * <h2>Usage</h2>
 * Subclasses should override {@link #execute(Blackboard)} and optionally
 * {@link #onEnter(Blackboard)} and {@link #onExit(Blackboard)}.
 * 
 * @see NodeState
 * @see Blackboard
 */
public abstract class BehaviorNode {
    
    /** Current state of this node */
    protected NodeState state = NodeState.FAILURE;
    
    /** Whether this node is currently running (between onEnter and onExit) */
    protected boolean isRunning = false;
    
    /** Optional name for debugging purposes */
    protected final String name;
    
    /**
     * Creates a behavior node with no name.
     */
    protected BehaviorNode() {
        this.name = getClass().getSimpleName();
    }
    
    /**
     * Creates a behavior node with the specified name.
     * 
     * @param name Debug name for this node
     */
    protected BehaviorNode(String name) {
        this.name = name != null ? name : getClass().getSimpleName();
    }
    
    /**
     * Evaluates this node for one tick.
     * <p>
     * This method handles the lifecycle:
     * <ol>
     *   <li>If not running, calls {@link #onEnter(Blackboard)}</li>
     *   <li>Calls {@link #execute(Blackboard)}</li>
     *   <li>If result is not RUNNING, calls {@link #onExit(Blackboard)}</li>
     * </ol>
     * 
     * @param blackboard Shared data context
     * @return The current state after this tick
     */
    public final NodeState evaluate(Blackboard blackboard) {
        if (!isRunning) {
            onEnter(blackboard);
            isRunning = true;
        }
        
        state = execute(blackboard);
        
        if (state != NodeState.RUNNING) {
            onExit(blackboard);
            isRunning = false;
        }
        
        return state;
    }
    
    /**
     * Performs the actual work of this node.
     * <p>
     * Subclasses must implement this method to define the node's behavior.
     * This method is called every tick while the node is active.
     * 
     * @param blackboard Shared data context
     * @return SUCCESS, FAILURE, or RUNNING
     */
    protected abstract NodeState execute(Blackboard blackboard);
    
    /**
     * Called once when this node starts executing.
     * <p>
     * Override this method to perform initialization, such as:
     * <ul>
     *   <li>Setting up timers</li>
     *   <li>Caching values from the blackboard</li>
     *   <li>Starting animations</li>
     * </ul>
     * 
     * @param blackboard Shared data context
     */
    protected void onEnter(Blackboard blackboard) {
        // Default: no-op
    }
    
    /**
     * Called once when this node finishes executing.
     * <p>
     * Override this method to perform cleanup, such as:
     * <ul>
     *   <li>Releasing resources</li>
     *   <li>Stopping animations</li>
     *   <li>Clearing temporary blackboard values</li>
     * </ul>
     * 
     * @param blackboard Shared data context
     */
    protected void onExit(Blackboard blackboard) {
        // Default: no-op
    }
    
    /**
     * Forcibly resets this node to its initial state.
     * <p>
     * Called when the behavior tree needs to abort this node before it completes,
     * such as when a higher-priority branch takes over.
     * 
     * @param blackboard Shared data context
     */
    public void reset(Blackboard blackboard) {
        if (isRunning) {
            onExit(blackboard);
            isRunning = false;
        }
        state = NodeState.FAILURE;
    }
    
    /**
     * Returns the current state of this node.
     * 
     * @return Current node state
     */
    public NodeState getState() {
        return state;
    }
    
    /**
     * Returns whether this node is currently running.
     * 
     * @return true if between onEnter and onExit
     */
    public boolean isRunning() {
        return isRunning;
    }
    
    /**
     * Returns the debug name of this node.
     * 
     * @return Node name
     */
    public String getName() {
        return name;
    }
    
    @Override
    public String toString() {
        return name + "[" + state + "]";
    }
}
