package com.castlewar.ai.behavior.composite;

import com.castlewar.ai.behavior.BehaviorNode;
import com.castlewar.ai.behavior.NodeState;
import com.castlewar.ai.blackboard.Blackboard;

/**
 * Parallel composite node - executes all children concurrently.
 * <p>
 * Unlike Selector and Sequence, Parallel evaluates ALL children every tick.
 * The return value is determined by a configurable {@link Policy}.
 * 
 * <h2>Policies</h2>
 * <ul>
 *   <li>{@link Policy#REQUIRE_ONE} - SUCCESS when first child succeeds</li>
 *   <li>{@link Policy#REQUIRE_ALL} - SUCCESS only when all children succeed</li>
 * </ul>
 * 
 * <h2>Use Case</h2>
 * Model "do multiple things at once" behavior:
 * <pre>{@code
 * // Move while watching for enemies
 * Parallel patrol = new ParallelNode("patrol-and-watch", Policy.REQUIRE_ONE)
 *     .addChild(new MoveToWaypointTask())  // Keep moving
 *     .addChild(new WatchForEnemyTask());  // Scan for threats
 * }</pre>
 * 
 * @see SelectorNode
 * @see SequenceNode
 */
public class ParallelNode extends CompositeNode {
    
    /**
     * Determines when a Parallel node succeeds or fails.
     */
    public enum Policy {
        /**
         * SUCCESS when any one child succeeds.
         * FAILURE when all children fail.
         */
        REQUIRE_ONE,
        
        /**
         * SUCCESS when all children succeed.
         * FAILURE when any one child fails.
         */
        REQUIRE_ALL
    }
    
    /** Success/failure policy */
    private final Policy policy;
    
    /**
     * Creates a parallel node with REQUIRE_ONE policy.
     */
    public ParallelNode() {
        this(null, Policy.REQUIRE_ONE);
    }
    
    /**
     * Creates a parallel node with a name and REQUIRE_ONE policy.
     * 
     * @param name Debug name
     */
    public ParallelNode(String name) {
        this(name, Policy.REQUIRE_ONE);
    }
    
    /**
     * Creates a parallel node with the specified policy.
     * 
     * @param name   Debug name
     * @param policy Success/failure policy
     */
    public ParallelNode(String name, Policy policy) {
        super(name);
        this.policy = policy != null ? policy : Policy.REQUIRE_ONE;
    }
    
    @Override
    protected NodeState execute(Blackboard blackboard) {
        if (children.isEmpty()) {
            return NodeState.SUCCESS;
        }
        
        int successCount = 0;
        int failureCount = 0;
        
        // Evaluate ALL children every tick
        for (BehaviorNode child : children) {
            NodeState childState = child.evaluate(blackboard);
            
            switch (childState) {
                case SUCCESS:
                    successCount++;
                    break;
                case FAILURE:
                    failureCount++;
                    break;
                case RUNNING:
                    // Just count via exclusion: running = total - success - failure
                    break;
            }
        }
        
        // Apply policy
        switch (policy) {
            case REQUIRE_ONE:
                // Success if any child succeeded
                if (successCount > 0) {
                    return NodeState.SUCCESS;
                }
                // Failure only if all children failed (none running)
                if (failureCount == children.size()) {
                    return NodeState.FAILURE;
                }
                // Otherwise still running
                return NodeState.RUNNING;
                
            case REQUIRE_ALL:
                // Failure if any child failed
                if (failureCount > 0) {
                    return NodeState.FAILURE;
                }
                // Success only if all children succeeded
                if (successCount == children.size()) {
                    return NodeState.SUCCESS;
                }
                // Otherwise still running
                return NodeState.RUNNING;
                
            default:
                return NodeState.FAILURE;
        }
    }
    
    @Override
    protected void onExit(Blackboard blackboard) {
        // Reset all running children when we exit
        for (BehaviorNode child : children) {
            if (child.isRunning()) {
                child.reset(blackboard);
            }
        }
    }
    
    /**
     * Returns the policy for this parallel node.
     * 
     * @return The success/failure policy
     */
    public Policy getPolicy() {
        return policy;
    }
    
    // Fluent API overrides to return ParallelNode type
    
    @Override
    public ParallelNode addChild(BehaviorNode child) {
        super.addChild(child);
        return this;
    }
    
    @Override
    public ParallelNode addChildren(BehaviorNode... childNodes) {
        super.addChildren(childNodes);
        return this;
    }
}
