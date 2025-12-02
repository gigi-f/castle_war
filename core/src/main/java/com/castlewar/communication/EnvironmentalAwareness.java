package com.castlewar.communication;

import com.badlogic.gdx.math.Vector3;
import com.castlewar.entity.Entity;
import com.castlewar.entity.Team;
import com.castlewar.entity.Unit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tracks environmental events that affect unit morale and behavior.
 * 
 * <h2>Environmental Factors</h2>
 * <ul>
 *   <li><b>Ally Deaths</b> - Nearby deaths reduce morale</li>
 *   <li><b>Enemy Deaths</b> - Nearby enemy deaths boost morale</li>
 *   <li><b>Commander Presence</b> - Near leader boosts morale</li>
 *   <li><b>Outnumbered</b> - Being outnumbered reduces morale</li>
 *   <li><b>Winning/Losing</b> - Battle state affects global morale</li>
 * </ul>
 * 
 * <h2>Morale Effects</h2>
 * <ul>
 *   <li>High morale: +20% damage, +10% speed</li>
 *   <li>Normal morale: Standard stats</li>
 *   <li>Low morale: -20% damage, -10% speed</li>
 *   <li>Broken morale: Unit may flee</li>
 * </ul>
 */
public class EnvironmentalAwareness {
    
    // ═══════════════════════════════════════════════════════════════════════════
    // CONSTANTS
    // ═══════════════════════════════════════════════════════════════════════════
    
    /** Radius to detect ally/enemy deaths */
    private static final float DEATH_AWARENESS_RADIUS = 15f;
    
    /** Radius for commander morale bonus */
    private static final float COMMANDER_BONUS_RADIUS = 20f;
    
    /** Morale loss per nearby ally death */
    private static final float ALLY_DEATH_MORALE_LOSS = 0.15f;
    
    /** Morale gain per nearby enemy death */
    private static final float ENEMY_DEATH_MORALE_GAIN = 0.10f;
    
    /** Morale bonus for being near commander */
    private static final float COMMANDER_MORALE_BONUS = 0.20f;
    
    /** Morale regeneration per second */
    private static final float MORALE_REGEN_RATE = 0.02f;
    
    /** Base morale (neutral) */
    private static final float BASE_MORALE = 0.5f;
    
    /** Morale threshold for "high morale" bonuses */
    private static final float HIGH_MORALE_THRESHOLD = 0.7f;
    
    /** Morale threshold for "low morale" penalties */
    private static final float LOW_MORALE_THRESHOLD = 0.3f;
    
    /** Morale threshold for potential fleeing */
    private static final float BROKEN_MORALE_THRESHOLD = 0.15f;
    
    // ═══════════════════════════════════════════════════════════════════════════
    // ENVIRONMENTAL EVENT
    // ═══════════════════════════════════════════════════════════════════════════
    
    public enum EventType {
        /** An ally has died */
        ALLY_DEATH(-0.15f, 15f),
        /** An enemy has died */
        ENEMY_DEATH(0.10f, 15f),
        /** Commander issued a rally */
        COMMANDER_RALLY(0.25f, 25f),
        /** Team scored a significant victory (gate breached, etc) */
        VICTORY_MOMENT(0.30f, 50f),
        /** Team suffered a significant loss */
        DEFEAT_MOMENT(-0.30f, 50f),
        /** Unit witnessed a heroic action */
        HEROIC_ACTION(0.15f, 20f),
        /** Unit witnessed a cowardly action */
        COWARDLY_ACTION(-0.10f, 15f);
        
        /** Morale impact (positive = boost, negative = loss) */
        public final float moraleImpact;
        /** Radius of effect */
        public final float radius;
        
        EventType(float moraleImpact, float radius) {
            this.moraleImpact = moraleImpact;
            this.radius = radius;
        }
    }
    
    /**
     * Record of an environmental event.
     */
    public record EnvironmentalEvent(
        EventType type,
        Vector3 position,
        Team affectedTeam,
        Entity source,
        float timestamp
    ) {}
    
