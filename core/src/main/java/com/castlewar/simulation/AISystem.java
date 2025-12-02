package com.castlewar.simulation;

import com.castlewar.combat.CombatService;
import com.castlewar.communication.AlertNetwork;
import com.castlewar.entity.Assassin;
import com.castlewar.entity.Entity;
import com.castlewar.entity.Unit;
import com.castlewar.navigation.FlowFieldManager;
import com.castlewar.world.GridWorld;

import java.util.List;

/**
 * System responsible for updating AI behavior, entity scanning, and combat systems.
 * 
 * <h2>Subsystems Updated</h2>
 * <ul>
 *   <li><b>AlertNetwork</b> - Unit communication and command propagation</li>
 *   <li><b>FlowFieldManager</b> - Group pathfinding cache management</li>
 *   <li><b>CombatService</b> - Poise regeneration and stagger updates</li>
 * </ul>
 * 
 * <h2>Update Order</h2>
 * <ol>
 *   <li>Update subsystems (AlertNetwork, FlowFieldManager)</li>
 *   <li>Scan for enemies</li>
 *   <li>Update AI and entity behaviors</li>
 *   <li>Update combat states (poise/stagger)</li>
 * </ol>
 */
public class AISystem implements SimulationSystem {
    private final List<Entity> entities;
    private final GridWorld gridWorld;
    
    // Subsystem references
    private final AlertNetwork alertNetwork;
    private final FlowFieldManager flowFieldManager;
    private final CombatService combatService;

    public AISystem(List<Entity> entities, GridWorld gridWorld) {
        this.entities = entities;
        this.gridWorld = gridWorld;
        
        // Get singleton instances
        this.alertNetwork = AlertNetwork.getInstance();
        this.flowFieldManager = FlowFieldManager.getInstance();
        this.combatService = CombatService.getInstance();
    }

    @Override
    public void update(float delta) {
        // 1. Update subsystems
        alertNetwork.update(delta);
        flowFieldManager.update(delta);
        
        // 2. Update AI and entity behaviors
        for (Entity entity : entities) {
            if (entity instanceof Unit unit) {
                if (!unit.isCorpse()) {
                    // Scan for enemies
                    unit.scanForEnemies(entities, gridWorld);
                    
                    // Update combat states
                    combatService.updatePoise(unit, delta);
                    combatService.updateStagger(unit, delta);
                }
            }
            
            // Special handling for assassins
            if (entity instanceof Assassin assassin) {
                if (!assassin.isCorpse()) {
                    assassin.checkForGuards(entities, gridWorld, delta);
                }
            }
            
            // Update entity
            entity.update(delta, gridWorld);
        }
    }
    
    /**
     * Gets the alert network for unit communication.
     */
    public AlertNetwork getAlertNetwork() {
        return alertNetwork;
    }
    
    /**
     * Gets the flow field manager for pathfinding.
     */
    public FlowFieldManager getFlowFieldManager() {
        return flowFieldManager;
    }
    
    /**
     * Gets the combat service for damage calculations.
     */
    public CombatService getCombatService() {
        return combatService;
    }
    
    /**
     * Clears all subsystem caches. Call when world changes significantly.
     */
    public void clearCaches() {
        alertNetwork.clear();
        flowFieldManager.clear();
    }
}
