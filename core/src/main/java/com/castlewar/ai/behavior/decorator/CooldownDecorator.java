package com.castlewar.ai.behavior.decorator;

import com.castlewar.ai.behavior.BehaviorNode;
import com.castlewar.ai.behavior.NodeState;
import com.castlewar.ai.blackboard.Blackboard;
import com.castlewar.ai.blackboard.BlackboardKey;

/**
 * Decorator that gates child execution based on a cooldown timer.
 * <p>
 * After the child completes (SUCCESS or FAILURE), the cooldown starts.
 * During cooldown, the decorator returns FAILURE without evaluating the child.
 * 
 * <h2>Use Case</h2>
 * Prevent abilities from being spammed:
 * <pre>{@code
 * // Can only attack every 1 second
 * CooldownDecorator attackCooldown = new CooldownDecorator("attack-cd", 1.0f, new AttackTask());
 * 
 * // Flee check only every 0.5 seconds (optimization)
 * CooldownDecorator fleeCheck = new CooldownDecorator("flee-cd", 0.5f, new FleeSequence());
 * }</pre>
 * 
 * <h2>Delta Time</h2>
 * This decorator reads delta time from the blackboard using a standard key.
 * Ensure your behavior tree sets this value each tick.
 */
public class CooldownDecorator extends DecoratorNode {
    
    /** Blackboard key for delta time (seconds since last tick) */
    public static final BlackboardKey DELTA_TIME_KEY = BlackboardKey.CUSTOM_1;
    
    /** Cooldown duration in seconds */
    private final float cooldownDuration;
    
    /** Time remaining on cooldown */
    private float cooldownRemaining;
    
    /** Whether we're currently on cooldown */
    private boolean onCooldown;
    
    /**
     * Creates a cooldown decorator.
     * 
     * @param name     Debug name
     * @param duration Cooldown duration in seconds
     * @param child    Child node to gate
     */
    public CooldownDecorator(String name, float duration, BehaviorNode child) {
        super(name, child);
        this.cooldownDuration = Math.max(0f, duration);
        this.cooldownRemaining = 0f;
        this.onCooldown = false;
    }
    
    /**
     * Creates a cooldown decorator with auto-generated name.
     * 
     * @param duration Cooldown duration in seconds
     * @param child    Child node to gate
     */
    public CooldownDecorator(float duration, BehaviorNode child) {
        this("Cooldown", duration, child);
    }
    
    @Override
    protected NodeState execute(Blackboard blackboard) {
        if (child == null) {
            return NodeState.FAILURE;
        }
        
        // Get delta time from blackboard (default to ~60 FPS if not set)
        float deltaTime = blackboard.getFloat(DELTA_TIME_KEY);
        if (deltaTime <= 0f) {
            deltaTime = 1f / 60f; // Fallback
        }
        
        // Update cooldown timer
        if (onCooldown) {
            cooldownRemaining -= deltaTime;
            if (cooldownRemaining <= 0f) {
                onCooldown = false;
                cooldownRemaining = 0f;
            } else {
                // Still on cooldown - fail without evaluating child
                return NodeState.FAILURE;
            }
        }
        
        // Not on cooldown - evaluate child
        NodeState childState = child.evaluate(blackboard);
        
        // Start cooldown when child completes
        if (childState != NodeState.RUNNING) {
            onCooldown = true;
            cooldownRemaining = cooldownDuration;
        }
        
        return childState;
    }
    
    @Override
    public void reset(Blackboard blackboard) {
        super.reset(blackboard);
        // Don't reset cooldown on tree reset - ability cooldowns should persist
    }
    
    /**
     * Forcibly resets the cooldown.
     */
    public void resetCooldown() {
        onCooldown = false;
        cooldownRemaining = 0f;
    }
    
    /**
     * Returns the cooldown duration.
     * 
     * @return Duration in seconds
     */
    public float getCooldownDuration() {
        return cooldownDuration;
    }
    
    /**
     * Returns the remaining cooldown time.
     * 
     * @return Remaining time in seconds (0 if not on cooldown)
     */
    public float getCooldownRemaining() {
        return cooldownRemaining;
    }
    
    /**
     * Returns whether currently on cooldown.
     * 
     * @return true if on cooldown
     */
    public boolean isOnCooldown() {
        return onCooldown;
    }
    
    /**
     * Returns cooldown progress as a percentage (0.0 = just started, 1.0 = ready).
     * 
     * @return Progress from 0.0 to 1.0
     */
    public float getCooldownProgress() {
        if (!onCooldown || cooldownDuration <= 0f) {
            return 1f;
        }
        return 1f - (cooldownRemaining / cooldownDuration);
    }
    
    @Override
    public CooldownDecorator setChild(BehaviorNode child) {
        super.setChild(child);
        return this;
    }
}
