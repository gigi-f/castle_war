package com.castlewar.communication;

import com.badlogic.gdx.math.Vector3;
import com.castlewar.entity.Entity;
import com.castlewar.entity.Team;
import com.castlewar.entity.Unit;
import com.castlewar.world.GridWorld;
import com.castlewar.world.GridWorld.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Tracks siege escalation and breach events that change AI priorities.
 * 
 * <h2>Escalation Levels</h2>
 * <ul>
 *   <li><b>STANDOFF</b> - Armies facing off, no engagement</li>
 *   <li><b>SKIRMISH</b> - Light combat, probing attacks</li>
 *   <li><b>SIEGE_BEGUN</b> - Siege engines deployed</li>
 *   <li><b>WALL_BREACH</b> - Walls have been breached</li>
 *   <li><b>GATE_BREACH</b> - Gates have been destroyed</li>
 *   <li><b>CASTLE_ASSAULT</b> - Full scale castle assault</li>
 *   <li><b>FINAL_STAND</b> - Last defenders at keep</li>
 * </ul>
 * 
 * <h2>Priority Shifts</h2>
 * <ul>
 *   <li>Pre-breach: Archers prioritize siege engines</li>
 *   <li>Post-breach: Guards rush to breach point</li>
 *   <li>Castle assault: King retreats to keep</li>
 *   <li>Final stand: All defenders rally to King</li>
 * </ul>
 */
public class SiegeEscalation {
    
    // ═══════════════════════════════════════════════════════════════════════════
    // ESCALATION LEVELS
    // ═══════════════════════════════════════════════════════════════════════════
    
    public enum EscalationLevel {
        /** Initial state - armies not engaged */
        STANDOFF(0, "Standoff"),
        /** Light combat has begun */
        SKIRMISH(1, "Skirmish"),
        /** Siege engines are attacking */
        SIEGE_BEGUN(2, "Siege Begun"),
        /** A wall section has been breached */
        WALL_BREACH(3, "Wall Breached"),
        /** A gate has been destroyed */
        GATE_BREACH(4, "Gate Breached"),
        /** Attackers inside castle */
        CASTLE_ASSAULT(5, "Castle Assault"),
        /** Defenders making last stand */
        FINAL_STAND(6, "Final Stand");
        
        public final int severity;
        public final String displayName;
        