    // ═══════════════════════════════════════════════════════════════════════════
    // MORALE STATE
    // ═══════════════════════════════════════════════════════════════════════════
    
    public enum MoraleState {
        /** High morale - combat bonuses */
        HIGH,
        /** Normal morale - standard performance */
        NORMAL,
        /** Low morale - combat penalties */
        LOW,
        /** Broken morale - may flee */
        BROKEN
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // STATE
    // ═══════════════════════════════════════════════════════════════════════════
    
    /** Per-unit morale tracking */
    private final Map<Unit, Float> unitMorale = new HashMap<>();
    
    /** Per-team global morale modifier */
    private final Map<Team, Float> teamMorale = new HashMap<>();
    
    /** Recent events for reference */
    private final List<EnvironmentalEvent> recentEvents = new ArrayList<>();
    
    /** Event history limit */
    private static final int MAX_EVENT_HISTORY = 50;
    
    private float currentTime = 0f;
    
    // ═══════════════════════════════════════════════════════════════════════════
    // SINGLETON
    // ═══════════════════════════════════════════════════════════════════════════
    
    private static EnvironmentalAwareness instance;
    
    public static EnvironmentalAwareness getInstance() {
        if (instance == null) {
            instance = new EnvironmentalAwareness();
        }
        return instance;
    }
    
