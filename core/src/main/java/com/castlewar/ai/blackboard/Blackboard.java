package com.castlewar.ai.blackboard;

import com.badlogic.gdx.math.Vector3;
import com.castlewar.entity.Entity;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.function.Consumer;

/**
 * Centralized data store for behavior tree nodes.
 * <p>
 * The Blackboard pattern allows nodes to share data without direct coupling.
 * Uses {@link EnumMap} for O(1) lookup performance with compile-time key safety.
 * 
 * <h2>Usage</h2>
 * <pre>{@code
 * Blackboard bb = new Blackboard();
 * 
 * // Setting values
 * bb.set(BlackboardKey.COMBAT_CURRENT_TARGET, enemy);
 * bb.set(BlackboardKey.PERCEPTION_ALERT_LEVEL, 0.8f);
 * 
 * // Getting values (type-safe)
 * Entity target = bb.getEntity(BlackboardKey.COMBAT_CURRENT_TARGET);
 * float alert = bb.getFloat(BlackboardKey.PERCEPTION_ALERT_LEVEL);
 * 
 * // Observing changes
 * bb.observe(BlackboardKey.COMBAT_CURRENT_TARGET, value -> {
 *     System.out.println("Target changed to: " + value);
 * });
 * }</pre>
 * 
 * <h2>Thread Safety</h2>
 * This class is NOT thread-safe. Each unit should have its own Blackboard instance.
 * 
 * @see BlackboardKey
 */
public class Blackboard {
    
    /** Data storage using EnumMap for O(1) performance */
    private final EnumMap<BlackboardKey, Object> data;
    
    /** Observers for value change notifications */
    private final EnumMap<BlackboardKey, List<Consumer<Object>>> observers;
    
