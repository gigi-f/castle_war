package com.castlewar.communication;

import com.badlogic.gdx.math.Vector3;
import com.castlewar.entity.Entity;
import com.castlewar.entity.Team;
import com.castlewar.entity.Unit;

import java.util.ArrayList;
import java.util.List;

/**
 * Network for broadcasting alerts and commands between allied units.
 * 
 * <h2>Alert Types</h2>
 * <ul>
 *   <li><b>ENEMY_SPOTTED</b> - Enemy unit detected</li>
 *   <li><b>UNDER_ATTACK</b> - Unit is being attacked</li>
 *   <li><b>ALLY_DOWN</b> - Allied unit has fallen</li>
 *   <li><b>SIEGE_WARNING</b> - Siege engine detected</li>
 *   <li><b>BREACH</b> - Wall/gate has been breached</li>
 * </ul>
 * 
 * <h2>Commands</h2>
 * <ul>
 *   <li><b>ADVANCE</b> - Move toward objective</li>
 *   <li><b>HOLD</b> - Defend current position</li>
 *   <li><b>CHARGE</b> - Cavalry charge attack</li>
 *   <li><b>VOLLEY</b> - Coordinated archer fire</li>
 *   <li><b>RETREAT</b> - Fall back to rally point</li>
 *   <li><b>PROTECT_SIEGE</b> - Guard siege engines</li>
 * </ul>
 */
public class AlertNetwork {
    
    // ═══════════════════════════════════════════════════════════════════════════
    // ALERT TYPES
    // ═══════════════════════════════════════════════════════════════════════════
    
    public enum AlertType {
        /** Enemy unit detected */
        ENEMY_SPOTTED(15f, 5f),
        /** Unit is under attack */
        UNDER_ATTACK(20f, 8f),
        /** Allied unit has been killed */
        ALLY_DOWN(25f, 10f),
        /** Siege engine detected */
        SIEGE_WARNING(30f, 15f),
        /** Wall or gate breached */
        BREACH(50f, 20f);
        
        /** How far the alert propagates */
        public final float range;
        /** How long the alert persists */
        public final float duration;
        
