package com.castlewar.ai.behavior.leaf;

import com.castlewar.ai.behavior.BehaviorNode;
import com.castlewar.ai.behavior.NodeState;
import com.castlewar.ai.blackboard.Blackboard;

import java.util.function.Predicate;

/**
 * Leaf node that evaluates a boolean condition.
 * <p>
 * Conditions are instantaneous checks that return SUCCESS (true) or FAILURE (false).
 * They never return RUNNING.
 * 
 * <h2>Usage</h2>
 * Create conditions using a lambda or method reference:
 * <pre>{@code
 * // Lambda condition
 * ConditionNode hasTarget = new ConditionNode("has-target",
 *     bb -> bb.has(BlackboardKey.COMBAT_CURRENT_TARGET));
 * 
 * // Method reference
 * ConditionNode lowHealth = new ConditionNode("low-health",
 *     this::isHealthLow);
 * 
 * private boolean isHealthLow(Blackboard bb) {
 *     return bb.getFloat(BlackboardKey.SELF_HP_PERCENT) < 0.3f;
 * }
 * }</pre>
 * 
 * <h2>Common Patterns</h2>
 * <ul>
 *   <li>Check blackboard values: {@code bb.has(key)}, {@code bb.getBool(key)}</li>
 *   <li>Compare values: {@code bb.getFloat(key) > threshold}</li>
 *   <li>Entity checks: {@code bb.getEntity(key) != null}</li>
 * </ul>
 * 
 * @see TaskNode
 */
public class ConditionNode extends BehaviorNode {
    
    /** The condition predicate to evaluate */
    private final Predicate<Blackboard> condition;
    
    /**
     * Creates a condition node with the specified predicate.
     * 
     * @param name      Debug name
     * @param condition Predicate that returns true for SUCCESS, false for FAILURE
     */
    public ConditionNode(String name, Predicate<Blackboard> condition) {
        super(name);
        this.condition = condition;
    }
    
    /**
     * Creates a condition node with an auto-generated name.
     * 
     * @param condition Predicate that returns true for SUCCESS, false for FAILURE
     */
    public ConditionNode(Predicate<Blackboard> condition) {
        super();
        this.condition = condition;
    }
    
    @Override
    protected NodeState execute(Blackboard blackboard) {
        if (condition == null) {
            return NodeState.FAILURE;
        }
        
        try {
            return condition.test(blackboard) ? NodeState.SUCCESS : NodeState.FAILURE;
        } catch (Exception e) {
            // If condition throws, treat as failure
            return NodeState.FAILURE;
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // FACTORY METHODS for common conditions
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Creates a condition that checks if a blackboard key has a value.
     * 
     * @param name Debug name
     * @param key  Blackboard key to check
     * @return ConditionNode that succeeds if key has a value
     */
    public static ConditionNode hasValue(String name, com.castlewar.ai.blackboard.BlackboardKey key) {
        return new ConditionNode(name, bb -> bb.has(key));
    }
    
    /**
     * Creates a condition that checks a boolean blackboard value.
     * 
     * @param name Debug name
     * @param key  Blackboard key for boolean value
     * @return ConditionNode that succeeds if boolean is true
     */
    public static ConditionNode isTrue(String name, com.castlewar.ai.blackboard.BlackboardKey key) {
        return new ConditionNode(name, bb -> bb.getBool(key));
    }
    
    /**
     * Creates a condition that checks if a float value exceeds a threshold.
     * 
     * @param name      Debug name
     * @param key       Blackboard key for float value
     * @param threshold Minimum value for success
     * @return ConditionNode that succeeds if value > threshold
     */
    public static ConditionNode greaterThan(String name, com.castlewar.ai.blackboard.BlackboardKey key, float threshold) {
        return new ConditionNode(name, bb -> bb.getFloat(key) > threshold);
    }
    
    /**
     * Creates a condition that checks if a float value is below a threshold.
     * 
     * @param name      Debug name
     * @param key       Blackboard key for float value
     * @param threshold Maximum value for success
     * @return ConditionNode that succeeds if value < threshold
     */
    public static ConditionNode lessThan(String name, com.castlewar.ai.blackboard.BlackboardKey key, float threshold) {
        return new ConditionNode(name, bb -> bb.getFloat(key) < threshold);
    }
    
    /**
     * Creates a condition that always succeeds.
     * Useful for testing or as a placeholder.
     * 
     * @param name Debug name
     * @return ConditionNode that always returns SUCCESS
     */
    public static ConditionNode alwaysTrue(String name) {
        return new ConditionNode(name, bb -> true);
    }
    
    /**
     * Creates a condition that always fails.
     * Useful for testing or disabling branches.
     * 
     * @param name Debug name
     * @return ConditionNode that always returns FAILURE
     */
    public static ConditionNode alwaysFalse(String name) {
        return new ConditionNode(name, bb -> false);
    }
}
