package com.castlewar.ai.behavior.decorator;

import com.castlewar.ai.behavior.BehaviorNode;
import com.castlewar.ai.behavior.NodeState;
import com.castlewar.ai.blackboard.Blackboard;

/**
 * Decorator that inverts the child's result.
 * <p>
 * Result mapping:
 * <ul>
 *   <li>SUCCESS → FAILURE</li>
 *   <li>FAILURE → SUCCESS</li>
 *   <li>RUNNING → RUNNING (unchanged)</li>
 * </ul>
 * 
 * <h2>Use Case</h2>
 * Turn conditions into their negation:
 * <pre>{@code
 * // "If NOT detected, then sneak"
 * Sequence sneak = new SequenceNode()
 *     .addChild(new InverterNode("not-detected", new IsDetectedCondition()))
 *     .addChild(new SneakTask());
 * }</pre>
 */
public class InverterNode extends DecoratorNode {
    
    /**
     * Creates an inverter with no child.
     */
    public InverterNode() {
        super("Inverter");
    }
    
    /**
     * Creates an inverter with a name.
     * 
     * @param name Debug name
     */
    public InverterNode(String name) {
        super(name);
    }
    
    /**
     * Creates an inverter with a child.
     * 
     * @param name  Debug name
     * @param child The child node to invert
     */
    public InverterNode(String name, BehaviorNode child) {
        super(name, child);
    }
    
    /**
     * Creates an inverter with a child and auto-generated name.
     * 
     * @param child The child node to invert
     */
    public InverterNode(BehaviorNode child) {
        super("Inverter", child);
    }
    
    @Override
    protected NodeState execute(Blackboard blackboard) {
        if (child == null) {
            return NodeState.FAILURE;
        }
        
        NodeState childState = child.evaluate(blackboard);
        
        switch (childState) {
            case SUCCESS:
                return NodeState.FAILURE;
            case FAILURE:
                return NodeState.SUCCESS;
            case RUNNING:
            default:
                return NodeState.RUNNING;
        }
    }
    
    @Override
    public InverterNode setChild(BehaviorNode child) {
        super.setChild(child);
        return this;
    }
}
