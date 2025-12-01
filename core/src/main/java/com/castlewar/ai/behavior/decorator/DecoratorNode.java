package com.castlewar.ai.behavior.decorator;

import com.castlewar.ai.behavior.BehaviorNode;
import com.castlewar.ai.blackboard.Blackboard;

/**
 * Abstract base class for decorator behavior tree nodes.
 * <p>
 * Decorators wrap a single child node and modify its behavior.
 * Common decorators:
 * <ul>
 *   <li>{@link InverterNode} - Flips SUCCESS ↔ FAILURE</li>
 *   <li>{@link RepeatNode} - Loops child N times or forever</li>
 *   <li>{@link CooldownDecorator} - Gates execution by time</li>
 *   <li>{@link ConditionalDecorator} - Gates execution by condition</li>
 * </ul>
 * 
 * @see BehaviorNode
 */
public abstract class DecoratorNode extends BehaviorNode {
    
    /** The child node being decorated */
    protected BehaviorNode child;
    
    /**
     * Creates a decorator with no child.
     * Use {@link #setChild(BehaviorNode)} to set the child.
     */
    protected DecoratorNode() {
        super();
    }
    
    /**
     * Creates a decorator with a name but no child.
     * 
     * @param name Debug name
     */
    protected DecoratorNode(String name) {
        super(name);
    }
    
    /**
     * Creates a decorator with a child.
     * 
     * @param name  Debug name
     * @param child The child node to decorate
     */
    protected DecoratorNode(String name, BehaviorNode child) {
        super(name);
        this.child = child;
    }
    
    /**
     * Sets the child node.
     * 
     * @param child The child node to decorate
     * @return This decorator for method chaining
     */
    public DecoratorNode setChild(BehaviorNode child) {
        this.child = child;
        return this;
    }
    
    /**
     * Returns the decorated child node.
     * 
     * @return The child node, or null if not set
     */
    public BehaviorNode getChild() {
        return child;
    }
    
    /**
     * Returns whether this decorator has a child.
     * 
     * @return true if a child is set
     */
    public boolean hasChild() {
        return child != null;
    }
    
    @Override
    public void reset(Blackboard blackboard) {
        super.reset(blackboard);
        if (child != null) {
            child.reset(blackboard);
        }
    }
    
    @Override
    public String toString() {
        String childName = child != null ? child.getName() : "null";
        return name + "[" + state + ", child=" + childName + "]";
    }
}
