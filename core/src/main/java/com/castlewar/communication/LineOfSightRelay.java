package com.castlewar.communication;

import com.badlogic.gdx.math.Vector3;
import com.castlewar.entity.Entity;
import com.castlewar.entity.Team;
import com.castlewar.entity.Unit;
import com.castlewar.world.GridWorld;
import com.castlewar.world.GridWorld.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Line-of-sight relay system for guard communication chains.
 * 
 * <h2>Relay Mechanics</h2>
 * <ul>
 *   <li>Guards who spot threats can relay to guards who can see them</li>
 *   <li>Creates chains of awareness across castle defenses</li>
 *   <li>Information degrades slightly with each relay hop</li>
 *   <li>Maximum relay chain length prevents infinite propagation</li>
 * </ul>
 * 
 * <h2>Sighting Types</h2>
 * <ul>
 *   <li><b>ASSASSIN_SPOTTED</b> - High priority, fast relay</li>
 *   <li><b>ENEMY_UNIT</b> - Standard enemy sighting</li>
 *   <li><b>SIEGE_ENGINE</b> - Siege weapon detected</li>
 *   <li><b>BREACH_DETECTED</b> - Wall/gate breach</li>
 * </ul>
 */
public class LineOfSightRelay {
    
    // ═══════════════════════════════════════════════════════════════════════════
    // CONSTANTS
    // ═══════════════════════════════════════════════════════════════════════════
    
    /** Maximum visual range for relay */
    private static final float MAX_RELAY_RANGE = 30f;
    
    /** Maximum hops in a relay chain */
    private static final int MAX_RELAY_HOPS = 5;
    
    /** Information accuracy degradation per hop */
    private static final float ACCURACY_DECAY_PER_HOP = 0.15f;
    
    /** Time before a sighting expires */
    private static final float SIGHTING_LIFETIME = 10f;
    
    /** Cooldown before same sighting can be relayed again */
    private static final float RELAY_COOLDOWN = 2f;
    
    // ═══════════════════════════════════════════════════════════════════════════
    // SIGHTING TYPE
    // ═══════════════════════════════════════════════════════════════════════════
    
    public enum SightingType {
        /** Assassin spotted - highest priority */
        ASSASSIN_SPOTTED(1.0f, 0.5f),
        /** Generic enemy unit */
        ENEMY_UNIT(0.7f, 1.0f),
        /** Siege engine detected */
        SIEGE_ENGINE(0.9f, 0.8f),
        /** Wall or gate breach */
        BREACH_DETECTED(1.0f, 0.3f);
        
        /** Priority for relay (higher = relayed first) */
        public final float priority;
        /** Relay delay multiplier (lower = faster relay) */
        public final float relayDelay;
        
