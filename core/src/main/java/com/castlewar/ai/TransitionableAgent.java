package com.castlewar.ai;

import com.castlewar.ai.behavior.BehaviorTree;
import com.castlewar.ai.behavior.NodeState;
import com.castlewar.ai.blackboard.Blackboard;
import com.castlewar.ai.blackboard.BlackboardKey;
import com.castlewar.entity.Entity;
import com.castlewar.entity.Unit;

import java.util.List;

/**
 * Base class for behavior tree-based AI agents.
 * <p>
 * Provides common infrastructure for all unit agents:
 * <ul>
 *   <li>Blackboard for data sharing between nodes</li>
 *   <li>Behavior tree execution</li>
 *   <li>Perception updates (scan for enemies)</li>
 *   <li>State tracking for animation/FSM output</li>
 * </ul>
 * 
 * <h2>Usage</h2>
 * Extend this class and implement {@link #createBehaviorTree()} to define
 * the agent's behavior:
 * <pre>{@code
 * public class GuardAgent extends TransitionableAgent<Guard, GuardState> {
 *     public GuardAgent(Guard owner, AiContext context) {
 *         super(owner, context, GuardState.class, GuardState.IDLE);
 *     }
 *     
 *     @Override
 *     protected BehaviorTree createBehaviorTree() {
 *         return BehaviorTree.builder("guard")
 *             .selector("root")
 *                 // ... define behavior
 *             .end()
 *             .build();
 *     }
 * }
 * }</pre>
 * 
 * @param <T> The unit type this agent controls
 * @param <S> The state enum type for FSM output
 */
public abstract class TransitionableAgent<T extends Unit, S extends Enum<S>> implements AiAgent {
    
    /** The unit this agent controls */
    protected final T owner;
    
    /** Access to world state and entities */
    protected final AiContext context;
    
    /** Shared data store for behavior tree nodes */
    protected final Blackboard blackboard;
    
    /** The behavior tree driving decisions */
    protected BehaviorTree behaviorTree;
    
    /** Current FSM state (output only, for animation/UI) */
    protected S currentState;
    
    /** State enum class for reflection */
    protected final Class<S> stateClass;
    
    /** Whether the agent is currently active */
    protected boolean active = true;
    
    /** Delta time for current frame */
    protected float deltaTime;
    
    // ═══════════════════════════════════════════════════════════════════════════
    // CONFIGURATION
    // ═══════════════════════════════════════════════════════════════════════════
    
    /** Vision range for enemy detection */
    protected float visionRange = 10f;
    
    /** Field of view in degrees */
    protected float fovDegrees = 120f;
    
    /** Health percentage threshold for fleeing */
    protected float fleeHealthThreshold = 0.3f;
    
    /**
     * Creates a new TransitionableAgent.
     * 
     * @param owner        The unit this agent controls
     * @param context      Access to world state
     * @param stateClass   State enum class
     * @param initialState Initial FSM state
     */
    protected TransitionableAgent(T owner, AiContext context, Class<S> stateClass, S initialState) {
        this.owner = owner;
        this.context = context;
        this.stateClass = stateClass;
        this.currentState = initialState;
        this.blackboard = new Blackboard();
        
        // Initialize blackboard with self reference
        blackboard.set(BlackboardKey.SELF_UNIT, owner);
        
        // Create behavior tree (deferred to allow subclass initialization)
        // Subclasses should call initializeBehaviorTree() after their constructor
    }
    
    /**
     * Initializes the behavior tree.
     * Call this at the end of subclass constructors.
     */
    protected void initializeBehaviorTree() {
        this.behaviorTree = createBehaviorTree();
    }
    
    /**
     * Creates the behavior tree for this agent.
     * Subclasses must implement this to define their behavior.
     * 
     * @return The constructed behavior tree
     */
    protected abstract BehaviorTree createBehaviorTree();
    
    // ═══════════════════════════════════════════════════════════════════════════
    // AI AGENT INTERFACE
    // ═══════════════════════════════════════════════════════════════════════════
    
    @Override
    public void update(float delta, AiContext ctx) {
        if (!active || owner.isDead()) {
            return;
        }
        
        this.deltaTime = delta;
        
        // Update blackboard with current state
        updateBlackboard(delta);
        
        // Update perception
        updatePerception(ctx.getEntities());
        
        // Tick behavior tree
        if (behaviorTree != null) {
            behaviorTree.tick(blackboard, delta);
        }
    }
    
    @Override
    public T getOwner() {
        return owner;
    }
    
    @Override
    public void reset() {
        if (behaviorTree != null) {
            behaviorTree.reset(blackboard);
        }
        blackboard.clear();
        blackboard.set(BlackboardKey.SELF_UNIT, owner);
        active = true;
    }
    
    @Override
    public void dispose() {
        blackboard.clearAllObservers();
    }
    
