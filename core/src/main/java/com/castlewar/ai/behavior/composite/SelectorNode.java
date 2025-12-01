package com.castlewar.ai.behavior.composite;

import com.castlewar.ai.behavior.BehaviorNode;
import com.castlewar.ai.behavior.NodeState;
import com.castlewar.ai.blackboard.Blackboard;

/**
 * Selector (fallback) composite node - implements OR logic.
 * <p>
 * Evaluates children left-to-right until one succeeds:
 * <ul>
 *   <li>If a child returns SUCCESS → Selector returns SUCCESS (stops evaluating)</li>
 *   <li>If a child returns RUNNING → Selector returns RUNNING (pauses here)</li>
 *   <li>If a child returns FAILURE → Selector tries the next child</li>
 *   <li>If all children fail → Selector returns FAILURE</li>
 * </ul>
 * 
 * <h2>Use Case</h2>
 * Model "try alternatives until one works" behavior:
 * <pre>{@code
 * Selector root = new SelectorNode("combat-or-patrol")
 *     .addChild(new AttackEnemyTask())   // Try attack first
 *     .addChild(new ChaseEnemyTask())    // If can't attack, chase
 *     .addChild(new PatrolTask());       // If no enemy, patrol
 * }</pre>
 * 
 * <h2>Memory Behavior</h2>
 * By default, Selector restarts from the first child each tick. For "reactive"
 * behavior where higher-priority children can interrupt lower ones, this is correct.
 * Set {@code reactive = false} to resume from the running child (memory selector).
 * 
 * @see SequenceNode
 * @see ParallelNode
 */
public class SelectorNode extends CompositeNode {
    
    /** Index of the currently running child (for memory mode) */
    private int currentChildIndex = 0;
    
    /** Whether to restart from index 0 each tick (reactive) */
    private final boolean reactive;
    
    /**
     * Creates a reactive selector (restarts from first child each tick).
     */
    public SelectorNode() {
        this(null, true);
    }
    
    /**
     * Creates a reactive selector with a name.
     * 
     * @param name Debug name
     */
    public SelectorNode(String name) {
        this(name, true);
    }
    
    /**
     * Creates a selector with configurable reactivity.
     * 
     * @param name     Debug name
     * @param reactive If true, restarts from child 0 each tick (can interrupt running children)
     */
    public SelectorNode(String name, boolean reactive) {
        super(name);
        this.reactive = reactive;
    }
    
    @Override
    protected NodeState execute(Blackboard blackboard) {
        if (children.isEmpty()) {
            return NodeState.FAILURE;
        }
        
        // Reactive selectors restart from the beginning each tick
        int startIndex = reactive ? 0 : currentChildIndex;
        
        // If reactive and a lower-priority child was running, reset it
        if (reactive && currentChildIndex > 0) {
            for (int i = currentChildIndex; i < children.size(); i++) {
                BehaviorNode child = children.get(i);
                if (child.isRunning()) {
                    child.reset(blackboard);
                }
            }
        }
        
        for (int i = startIndex; i < children.size(); i++) {
            BehaviorNode child = children.get(i);
            NodeState childState = child.evaluate(blackboard);
            
            switch (childState) {
                case SUCCESS:
                    // Child succeeded - selector succeeds
                    currentChildIndex = 0;
                    return NodeState.SUCCESS;
                    
                case RUNNING:
                    // Child still running - remember position and return RUNNING
                    currentChildIndex = i;
                    return NodeState.RUNNING;
                    
                case FAILURE:
                    // Child failed - try next child
                    break;
            }
        }
        
        // All children failed
        currentChildIndex = 0;
        return NodeState.FAILURE;
    }
    
    @Override
    protected void onEnter(Blackboard blackboard) {
        currentChildIndex = 0;
    }
    
    @Override
    protected void onExit(Blackboard blackboard) {
        // Reset any running children when we exit
        for (BehaviorNode child : children) {
            if (child.isRunning()) {
                child.reset(blackboard);
            }
        }
        currentChildIndex = 0;
    }
    
    @Override
    public void reset(Blackboard blackboard) {
        super.reset(blackboard);
        currentChildIndex = 0;
    }
    
    /**
     * Returns whether this selector is reactive.
     * 
     * @return true if reactive (restarts from child 0 each tick)
     */
    public boolean isReactive() {
        return reactive;
    }
    
    // Fluent API overrides to return SelectorNode type
    
    @Override
    public SelectorNode addChild(BehaviorNode child) {
        super.addChild(child);
        return this;
    }
    
    @Override
    public SelectorNode addChildren(BehaviorNode... childNodes) {
        super.addChildren(childNodes);
        return this;
    }
}
