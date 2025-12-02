package com.castlewar.combat;

/**
 * Damage types for combat calculations.
 * Different damage types have strengths and weaknesses against different targets.
 * 
 * <table>
 *   <tr><th>Type</th><th>Strong Against</th><th>Weak Against</th></tr>
 *   <tr><td>PIERCING</td><td>Unarmored units</td><td>Shields, heavy armor</td></tr>
 *   <tr><td>CRUSHING</td><td>Armor, structures</td><td>Nothing</td></tr>
 *   <tr><td>SLASHING</td><td>Unarmored</td><td>Armor</td></tr>
 *   <tr><td>SIEGE</td><td>Structures, groups</td><td>Single armored units</td></tr>
 * </table>
 */
public enum DamageType {
    
    /** Piercing damage (arrows, spears) - effective vs unarmored, weak vs shields */
    PIERCING(1.5f, 0.5f, 1.0f, 0.75f),
    
    /** Crushing damage (maces, rams) - effective vs armor and structures */
    CRUSHING(1.0f, 1.25f, 1.5f, 1.0f),
    
    /** Slashing damage (swords) - effective vs unarmored, weak vs armor */
    SLASHING(1.25f, 0.75f, 0.5f, 1.0f),
    
    /** Siege damage (trebuchets) - effective vs structures, reduced vs single armored targets */
    SIEGE(1.0f, 0.5f, 2.0f, 1.5f);
    
    /** Multiplier against unarmored targets */
    private final float vsUnarmored;
    
    /** Multiplier against armored targets */
    private final float vsArmored;
    
    /** Multiplier against structures */
    private final float vsStructure;
    
    /** Multiplier against groups (AoE effectiveness) */
    private final float vsGroup;
    
    DamageType(float vsUnarmored, float vsArmored, float vsStructure, float vsGroup) {
        this.vsUnarmored = vsUnarmored;
        this.vsArmored = vsArmored;
        this.vsStructure = vsStructure;
        this.vsGroup = vsGroup;
    }
    
    /**
     * Gets the damage multiplier for this damage type against the given armor type.
     * 
     * @param armorType The target's armor type
     * @return Damage multiplier (1.0 = normal, >1 = effective, <1 = reduced)
     */
    public float getMultiplier(ArmorType armorType) {
        return switch (armorType) {
            case NONE -> vsUnarmored;
            case LIGHT -> (vsUnarmored + vsArmored) / 2f; // Average
            case HEAVY -> vsArmored;
            case STRUCTURE -> vsStructure;
        };
    }
    
    /**
     * Gets the AoE effectiveness multiplier for this damage type.
     */
    public float getGroupMultiplier() {
        return vsGroup;
    }
    
    /**
     * Armor types for damage calculation.
     */
    public enum ArmorType {
        /** No armor - takes full damage from most sources */
        NONE,
        /** Light armor - partial protection */
        LIGHT,
        /** Heavy armor - significant protection vs piercing/slashing */
        HEAVY,
        /** Structure - walls, gates, buildings */
        STRUCTURE
    }
}