    @Override
    public boolean isActive() {
        return active && !owner.isDead();
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // BLACKBOARD UPDATES
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Updates the blackboard with current unit state.
     * Called at the start of each update.
     */
    protected void updateBlackboard(float delta) {
        // Self state
        blackboard.setFloat(BlackboardKey.SELF_HP, owner.getHp());
        blackboard.setFloat(BlackboardKey.SELF_HP_PERCENT, owner.getHp() / owner.getMaxHp());
        blackboard.setBool(BlackboardKey.SELF_IS_ALIVE, !owner.isDead());
        blackboard.setVector3(BlackboardKey.SELF_POSITION, owner.getPosition());
        blackboard.setVector3(BlackboardKey.SELF_FACING, owner.getFacing());
        
        // Movement state
        blackboard.setBool(BlackboardKey.MOVEMENT_ENABLED, !owner.isStunned());
    }
    
    /**
     * Updates perception - scans for visible enemies.
     */
    protected void updatePerception(List<Entity> entities) {
        Unit nearestEnemy = null;
        float nearestDist = visionRange;
        
        for (Entity e : entities) {
            if (!(e instanceof Unit)) continue;
            Unit unit = (Unit) e;
            
            if (unit.getTeam() == owner.getTeam()) continue;
            if (unit.isDead()) continue;
            
            float dist = owner.getPosition().dst(unit.getPosition());
            if (dist > visionRange) continue;
            
            // FOV check
            if (!isInFieldOfView(unit)) continue;
            
            if (dist < nearestDist) {
                nearestDist = dist;
                nearestEnemy = unit;
            }
        }
        
        // Update blackboard
        if (nearestEnemy != null) {
            blackboard.set(BlackboardKey.COMBAT_CURRENT_TARGET, nearestEnemy);
            blackboard.setVector3(BlackboardKey.PERCEPTION_LAST_KNOWN_THREAT_POS, nearestEnemy.getPosition());
            blackboard.setFloat(BlackboardKey.PERCEPTION_ALERT_LEVEL, 
                Math.min(1f, blackboard.getFloat(BlackboardKey.PERCEPTION_ALERT_LEVEL) + 0.3f));
            
            // Check attack range
            float attackRange = getAttackRange();
            blackboard.setBool(BlackboardKey.COMBAT_IN_ATTACK_RANGE, nearestDist <= attackRange);
        } else {
            // Decay alert level
            float alert = blackboard.getFloat(BlackboardKey.PERCEPTION_ALERT_LEVEL);
            if (alert > 0) {
                blackboard.setFloat(BlackboardKey.PERCEPTION_ALERT_LEVEL, 
                    Math.max(0f, alert - deltaTime * 0.1f));
            }
        }
    }
    
    /**
     * Checks if target is within field of view.
     */
    protected boolean isInFieldOfView(Entity target) {
        com.badlogic.gdx.math.Vector3 toTarget = new com.badlogic.gdx.math.Vector3(target.getPosition())
            .sub(owner.getPosition());
        toTarget.z = 0; // Ignore vertical
        toTarget.nor();
        
        float dot = owner.getFacing().dot(toTarget);
        float fovCos = (float) Math.cos(Math.toRadians(fovDegrees / 2));
        
        return dot >= fovCos;
    }
    
    /**
     * Returns the attack range for this unit.
     * Override in subclasses for different ranges.
     */
    protected float getAttackRange() {
        return 1.5f;
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // STATE MANAGEMENT (FSM OUTPUT)
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Changes the current FSM state.
     * This is OUTPUT ONLY - called by behavior tree tasks to signal
     * state changes for animation/UI purposes.
     * 
     * @param newState The new state
     */
    public void changeState(S newState) {
        if (newState != null && newState != currentState) {
            S oldState = currentState;
            currentState = newState;
            onStateChanged(oldState, newState);
        }
    }
    
    /**
     * Called when the FSM state changes.
     * Override to trigger animations or other side effects.
     */
    protected void onStateChanged(S oldState, S newState) {
        // Default: no-op
    }
    
    /**
     * Returns the current FSM state.
     * 
     * @return Current state
     */
    public S getCurrentState() {
        return currentState;
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // ACCESSORS
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Returns the blackboard.
     * 
     * @return Blackboard instance
     */
    public Blackboard getBlackboard() {
        return blackboard;
    }
    
    /**
     * Returns the AI context.
     * 
     * @return AiContext instance
     */
    public AiContext getContext() {
        return context;
    }
    
    /**
     * Returns the behavior tree.
     * 
     * @return BehaviorTree instance
     */
    public BehaviorTree getBehaviorTree() {
        return behaviorTree;
    }
    
    /**
     * Sets whether the agent is active.
     * 
     * @param active true to enable updates
     */
    public void setActive(boolean active) {
        this.active = active;
    }
    
    /**
     * Returns the current delta time.
     * Useful for tasks that need frame timing.
     * 
     * @return Delta time in seconds
     */
    public float getDeltaTime() {
        return deltaTime;
    }
}
