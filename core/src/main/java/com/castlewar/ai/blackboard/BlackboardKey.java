package com.castlewar.ai.blackboard;

/**
 * Type-safe keys for blackboard data access.
 * <p>
 * Using an enum instead of String keys provides:
 * <ul>
 *   <li>Compile-time safety (no typos)</li>
 *   <li>O(1) EnumMap lookup performance</li>
 *   <li>IDE autocomplete support</li>
 *   <li>Refactoring safety</li>
 * </ul>
 * 
 * <h2>Key Categories</h2>
 * Keys are prefixed by category for organization:
 * <ul>
 *   <li>{@code PERCEPTION_*} - Sensory data (vision, hearing)</li>
 *   <li>{@code COMBAT_*} - Combat state and targets</li>
 *   <li>{@code MOVEMENT_*} - Navigation and positioning</li>
 *   <li>{@code VEHICLE_*} - Mount and vehicle state</li>
 *   <li>{@code SIEGE_*} - Siege engine operations</li>
 *   <li>{@code FORMATION_*} - Group coordination</li>
 * </ul>
 * 
 * @see Blackboard
 */
public enum BlackboardKey {
    
    // ═══════════════════════════════════════════════════════════════════════════
    // PERCEPTION - Sensory data from AwarenessService
    // ═══════════════════════════════════════════════════════════════════════════
    
    /** List of entities currently visible to this unit. Type: {@code List<Entity>} */
    PERCEPTION_VISIBLE_ENEMIES,
    
    /** List of allied entities currently visible. Type: {@code List<Entity>} */
    PERCEPTION_VISIBLE_ALLIES,
    
    /** Last known position of a threat. Type: {@code Vector3} */
    PERCEPTION_LAST_KNOWN_THREAT_POS,
    
    /** Current alertness level from 0.0 (relaxed) to 1.0 (combat). Type: {@code Float} */
    PERCEPTION_ALERT_LEVEL,
    
    /** Position of a sound that was heard. Type: {@code Vector3} */
    PERCEPTION_HEARD_SOUND_POS,
    
    /** Time since last enemy contact in seconds. Type: {@code Float} */
    PERCEPTION_TIME_SINCE_CONTACT,
    
    // ═══════════════════════════════════════════════════════════════════════════
    // COMBAT - Combat state and targeting
    // ═══════════════════════════════════════════════════════════════════════════
    
    /** Current combat target entity. Type: {@code Entity} */
    COMBAT_CURRENT_TARGET,
    
    /** Whether target is within attack range. Type: {@code Boolean} */
    COMBAT_IN_ATTACK_RANGE,
    
    /** Remaining poise before stagger. Type: {@code Float} */
    COMBAT_POISE_REMAINING,
    
    /** Maximum poise value. Type: {@code Float} */
    COMBAT_POISE_MAX,
    
    /** Current ammunition count (for ranged units). Type: {@code Integer} */
    COMBAT_AMMO_COUNT,
    
    /** Maximum ammunition capacity. Type: {@code Integer} */
    COMBAT_AMMO_MAX,
    
    /** Whether currently blocking. Type: {@code Boolean} */
    COMBAT_IS_BLOCKING,
    
    /** Time until next attack is available. Type: {@code Float} */
    COMBAT_ATTACK_COOLDOWN,
    
    /** Whether backstab conditions are met. Type: {@code Boolean} */
    COMBAT_CAN_BACKSTAB,
    
    // ═══════════════════════════════════════════════════════════════════════════
    // MOVEMENT - Navigation and positioning
    // ═══════════════════════════════════════════════════════════════════════════
    
    /** Current waypoint destination. Type: {@code Vector3} */
    MOVEMENT_CURRENT_WAYPOINT,
    
    /** List of waypoints for patrol. Type: {@code List<Vector3>} */
    MOVEMENT_PATROL_WAYPOINTS,
    
    /** Current index in patrol waypoints. Type: {@code Integer} */
    MOVEMENT_PATROL_INDEX,
    
