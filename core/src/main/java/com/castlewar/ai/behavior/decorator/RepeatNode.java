package com.castlewar.ai.behavior.decorator;

import com.castlewar.ai.behavior.BehaviorNode;
import com.castlewar.ai.behavior.NodeState;
import com.castlewar.ai.blackboard.Blackboard;

/**
 * Decorator that repeats the child execution.
 * <p>
 * Modes:
 * <ul>
 *   <li>Finite: Repeat N times, then return child's last result</li>
 *   <li>Infinite: Repeat forever (always returns RUNNING)</li>
 *   <li>Until Fail: Repeat until child fails</li>
 *   <li>Until Success: Repeat until child succeeds</li>
 * </ul>
 * 
 * <h2>Use Case</h2>
 * <pre>{@code
 * // Patrol forever
 * RepeatNode patrolForever = RepeatNode.forever("patrol-loop", new PatrolTask());
 * 
 * // Attack 3 times
 * RepeatNode tripleStrike = RepeatNode.times("triple-strike", 3, new AttackTask());
 * 
 * // Keep trying until success
 * RepeatNode retryPickLock = RepeatNode.untilSuccess("retry-pick", new PickLockTask());
 * }</pre>
 */
public class RepeatNode extends DecoratorNode {
    
    /** Repeat mode */
    public enum Mode {
        /** Repeat exactly N times */
        FINITE,
        /** Repeat forever */
        INFINITE,
        /** Repeat until child fails */
        UNTIL_FAIL,
        /** Repeat until child succeeds */
        UNTIL_SUCCESS
    }
    
    private final Mode mode;
    private final int maxCount;
    private int currentCount;
    
    /**
     * Creates a finite repeat decorator.
     * 
     * @param name  Debug name
     * @param count Number of repetitions
     * @param child Child node to repeat
     */
    public RepeatNode(String name, int count, BehaviorNode child) {
        super(name, child);
        this.mode = Mode.FINITE;
        this.maxCount = Math.max(1, count);
        this.currentCount = 0;
    }
    
    /**
     * Creates a repeat decorator with specified mode.
     * 
     * @param name  Debug name
     * @param mode  Repeat mode
     * @param child Child node to repeat
     */
    public RepeatNode(String name, Mode mode, BehaviorNode child) {
        super(name, child);
        this.mode = mode;
        this.maxCount = -1;
        this.currentCount = 0;
    }
    
    @Override
    protected void onEnter(Blackboard blackboard) {
        currentCount = 0;
    }
    
    @Override
    protected NodeState execute(Blackboard blackboard) {
        if (child == null) {
            return NodeState.FAILURE;
        }
        
        NodeState childState = child.evaluate(blackboard);
        
        // If child is still running, wait for it
        if (childState == NodeState.RUNNING) {
            return NodeState.RUNNING;
        }
        
        // Child completed - check mode
        switch (mode) {
            case FINITE:
                currentCount++;
                if (currentCount >= maxCount) {
                    return childState; // Return last result
                }
                // Reset child for next iteration
                child.reset(blackboard);
                return NodeState.RUNNING;
                
            case INFINITE:
                // Reset and continue forever
                child.reset(blackboard);
                return NodeState.RUNNING;
                
            case UNTIL_FAIL:
                if (childState == NodeState.FAILURE) {
                    return NodeState.SUCCESS; // We wanted it to fail
                }
                child.reset(blackboard);
                return NodeState.RUNNING;
                
            case UNTIL_SUCCESS:
                if (childState == NodeState.SUCCESS) {
                    return NodeState.SUCCESS;
                }
                child.reset(blackboard);
                return NodeState.RUNNING;
                
            default:
                return NodeState.FAILURE;
        }
    }
    
    @Override
    public void reset(Blackboard blackboard) {
        super.reset(blackboard);
        currentCount = 0;
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // FACTORY METHODS
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Creates a decorator that repeats N times.
     */
    public static RepeatNode times(String name, int count, BehaviorNode child) {
        return new RepeatNode(name, count, child);
    }
    
    /**
     * Creates a decorator that repeats forever.
     */
    public static RepeatNode forever(String name, BehaviorNode child) {
        return new RepeatNode(name, Mode.INFINITE, child);
    }
    
    /**
     * Creates a decorator that repeats until the child fails.
     */
    public static RepeatNode untilFail(String name, BehaviorNode child) {
        return new RepeatNode(name, Mode.UNTIL_FAIL, child);
    }
    
    /**
     * Creates a decorator that repeats until the child succeeds.
     */
    public static RepeatNode untilSuccess(String name, BehaviorNode child) {
        return new RepeatNode(name, Mode.UNTIL_SUCCESS, child);
    }
    
    @Override
    public RepeatNode setChild(BehaviorNode child) {
        super.setChild(child);
        return this;
    }
}
