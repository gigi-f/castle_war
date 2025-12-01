package com.castlewar.ai.behavior.decorator;

import com.castlewar.ai.behavior.BehaviorNode;
import com.castlewar.ai.behavior.NodeState;
import com.castlewar.ai.blackboard.Blackboard;

import java.util.function.Predicate;

/**
 * Decorator that gates child execution based on a condition.
 * <p>
 * The condition is checked before evaluating the child:
 * <ul>
 *   <li>If condition is TRUE → evaluate child and return its result</li>
 *   <li>If condition is FALSE → return FAILURE without evaluating child</li>
 * </ul>
 * 
 * <h2>Use Case</h2>
 * <pre>{@code
 * // Only attack if we have stamina
 * ConditionalDecorator attackIfStamina = new ConditionalDecorator(
 *     "has-stamina",
 *     bb -> bb.getFloat(BlackboardKey.SELF_STAMINA) > 10f,
 *     new AttackTask()
 * );
 * }</pre>
 * 
 * <h2>Difference from ConditionNode in Sequence</h2>
 * Using a Sequence with ConditionNode + Task is equivalent, but ConditionalDecorator:
 * <ul>
 *   <li>Is more compact for single conditions</li>
 *   <li>Clearly shows the condition guards the task</li>
 *   <li>Can abort a running child if condition becomes false (optional)</li>
 * </ul>
 */
public class ConditionalDecorator extends DecoratorNode {
    
    /** The condition predicate */
    private final Predicate<Blackboard> condition;
    
    /** Whether to re-check condition every tick (can abort running child) */
    private final boolean reactive;
    
    /**
     * Creates a conditional decorator.
     * 
     * @param name      Debug name
     * @param condition Predicate that must return true for child to execute
     * @param child     Child node to gate
     */
    public ConditionalDecorator(String name, Predicate<Blackboard> condition, BehaviorNode child) {
        this(name, condition, child, false);
    }
    
    /**
     * Creates a conditional decorator with reactivity option.
     * 
     * @param name      Debug name
     * @param condition Predicate that must return true for child to execute
     * @param child     Child node to gate
     * @param reactive  If true, re-checks condition every tick and can abort running child
     */
    public ConditionalDecorator(String name, Predicate<Blackboard> condition, BehaviorNode child, boolean reactive) {
        super(name, child);
        this.condition = condition;
        this.reactive = reactive;
    }
    
    @Override
    protected NodeState execute(Blackboard blackboard) {
        if (child == null || condition == null) {
            return NodeState.FAILURE;
        }
        
        // Check condition
        boolean conditionMet;
        try {
            conditionMet = condition.test(blackboard);
        } catch (Exception e) {
            return NodeState.FAILURE;
        }
        
        if (!conditionMet) {
            // Condition failed
            if (reactive && child.isRunning()) {
                // Abort running child
                child.reset(blackboard);
            }
            return NodeState.FAILURE;
        }
        
        // Condition passed - evaluate child
        return child.evaluate(blackboard);
    }
    
    /**
     * Returns whether this decorator is reactive.
     * 
     * @return true if condition is re-checked every tick
     */
    public boolean isReactive() {
        return reactive;
    }
    
    @Override
    public ConditionalDecorator setChild(BehaviorNode child) {
        super.setChild(child);
        return this;
    }
}
