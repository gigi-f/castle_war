# NPC Agent Implementation Plan for Castle War

## Executive Summary

Based on research from industry-standard game AI tutorials, combat design principles, and emergent gameplay patterns, this plan outlines a comprehensive behavior tree and state machine hybrid architecture for Castle War's NPC agents and vehicles.

**Research Sources:**
- [Behavior Trees Tutorial](https://generalistprogrammer.com/tutorials/game-ai-behavior-trees-complete-implementation-tutorial)
- [NPC Behavior Guide](https://generalistprogrammer.com/tutorials/npc-behavior-complete-game-ai-tutorial)
- [Combat Design](https://gamedesignskills.com/game-design/combat-design/)
- [Emergent Gameplay](https://gamedesignskills.com/game-design/emergent-gameplay/)

---

## 1. Unit & Vehicle Roster

### 1.1 Infantry Units

| Unit Type | Role | HP | Damage | Speed | Special Abilities |
|-----------|------|-----|--------|-------|-------------------|
| **King** | VIP/Commander | 150 | 20 | Slow | Rally guards, high knockback |
| **Guard** | Defender | 80 | 15 | Medium | Block (50% reduction), patrol |
| **Infantry** | Front-line | 60 | 12 | Medium | Shield wall formation |
| **Assassin** | Stealth/Strike | 30 | 40 | Fast | Backstab (2.5×), dodge i-frames |

### 1.2 Ranged Units

| Unit Type | Role | HP | Damage | Range | Special Abilities |
|-----------|------|-----|--------|-------|-------------------|
| **Archer** | Ranged DPS | 40 | 18 | 20 blocks | Volley fire, elevation bonus |
| **Crossbowman** | Anti-armor | 45 | 25 | 15 blocks | Armor piercing, slow reload |

### 1.3 Mounted Units (Vehicles)

| Vehicle | Crew | HP | Damage | Speed | Special |
|---------|------|-----|--------|-------|---------|
| **Cavalry (Horse)** | 1 rider | 100 | 20 | Very Fast | Charge attack (3× on impact), trample |
| **War Horse** | 1 knight | 120 | 25 | Fast | Lance charge, knockdown |

### 1.4 Siege Vehicles

| Vehicle | Crew | HP | Damage | Speed | Special |
|---------|------|-----|--------|-------|---------|
| **Trebuchet** | 3 operators | 200 | 80 (AoE) | Immobile | Long range (50+ blocks), destroys walls |
| **Battering Ram** | 4 operators | 300 | 100 (structures) | Very Slow | Gate destruction, crew protection |
| **Siege Tower** | 6 passengers | 250 | 0 | Very Slow | Wall scaling, troop deployment |

---

## 2. Core Architecture: Behavior Tree + State Machine Hybrid

### Service Layer

Four core services consolidate common NPC behavior:

1. **AwarenessService**: Threat detection and memory
   - Tracks threat presence, curiosity state, flee requirement
   - Automatically decays threat memory after 5 seconds
   - Usage: `agent.getAwareness().detectThreat(enemy, pos)`

2. **MovementController**: Positioning and velocity management
   - Abstracts target-seeking and retreat vectors
   - Handles arrival detection and formation positioning
   - Usage: `agent.getMovement().setTargetPosition(target)`

3. **TargetingService**: Enemy/ally selection
   - Finds nearest ally/enemy by team affiliation
   - Centralized backstab angle checks
   - Usage: `agent.getTargeting().findNearestEnemy(unit, entities)`

4. **TimerRegistry**: Centralized timer management
   - Named timer API: `agent.getTimers().start("reload", 2f)`

### Base Class: TransitionableAgent

```java
TransitionableAgent<T extends Unit, S extends Enum<S>> implements AiAgent
├── awareness : AwarenessService
├── movement : MovementController
├── targeting : TargetingService
├── timers : TimerRegistry
├── blackboard : Blackboard
├── behaviorTree : BehaviorTree  ← DECISION AUTHORITY
└── stateMachine : DefaultStateMachine<T, S>  ← STATE CONTAINER ONLY
```

> **Integration Note:** `TransitionableAgent` implements the existing `AiAgent` interface 
> to ensure compatibility with `WorldContext` entity management. The current standalone 
> agents (e.g., `GuardAgent`) will be migrated to extend `TransitionableAgent`.

### Recommended Architecture

Adopt a **hierarchical behavior tree** as the **sole decision maker**, with FSM serving only as a state container:

```
BehaviorTree (priority selector) ← DECISION AUTHORITY
├── Emergency Branch (highest priority)
│   ├── Flee sequence (health critical)
│   └── Combat response sequence
├── Tactical Branch
│   ├── Investigation sequence
│   └── Pursuit sequence
└── Default Branch (lowest priority)
    └── Patrol/Idle behavior

FSM (state container) ← OUTPUT ONLY
├── Tracks current animation state
├── Exposes capability flags (canAttack, canMove)
└── Changed BY behavior tree leaf nodes, never drives logic
```

> **Critical Design Rule:** The FSM is an *output* of behavior tree decisions, not a logic driver.
> Example: `AttackTask` leaf node calls `stateMachine.changeState(ATTACKING)` as a side effect.

### Key Components to Implement

#### 2.1 Behavior Tree Node Base Classes
```java
// Package: com.castlewar.ai.behavior
public enum NodeState { SUCCESS, FAILURE, RUNNING }

public abstract class BehaviorNode {
    protected NodeState state;
    public abstract NodeState evaluate();
    public void onEnter() {}
    public void onExit() {}
}
```

#### 2.2 Composite Nodes
- **SelectorNode**: OR logic - tries children until one succeeds
- **SequenceNode**: AND logic - executes children in order until one fails
- **ParallelNode**: Runs multiple children concurrently

#### 2.3 Decorator Nodes
- **InverterNode**: Flips success/failure
- **RepeatNode**: Loops child execution
- **CooldownDecorator**: Gates execution based on timer
- **ConditionalDecorator**: Requires condition to pass

---

## 3. Blackboard System

### Purpose
Centralized data sharing between behavior tree nodes.

### Design (Performance-Optimized)

Use **enum keys** instead of raw strings to avoid hashing overhead and typos:

```java
public enum BlackboardKey {
    // Perception
    VISIBLE_ENEMIES,
    LAST_KNOWN_THREAT_POS,
    ALERT_LEVEL,
    
    // Combat
    CURRENT_TARGET,
    IN_ATTACK_RANGE,
    POISE_REMAINING,
    AMMO_COUNT,
    
    // Movement
    CURRENT_WAYPOINT,
    FLEE_DESTINATION,
    FORMATION_SLOT,
    
    // Vehicle
    CURRENT_MOUNT,
    IS_CHARGING,
    CREW_COUNT,
    
    // Siege
    TARGET_STRUCTURE,
    RELOAD_PROGRESS
}

public class Blackboard {
    private final EnumMap<BlackboardKey, Object> data = new EnumMap<>(BlackboardKey.class);
    private final EnumMap<BlackboardKey, List<Consumer<Object>>> observers = new EnumMap<>(BlackboardKey.class);
    
    @SuppressWarnings("unchecked")
    public <T> T get(BlackboardKey key, Class<T> type) {
        return (T) data.get(key);
    }
    
    public Vector3 getVector3(BlackboardKey key) { return get(key, Vector3.class); }
    public boolean getBool(BlackboardKey key) { return Boolean.TRUE.equals(data.get(key)); }
    public float getFloat(BlackboardKey key) { return get(key, Float.class); }
    public Entity getEntity(BlackboardKey key) { return get(key, Entity.class); }
    
    public void set(BlackboardKey key, Object value) {
        data.put(key, value);
        notifyObservers(key, value);
    }
    
    public void registerObserver(BlackboardKey key, Consumer<Object> callback);
}
```

### Standard Blackboard Keys
| Key | Type | Description |
|-----|------|-------------|
| `CURRENT_TARGET` | Entity | Current combat target |
| `LAST_KNOWN_THREAT_POS` | Vector3 | Last seen enemy position |
| `ALERT_LEVEL` | Float | 0.0 (relaxed) to 1.0 (combat) |
| `CURRENT_WAYPOINT` | Vector3 | Current patrol destination |
| `FLEE_DESTINATION` | Vector3 | Safe retreat position |
| `FORMATION_SLOT` | Integer | Position in formation |
| `CURRENT_MOUNT` | Vehicle | Current vehicle (if mounted) |

---

## 4. Perception System

### 4.1 Vision Cone Detection
```java
public class PerceptionService {
    private static final float DEFAULT_VIEW_RANGE = 15f;
    private static final float DEFAULT_FOV_DEGREES = 110f;
    
    public List<Entity> getVisibleEntities(Unit self, List<Entity> allEntities);
    public float calculateThreatLevel(Unit self, List<Entity> threats);
}
```

### 4.2 Hearing System
- Detect footsteps within configurable radius
- Combat sounds have larger radius (alerts nearby units)
- Siege weapons create massive audio signatures

### 4.3 Archer Elevation Bonus
- Height advantage increases range by 20%
- Accuracy bonus when firing downward

---

## 5. Entity-Specific Behaviors

### 5.1 Guard Behavior Tree

```
Guard Root (Selector)
├── Flee Branch (HP < 30%)
├── Combat Branch (Enemy in range → Attack)
├── Chase Branch (Enemy visible → Pursue)
├── Investigate Branch (Alert > 0.3 → Check last known pos)
└── Patrol Branch (Waypoint cycle)
```

**States:** `IDLE, PATROL, ALERT, ENGAGE, FLEE`

### 5.2 Assassin Behavior Tree

```
Assassin Root (Selector)
├── Flee Branch (Cornered OR HP < 40%)
├── Strike Branch (King in range AND !detected → Backstab)
├── Evade Branch (Guard nearby → Find cover)
└── Infiltrate Branch (Navigate to enemy castle)
```

**States:** `IDLE, SNEAK, STRIKE, FLEE`

### 5.3 King Behavior Tree

```
King Root (Selector)
├── Flee Branch (Assassin nearby → Move to guard)
├── Rally Branch (Alert high → Signal guards)
└── Idle Branch (Courtyard patrol)
```

**States:** `IDLE, PATROL, ALERT, ENGAGE`

### 5.4 Infantry Behavior Tree

```
Infantry Root (Selector)
├── Flee Branch (HP < 20% AND no allies nearby)
├── Formation Branch (Leader present → Maintain formation slot)
├── Combat Branch (Enemy in melee range → Attack)
├── Advance Branch (Formation command → Move with unit)
└── Hold Branch (Default → Hold position)
```

**States:** `IDLE, MARCH, FORMATION, ENGAGE, FLEE`

### 5.5 Archer Behavior Tree

```
Archer Root (Selector)
├── Flee Branch (Enemy in melee range → Retreat)
├── Reload Branch (Ammo depleted → Resupply from cache)
├── Volley Branch (Commander signals → Coordinated fire)
├── Snipe Branch (High-value target visible → Prioritize)
├── Suppress Branch (Enemy group → Area fire)
└── Reposition Branch (Seek elevation advantage)
```

**States:** `IDLE, AIM, FIRE, RELOAD, REPOSITION, FLEE`

### 5.6 Cavalry Behavior Tree

```
Cavalry Root (Selector)
├── Dismount Branch (Horse HP critical OR confined space)
├── Charge Branch (Clear path to enemy → Lance charge)
├── Harass Branch (Ranged enemies → Hit and run)
├── Trample Branch (Infantry cluster → Ride through)
├── Flank Branch (Battle engaged → Circle to rear)
└── Rally Branch (Regroup at commander position)
```

**States:** `MOUNTED_IDLE, CHARGE, HARASS, FLANK, DISMOUNTED`

### 5.7 Trebuchet Behavior Tree

```
Trebuchet Root (Selector)
├── Relocate Branch (Under attack → Emergency move)
├── Target Wall Branch (Wall segment in range → Fire)
├── Target Gate Branch (Gate intact → Prioritize)
├── Target Cluster Branch (Enemy group → Area bombardment)
└── Reload Branch (Cooldown active → Wait)
```

**States:** `IDLE, AIMING, FIRING, RELOADING, RELOCATING`

**Crew Behavior:**
- 3 operators required for full fire rate
- 2 operators = 50% fire rate
- 1 operator = 25% fire rate
- 0 operators = inoperable

### 5.8 Battering Ram Behavior Tree

```
Battering Ram Root (Selector)
├── Retreat Branch (HP < 30% → Pull back for repairs)
├── Ram Gate Branch (At gate → Execute ram attack)
├── Approach Branch (Gate not destroyed → Path to gate)
└── Protect Crew Branch (Under archer fire → Raise shields)
```

**States:** `IDLE, APPROACHING, RAMMING, RETREATING`

**Crew Behavior:**
- 4 operators push the ram
- Operators can raise shields (reduces movement, +50% crew protection)
- If operators killed, ram becomes inoperable

---

## 6. Vehicle System

### 6.1 Vehicle Base Class
```java
public abstract class Vehicle extends Entity {
    protected List<Unit> crew;
    protected int minCrew;
    protected int maxCrew;
    protected float operationalEfficiency; // Based on crew count
    
    public boolean isOperable() { return crew.size() >= minCrew; }
    public void mount(Unit unit);
    public void dismount(Unit unit);
    public abstract void operate(float delta);
}
```

### 6.2 Mount System (Horses)

**Mounting Contract:**
- Rider **remains an active entity** in the world (can receive damage independently)
- Rider's physics/movement is **disabled** while mounted
- Rider position is **locked to saddle offset** on the mount
- Damage routing: Attacks can target rider OR mount separately

```java
public class Mount extends Vehicle {
    private Unit rider;
    private final Vector3 saddleOffset = new Vector3(0, 0, 1.2f); // Above horse
    private float chargeSpeed = 12f;
    private float chargeDamageMultiplier = 3f;
    private boolean isCharging;
    
    @Override
    public void mount(Unit unit) {
        if (rider != null) return;
        this.rider = unit;
        rider.setMounted(true);
        rider.setMovementEnabled(false); // Disable rider physics
    }
    
    @Override
    public void dismount(Unit unit) {
        if (this.rider != unit) return;
        rider.setMounted(false);
        rider.setMovementEnabled(true);
        rider.getPosition().set(this.position).add(1, 0, 0); // Dismount beside horse
        this.rider = null;
    }
    
    @Override
    public void update(float delta, GridWorld world) {
        super.update(delta, world);
        // Lock rider to saddle position
        if (rider != null) {
            rider.getPosition().set(this.position).add(saddleOffset);
        }
    }
    
    public void startCharge();
    public void endCharge();
    public float getChargeDamage();
}
```

### 6.3 Siege Engine System

**Multi-Block Unit Pathfinding:**
Siege engines occupy multiple blocks (e.g., Trebuchet = 3×3, Ram = 2×4). Standard A* assumes 1×1.

**Solution: Clearance-Based Pathfinding**
- Each siege engine defines a `clearanceRadius` (blocks required around center)
- Pathfinder checks clearance at each node before adding to open set
- Siege engines restricted to **roads and open terrain** (minimum 3-block wide paths)
- Alternative: Pre-computed "siege lanes" in castle layout generation

```java
public abstract class SiegeEngine extends Vehicle {
    protected float reloadTime;
    protected float currentReload;
    protected float range;
    protected float damage;
    protected float aoeRadius;
    
    // Multi-block footprint
    protected int footprintWidth = 3;  // X dimension in blocks
    protected int footprintDepth = 3;  // Y dimension in blocks
    
    /**
     * Returns the clearance radius for pathfinding.
     * Pathfinder must ensure this many blocks are clear in all directions.
     */
    public int getClearanceRadius() {
        return Math.max(footprintWidth, footprintDepth) / 2 + 1;
    }
    
    public abstract void fire(Vector3 target);
    public boolean canFire() { return currentReload <= 0 && isOperable(); }
}
```

### 6.4 Projectile System (for Trebuchet/Archers)
```java
public class Projectile extends Entity {
    private Vector3 velocity;
    private float gravity = 9.8f;
    private float damage;
    private float aoeRadius; // 0 for arrows
    private Entity source;
    
    public void update(float delta, GridWorld world);
    private void onImpact(Vector3 position);
}
```

---

## 7. Combat Design

### 7.1 Combat Mechanics Layer
| Action | Mechanic | Effect |
|--------|----------|--------|
| Strike | Damage + Poise damage | Reduces HP, staggers if poise breaks |
| Dodge | I-frame window | Brief invulnerability (assassin only) |
| Block | Damage reduction | Guards/Infantry reduce damage by 50% |
| Backstab | Critical multiplier | Assassin deals 2.5× damage from behind |
| Charge | Impact damage | Cavalry deals 3× damage on charge impact |
| Volley | Coordinated fire | Archers +25% accuracy when in volley |
| Ram | Structure damage | 100 damage to gates per hit |
| Bombardment | AoE damage | Trebuchet deals 80 damage in 5-block radius |

### 7.2 Damage Types
| Type | Strong Against | Weak Against |
|------|---------------|--------------|
| Piercing (arrows) | Unarmored units | Shields, heavy armor |
| Crushing (maces, rams) | Armor, structures | Nothing |
| Slashing (swords) | Unarmored | Armor |
| Siege (trebuchet) | Structures, groups | Single armored units |

### 7.3 Stagger/Poise System
- Each unit has **poise** (stagger resistance)
- Attacks deal **poise damage**
- When poise breaks → unit is staggered (vulnerable)
- Poise regenerates over time when not attacked

---

## 8. Formation System

### 8.1 Formation Types
```java
public enum FormationType {
    LINE,           // Standard battle line
    WEDGE,          // Cavalry charge formation
    SQUARE,         // Defensive anti-cavalry
    COLUMN,         // Marching formation
    SCATTERED       // Loose formation (vs archers)
}
```

### 8.2 Formation Controller
```java
public class FormationController {
    private FormationType currentFormation;
    private List<Unit> members;
    private Unit leader;
    
    public Vector3 getSlotPosition(int slotIndex);
    public void changeFormation(FormationType type);
    public void advance(Vector3 direction);
    public void halt();
}
```

---

## 9. Emergent Gameplay Enablers

### 9.1 Systemic Interactions
- **Sound propagation**: Combat alerts nearby guards
- **Line-of-sight chains**: Guards relay spotted assassins
- **Environmental awareness**: Units react to fallen allies
- **Siege escalation**: Wall breaches change AI priorities

### 9.2 Agent Communication
```java
public class AlertNetwork {
    public void broadcastAlert(Unit source, Vector3 position, float radius);
    public void issueCommand(Unit commander, Command command, List<Unit> targets);
}
```

### 9.3 Commander Orders
| Command | Effect |
|---------|--------|
| ADVANCE | Formation moves toward objective |
| HOLD | Units stop and defend position |
| CHARGE | Cavalry initiate charge attack |
| VOLLEY | Archers fire coordinated volley |
| RETREAT | Units fall back to rally point |
| PROTECT_SIEGE | Guard siege engines |

---

## 10. Performance Optimization

### 10.1 AI LOD (Level of Detail)
```java
public class AILODManager {
    private static final float HIGH_LOD_DISTANCE = 30f;   // Every frame
    private static final float MED_LOD_DISTANCE = 60f;    // Every 3 frames
    private static final float LOW_LOD_DISTANCE = 100f;   // Every 10 frames
    
    public int getUpdateInterval(Entity entity, Vector3 cameraPos);
}
```

### 10.2 Spatial Partitioning
- Grid-based entity lookup for perception
- Only scan nearby cells for threats
- Separate grids for units vs projectiles

### 10.3 Pathfinding Scalability

With 100+ units, per-entity A* every frame is not viable.

**Strategy 1: Flow Fields (for group movement)**
```java
public class FlowFieldManager {
    private Map<Vector3, FlowField> cachedFields = new HashMap<>();
    
    /**
     * Get or compute a flow field to the target.
     * All units moving to same target share one field.
     */
    public FlowField getFlowField(Vector3 target) {
        return cachedFields.computeIfAbsent(target, this::computeFlowField);
    }
    
    public void invalidate(Vector3 target) {
        cachedFields.remove(target);
    }
}
```

**Strategy 2: Time-Sliced A***
```java
public class PathfindingScheduler {
    private static final int MAX_PATHS_PER_FRAME = 5;
    private final Queue<PathRequest> pendingRequests = new LinkedList<>();
    
    public void requestPath(Unit unit, Vector3 target, Consumer<List<Vector3>> callback);
    
    public void update() {
        int processed = 0;
        while (!pendingRequests.isEmpty() && processed < MAX_PATHS_PER_FRAME) {
            PathRequest request = pendingRequests.poll();
            List<Vector3> path = computePath(request);
            request.callback.accept(path);
            processed++;
        }
    }
}
```

**Strategy 3: Hierarchical Pathfinding**
- Divide world into 16×16 "chunks"
- First: A* on chunk graph (coarse)
- Then: A* within each chunk (fine)
- Cache chunk connectivity

### 10.4 Formation Movement Optimization
- Only leader pathfinds; members follow via offset
- Formation slot positions calculated relative to leader
- Reduces pathfinding calls by 90% for grouped units

---

## 11. Implementation Phases

### Phase 1: Core Framework (Week 1-2)
- [ ] Implement `BehaviorNode` base class
- [ ] Implement `SelectorNode`, `SequenceNode`, `ParallelNode`
- [ ] Implement `ConditionNode`, `TaskNode`
- [ ] Implement `BlackboardKey` enum
- [ ] Implement `Blackboard` with EnumMap and type-safe accessors
- [ ] Add `BehaviorTree` manager class
- [ ] Define `AiAgent` interface (if not existing)
- [ ] Implement `PathfindingScheduler` for time-sliced A*

### Phase 2: Existing Unit Migration (Week 2-3)
- [ ] Migrate Guard to behavior tree
- [ ] Migrate Assassin to behavior tree
- [ ] Migrate King to behavior tree
- [ ] Implement AlertNetwork

### Phase 3: New Infantry Units (Week 3-4)
- [ ] Implement `Infantry` entity class
- [ ] Create `InfantryBehaviorTree`
- [ ] Implement `FormationController`
- [ ] Add shield wall mechanic

### Phase 4: Ranged Units (Week 4-5)
- [ ] Implement `Archer` entity class
- [ ] Create `ArcherBehaviorTree`
- [ ] Implement `Projectile` system
- [ ] Add volley fire coordination
- [ ] Implement elevation bonuses

### Phase 5: Mounted Units (Week 5-6)
- [ ] Implement `Mount` vehicle class with saddle offset
- [ ] Implement rider physics disable/enable on mount/dismount
- [ ] Implement `Cavalry` unit (rider)
- [ ] Create `CavalryBehaviorTree`
- [ ] Add charge attack mechanics
- [ ] Test independent rider/mount damage

### Phase 6: Siege Vehicles (Week 6-8)
- [ ] Implement `SiegeEngine` base class with clearance metrics
- [ ] Implement clearance-aware pathfinding for multi-block units
- [ ] Implement `Trebuchet` with crew system
- [ ] Implement `BatteringRam` with crew system
- [ ] Add structure damage to `GridWorld`
- [ ] Create siege behavior trees
- [ ] Define "siege lanes" in castle generation (min 3-block wide)

### Phase 7: Combat & Polish (Week 8-9)
- [ ] Implement `CombatService` with poise/stagger
- [ ] Add damage types and resistances
- [ ] Implement commander order system
- [ ] Implement `FlowFieldManager` for group movement
- [ ] Performance optimization pass
- [ ] Profile flow fields vs time-sliced A* for formations

### Phase 8: Testing & Balancing (Week 9+)
- [ ] Profile performance with 100+ entities
- [ ] Balance unit stats and costs
- [ ] Document behaviors in `GAME_MANUAL.md`

---

## 12. File Structure

```
core/src/main/java/com/castlewar/
├── ai/
│   ├── behavior/
│   │   ├── BehaviorNode.java
│   │   ├── BehaviorTree.java
│   │   ├── composite/
│   │   │   ├── SelectorNode.java
│   │   │   ├── SequenceNode.java
│   │   │   └── ParallelNode.java
│   │   ├── decorator/
│   │   │   ├── InverterNode.java
│   │   │   ├── CooldownDecorator.java
│   │   │   └── ConditionalDecorator.java
│   │   └── leaf/
│   │       ├── ConditionNode.java
│   │       └── TaskNode.java
│   ├── blackboard/
│   │   ├── Blackboard.java
│   │   └── BlackboardKey.java
│   ├── pathfinding/
│   │   ├── PathfindingScheduler.java
│   │   ├── FlowFieldManager.java
│   │   └── ClearancePathfinder.java
│   ├── service/
│   │   ├── AwarenessService.java
│   │   ├── MovementController.java
│   │   ├── TargetingService.java
│   │   └── TimerRegistry.java
│   ├── TransitionableAgent.java
│   ├── guard/
│   ├── assassin/
│   ├── king/
│   ├── infantry/
│   ├── archer/
│   └── cavalry/
├── entity/
│   ├── Unit.java
│   ├── King.java
│   ├── Guard.java
│   ├── Assassin.java
│   ├── Infantry.java
│   ├── Archer.java
│   └── Projectile.java
├── vehicle/
│   ├── Vehicle.java
│   ├── Mount.java
│   ├── SiegeEngine.java
│   ├── Trebuchet.java
│   ├── BatteringRam.java
│   └── SiegeTower.java
├── combat/
│   ├── CombatService.java
│   ├── DamageType.java
│   └── FormationController.java
└── communication/
    └── AlertNetwork.java
```

---

## 13. Key Design Decisions

| Decision | Rationale |
|----------|-----------|
| **Behavior Tree as sole decision maker** | FSM becomes state container only; avoids dual-authority conflicts |
| **EnumMap for Blackboard keys** | O(1) lookup, compile-time safety, no string typos |
| **Rider remains active entity when mounted** | Allows independent damage, simpler collision handling |
| **Clearance-based siege pathfinding** | Multi-block units need explicit corridor width checks |
| **Flow fields for group movement** | Scales to 100+ units; shared computation |
| **TransitionableAgent implements AiAgent** | Backward compatibility with existing entity management |
| **Separate Vehicle from Unit** | Vehicles have crew; different lifecycle |
| **Projectile as Entity** | Physics simulation, collision detection |
| **Formation slots via Blackboard** | Decouples units from formation logic |
| **Damage types** | Tactical depth, counter-play |
| **Crew-based siege efficiency** | Emergent "protect the crew" gameplay |

---

## 14. Success Metrics

1. **Behavioral Correctness**: All unit types behave as specified
2. **Performance**: 100+ entities at 60 FPS
3. **Extensibility**: New unit type in < 150 lines
4. **Tactical Depth**: Observable formation tactics, siege coordination
5. **Emergent Moments**: Guard flanking, archer elevation seeking, cavalry charges

---

## Appendix A: Behavior Tree Node Reference

### Composite Nodes
| Node | Logic | Behavior |
|------|-------|----------|
| Selector | OR | Tries children L→R until SUCCESS |
| Sequence | AND | Runs children L→R until FAILURE |
| Parallel | Concurrent | Runs all; policy determines result |

### Decorator Nodes
| Node | Effect |
|------|--------|
| Inverter | Flips SUCCESS ↔ FAILURE |
| Repeater | Loops child N times |
| Cooldown | Blocks re-execution for duration |
| Conditional | Gates on condition |

---

## Appendix B: Blackboard Key Conventions

All keys are defined in `BlackboardKey` enum for type safety and performance:

```java
public enum BlackboardKey {
    // Perception
    VISIBLE_ENEMIES,          // List<Entity>
    LAST_KNOWN_THREAT_POS,    // Vector3
    ALERT_LEVEL,              // Float (0.0-1.0)

    // Combat
    CURRENT_TARGET,           // Entity
    IN_ATTACK_RANGE,          // Boolean
    POISE_REMAINING,          // Float
    AMMO_COUNT,               // Integer (archers)

    // Movement
    CURRENT_WAYPOINT,         // Vector3
    FLEE_DESTINATION,         // Vector3
    FORMATION_SLOT,           // Integer

    // Vehicle
    CURRENT_MOUNT,            // Mount
    IS_CHARGING,              // Boolean
    CREW_COUNT,               // Integer

    // Siege
    TARGET_STRUCTURE,         // Vector3
    RELOAD_PROGRESS           // Float
}
```

---

## Appendix C: Claude Implementation Skills

When implementing this plan, Claude can leverage these specialized skills:

### Code Generation Skills

```yaml
# Behavior Tree Framework
- Generate BehaviorNode base class with proper LibGDX integration
- Create composite nodes (Selector, Sequence, Parallel) with child management
- Implement decorator nodes with configurable parameters
- Build type-safe Blackboard with generics

# New Unit Types
- Implement Infantry with formation slot tracking
- Build Archer with projectile spawning and reload mechanics
- Create Cavalry with mount/dismount and charge systems
- Implement Trebuchet with crew efficiency and AoE targeting
- Build BatteringRam with gate targeting and crew protection

# Vehicle System
- Create Vehicle base class with crew management
- Implement Mount with charge physics
- Build SiegeEngine with reload and range calculations
```

### Refactoring Skills

```yaml
# Migration Tasks
- Extract common logic from Unit subclasses into services
- Convert inline timer management to TimerRegistry
- Replace scattered threat detection with AwarenessService
- Migrate movement code to MovementController

# Code Organization
- Create package structure for behavior tree components
- Organize entity-specific tasks and conditions into subpackages
```

### Integration Skills

```yaml
# LibGDX Integration
- Use gdx-ai DefaultStateMachine for FSM components
- Leverage Vector3 pooling from LibGDX math utilities
- Integrate projectile physics with GridWorld collision

# Existing Architecture
- Preserve TransitionableAgent service layer
- Integrate with WorldContext entity management
- Use existing AiContext bridge pattern
```

### Suggested Prompts for Implementation

**New Unit Types:**
```
"Implement the Infantry entity class with formation slot tracking. Infantry 
should maintain position relative to a formation leader and support shield 
wall stance that reduces incoming damage."

"Create the Archer entity with projectile spawning. Archers should track 
ammo count, calculate arrow trajectory with gravity, and gain accuracy 
bonuses from elevation."

"Implement the Cavalry mount system. Horses should support mounting/dismounting, 
charge attacks with damage multiplier, and trample damage to infantry."
```

**Siege Vehicles:**
```
"Create the Trebuchet siege engine with crew efficiency system. Fire rate 
should scale with crew count (3 = 100%, 2 = 50%, 1 = 25%). Implement 
projectile arc calculation for long-range bombardment."

"Implement the BatteringRam with gate targeting. The ram should path to 
the nearest enemy gate, require 4 crew to operate, and deal structure 
damage on impact."
```

**Formations:**
```
"Create FormationController that manages unit positioning. Support LINE, 
WEDGE, SQUARE, and COLUMN formations. Units should smoothly transition 
between slot positions when formation changes."
```

### Code Style Preferences

```java
// Prefer composition over inheritance for behavior nodes
public class ChargeTask extends TaskNode {
    private final Mount mount;
    private final MovementController movement;
    
    public ChargeTask(Mount mount, MovementController movement) {
        this.mount = mount;
        this.movement = movement;
    }
}

// Use builder pattern for complex behavior trees
BehaviorTree cavalryTree = BehaviorTree.builder()
    .selector("root")
        .sequence("charge")
            .condition(this::hasChargeTarget)
            .task(new ChargeTask(mount, movement))
        .end()
    .end()
    .build();

// Vehicle crew management
public class Trebuchet extends SiegeEngine {
    @Override
    public float getEfficiency() {
        return Math.min(1f, (float) crew.size() / maxCrew);
    }
}
```

### Error Handling Patterns

```java
// Vehicle operability checks
public boolean canFire() {
    return currentReload <= 0 && isOperable() && hasAmmo();
}

// Formation slot validation
public Vector3 getSlotPosition(int slot) {
    if (slot < 0 || slot >= members.size()) {
        return leader.getPosition(); // Fallback to leader
    }
    return calculateSlotOffset(slot);
}

// Mount safety
public void dismount(Unit rider) {
    if (this.rider != rider) return;
    this.rider = null;
    rider.setMounted(false);
}
```

---

*Last Updated: November 30, 2025*