    /**
     * Creates a new empty blackboard.
     */
    public Blackboard() {
        this.data = new EnumMap<>(BlackboardKey.class);
        this.observers = new EnumMap<>(BlackboardKey.class);
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // GENERIC ACCESSORS
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Sets a value in the blackboard.
     * Notifies any registered observers of the change.
     * 
     * @param key   The blackboard key
     * @param value The value to store (can be null to clear)
     */
    public void set(BlackboardKey key, Object value) {
        data.put(key, value);
        notifyObservers(key, value);
    }
    
    /**
     * Gets a value from the blackboard with explicit type cast.
     * 
     * @param key  The blackboard key
     * @param type The expected type class
     * @param <T>  The expected type
     * @return The value, or null if not present or wrong type
     */
    @SuppressWarnings("unchecked")
    public <T> T get(BlackboardKey key, Class<T> type) {
        Object value = data.get(key);
        if (value == null) {
            return null;
        }
        if (type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }
    
    /**
     * Gets a raw value from the blackboard.
     * 
     * @param key The blackboard key
     * @return The value, or null if not present
     */
    public Object get(BlackboardKey key) {
        return data.get(key);
    }
    
    /**
     * Checks if a key has a non-null value.
     * 
     * @param key The blackboard key
     * @return true if the key has a value
     */
    public boolean has(BlackboardKey key) {
        return data.get(key) != null;
    }
    
    /**
     * Removes a value from the blackboard.
     * 
     * @param key The blackboard key
     */
    public void remove(BlackboardKey key) {
        data.remove(key);
        notifyObservers(key, null);
    }
    
    /**
     * Clears all values from the blackboard.
     */
    public void clear() {
        data.clear();
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // TYPE-SAFE ACCESSORS
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Gets a boolean value.
     * 
     * @param key The blackboard key
     * @return The boolean value, or false if not present
     */
    public boolean getBool(BlackboardKey key) {
        Object value = data.get(key);
        return Boolean.TRUE.equals(value);
    }
    
    /**
     * Gets an integer value.
     * 
     * @param key The blackboard key
     * @return The integer value, or 0 if not present
     */
    public int getInt(BlackboardKey key) {
        Object value = data.get(key);
        if (value instanceof Integer) {
            return (Integer) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return 0;
    }
    
    /**
     * Gets a float value.
     * 
     * @param key The blackboard key
     * @return The float value, or 0f if not present
     */
    public float getFloat(BlackboardKey key) {
        Object value = data.get(key);
        if (value instanceof Float) {
            return (Float) value;
        }
        if (value instanceof Number) {
            return ((Number) value).floatValue();
        }
        return 0f;
    }
    
    /**
     * Gets a Vector3 value.
     * 
     * @param key The blackboard key
     * @return The Vector3 value, or null if not present
     */
    public Vector3 getVector3(BlackboardKey key) {
        return get(key, Vector3.class);
    }
    
    /**
     * Gets an Entity value.
     * 
     * @param key The blackboard key
     * @return The Entity value, or null if not present
     */
    public Entity getEntity(BlackboardKey key) {
        return get(key, Entity.class);
    }
    
    /**
     * Gets a List value.
     * 
     * @param key The blackboard key
     * @param <T> The list element type
     * @return The List value, or empty list if not present
     */
    @SuppressWarnings("unchecked")
    public <T> List<T> getList(BlackboardKey key) {
        Object value = data.get(key);
        if (value instanceof List) {
            return (List<T>) value;
        }
        return new ArrayList<>();
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // CONVENIENCE SETTERS
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Sets a boolean value.
     * 
     * @param key   The blackboard key
     * @param value The boolean value
     */
    public void setBool(BlackboardKey key, boolean value) {
        set(key, value);
    }
    
    /**
     * Sets an integer value.
     * 
     * @param key   The blackboard key
     * @param value The integer value
     */
    public void setInt(BlackboardKey key, int value) {
        set(key, value);
    }
    
    /**
     * Sets a float value.
     * 
     * @param key   The blackboard key
     * @param value The float value
     */
    public void setFloat(BlackboardKey key, float value) {
        set(key, value);
    }
    
    /**
     * Sets a Vector3 value (stores a copy to prevent aliasing).
     * 
     * @param key   The blackboard key
     * @param value The Vector3 value
     */
    public void setVector3(BlackboardKey key, Vector3 value) {
        if (value == null) {
            set(key, null);
        } else {
            // Check if we already have a Vector3 to reuse (avoid allocation)
            Object existing = data.get(key);
            if (existing instanceof Vector3) {
                ((Vector3) existing).set(value);
                notifyObservers(key, existing);
            } else {
                set(key, new Vector3(value));
            }
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // OBSERVERS
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Registers an observer for value changes on a specific key.
     * 
     * @param key      The blackboard key to observe
     * @param callback Called when the value changes
     */
    public void observe(BlackboardKey key, Consumer<Object> callback) {
        observers.computeIfAbsent(key, k -> new ArrayList<>()).add(callback);
    }
    
    /**
     * Removes an observer.
     * 
     * @param key      The blackboard key
     * @param callback The callback to remove
     */
    public void unobserve(BlackboardKey key, Consumer<Object> callback) {
        List<Consumer<Object>> keyObservers = observers.get(key);
        if (keyObservers != null) {
            keyObservers.remove(callback);
        }
    }
    
    /**
     * Removes all observers for a key.
     * 
     * @param key The blackboard key
     */
    public void clearObservers(BlackboardKey key) {
        observers.remove(key);
    }
    
    /**
     * Removes all observers.
     */
    public void clearAllObservers() {
        observers.clear();
    }
    
    /**
     * Notifies observers of a value change.
     */
    private void notifyObservers(BlackboardKey key, Object value) {
        List<Consumer<Object>> keyObservers = observers.get(key);
        if (keyObservers != null) {
            for (Consumer<Object> observer : keyObservers) {
                observer.accept(value);
            }
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // DEBUG
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Returns a debug string showing all set keys and values.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Blackboard{\n");
        for (BlackboardKey key : BlackboardKey.values()) {
            Object value = data.get(key);
            if (value != null) {
                sb.append("  ").append(key).append(" = ").append(value).append("\n");
            }
        }
        sb.append("}");
        return sb.toString();
    }
    
    /**
     * Returns the number of set keys.
     * 
     * @return Number of non-null values
     */
    public int size() {
        return data.size();
    }
}