        AlertType(float range, float duration) {
            this.range = range;
            this.duration = duration;
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // COMMAND TYPES
    // ═══════════════════════════════════════════════════════════════════════════
    
    public enum Command {
        /** Formation moves toward objective */
        ADVANCE,
        /** Units stop and defend position */
        HOLD,
        /** Cavalry initiate charge attack */
        CHARGE,
        /** Archers fire coordinated volley */
        VOLLEY,
        /** Units fall back to rally point */
        RETREAT,
        /** Guard siege engines */
        PROTECT_SIEGE,
        /** Form defensive formation */
        FORM_SQUARE,
        /** Spread out to avoid AoE */
        SCATTER
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // ALERT RECORD
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Represents an active alert in the network.
     */
    public record Alert(
        AlertType type,
        Vector3 position,
        Entity source,
        Team team,
        float expirationTime,
        Entity target
    ) {
        public boolean isExpired(float currentTime) {
            return currentTime >= expirationTime;
        }
    }
    
    /**
     * Represents an active command in the network.
     */
    public record ActiveCommand(
        Command command,
        Unit commander,
        List<Unit> targets,
        Vector3 targetPosition,
        float issuedTime
    ) {}
    
    // ═══════════════════════════════════════════════════════════════════════════
    // STATE
    // ═══════════════════════════════════════════════════════════════════════════
    
    private final List<Alert> activeAlerts = new ArrayList<>();
    private final List<ActiveCommand> activeCommands = new ArrayList<>();
    private float currentTime = 0f;
    
    /** Listeners for alerts (units can register to receive alerts) */
    private final List<AlertListener> listeners = new ArrayList<>();
    
    // ═══════════════════════════════════════════════════════════════════════════
    // SINGLETON
    // ═══════════════════════════════════════════════════════════════════════════
    
    private static AlertNetwork instance;
    
    public static AlertNetwork getInstance() {
        if (instance == null) {
            instance = new AlertNetwork();
        }
        return instance;
    }
    
    private AlertNetwork() {}
    
    // ═══════════════════════════════════════════════════════════════════════════
    // UPDATE
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Updates the alert network, removing expired alerts.
     * Call this once per frame.
     */
    public void update(float delta) {
        currentTime += delta;
        
        // Remove expired alerts
        activeAlerts.removeIf(alert -> alert.isExpired(currentTime));
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // ALERT BROADCASTING
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Broadcasts an alert to nearby allied units.
     * 
     * @param source   Unit sending the alert
     * @param type     Type of alert
     * @param position Location of the event
     * @param target   Target entity (e.g., spotted enemy), can be null
     */
    public void broadcastAlert(Unit source, AlertType type, Vector3 position, Entity target) {
        if (source == null) return;
        
        Alert alert = new Alert(
            type,
            new Vector3(position),
            source,
            source.getTeam(),
            currentTime + type.duration,
            target
        );
        
        activeAlerts.add(alert);
        
        // Notify listeners
        for (AlertListener listener : listeners) {
            if (listener.getTeam() == source.getTeam()) {
                float distance = listener.getPosition().dst(position);
                if (distance <= type.range) {
                    listener.onAlert(alert);
                }
            }
        }
    }
    
    /**
     * Simplified broadcast without specific target.
     */
    public void broadcastAlert(Unit source, AlertType type, Vector3 position) {
        broadcastAlert(source, type, position, null);
    }
    
    /**
     * Broadcasts an alert when a unit spots an enemy.
     */
    public void alertEnemySpotted(Unit source, Entity enemy) {
        broadcastAlert(source, AlertType.ENEMY_SPOTTED, enemy.getPosition(), enemy);
    }
    
    /**
     * Broadcasts an alert when a unit is under attack.
     */
    public void alertUnderAttack(Unit source, Entity attacker) {
        broadcastAlert(source, AlertType.UNDER_ATTACK, source.getPosition(), attacker);
    }
    
    /**
     * Broadcasts when an ally dies.
     */
    public void alertAllyDown(Unit fallen) {
        broadcastAlert(fallen, AlertType.ALLY_DOWN, fallen.getPosition(), null);
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // COMMAND SYSTEM
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Issues a command from a commander to specific units.
     * 
     * @param commander  Unit issuing the command
     * @param command    Command type
     * @param targets    Units receiving the command
     * @param position   Target position for movement commands (can be null)
     */
    public void issueCommand(Unit commander, Command command, List<Unit> targets, Vector3 position) {
        ActiveCommand cmd = new ActiveCommand(
            command,
            commander,
            new ArrayList<>(targets),
            position != null ? new Vector3(position) : null,
            currentTime
        );
        
        activeCommands.add(cmd);
        
        // Notify targeted units
        for (Unit target : targets) {
            for (AlertListener listener : listeners) {
                if (listener.getUnit() == target) {
                    listener.onCommand(cmd);
                    break;
                }
            }
        }
    }
    
    /**
     * Issues a command to all nearby allied units.
     * 
     * @param commander Unit issuing the command
     * @param command   Command type
     * @param radius    Radius to find units
     * @param allUnits  All units in the game
     * @param position  Target position for movement commands
     */
    public void issueAreaCommand(Unit commander, Command command, float radius, 
                                  List<Entity> allUnits, Vector3 position) {
        List<Unit> targets = new ArrayList<>();
        
        for (Entity e : allUnits) {
            if (!(e instanceof Unit)) continue;
            Unit unit = (Unit) e;
            if (unit == commander) continue;
            if (unit.getTeam() != commander.getTeam()) continue;
            if (unit.isDead()) continue;
            
            if (commander.getPosition().dst(unit.getPosition()) <= radius) {
                targets.add(unit);
            }
        }
        
        if (!targets.isEmpty()) {
            issueCommand(commander, command, targets, position);
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // ALERT QUERIES
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Gets all active alerts for a team.
     */
    public List<Alert> getActiveAlerts(Team team) {
        List<Alert> teamAlerts = new ArrayList<>();
        for (Alert alert : activeAlerts) {
            if (alert.team == team) {
                teamAlerts.add(alert);
            }
        }
        return teamAlerts;
    }
    
    /**
     * Gets the nearest alert of a specific type for a unit.
     */
    public Alert getNearestAlert(Unit unit, AlertType type) {
        Alert nearest = null;
        float nearestDist = Float.MAX_VALUE;
        
        for (Alert alert : activeAlerts) {
            if (alert.team != unit.getTeam()) continue;
            if (alert.type != type) continue;
            
            float dist = unit.getPosition().dst(alert.position);
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = alert;
            }
        }
        
        return nearest;
    }
    
    /**
     * Checks if there's an active alert of a type within range.
     */
    public boolean hasAlertInRange(Unit unit, AlertType type, float range) {
        for (Alert alert : activeAlerts) {
            if (alert.team != unit.getTeam()) continue;
            if (alert.type != type) continue;
            
            if (unit.getPosition().dst(alert.position) <= range) {
                return true;
            }
        }
        return false;
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // LISTENER SYSTEM
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Registers a listener for alerts and commands.
     */
    public void registerListener(AlertListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }
    
    /**
     * Unregisters a listener.
     */
    public void unregisterListener(AlertListener listener) {
        listeners.remove(listener);
    }
    
    /**
     * Clears all alerts and commands.
     */
    public void clear() {
        activeAlerts.clear();
        activeCommands.clear();
        currentTime = 0f;
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // LISTENER INTERFACE
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Interface for units that want to receive alerts and commands.
     */
    public interface AlertListener {
        Team getTeam();
        Vector3 getPosition();
        Unit getUnit();
        void onAlert(Alert alert);
        void onCommand(ActiveCommand command);
    }
}