    /** Destination when fleeing. Type: {@code Vector3} */
    MOVEMENT_FLEE_DESTINATION,
    
    /** Whether unit has arrived at destination. Type: {@code Boolean} */
    MOVEMENT_ARRIVED,
    
    /** Movement speed multiplier. Type: {@code Float} */
    MOVEMENT_SPEED_MULTIPLIER,
    
    /** Whether movement is currently enabled. Type: {@code Boolean} */
    MOVEMENT_ENABLED,
    
    // ═══════════════════════════════════════════════════════════════════════════
    // VEHICLE - Mount and vehicle state
    // ═══════════════════════════════════════════════════════════════════════════
    
    /** Current mount/vehicle. Type: {@code Vehicle} */
    VEHICLE_CURRENT_MOUNT,
    
    /** Whether currently mounted on a vehicle. Type: {@code Boolean} */
    VEHICLE_IS_MOUNTED,
    
    /** Whether currently charging (cavalry). Type: {@code Boolean} */
    VEHICLE_IS_CHARGING,
    
    /** Charge target position. Type: {@code Vector3} */
    VEHICLE_CHARGE_TARGET,
    
    /** Number of crew members (siege). Type: {@code Integer} */
    VEHICLE_CREW_COUNT,
    
    // ═══════════════════════════════════════════════════════════════════════════
    // SIEGE - Siege engine operations
    // ═══════════════════════════════════════════════════════════════════════════
    
    /** Target structure position for siege. Type: {@code Vector3} */
    SIEGE_TARGET_STRUCTURE,
    
    /** Current reload progress (0.0 to 1.0). Type: {@code Float} */
    SIEGE_RELOAD_PROGRESS,
    
    /** Whether siege engine can fire. Type: {@code Boolean} */
    SIEGE_CAN_FIRE,
    
    /** Operational efficiency based on crew (0.0 to 1.0). Type: {@code Float} */
    SIEGE_EFFICIENCY,
    
    // ═══════════════════════════════════════════════════════════════════════════
    // FORMATION - Group coordination
    // ═══════════════════════════════════════════════════════════════════════════
    
    /** Assigned slot index in formation. Type: {@code Integer} */
    FORMATION_SLOT_INDEX,
    
    /** Target position for formation slot. Type: {@code Vector3} */
    FORMATION_SLOT_POSITION,
    
    /** Reference to formation leader. Type: {@code Entity} */
    FORMATION_LEADER,
    
    /** Current formation type. Type: {@code FormationType} */
    FORMATION_TYPE,
    
    /** Whether in active formation. Type: {@code Boolean} */
    FORMATION_ACTIVE,
    
    /** Current commander order. Type: {@code Command} */
    FORMATION_CURRENT_ORDER,
    
    // ═══════════════════════════════════════════════════════════════════════════
    // SELF - Unit's own state (cached for quick access)
    // ═══════════════════════════════════════════════════════════════════════════
    
    /** Reference to self entity. Type: {@code Unit} */
    SELF_UNIT,
    
    /** Current health points. Type: {@code Float} */
    SELF_HP,
    
    /** Health as percentage (0.0 to 1.0). Type: {@code Float} */
    SELF_HP_PERCENT,
    
    /** Whether unit is alive. Type: {@code Boolean} */
    SELF_IS_ALIVE,
    
    /** Current position. Type: {@code Vector3} */
    SELF_POSITION,
    
    /** Current facing direction. Type: {@code Vector3} */
    SELF_FACING,
    
    // ═══════════════════════════════════════════════════════════════════════════
    // CUSTOM - For unit-specific or temporary data
    // ═══════════════════════════════════════════════════════════════════════════
    
    /** Generic custom data slot 1. Type: varies */
    CUSTOM_1,
    
    /** Generic custom data slot 2. Type: varies */
    CUSTOM_2,
    
    /** Generic custom data slot 3. Type: varies */
    CUSTOM_3
}
