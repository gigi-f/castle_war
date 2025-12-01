package com.castlewar.ai.behavior.composite;

import com.castlewar.ai.behavior.BehaviorNode;
import com.castlewar.ai.behavior.NodeState;
import com.castlewar.ai.blackboard.Blackboard;

/**
 * Sequence composite node - implements AND logic.
 * <p>
 * Evaluates children left-to-right, requiring all to succeed:
 * <ul>
 *   <li>If a child returns SUCCESS → Sequence moves to next child</li>
 *   <li>If a child returns RUNNING → Sequence returns RUNNING (pauses here)</li>
 *   <li>If a child returns FAILURE → Sequence returns FAILURE (stops evaluating)</li>
 *   <li>If all children succeed → Sequence returns SUCCESS</li>
 * </ul>
 * 
 * <h2>Use Case</h2>
 * Model "do all these steps in order" behavior:
 * <pre>{@code
 * Sequence attack = new SequenceNode("attack-sequence")
 *     .addChild(new HasTargetCondition())    // Check: have target?
 *     .addChild(new InRangeCondition())      // Check: in range?
 *     .addChild(new AttackTask());           // Action: attack!
 * }</pre>
 * 
 * <h2>Memory Behavior</h2>
 * By default, Sequence remembers which child is running and resumes from there.
 * Set {@code reactive = true} to restart from child 0 each tick, allowing earlier
 * children (usually conditions) to abort the sequence.
 * 
 * @see SelectorNode
 * @see ParallelNode
 */
public class SequenceNode extends CompositeNode {
    
    /** Index of the currently running child */
    private int currentChildIndex = 0;
    
    /** Whether to restart from index 0 each tick (reactive) */
    private final boolean reactive;
    
    /**
     * Creates a non-reactive sequence (resumes from running child).
     */
    public SequenceNode() {
        this(null, false);
    }
    
    /**
     * Creates a non-reactive sequence with a name.
     * 
     * @param name Debug name
     */
    public SequenceNode(String name) {
        this(name, false);
    }
    
    /**
     * Creates a sequence with configurable reactivity.
     * 
     * @param name     Debug name
     * @param reactive If true, restarts from child 0 each tick (re-checks conditions)
     */
    public SequenceNode(String name, boolean reactive) {
        super(name);
        this.reactive = reactive;
    }
    
    @Override
    protected NodeState execute(Blackboard blackboard) {
        if (children.isEmpty()) {
            return NodeState.SUCCESS;
        }
        
        // Reactive sequences restart from the beginning each tick
        int startIndex = reactive ? 0 : currentChildIndex;
        
        for (int i = startIndex; i < children.size(); i++) {
            BehaviorNode child = children.get(i);
            NodeState childState = child.evaluate(blackboard);
            
            switch (childState) {
                case FAILURE:
                    // Child failed - sequence fails
                    currentChildIndex = 0;
                    return NodeState.FAILURE;
                    
                case RUNNING:
                    // Child still running - remember position and return RUNNING
                    currentChildIndex = i;
                    return NodeState.RUNNING;
                    
                case SUCCESS:
                    // Child succeeded - move to next child
                    break;
            }
        }
        
        // All children succeeded
        currentChildIndex = 0;
        return NodeState.SUCCESS;
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
     * Returns whether this sequence is reactive.
     * 
     * @return true if reactive (restarts from child 0 each tick)
     */
    public boolean isReactive() {
        return reactive;
    }
    
    // Fluent API overrides to return SequenceNode type
    
    @Override
    public SequenceNode addChild(BehaviorNode child) {
        super.addChild(child);
        return this;
    }
    
    @Override
    public SequenceNode addChildren(BehaviorNode... childNodes) {
        super.addChildren(childNodes);
        return this;
    }
}
