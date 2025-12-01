package com.castlewar.ai.behavior.composite;

import com.castlewar.ai.behavior.BehaviorNode;
import com.castlewar.ai.blackboard.Blackboard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Abstract base class for composite behavior tree nodes.
 * <p>
 * Composite nodes contain multiple children and define how they are evaluated.
 * Common composite types:
 * <ul>
 *   <li>{@link SelectorNode} - OR logic (success if any child succeeds)</li>
 *   <li>{@link SequenceNode} - AND logic (success if all children succeed)</li>
 *   <li>{@link ParallelNode} - Concurrent execution with policy</li>
 * </ul>
 * 
 * <h2>Child Management</h2>
 * Children are evaluated in order (index 0 first). Use {@link #addChild(BehaviorNode)}
 * to add children, or the builder pattern in {@link com.castlewar.ai.behavior.BehaviorTree}.
 * 
 * @see BehaviorNode
 */
public abstract class CompositeNode extends BehaviorNode {
    
    /** Child nodes in evaluation order */
    protected final List<BehaviorNode> children;
    
    /** Unmodifiable view of children for safe iteration */
    private final List<BehaviorNode> childrenView;
    
    /**
     * Creates a composite node with no name.
     */
    protected CompositeNode() {
        super();
        this.children = new ArrayList<>();
        this.childrenView = Collections.unmodifiableList(children);
    }
    
    /**
     * Creates a composite node with the specified name.
     * 
     * @param name Debug name for this node
     */
    protected CompositeNode(String name) {
        super(name);
        this.children = new ArrayList<>();
        this.childrenView = Collections.unmodifiableList(children);
    }
    
    /**
     * Adds a child node to this composite.
     * Children are evaluated in the order they are added.
     * 
     * @param child The child node to add
     * @return This composite for method chaining
     */
    public CompositeNode addChild(BehaviorNode child) {
        if (child != null) {
            children.add(child);
        }
        return this;
    }
    
    /**
     * Adds multiple children to this composite.
     * 
     * @param childNodes The children to add
     * @return This composite for method chaining
     */
    public CompositeNode addChildren(BehaviorNode... childNodes) {
        for (BehaviorNode child : childNodes) {
            addChild(child);
        }
        return this;
    }
    
    /**
     * Returns an unmodifiable view of the children.
     * 
     * @return Unmodifiable list of child nodes
     */
    public List<BehaviorNode> getChildren() {
        return childrenView;
    }
    
    /**
     * Returns the number of children.
     * 
     * @return Child count
     */
    public int getChildCount() {
        return children.size();
    }
    
    /**
     * Returns whether this composite has any children.
     * 
     * @return true if at least one child exists
     */
    public boolean hasChildren() {
        return !children.isEmpty();
    }
    
    /**
     * Resets all children in addition to this node.
     */
    @Override
    public void reset(Blackboard blackboard) {
        super.reset(blackboard);
        for (BehaviorNode child : children) {
            child.reset(blackboard);
        }
    }
    
    @Override
    public String toString() {
        return name + "[" + state + ", children=" + children.size() + "]";
    }
}
