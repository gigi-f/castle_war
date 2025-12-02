package com.castlewar.simulation;

import com.castlewar.combat.CombatService;
import com.castlewar.communication.AlertNetwork;
import com.castlewar.communication.EnvironmentalAwareness;
import com.castlewar.communication.LineOfSightRelay;
import com.castlewar.communication.SiegeEscalation;
import com.castlewar.communication.SoundPropagation;
import com.castlewar.entity.Assassin;
import com.castlewar.entity.Entity;
import com.castlewar.entity.Team;
import com.castlewar.entity.Unit;
import com.castlewar.navigation.FlowFieldManager;
import com.castlewar.world.GridWorld;

import java.util.List;

/**
 * System responsible for updating AI behavior, entity scanning, and emergent gameplay systems.
 * 
 * <h2>Subsystems Updated</h2>
 * <ul>
 *   <li><b>AlertNetwork</b> - Unit communication and command propagation</li>
 *   <li><b>FlowFieldManager</b> - Group pathfinding cache management</li>
 *   <li><b>CombatService</b> - Poise regeneration and stagger updates</li>
 *   <li><b>SoundPropagation</b> - Sound-based awareness and detection</li>
 *   <li><b>LineOfSightRelay</b> - Guard sighting relay chains</li>
 *   <li><b>EnvironmentalAwareness</b> - Morale system and environmental events</li>
 *   <li><b>SiegeEscalation</b> - Battle escalation and breach tracking</li>
 * </ul>
 * 
 * <h2>Update Order</h2>
 * <ol>
 *   <li>Update communication systems (AlertNetwork, SoundPropagation, LineOfSightRelay)</li>
 *   <li>Update environmental and escalation systems</li>
 *   <li>Scan for enemies and update awareness</li>
 *   <li>Update AI and entity behaviors</li>
 *   <li>Update combat states (poise/stagger)</li>
 * </ol>
 */
public class AISystem implements SimulationSystem {
    private final List<Entity> entities;
    private final GridWorld gridWorld;
    
    // Core subsystem references
    private final AlertNetwork alertNetwork;
    private final FlowFieldManager flowFieldManager;
    private final CombatService combatService;
    
    // Emergent gameplay subsystems
    private final SoundPropagation soundPropagation;
    private final LineOfSightRelay lineOfSightRelay;
    private final EnvironmentalAwareness environmentalAwareness;
    private final SiegeEscalation siegeEscalation;
    
    // Team objective tracking
    private final TeamObjective teamObjective;

    public AISystem(List<Entity> entities, GridWorld gridWorld) {
        this.entities = entities;
        this.gridWorld = gridWorld;
        
        // Get singleton instances for core systems
        this.alertNetwork = AlertNetwork.getInstance();
        this.flowFieldManager = FlowFieldManager.getInstance();
        this.combatService = CombatService.getInstance();
        
        // Get singleton instances for emergent gameplay systems
        this.soundPropagation = SoundPropagation.getInstance();
        this.lineOfSightRelay = LineOfSightRelay.getInstance();
        this.environmentalAwareness = EnvironmentalAwareness.getInstance();
        this.siegeEscalation = SiegeEscalation.getInstance();
        
        // Initialize team objective tracking
        this.teamObjective = TeamObjective.getInstance();
        this.teamObjective.initialize(entities);
    }