        EscalationLevel(int severity, String displayName) {
            this.severity = severity;
            this.displayName = displayName;
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // BREACH EVENT
    // ═══════════════════════════════════════════════════════════════════════════
    
    public enum BreachType {
        /** Wall section destroyed */
        WALL_BREACH(3, 20f),
        /** Gate destroyed */
        GATE_BREACH(5, 30f),
        /** Tower destroyed */
        TOWER_BREACH(4, 25f),
        /** Multiple breaches near each other */
        MAJOR_BREACH(6, 40f);
        
        /** Priority for defenders (higher = more important) */
        public final int priority;
        /** Radius for nearby unit response */
        public final float responseRadius;
        
        BreachType(int priority, float responseRadius) {
            this.priority = priority;
            this.responseRadius = responseRadius;
        }
    }
    
    /**
     * Record of a breach event.
     */
    public static class Breach {
        public final BreachType type;
        public final Vector3 position;
        public final Team defendingTeam;
        public final float timestamp;
        public boolean isSealed;
        public int defendersAssigned;
        
        public Breach(BreachType type, Vector3 position, Team defendingTeam, float timestamp) {
            this.type = type;
            this.position = new Vector3(position);
            this.defendingTeam = defendingTeam;
            this.timestamp = timestamp;
            this.isSealed = false;
            this.defendersAssigned = 0;
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // STATE
    // ═══════════════════════════════════════════════════════════════════════════
    
    /** Current escalation for each team (as defender) */
    private EscalationLevel whiteTeamEscalation = EscalationLevel.STANDOFF;
    private EscalationLevel blackTeamEscalation = EscalationLevel.STANDOFF;
    
    /** Active breaches */
    private final List<Breach> activeBreaches = new ArrayList<>();
    
    /** Siege engine count by attacking team */
    private int whiteSiegeEngines = 0;
    private int blackSiegeEngines = 0;
    
    /** Combat statistics for escalation tracking */
    private int whiteCasualties = 0;
    private int blackCasualties = 0;
    
    /** Escalation listeners */
    private final List<EscalationListener> listeners = new ArrayList<>();
    
    private float currentTime = 0f;
    
    // ═══════════════════════════════════════════════════════════════════════════
    // SINGLETON
    // ═══════════════════════════════════════════════════════════════════════════
    
    private static SiegeEscalation instance;
    
    public static SiegeEscalation getInstance() {
        if (instance == null) {
            instance = new SiegeEscalation();
        }
        return instance;
    }
    
    private SiegeEscalation() {}
    
    // ═══════════════════════════════════════════════════════════════════════════
    // UPDATE
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Updates siege escalation state.
     */
    public void update(float delta) {
        currentTime += delta;
        
        // Re-evaluate escalation levels periodically
        updateEscalation(Team.WHITE);
        updateEscalation(Team.BLACK);
    }
    
    /**
     * Updates escalation level for a defending team.
     */
    private void updateEscalation(Team defendingTeam) {
        EscalationLevel current = getEscalation(defendingTeam);
        EscalationLevel newLevel = calculateEscalation(defendingTeam);
        
        if (newLevel.severity > current.severity) {
            setEscalation(defendingTeam, newLevel);
            notifyEscalationChange(defendingTeam, current, newLevel);
        }
    }
    
    /**
     * Calculates appropriate escalation level based on game state.
     */
    private EscalationLevel calculateEscalation(Team defendingTeam) {
        // Check for final stand
        if (hasBreachType(defendingTeam, BreachType.GATE_BREACH) && 
            getCasualties(defendingTeam) > 10) {
            return EscalationLevel.FINAL_STAND;
        }
        
        // Check for castle assault
        if (hasAnyBreach(defendingTeam) && getActiveBreachCount(defendingTeam) >= 2) {
            return EscalationLevel.CASTLE_ASSAULT;
        }
        
        // Check for gate breach
        if (hasBreachType(defendingTeam, BreachType.GATE_BREACH)) {
            return EscalationLevel.GATE_BREACH;
        }
        
        // Check for wall breach
        if (hasBreachType(defendingTeam, BreachType.WALL_BREACH)) {
            return EscalationLevel.WALL_BREACH;
        }
        
        // Check for siege begun
        Team attackingTeam = defendingTeam == Team.WHITE ? Team.BLACK : Team.WHITE;
        if (getSiegeEngineCount(attackingTeam) > 0) {
            return EscalationLevel.SIEGE_BEGUN;
        }
        
        // Check for skirmish
        if (getCasualties(defendingTeam) > 0 || getCasualties(attackingTeam) > 0) {
            return EscalationLevel.SKIRMISH;
        }
        
        return EscalationLevel.STANDOFF;
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // ESCALATION MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Gets current escalation for a defending team.
     */
    public EscalationLevel getEscalation(Team defendingTeam) {
        return defendingTeam == Team.WHITE ? whiteTeamEscalation : blackTeamEscalation;
    }
    
    /**
     * Sets escalation level.
     */
    public void setEscalation(Team defendingTeam, EscalationLevel level) {
        if (defendingTeam == Team.WHITE) {
            whiteTeamEscalation = level;
        } else {
            blackTeamEscalation = level;
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // BREACH MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Records a breach event.
     * 
     * @param type         Type of breach
     * @param position     Location of the breach
     * @param defendingTeam Team whose defenses were breached
     */
    public void recordBreach(BreachType type, Vector3 position, Team defendingTeam) {
        Breach breach = new Breach(type, position, defendingTeam, currentTime);
        activeBreaches.add(breach);
        
        // Notify listeners
        for (EscalationListener listener : listeners) {
            listener.onBreach(breach);
        }
        
        // Force escalation update
        updateEscalation(defendingTeam);
    }
    
    /**
     * Records a wall breach at grid coordinates.
     */
    public void recordWallBreach(int x, int y, int z, Team defendingTeam) {
        recordBreach(BreachType.WALL_BREACH, new Vector3(x, y, z), defendingTeam);
    }
    
    /**
     * Records a gate breach.
     */
    public void recordGateBreach(int x, int y, int z, Team defendingTeam) {
        recordBreach(BreachType.GATE_BREACH, new Vector3(x, y, z), defendingTeam);
    }
    
    /**
     * Marks a breach as sealed (repaired or blocked with defenders).
     */
    public void sealBreach(Breach breach) {
        breach.isSealed = true;
    }
    
    /**
     * Gets all active (unsealed) breaches for a team.
     */
    public List<Breach> getActiveBreaches(Team defendingTeam) {
        List<Breach> result = new ArrayList<>();
        for (Breach breach : activeBreaches) {
            if (breach.defendingTeam == defendingTeam && !breach.isSealed) {
                result.add(breach);
            }
        }
        return result;
    }
    
    /**
     * Gets the highest priority breach for a team.
     */
    public Breach getHighestPriorityBreach(Team defendingTeam) {
        Breach highest = null;
        for (Breach breach : activeBreaches) {
            if (breach.defendingTeam == defendingTeam && !breach.isSealed) {
                if (highest == null || breach.type.priority > highest.type.priority) {
                    highest = breach;
                }
            }
        }
        return highest;
    }
    
    /**
     * Gets the nearest breach to a position.
     */
    public Breach getNearestBreach(Team defendingTeam, Vector3 position) {
        Breach nearest = null;
        float nearestDist = Float.MAX_VALUE;
        
        for (Breach breach : activeBreaches) {
            if (breach.defendingTeam == defendingTeam && !breach.isSealed) {
                float dist = breach.position.dst(position);
                if (dist < nearestDist) {
                    nearestDist = dist;
                    nearest = breach;
                }
            }
        }
        
        return nearest;
    }
    
    /**
     * Checks if team has any active breach.
     */
    public boolean hasAnyBreach(Team defendingTeam) {
        return !getActiveBreaches(defendingTeam).isEmpty();
    }
    
    /**
     * Checks if team has a specific breach type.
     */
    public boolean hasBreachType(Team defendingTeam, BreachType type) {
        for (Breach breach : activeBreaches) {
            if (breach.defendingTeam == defendingTeam && 
                breach.type == type && !breach.isSealed) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Gets count of active breaches.
     */
    public int getActiveBreachCount(Team defendingTeam) {
        return getActiveBreaches(defendingTeam).size();
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // SIEGE ENGINE TRACKING
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Updates siege engine count for a team.
     */
    public void setSiegeEngineCount(Team team, int count) {
        if (team == Team.WHITE) {
            whiteSiegeEngines = count;
        } else {
            blackSiegeEngines = count;
        }
    }
    
    /**
     * Gets siege engine count for a team.
     */
    public int getSiegeEngineCount(Team team) {
        return team == Team.WHITE ? whiteSiegeEngines : blackSiegeEngines;
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // CASUALTY TRACKING
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Records a casualty.
     */
    public void recordCasualty(Team team) {
        if (team == Team.WHITE) {
            whiteCasualties++;
        } else {
            blackCasualties++;
        }
    }
    
    /**
     * Gets casualty count.
     */
    public int getCasualties(Team team) {
        return team == Team.WHITE ? whiteCasualties : blackCasualties;
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // AI PRIORITY QUERIES
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Checks if archers should prioritize siege engines.
     */
    public boolean shouldPrioritizeSiegeEngines(Team defendingTeam) {
        EscalationLevel level = getEscalation(defendingTeam);
        return level == EscalationLevel.SIEGE_BEGUN || 
               level == EscalationLevel.SKIRMISH;
    }
    
    /**
     * Checks if guards should rush to breach point.
     */
    public boolean shouldRushToBreach(Team defendingTeam) {
        EscalationLevel level = getEscalation(defendingTeam);
        return level.severity >= EscalationLevel.WALL_BREACH.severity;
    }
    
    /**
     * Checks if king should retreat to keep.
     */
    public boolean shouldKingRetreat(Team team) {
        EscalationLevel level = getEscalation(team);
        return level.severity >= EscalationLevel.CASTLE_ASSAULT.severity;
    }
    
    /**
     * Checks if all defenders should rally to king.
     */
    public boolean shouldRallyToKing(Team team) {
        return getEscalation(team) == EscalationLevel.FINAL_STAND;
    }
    
    /**
     * Gets the recommended response position for a defender.
     */
    public Vector3 getDefenderResponsePoint(Unit defender) {
        Team team = defender.getTeam();
        
        // If in final stand, return null (should rally to king instead)
        if (shouldRallyToKing(team)) {
            return null;
        }
        
        // Otherwise, return nearest breach
        Breach breach = getNearestBreach(team, defender.getPosition());
        if (breach != null) {
            return new Vector3(breach.position);
        }
        
        return null;
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // LISTENER SYSTEM
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Registers an escalation listener.
     */
    public void registerListener(EscalationListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }
    
    /**
     * Unregisters a listener.
     */
    public void unregisterListener(EscalationListener listener) {
        listeners.remove(listener);
    }
    
    /**
     * Notifies listeners of escalation change.
     */
    private void notifyEscalationChange(Team team, EscalationLevel oldLevel, EscalationLevel newLevel) {
        for (EscalationListener listener : listeners) {
            listener.onEscalationChange(team, oldLevel, newLevel);
        }
    }
    
    /**
     * Clears all state.
     */
    public void clear() {
        whiteTeamEscalation = EscalationLevel.STANDOFF;
        blackTeamEscalation = EscalationLevel.STANDOFF;
        activeBreaches.clear();
        whiteSiegeEngines = 0;
        blackSiegeEngines = 0;
        whiteCasualties = 0;
        blackCasualties = 0;
        currentTime = 0f;
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // LISTENER INTERFACE
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Interface for listening to escalation events.
     */
    public interface EscalationListener {
        void onEscalationChange(Team team, EscalationLevel oldLevel, EscalationLevel newLevel);
        void onBreach(Breach breach);
    }
}
