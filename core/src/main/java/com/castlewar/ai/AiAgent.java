package com.castlewar.ai;

import com.castlewar.entity.Unit;

/**
 * Contract for all AI agents in Castle War.
 * <p>
 * All agent implementations must implement this interface to ensure
 * compatibility with the entity update loop in {@link com.castlewar.simulation.WorldContext}.
 * 
 * <h2>Implementation</h2>
 * Implement this interface directly for simple agents, or extend
 * {@link com.castlewar.ai.TransitionableAgent} for behavior tree-based agents.
 * 
 * <pre>{@code
 * public class SimpleAgent implements AiAgent {
 *     private final Unit owner;
 *     
 *     public SimpleAgent(Unit owner) {
 *         this.owner = owner;
 *     }
 *     
 *     @Override
 *     public void update(float delta, AiContext context) {
 *         // Simple behavior logic
 *     }
 *     
 *     @Override
 *     public Unit getOwner() {
 *         return owner;
 *     }
 * }
 * }</pre>
 * 
 * @see TransitionableAgent
 * @see AiContext
 */
public interface AiAgent {
    
    /**
     * Updates the agent's behavior for one frame.
     * <p>
     * Called every frame by the entity update loop. Implementations should:
     * <ul>
     *   <li>Process sensory input (perception)</li>
     *   <li>Make decisions (behavior tree/FSM)</li>
     *   <li>Execute actions (movement, combat)</li>
     * </ul>
     * 
     * @param delta   Time since last frame in seconds
     * @param context Access to world state and entities
     */
    void update(float delta, AiContext context);
    
    /**
     * Returns the unit this agent controls.
     * 
     * @return The owner unit
     */
    Unit getOwner();
    
    /**
     * Called when the agent should reset to initial state.
     * <p>
     * This is called when:
     * <ul>
     *   <li>The unit respawns</li>
     *   <li>The game state is reset</li>
     *   <li>The unit is disabled/re-enabled</li>
     * </ul>
     * 
     * Default implementation does nothing.
     */
    default void reset() {
        // Default: no-op
    }
    
    /**
     * Called when the agent is being disposed.
     * <p>
     * Clean up any resources (timers, observers, etc.).
     * Default implementation does nothing.
     */
    default void dispose() {
        // Default: no-op
    }
    
    /**
     * Returns whether this agent is currently active.
     * <p>
     * Inactive agents skip the update loop.
     * Default returns true.
     * 
     * @return true if agent should be updated
     */
    default boolean isActive() {
        return true;
    }
}
