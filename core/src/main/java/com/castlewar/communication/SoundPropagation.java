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
 * Sound propagation system for emergent gameplay.
 * 
 * <h2>Sound Types</h2>
 * <ul>
 *   <li><b>FOOTSTEP</b> - Walking/running units (small radius)</li>
 *   <li><b>COMBAT</b> - Melee combat sounds (medium radius)</li>
 *   <li><b>DEATH_CRY</b> - Unit death (medium radius, alerts allies)</li>
 *   <li><b>SIEGE_FIRE</b> - Trebuchet/siege weapon firing (large radius)</li>
 *   <li><b>SIEGE_IMPACT</b> - Projectile hitting structure (large radius)</li>
 *   <li><b>GATE_BREACH</b> - Gate destroyed (very large radius)</li>
 *   <li><b>HORN_SIGNAL</b> - Commander rally/order (team-wide)</li>
 * </ul>
 * 
 * <h2>Propagation Rules</h2>
 * <ul>
 *   <li>Sound is blocked/reduced by solid walls</li>
 *   <li>Sound travels farther outdoors than indoors</li>
 *   <li>Vertical distance reduces sound more than horizontal</li>
 * </ul>
 */
public class SoundPropagation {
    
    // ═══════════════════════════════════════════════════════════════════════════
    // SOUND TYPES
    // ═══════════════════════════════════════════════════════════════════════════
    
    public enum SoundType {
        /** Walking/running footsteps */
        FOOTSTEP(5f, 0.5f, false),
        /** Running footsteps (louder) */
        RUNNING(8f, 0.3f, false),
        /** Melee combat clash */
        COMBAT(15f, 2f, true),
        /** Unit death scream */
        DEATH_CRY(20f, 3f, true),
        /** Bow/crossbow firing */
        ARROW_FIRE(10f, 0.5f, false),
        /** Siege weapon firing */
        SIEGE_FIRE(40f, 5f, true),
        /** Projectile impact on structure */
        SIEGE_IMPACT(35f, 4f, true),
        /** Gate/wall destroyed */
        GATE_BREACH(60f, 10f, true),
        /** Commander horn/rally signal */
        HORN_SIGNAL(50f, 8f, true),
        /** Cavalry charge (hooves) */
        CAVALRY_CHARGE(25f, 3f, true),
        /** Battering ram strike */
        RAM_STRIKE(30f, 5f, true);
        
        /** Base propagation radius */
        public final float baseRadius;
        /** Duration the sound persists for detection */
        public final float duration;
        /** Whether this sound triggers alert state */
        public final boolean alerting;
        