    private EnvironmentalAwareness() {
        teamMorale.put(Team.WHITE, BASE_MORALE);
        teamMorale.put(Team.BLACK, BASE_MORALE);
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // UPDATE
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Updates environmental awareness system.
     */
    public void update(float delta) {
        currentTime += delta;
        
        // Regenerate morale slowly toward base
        for (Map.Entry<Unit, Float> entry : unitMorale.entrySet()) {
            float morale = entry.getValue();
            if (morale < BASE_MORALE) {
                morale = Math.min(BASE_MORALE, morale + MORALE_REGEN_RATE * delta);
            } else if (morale > BASE_MORALE) {
                morale = Math.max(BASE_MORALE, morale - MORALE_REGEN_RATE * delta * 0.5f);
            }
            entry.setValue(morale);
        }
        
        // Clean old events
        while (recentEvents.size() > MAX_EVENT_HISTORY) {
            recentEvents.remove(0);
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // EVENT RECORDING
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Records an environmental event and applies morale effects.
     * 
     * @param type         Event type
     * @param position     Where the event occurred
     * @param affectedTeam Team affected (for deaths, the team that lost the unit)
     * @param source       Entity involved (the unit that died, etc)
     * @param allUnits     All units to check for proximity
     */
    public void recordEvent(EventType type, Vector3 position, Team affectedTeam, 
                           Entity source, List<Entity> allUnits) {
        EnvironmentalEvent event = new EnvironmentalEvent(
            type, new Vector3(position), affectedTeam, source, currentTime
        );
        
        recentEvents.add(event);
        
        // Apply morale effects to nearby units
        applyMoraleEffects(event, allUnits);
        
        // Update team morale
        updateTeamMorale(event);
    }
    
    /**
     * Records an ally death.
     */
    public void recordAllyDeath(Unit fallen, List<Entity> allUnits) {
        recordEvent(EventType.ALLY_DEATH, fallen.getPosition(), fallen.getTeam(), fallen, allUnits);
    }
    
    /**
     * Records an enemy death (from the perspective of the killer's team).
     */
    public void recordEnemyKill(Unit killer, Unit killed, List<Entity> allUnits) {
        recordEvent(EventType.ENEMY_DEATH, killed.getPosition(), killer.getTeam(), killed, allUnits);
    }
    
    /**
     * Records a commander rally.
     */
    public void recordCommanderRally(Unit commander, List<Entity> allUnits) {
        recordEvent(EventType.COMMANDER_RALLY, commander.getPosition(), 
                   commander.getTeam(), commander, allUnits);
    }
    
    /**
     * Records a victory moment (gate breach, etc).
     */
    public void recordVictoryMoment(Team victoriousTeam, Vector3 position, List<Entity> allUnits) {
        recordEvent(EventType.VICTORY_MOMENT, position, victoriousTeam, null, allUnits);
    }
    
    /**
     * Records a defeat moment.
     */
    public void recordDefeatMoment(Team defeatedTeam, Vector3 position, List<Entity> allUnits) {
        recordEvent(EventType.DEFEAT_MOMENT, position, defeatedTeam, null, allUnits);
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // MORALE EFFECTS
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Applies morale effects to nearby units.
     */
    private void applyMoraleEffects(EnvironmentalEvent event, List<Entity> allUnits) {
        for (Entity entity : allUnits) {
            if (!(entity instanceof Unit unit)) continue;
            if (unit.isDead()) continue;
            
            float distance = unit.getPosition().dst(event.position);
            if (distance > event.type.radius) continue;
            
            // Calculate effect based on team and distance
            float distanceFactor = 1f - (distance / event.type.radius);
            float moraleChange = 0f;
            
            if (event.type == EventType.ALLY_DEATH) {
                // Allies lose morale when teammates die
                if (unit.getTeam() == event.affectedTeam) {
                    moraleChange = event.type.moraleImpact * distanceFactor;
                }
            } else if (event.type == EventType.ENEMY_DEATH) {
                // Team gains morale when they kill enemies
                if (unit.getTeam() == event.affectedTeam) {
                    moraleChange = event.type.moraleImpact * distanceFactor;
                }
            } else if (event.type == EventType.COMMANDER_RALLY) {
                // Rally affects same team
                if (unit.getTeam() == event.affectedTeam) {
                    moraleChange = event.type.moraleImpact * distanceFactor;
                }
            } else if (event.type == EventType.VICTORY_MOMENT) {
                // Victory boosts same team, hurts opposite
                if (unit.getTeam() == event.affectedTeam) {
                    moraleChange = event.type.moraleImpact * distanceFactor;
                } else {
                    moraleChange = -event.type.moraleImpact * distanceFactor * 0.5f;
                }
            } else if (event.type == EventType.DEFEAT_MOMENT) {
                // Defeat hurts same team, boosts opposite
                if (unit.getTeam() == event.affectedTeam) {
                    moraleChange = event.type.moraleImpact * distanceFactor;
                } else {
                    moraleChange = -event.type.moraleImpact * distanceFactor * 0.5f;
                }
            }
            
            if (moraleChange != 0) {
                adjustMorale(unit, moraleChange);
            }
        }
    }
    
    /**
     * Updates team-wide morale.
     */
    private void updateTeamMorale(EnvironmentalEvent event) {
        Float currentTeamMorale = teamMorale.get(event.affectedTeam);
        if (currentTeamMorale == null) return;
        
        float change = event.type.moraleImpact * 0.3f; // Team effect is reduced
        float newMorale = Math.max(0f, Math.min(1f, currentTeamMorale + change));
        teamMorale.put(event.affectedTeam, newMorale);
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // MORALE QUERIES
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Gets a unit's current morale (0.0 to 1.0).
     */
    public float getMorale(Unit unit) {
        Float morale = unitMorale.get(unit);
        if (morale == null) {
            morale = BASE_MORALE;
            unitMorale.put(unit, morale);
        }
        return morale;
    }
    
    /**
     * Adjusts a unit's morale.
     */
    public void adjustMorale(Unit unit, float change) {
        float current = getMorale(unit);
        float newMorale = Math.max(0f, Math.min(1f, current + change));
        unitMorale.put(unit, newMorale);
    }
    
    /**
     * Sets a unit's morale directly.
     */
    public void setMorale(Unit unit, float morale) {
        unitMorale.put(unit, Math.max(0f, Math.min(1f, morale)));
    }
    
    /**
     * Gets the morale state of a unit.
     */
    public MoraleState getMoraleState(Unit unit) {
        float morale = getMorale(unit);
        
        if (morale >= HIGH_MORALE_THRESHOLD) return MoraleState.HIGH;
        if (morale <= BROKEN_MORALE_THRESHOLD) return MoraleState.BROKEN;
        if (morale <= LOW_MORALE_THRESHOLD) return MoraleState.LOW;
        return MoraleState.NORMAL;
    }
    
    /**
     * Gets the damage multiplier based on morale.
     */
    public float getDamageMultiplier(Unit unit) {
        MoraleState state = getMoraleState(unit);
        return switch (state) {
            case HIGH -> 1.2f;
            case NORMAL -> 1.0f;
            case LOW -> 0.8f;
            case BROKEN -> 0.6f;
        };
    }
    
    /**
     * Gets the speed multiplier based on morale.
     */
    public float getSpeedMultiplier(Unit unit) {
        MoraleState state = getMoraleState(unit);
        return switch (state) {
            case HIGH -> 1.1f;
            case NORMAL -> 1.0f;
            case LOW -> 0.9f;
            case BROKEN -> 0.8f;
        };
    }
    
    /**
     * Checks if a unit should consider fleeing.
     */
    public boolean shouldConsiderFleeing(Unit unit) {
        return getMoraleState(unit) == MoraleState.BROKEN;
    }
    
    /**
     * Gets team-wide morale.
     */
    public float getTeamMorale(Team team) {
        Float morale = teamMorale.get(team);
        return morale != null ? morale : BASE_MORALE;
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // COMMANDER BONUS
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Checks if a unit is near a commander for morale bonus.
     */
    public boolean isNearCommander(Unit unit, List<Entity> allUnits) {
        for (Entity entity : allUnits) {
            if (!(entity instanceof Unit other)) continue;
            if (other.getTeam() != unit.getTeam()) continue;
            if (other == unit) continue;
            if (other.isDead()) continue;
            
            // Check if other is a commander (King)
            String className = other.getClass().getSimpleName();
            if ("King".equals(className)) {
                if (unit.getPosition().dst(other.getPosition()) <= COMMANDER_BONUS_RADIUS) {
                    return true;
                }
            }
        }
        return false;
    }
    
    /**
     * Gets the total morale modifier including commander bonus.
     */
    public float getTotalMoraleModifier(Unit unit, List<Entity> allUnits) {
        float baseMorale = getMorale(unit);
        
        if (isNearCommander(unit, allUnits)) {
            baseMorale = Math.min(1f, baseMorale + COMMANDER_MORALE_BONUS);
        }
        
        return baseMorale;
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // EVENT QUERIES
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Gets recent events of a specific type.
     */
    public List<EnvironmentalEvent> getRecentEvents(EventType type, float maxAge) {
        List<EnvironmentalEvent> result = new ArrayList<>();
        float cutoff = currentTime - maxAge;
        
        for (EnvironmentalEvent event : recentEvents) {
            if (event.type == type && event.timestamp >= cutoff) {
                result.add(event);
            }
        }
        
        return result;
    }
    
    /**
     * Counts recent deaths near a position.
     */
    public int countRecentDeathsNear(Vector3 position, float radius, float maxAge, Team team) {
        int count = 0;
        float cutoff = currentTime - maxAge;
        
        for (EnvironmentalEvent event : recentEvents) {
            if (event.type != EventType.ALLY_DEATH) continue;
            if (event.timestamp < cutoff) continue;
            if (event.affectedTeam != team) continue;
            if (event.position.dst(position) > radius) continue;
            count++;
        }
        
        return count;
    }
    
    /**
     * Clears all state.
     */
    public void clear() {
        unitMorale.clear();
        teamMorale.put(Team.WHITE, BASE_MORALE);
        teamMorale.put(Team.BLACK, BASE_MORALE);
        recentEvents.clear();
        currentTime = 0f;
    }
    
    /**
     * Removes tracking for a unit (call when unit is destroyed).
     */
    public void removeUnit(Unit unit) {
        unitMorale.remove(unit);
    }
}
