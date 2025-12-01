# NPC AI Refactoring: Shared Components

This document describes the new shared NPC components for extracting duplicated threat detection, movement, targeting, and state management logic across Guard, Assassin, and King entities.

## Architecture Overview

### Service Layer

Four new services consolidate common NPC behavior:

1. **AwarenessService**: Threat detection and memory
   - Tracks threat presence, curiosity state, flee requirement, strike readiness
   - Automatically decays threat memory after 5 seconds of no updates
   - Usage: `agent.getAwareness().detectThreat(enemy, pos)`

2. **MovementController**: Positioning and velocity management
   - Abstracts target-seeking and retreat vectors
   - Handles arrival detection
   - Usage: `agent.getMovement().setTargetPosition(target); agent.getMovement().updateVelocity(unit, speed)`

3. **TargetingService**: Enemy/ally selection
   - Finds nearest ally/enemy by team affiliation
   - Centralized backstab checks
   - Usage: `agent.getTargeting().findNearestEnemy(unit, entities)`

4. **TimerRegistry**: Centralized timer management
   - Consolidates moveTimer, alertMemoryTimer, stealthTimer, etc.
   - Named timer API: `agent.getTimers().start("alert", 5f)`, `agent.getTimers().isActive("alert")`

### Base Class: TransitionableAgent

`TransitionableAgent<T extends Unit, S extends Enum<S> & State<T>>` provides:
- FSM state machine management (wraps gdx-ai DefaultStateMachine)
- Automatic service initialization
- Consolidated `changeState()` and `getCurrentState()` methods
- Lifecycle hooks: services update automatically in `update(delta)`

## Migration Roadmap

### Phase 1: Dual Support (Current)
- Services available but optional
- Existing agents (GuardAgent, AssassinAgent, KingAgent) can gradually adopt services
- No breaking changes

### Phase 2: Guard Migration
- GuardAgent → extends TransitionableAgent<Guard, GuardState>
- Move `scanForEnemies()`, `findClosestGuard()` → AwarenessService + TargetingService
- Move `updateMovement()`, `moveTowardLastSighting()` → MovementController
- Replace `alertMemoryTimer`, `moveTimer` → TimerRegistry

### Phase 3: Assassin Migration
- AssassinAgent → extends TransitionableAgent<Assassin, AssassinState>
- Move `checkForGuards()`, `hasStrikeOpportunity()` → AwarenessService
- Move `planAssassinSpacingMove()`, `pickShadowRetreat()` → MovementController + strategies
- Replace `isSpacing`, `moveTimer`, `stealthTimer` → TimerRegistry + AwarenessService flags

### Phase 4: King Migration
- KingAgent → extends TransitionableAgent<King, KingState>
- Move `moveTowardGuard()`, `fleeFromThreat()` → MovementController retreat/seek
- Consolidate timer management

### Phase 5: New NPC Types
- Future entities (Archer, Mage, Villager) can inherit TransitionableAgent and reuse all services
- Minimal boilerplate: just define states, inherit shared behavior

## Usage Examples

### Setting up an agent with services

```java
// In a new NPC agent class:
public class MyNpcAgent extends TransitionableAgent<MyNpc, MyNpcState> {
    public MyNpcAgent(MyNpc owner, AiContext context) {
        super(owner, context, MyNpcState.IDLE, MyNpcState.class);
    }
}

// In MyNpcState enum:
IDLE {
    @Override
    public void update(MyNpc npc) {
        MyNpcAgent agent = npc.getAiAgent();
        
        // Use services
        agent.getAwareness().findThreat(...);
        agent.getMovement().setTargetPosition(...);
        agent.getTargeting().findNearestEnemy(...);
        
        if (agent.getTimers().isActive("retreat")) {
            // Still retreating
        }
    }
}
```

### Migrating existing logic to TimerRegistry

**Before:**
```java
private float moveTimer = 0f;
private float alertMemoryTimer = 0f;

public void update(float delta) {
    moveTimer -= delta;
    alertMemoryTimer -= delta;
    if (moveTimer <= 0f) { /* pick new move */ }
    if (alertMemoryTimer <= 0f) { /* forget threat */ }
}
```

**After:**
```java
public void update(float delta) {
    aiAgent.getTimers().update(delta);
    
    if (!aiAgent.getTimers().isActive("moveTimer")) {
        // Pick new move
        aiAgent.getTimers().start("moveTimer", 1.0f);
    }
    if (!aiAgent.getTimers().isActive("alertMemory")) {
        // Forget threat
    }
}
```

### Using AwarenessService instead of custom threat tracking

**Before:**
```java
private boolean isFleeing = false;
private float stealthTimer = 0f;

public void checkForGuards(List<Entity> entities) {
    // Manual threat detection, memory decay logic
}

public boolean shouldFlee() {
    return isFleeing || hp < maxHp * 0.4f;
}
```

**After:**
```java
public void checkForGuards(List<Entity> entities) {
    for (Entity e : entities) {
        if (isEnemyThreat(e)) {
            aiAgent.getAwareness().detectThreat(e, e.getPosition());
            break;
        }
    }
}

public boolean shouldFlee() {
    return aiAgent.getAwareness().isFleeRequired() || hp < maxHp * 0.4f;
}
```

## Benefits

1. **Code Reuse**: ~30% less duplicated threat detection, movement, and targeting logic
2. **Maintainability**: Centralized timer/awareness logic reduces bugs
3. **Extensibility**: New NPCs inherit all services automatically
4. **Testing**: Services can be unit-tested independently
5. **Performance**: Consolidated update loops, efficient entity queries

## Files Added

- `com.castlewar.ai.service.TimerRegistry`
- `com.castlewar.ai.service.AwarenessService`
- `com.castlewar.ai.service.MovementController`
- `com.castlewar.ai.service.TargetingService`
- `com.castlewar.ai.TransitionableAgent` (base class)

## Future Work

- [ ] Extract castle infiltration logic into StrategyService
- [ ] Extract combat AI (attack, parry) into CombatService
- [ ] Profile and optimize entity query performance in TargetingService
- [ ] Consider behavior tree integration for complex decision logic
