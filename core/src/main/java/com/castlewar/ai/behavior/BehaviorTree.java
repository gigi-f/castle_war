package com.castlewar.ai.behavior;

import com.castlewar.ai.behavior.composite.ParallelNode;
import com.castlewar.ai.behavior.composite.SelectorNode;
import com.castlewar.ai.behavior.composite.SequenceNode;
import com.castlewar.ai.behavior.decorator.ConditionalDecorator;
import com.castlewar.ai.behavior.decorator.CooldownDecorator;
import com.castlewar.ai.behavior.decorator.InverterNode;
import com.castlewar.ai.behavior.decorator.RepeatNode;
import com.castlewar.ai.behavior.leaf.ConditionNode;
import com.castlewar.ai.behavior.leaf.TaskNode;
import com.castlewar.ai.blackboard.Blackboard;
import com.castlewar.ai.blackboard.BlackboardKey;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Behavior tree manager that wraps a root node and provides tick execution.
 * <p>
 * Use the {@link Builder} for fluent tree construction:
 * <pre>{@code
 * BehaviorTree tree = BehaviorTree.builder("guard-ai")
 *     .selector("root")
 *         .sequence("flee")
 *             .condition("low-hp", bb -> bb.getFloat(HP_PERCENT) < 0.3f)
 *             .task("run-away", new FleeTask())
 *         .end()
 *         .sequence("attack")
 *             .condition("has-target", bb -> bb.has(COMBAT_TARGET))
 *             .task("attack", new AttackTask())
 *         .end()
 *         .task("patrol", new PatrolTask())
 *     .end()
 *     .build();
 * }</pre>
 * 
 * <h2>Execution</h2>
 * Call {@link #tick(Blackboard)} each frame to evaluate the tree.
 * The tree will return {@link NodeState#RUNNING} if any node is still executing.
 * 
 * @see BehaviorNode
 * @see Blackboard
 */
public class BehaviorTree {
    
    /** Name for debugging */
    private final String name;
    
    /** Root node of the tree */
    private final BehaviorNode root;
    
    /** Cached last state */
    private NodeState lastState = NodeState.FAILURE;
    
    /**
     * Creates a behavior tree with the given root.
     * 
     * @param name Name for debugging
     * @param root Root node
     */
    public BehaviorTree(String name, BehaviorNode root) {
        this.name = name != null ? name : "BehaviorTree";
        this.root = root;
    }
    
    /**
     * Creates a behavior tree with auto-generated name.
     * 
     * @param root Root node
     */
    public BehaviorTree(BehaviorNode root) {
        this(null, root);
    }
    
    /**
     * Evaluates the behavior tree for one tick.
     * 
     * @param blackboard Shared data context
     * @return The result of the root node evaluation
     */
    public NodeState tick(Blackboard blackboard) {
        if (root == null) {
            lastState = NodeState.FAILURE;
            return lastState;
        }
        
        lastState = root.evaluate(blackboard);
        return lastState;
    }
    
    /**
     * Evaluates the tree with delta time set in blackboard.
     * 
     * @param blackboard Shared data context
     * @param deltaTime  Time since last tick in seconds
     * @return The result of the root node evaluation
     */
    public NodeState tick(Blackboard blackboard, float deltaTime) {
        blackboard.setFloat(CooldownDecorator.DELTA_TIME_KEY, deltaTime);
        return tick(blackboard);
    }
    
    /**
     * Resets the tree to its initial state.
     * Call this when the tree needs to restart (e.g., unit respawns).
     * 
     * @param blackboard Shared data context
     */
    public void reset(Blackboard blackboard) {
        if (root != null) {
            root.reset(blackboard);
        }
        lastState = NodeState.FAILURE;
    }
    
    /**
     * Returns the last state returned by tick().
     * 
     * @return Last tick result
     */
    public NodeState getLastState() {
        return lastState;
    }
    
    /**
     * Returns the root node.
     * 
     * @return Root node
     */
    public BehaviorNode getRoot() {
        return root;
    }
    
    /**
     * Returns the tree name.
     * 
     * @return Tree name
     */
    public String getName() {
        return name;
    }
    
    @Override
    public String toString() {
        return name + "[" + lastState + "]";
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // BUILDER
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Creates a new behavior tree builder.
     * 
     * @param name Tree name for debugging
     * @return New builder instance
     */
    public static Builder builder(String name) {
        return new Builder(name);
    }
    
    /**
     * Creates a new behavior tree builder with auto-generated name.
     * 
     * @return New builder instance
     */
    public static Builder builder() {
        return new Builder(null);
    }
    
    /**
     * Fluent builder for constructing behavior trees.
     * <p>
     * Use composite methods (selector, sequence, parallel) to start a branch,
     * then add children, and call end() to close the branch.
     */
    public static class Builder {
        private final String treeName;
        private final Deque<BehaviorNode> nodeStack = new ArrayDeque<>();
        private BehaviorNode root;
        
        Builder(String name) {
            this.treeName = name;
        }
        
        // ═══════════════════════════════════════════════════════════════════════
        // COMPOSITES
        // ═══════════════════════════════════════════════════════════════════════
        
        /**
         * Starts a selector branch (OR logic).
         */
        public Builder selector(String name) {
            return pushComposite(new SelectorNode(name));
        }
        
        /**
         * Starts a reactive selector branch.
         */
        public Builder reactiveSelector(String name) {
            return pushComposite(new SelectorNode(name, true));
        }
        
        /**
         * Starts a sequence branch (AND logic).
         */
        public Builder sequence(String name) {
            return pushComposite(new SequenceNode(name));
        }
        
        /**
         * Starts a reactive sequence branch.
         */
        public Builder reactiveSequence(String name) {
            return pushComposite(new SequenceNode(name, true));
        }
        
        /**
         * Starts a parallel branch.
         */
        public Builder parallel(String name) {
            return pushComposite(new ParallelNode(name));
        }
        
        /**
         * Starts a parallel branch with specified policy.
         */
        public Builder parallel(String name, ParallelNode.Policy policy) {
            return pushComposite(new ParallelNode(name, policy));
        }
        
        /**
         * Ends the current composite branch.
         */
        public Builder end() {
            if (nodeStack.isEmpty()) {
                throw new IllegalStateException("No composite to end");
            }
            
            BehaviorNode completed = nodeStack.pop();
            
            if (nodeStack.isEmpty()) {
                // This was the root
                root = completed;
            }
            // If stack not empty, node was already added to parent in push
            
            return this;
        }
        
        // ═══════════════════════════════════════════════════════════════════════
        // DECORATORS
        // ═══════════════════════════════════════════════════════════════════════
        
        /**
         * Adds an inverter decorator (next node will be its child).
         */
        public Builder inverter(String name) {
            return pushDecorator(new InverterNode(name));
        }
        
        /**
         * Adds a repeat decorator.
         */
        public Builder repeat(String name, int times) {
            return pushDecorator(RepeatNode.times(name, times, null));
        }
        
        /**
         * Adds a repeat-forever decorator.
         */
        public Builder repeatForever(String name) {
            return pushDecorator(RepeatNode.forever(name, null));
        }
        
        /**
         * Adds a cooldown decorator.
         */
        public Builder cooldown(String name, float seconds) {
            return pushDecorator(new CooldownDecorator(name, seconds, null));
        }
        
        /**
         * Adds a conditional decorator.
         */
        public Builder conditional(String name, Predicate<Blackboard> condition) {
            return pushDecorator(new ConditionalDecorator(name, condition, null));
        }
        
        // ═══════════════════════════════════════════════════════════════════════
        // LEAVES
        // ═══════════════════════════════════════════════════════════════════════
        
        /**
         * Adds a condition node.
         */
        public Builder condition(String name, Predicate<Blackboard> condition) {
            return addNode(new ConditionNode(name, condition));
        }
        
        /**
         * Adds a condition that checks for a blackboard value.
         */
        public Builder hasValue(String name, BlackboardKey key) {
            return addNode(ConditionNode.hasValue(name, key));
        }
        
        /**
         * Adds a condition that checks a boolean blackboard value.
         */
        public Builder isTrue(String name, BlackboardKey key) {
            return addNode(ConditionNode.isTrue(name, key));
        }
        
        /**
         * Adds a task node.
         */
        public Builder task(String name, Function<Blackboard, NodeState> task) {
            return addNode(new TaskNode(name, task));
        }
        
        /**
         * Adds an existing task node.
         */
        public Builder task(TaskNode task) {
            return addNode(task);
        }
        
        /**
         * Adds any existing node.
         */
        public Builder node(BehaviorNode node) {
            return addNode(node);
        }
        
        /**
         * Adds a success node (always succeeds).
         */
        public Builder success(String name) {
            return addNode(TaskNode.success(name));
        }
        
        /**
         * Adds a failure node (always fails).
         */
        public Builder failure(String name) {
            return addNode(TaskNode.failure(name));
        }
        
        /**
         * Adds a wait node.
         */
        public Builder wait(String name, float seconds) {
            return addNode(TaskNode.wait(name, seconds));
        }
        
        // ═══════════════════════════════════════════════════════════════════════
        // BUILD
        // ═══════════════════════════════════════════════════════════════════════
        
        /**
         * Builds the behavior tree.
         * 
         * @return Constructed BehaviorTree
         * @throws IllegalStateException if tree is incomplete
         */
        public BehaviorTree build() {
            if (!nodeStack.isEmpty()) {
                throw new IllegalStateException(
                    "Unclosed composites: " + nodeStack.size() + " end() calls missing"
                );
            }
            
            if (root == null) {
                throw new IllegalStateException("No root node defined");
            }
            
            return new BehaviorTree(treeName, root);
        }
        
        // ═══════════════════════════════════════════════════════════════════════
        // INTERNAL
        // ═══════════════════════════════════════════════════════════════════════
        
        private Builder pushComposite(BehaviorNode composite) {
            addToParentOrRoot(composite);
            nodeStack.push(composite);
            return this;
        }
        
        private Builder pushDecorator(BehaviorNode decorator) {
            addToParentOrRoot(decorator);
            nodeStack.push(decorator);
            return this;
        }
        
        private Builder addNode(BehaviorNode node) {
            if (node == null) return this;
            
            // Check if top of stack is a decorator waiting for child
            if (!nodeStack.isEmpty()) {
                BehaviorNode top = nodeStack.peek();
                if (top instanceof com.castlewar.ai.behavior.decorator.DecoratorNode) {
                    com.castlewar.ai.behavior.decorator.DecoratorNode decorator = 
                        (com.castlewar.ai.behavior.decorator.DecoratorNode) top;
                    if (!decorator.hasChild()) {
                        decorator.setChild(node);
                        nodeStack.pop(); // Decorator is complete
                        return this;
                    }
                }
            }
            
            addToParentOrRoot(node);
            return this;
        }
        
        private void addToParentOrRoot(BehaviorNode node) {
            if (nodeStack.isEmpty()) {
                // This will be root (or decorator will set it)
                if (root == null) {
                    root = node;
                }
            } else {
                BehaviorNode parent = nodeStack.peek();
                if (parent instanceof com.castlewar.ai.behavior.composite.CompositeNode) {
                    ((com.castlewar.ai.behavior.composite.CompositeNode) parent).addChild(node);
                } else if (parent instanceof com.castlewar.ai.behavior.decorator.DecoratorNode) {
                    ((com.castlewar.ai.behavior.decorator.DecoratorNode) parent).setChild(node);
                }
            }
        }
    }
}