        SightingType(float priority, float relayDelay) {
            this.priority = priority;
            this.relayDelay = relayDelay;
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // SIGHTING RECORD
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Represents a sighting that can be relayed.
     */
    public static class Sighting {
        public final SightingType type;
        public final Entity target;
        public final Vector3 lastKnownPosition;
        public final Team reportingTeam;
        public final Unit originalSpotter;
        public final float accuracy;
        public final int hopCount;
        public final float timestamp;
        public float expirationTime;
        
        public Sighting(SightingType type, Entity target, Vector3 position, 
                       Team team, Unit spotter, float accuracy, int hops, float time) {
            this.type = type;
            this.target = target;
            this.lastKnownPosition = new Vector3(position);
            this.reportingTeam = team;
            this.originalSpotter = spotter;
            this.accuracy = accuracy;
            this.hopCount = hops;
            this.timestamp = time;
            this.expirationTime = time + SIGHTING_LIFETIME;
        }
        
        public boolean isExpired(float currentTime) {
            return currentTime >= expirationTime;
        }
        
        /**
         * Creates a degraded copy for relay.
         */
        public Sighting createRelayedCopy(float currentTime) {
            float newAccuracy = Math.max(0.1f, accuracy - ACCURACY_DECAY_PER_HOP);
            return new Sighting(
                type, target, lastKnownPosition, reportingTeam,
                originalSpotter, newAccuracy, hopCount + 1, currentTime
            );
        }
        
        /**
         * Gets position with accuracy-based offset.
         * Lower accuracy = larger potential error in reported position.
         */
        public Vector3 getReportedPosition() {
            if (accuracy >= 0.95f) {
                return new Vector3(lastKnownPosition);
            }
            
            // Add error based on inaccuracy
            float error = (1f - accuracy) * 5f; // Up to 5 blocks of error at 0% accuracy
            float offsetX = (float) (Math.random() - 0.5) * error * 2;
            float offsetY = (float) (Math.random() - 0.5) * error * 2;
            
            return new Vector3(
                lastKnownPosition.x + offsetX,
                lastKnownPosition.y + offsetY,
                lastKnownPosition.z
            );
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // STATE
    // ═══════════════════════════════════════════════════════════════════════════
    
    /** Active sightings by team */
    private final Map<Team, List<Sighting>> teamSightings = new HashMap<>();
    
    /** Pending relays (unit -> sighting to relay) */
    private final Map<Unit, PendingRelay> pendingRelays = new HashMap<>();
    
    /** Cooldown tracking (unit -> last relay time) */
    private final Map<Unit, Float> relayCooldowns = new HashMap<>();
    
    private float currentTime = 0f;
    
    // ═══════════════════════════════════════════════════════════════════════════
    // SINGLETON
    // ═══════════════════════════════════════════════════════════════════════════
    
    private static LineOfSightRelay instance;
    
    public static LineOfSightRelay getInstance() {
        if (instance == null) {
            instance = new LineOfSightRelay();
        }
        return instance;
    }
    
    private LineOfSightRelay() {
        teamSightings.put(Team.WHITE, new ArrayList<>());
        teamSightings.put(Team.BLACK, new ArrayList<>());
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // UPDATE
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Updates the relay system.
     */
    public void update(float delta) {
        currentTime += delta;
        
        // Remove expired sightings
        for (List<Sighting> sightings : teamSightings.values()) {
            sightings.removeIf(s -> s.isExpired(currentTime));
        }
        
        // Process pending relays
        processPendingRelays(delta);
        
        // Clear expired cooldowns
        relayCooldowns.entrySet().removeIf(e -> currentTime - e.getValue() > RELAY_COOLDOWN);
    }
    
    /**
     * Processes pending relay transmissions.
     */
    private void processPendingRelays(float delta) {
        List<Unit> toRemove = new ArrayList<>();
        
        for (Map.Entry<Unit, PendingRelay> entry : pendingRelays.entrySet()) {
            PendingRelay pending = entry.getValue();
            pending.delay -= delta;
            
            if (pending.delay <= 0) {
                // Execute the relay
                executeRelay(entry.getKey(), pending.sighting);
                toRemove.add(entry.getKey());
            }
        }
        
        for (Unit unit : toRemove) {
            pendingRelays.remove(unit);
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // SIGHTING REPORTING
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Reports a new sighting (initial spot, not a relay).
     * 
     * @param spotter Unit that spotted the target
     * @param target  Entity that was spotted
     * @param type    Type of sighting
     */
    public void reportSighting(Unit spotter, Entity target, SightingType type) {
        if (spotter == null || target == null) return;
        
        Sighting sighting = new Sighting(
            type,
            target,
            target.getPosition(),
            spotter.getTeam(),
            spotter,
            1.0f, // Initial sighting is 100% accurate
            0,    // No hops yet
            currentTime
        );
        
        addSighting(sighting);
        
        // Start relay chain
        initiateRelayChain(spotter, sighting);
    }
    
    /**
     * Adds a sighting to the team's records.
     */
    private void addSighting(Sighting sighting) {
        List<Sighting> sightings = teamSightings.get(sighting.reportingTeam);
        if (sightings == null) return;
        
        // Check for duplicate (same target, recent)
        for (Sighting existing : sightings) {
            if (existing.target == sighting.target && 
                currentTime - existing.timestamp < 2f) {
                // Update existing instead of adding duplicate
                existing.expirationTime = currentTime + SIGHTING_LIFETIME;
                return;
            }
        }
        
        sightings.add(sighting);
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // RELAY CHAIN
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Initiates a relay chain from the spotter.
     */
    private void initiateRelayChain(Unit spotter, Sighting sighting) {
        // Find allies who can see the spotter
        List<Unit> potentialRelays = findRelayTargets(spotter);
        
        for (Unit relay : potentialRelays) {
            scheduleRelay(relay, sighting);
        }
    }
    
    /**
     * Finds units that can relay a sighting from the source.
     */
    private List<Unit> findRelayTargets(Unit source) {
        List<Unit> targets = new ArrayList<>();
        
        // This would need access to the entity list
        // For now, return empty - actual implementation needs WorldContext integration
        
        return targets;
    }
    
    /**
     * Schedules a relay transmission.
     */
    private void scheduleRelay(Unit relayer, Sighting sighting) {
        // Check hop limit
        if (sighting.hopCount >= MAX_RELAY_HOPS) return;
        
        // Check cooldown
        Float lastRelay = relayCooldowns.get(relayer);
        if (lastRelay != null && currentTime - lastRelay < RELAY_COOLDOWN) return;
        
        // Schedule the relay with delay
        float delay = sighting.type.relayDelay * 0.5f; // Base relay delay
        pendingRelays.put(relayer, new PendingRelay(sighting, delay));
    }
    
    /**
     * Executes a relay transmission.
     */
    private void executeRelay(Unit relayer, Sighting original) {
        Sighting relayed = original.createRelayedCopy(currentTime);
        addSighting(relayed);
        
        // Update cooldown
        relayCooldowns.put(relayer, currentTime);
        
        // Continue the chain
        initiateRelayChain(relayer, relayed);
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // LINE OF SIGHT
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Checks if two positions have line of sight.
     */
    public boolean hasLineOfSight(Vector3 from, Vector3 to, GridWorld world) {
        float dx = to.x - from.x;
        float dy = to.y - from.y;
        float dz = to.z - from.z;
        float distance = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        
        if (distance > MAX_RELAY_RANGE) return false;
        if (distance < 1f) return true;
        
        // Normalize
        dx /= distance;
        dy /= distance;
        dz /= distance;
        
        // Ray march
        int steps = (int) Math.ceil(distance);
        float x = from.x;
        float y = from.y;
        float z = from.z;
        
        for (int i = 0; i < steps; i++) {
            x += dx;
            y += dy;
            z += dz;
            
            BlockState block = world.getBlock((int) x, (int) y, (int) z);
            if (world.isOpaque(block)) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Checks if two units have line of sight.
     */
    public boolean hasLineOfSight(Unit from, Unit to, GridWorld world) {
        return hasLineOfSight(from.getPosition(), to.getPosition(), world);
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // QUERIES
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Gets all active sightings for a team.
     */
    public List<Sighting> getSightings(Team team) {
        List<Sighting> result = teamSightings.get(team);
        return result != null ? new ArrayList<>(result) : new ArrayList<>();
    }
    
    /**
     * Gets the most recent sighting of a specific type.
     */
    public Sighting getLatestSighting(Team team, SightingType type) {
        List<Sighting> sightings = teamSightings.get(team);
        if (sightings == null) return null;
        
        Sighting latest = null;
        for (Sighting s : sightings) {
            if (s.type == type) {
                if (latest == null || s.timestamp > latest.timestamp) {
                    latest = s;
                }
            }
        }
        return latest;
    }
    
    /**
     * Checks if a team has any active sightings.
     */
    public boolean hasActiveSightings(Team team) {
        List<Sighting> sightings = teamSightings.get(team);
        return sightings != null && !sightings.isEmpty();
    }
    
    /**
     * Gets sightings near a position.
     */
    public List<Sighting> getSightingsNear(Team team, Vector3 position, float radius) {
        List<Sighting> result = new ArrayList<>();
        List<Sighting> sightings = teamSightings.get(team);
        
        if (sightings == null) return result;
        
        for (Sighting s : sightings) {
            if (s.lastKnownPosition.dst(position) <= radius) {
                result.add(s);
            }
        }
        
        return result;
    }
    
    /**
     * Clears all sightings.
     */
    public void clear() {
        for (List<Sighting> sightings : teamSightings.values()) {
            sightings.clear();
        }
        pendingRelays.clear();
        relayCooldowns.clear();
        currentTime = 0f;
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // HELPER CLASS
    // ═══════════════════════════════════════════════════════════════════════════
    
    private static class PendingRelay {
        final Sighting sighting;
        float delay;
        
        PendingRelay(Sighting sighting, float delay) {
            this.sighting = sighting;
            this.delay = delay;
        }
    }
}