        SoundType(float baseRadius, float duration, boolean alerting) {
            this.baseRadius = baseRadius;
            this.duration = duration;
            this.alerting = alerting;
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // SOUND EVENT
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Represents an active sound in the world.
     */
    public record SoundEvent(
        SoundType type,
        Vector3 position,
        Entity source,
        Team sourceTeam,
        float intensity,
        float expirationTime
    ) {
        public boolean isExpired(float currentTime) {
            return currentTime >= expirationTime;
        }
        
        /**
         * Gets the effective radius based on intensity.
         */
        public float getEffectiveRadius() {
            return type.baseRadius * intensity;
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // STATE
    // ═══════════════════════════════════════════════════════════════════════════
    
    private final List<SoundEvent> activeSounds = new ArrayList<>();
    private float currentTime = 0f;
    
    /** Listeners for sound events */
    private final List<SoundListener> listeners = new ArrayList<>();
    
    // ═══════════════════════════════════════════════════════════════════════════
    // SINGLETON
    // ═══════════════════════════════════════════════════════════════════════════
    
    private static SoundPropagation instance;
    
    public static SoundPropagation getInstance() {
        if (instance == null) {
            instance = new SoundPropagation();
        }
        return instance;
    }
    
    private SoundPropagation() {}
    
    // ═══════════════════════════════════════════════════════════════════════════
    // UPDATE
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Updates sound propagation, removing expired sounds.
     */
    public void update(float delta) {
        currentTime += delta;
        activeSounds.removeIf(sound -> sound.isExpired(currentTime));
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // SOUND EMISSION
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Emits a sound at a position.
     * 
     * @param type      Type of sound
     * @param position  Source position
     * @param source    Entity making the sound (can be null)
     * @param intensity Volume multiplier (1.0 = normal)
     */
    public void emitSound(SoundType type, Vector3 position, Entity source, float intensity) {
        Team team = (source instanceof Unit unit) ? unit.getTeam() : null;
        
        SoundEvent event = new SoundEvent(
            type,
            new Vector3(position),
            source,
            team,
            intensity,
            currentTime + type.duration
        );
        
        activeSounds.add(event);
        
        // Notify listeners within range
        notifyListeners(event);
    }
    
    /**
     * Emits a sound with default intensity.
     */
    public void emitSound(SoundType type, Vector3 position, Entity source) {
        emitSound(type, position, source, 1.0f);
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // SOUND PROPAGATION
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Calculates effective sound intensity at a listener position.
     * Accounts for distance, walls, and vertical separation.
     * 
     * @param sound     The sound event
     * @param listener  Position of the listener
     * @param world     Grid world for wall checks
     * @return Intensity at listener position (0 = inaudible, 1 = full volume)
     */
    public float calculateIntensityAt(SoundEvent sound, Vector3 listener, GridWorld world) {
        float effectiveRadius = sound.getEffectiveRadius();
        
        // Calculate 3D distance
        float dx = listener.x - sound.position.x;
        float dy = listener.y - sound.position.y;
        float dz = listener.z - sound.position.z;
        
        // Vertical distance is weighted more heavily (sound travels horizontally better)
        float horizontalDist = (float) Math.sqrt(dx * dx + dy * dy);
        float verticalDist = Math.abs(dz) * 1.5f; // 50% penalty for vertical
        float effectiveDist = horizontalDist + verticalDist;
        
        if (effectiveDist > effectiveRadius) {
            return 0f; // Out of range
        }
        
        // Base intensity from distance falloff
        float distanceFalloff = 1f - (effectiveDist / effectiveRadius);
        
        // Wall occlusion (simplified ray cast)
        float occlusion = calculateOcclusion(sound.position, listener, world);
        
        return distanceFalloff * occlusion * sound.intensity;
    }
    
    /**
     * Calculates sound occlusion through walls.
     * Returns 1.0 for no occlusion, lower values for more blocking.
     */
    private float calculateOcclusion(Vector3 from, Vector3 to, GridWorld world) {
        // Simple ray march through blocks
        float dx = to.x - from.x;
        float dy = to.y - from.y;
        float dz = to.z - from.z;
        float distance = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        
        if (distance < 1f) return 1f; // Same position
        
        // Normalize direction
        dx /= distance;
        dy /= distance;
        dz /= distance;
        
        float occlusion = 1f;
        int steps = (int) Math.min(distance, 30); // Max 30 checks for performance
        float stepSize = distance / steps;
        
        float x = from.x;
        float y = from.y;
        float z = from.z;
        
        for (int i = 0; i < steps; i++) {
            x += dx * stepSize;
            y += dy * stepSize;
            z += dz * stepSize;
            
            BlockState block = world.getBlock((int) x, (int) y, (int) z);
            
            // Solid blocks reduce sound
            if (world.isSolid(block)) {
                occlusion *= 0.5f; // Each solid block halves sound
                
                if (occlusion < 0.1f) {
                    return 0f; // Effectively blocked
                }
            }
        }
        
        return occlusion;
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // LISTENER SYSTEM
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Notifies all listeners about a sound event.
     */
    private void notifyListeners(SoundEvent event) {
        for (SoundListener listener : listeners) {
            // Don't notify the source
            if (listener.getUnit() == event.source) continue;
            
            float intensity = calculateIntensityAtListener(event, listener);
            if (intensity > 0.1f) {
                listener.onSoundHeard(event, intensity);
            }
        }
    }
    
    /**
     * Calculates intensity at a listener (without world for simplified notification).
     */
    private float calculateIntensityAtListener(SoundEvent event, SoundListener listener) {
        float effectiveRadius = event.getEffectiveRadius();
        float distance = listener.getPosition().dst(event.position);
        
        if (distance > effectiveRadius) return 0f;
        
        return (1f - distance / effectiveRadius) * event.intensity;
    }
    
    /**
     * Registers a sound listener.
     */
    public void registerListener(SoundListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }
    
    /**
     * Unregisters a sound listener.
     */
    public void unregisterListener(SoundListener listener) {
        listeners.remove(listener);
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // QUERIES
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Checks if a unit can hear any alerting sounds.
     */
    public boolean canHearAlertingSound(Unit unit, GridWorld world) {
        for (SoundEvent sound : activeSounds) {
            if (!sound.type.alerting) continue;
            
            float intensity = calculateIntensityAt(sound, unit.getPosition(), world);
            if (intensity > 0.2f) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Gets the loudest sound a unit can hear.
     */
    public SoundEvent getLoudestSound(Unit unit, GridWorld world) {
        SoundEvent loudest = null;
        float loudestIntensity = 0f;
        
        for (SoundEvent sound : activeSounds) {
            float intensity = calculateIntensityAt(sound, unit.getPosition(), world);
            if (intensity > loudestIntensity) {
                loudestIntensity = intensity;
                loudest = sound;
            }
        }
        
        return loudest;
    }
    
    /**
     * Gets all sounds within earshot.
     */
    public List<SoundEvent> getSoundsInRange(Vector3 position, float range) {
        List<SoundEvent> result = new ArrayList<>();
        for (SoundEvent sound : activeSounds) {
            if (position.dst(sound.position) <= range) {
                result.add(sound);
            }
        }
        return result;
    }
    
    /**
     * Clears all active sounds.
     */
    public void clear() {
        activeSounds.clear();
        currentTime = 0f;
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // LISTENER INTERFACE
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Interface for entities that can hear sounds.
     */
    public interface SoundListener {
        Vector3 getPosition();
        Unit getUnit();
        void onSoundHeard(SoundEvent event, float intensity);
    }
}