    @Override
    public void update(float delta) {
        // 1. Update communication systems
        alertNetwork.update(delta);
        soundPropagation.update(delta);
        lineOfSightRelay.update(delta);
        
        // 2. Update environmental and escalation systems
        environmentalAwareness.update(delta);
        siegeEscalation.update(delta);
        flowFieldManager.update(delta);
        
        // 3. Count siege engines for escalation tracking
        updateSiegeEngineCount();
        
        // 4. Update AI and entity behaviors
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
     * Updates siege engine counts for escalation tracking.
     */
    private void updateSiegeEngineCount() {
        int whiteCount = 0;
        int blackCount = 0;
        
        for (Entity entity : entities) {
            String className = entity.getClass().getSimpleName();
            if ("Trebuchet".equals(className) || "BatteringRam".equals(className)) {
                if (entity instanceof Unit unit && !unit.isDead()) {
                    if (unit.getTeam() == Team.WHITE) {
                        whiteCount++;
                    } else {
                        blackCount++;
                    }
                }
            }
        }
        
        siegeEscalation.setSiegeEngineCount(Team.WHITE, whiteCount);
        siegeEscalation.setSiegeEngineCount(Team.BLACK, blackCount);
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // CORE SYSTEM GETTERS
    // ═══════════════════════════════════════════════════════════════════════════
    
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
    
    // ═══════════════════════════════════════════════════════════════════════════
    // EMERGENT GAMEPLAY SYSTEM GETTERS
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Gets the sound propagation system.
     */
    public SoundPropagation getSoundPropagation() {
        return soundPropagation;
    }
    
    /**
     * Gets the line of sight relay system.
     */
    public LineOfSightRelay getLineOfSightRelay() {
        return lineOfSightRelay;
    }
    
    /**
     * Gets the environmental awareness system.
     */
    public EnvironmentalAwareness getEnvironmentalAwareness() {
        return environmentalAwareness;
    }
    
    /**
     * Gets the siege escalation system.
     */
    public SiegeEscalation getSiegeEscalation() {
        return siegeEscalation;
    }
    
    /**
     * Gets the team objective tracker for king assassination goals.
     */
    public TeamObjective getTeamObjective() {
        return teamObjective;
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // ENTITY LIFECYCLE
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Called when a unit dies. Records death events for emergent systems.
     * 
     * @param fallen The unit that died
     * @param killer The unit that killed it (can be null)
     */
    public void onUnitDeath(Unit fallen, Unit killer) {
        // Record casualty for escalation
        siegeEscalation.recordCasualty(fallen.getTeam());
        
        // Record environmental event for morale
        environmentalAwareness.recordAllyDeath(fallen, entities);
        
        // If there was a killer, record kill for killer's team morale
        if (killer != null) {
            environmentalAwareness.recordEnemyKill(killer, fallen, entities);
        }
        
        // Emit death cry sound
        soundPropagation.emitSound(
            SoundPropagation.SoundType.DEATH_CRY,
            fallen.getPosition(),
            fallen
        );
        
        // Broadcast alert to allies
        alertNetwork.alertAllyDown(fallen);
        
        // Clean up morale tracking
        environmentalAwareness.removeUnit(fallen);
    }
    
    /**
     * Called when a breach occurs.
     * 
     * @param x             X position of breach
     * @param y             Y position of breach
     * @param z             Z position of breach
     * @param defendingTeam Team whose defenses were breached
     * @param isGate        True if gate breach, false if wall breach
     */
    public void onBreach(int x, int y, int z, Team defendingTeam, boolean isGate) {
        if (isGate) {
            siegeEscalation.recordGateBreach(x, y, z, defendingTeam);
        } else {
            siegeEscalation.recordWallBreach(x, y, z, defendingTeam);
        }
        
        // Emit breach sound
        soundPropagation.emitSound(
            SoundPropagation.SoundType.GATE_BREACH,
            new com.badlogic.gdx.math.Vector3(x, y, z),
            null,
            1.5f // Extra loud
        );
        
        // Record environmental event
        Team attackingTeam = defendingTeam == Team.WHITE ? Team.BLACK : Team.WHITE;
        environmentalAwareness.recordVictoryMoment(attackingTeam, 
            new com.badlogic.gdx.math.Vector3(x, y, z), entities);
        environmentalAwareness.recordDefeatMoment(defendingTeam,
            new com.badlogic.gdx.math.Vector3(x, y, z), entities);
    }
    
    /**
     * Clears all subsystem caches. Call when world changes significantly.
     */
    public void clearCaches() {
        alertNetwork.clear();
        flowFieldManager.clear();
        soundPropagation.clear();
        lineOfSightRelay.clear();
        environmentalAwareness.clear();
        siegeEscalation.clear();
    }
}
