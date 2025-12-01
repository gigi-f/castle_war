package com.castlewar.simulation;

/**
 * Interface for simulation system components that update game state.
 * Systems should be stateless or manage their own state independently.
 */
public interface SimulationSystem {
    /**
     * Updates the simulation system.
     * @param delta Time elapsed since last update in seconds
     */
    void update(float delta);
}
