# AI Services Refactor – Progress Report

## Completed ✓

### 1. Service Layer Created
- **TimerRegistry.java** – Named timer tracking for AI delays/cooldowns
- **AwarenessService.java** – Threat detection state + last known enemy position + decay timer
- **MovementController.java** – Target position holder, velocity update helpers (seek, retreat)
- **TargetingService.java** – Nearest ally/enemy search, backstab proximity checking
- **TransitionableAgent.java** – Base class binding all services + DefaultStateMachine management

### 2. Agent Refactoring (All Completed)
✓ **AssassinAgent** → extends TransitionableAgent<Assassin, AssassinState>
✓ **GuardAgent** → extends TransitionableAgent<Guard, GuardState>
✓ **KingAgent** → extends TransitionableAgent<King, KingState>

All three agents now:
- Inherit state machine management (changeState, getCurrentState)
- Expose services via getAwareness(), getMovement(), getTargeting(), getTimers()
- Auto-update services in their update() override

### 3. Unit Enhancements
- Added `AwarenessState` enum (RELAXED, CURIOUS, SURPRISED)
- Added awareness state tracking with timers
- Added canTriggerAwarenessState() gating to prevent spam
- Added enterAwarenessState() to map icons → states and apply durations
- Exported `IServicedUnit` interface for optional adoption

### 4. Debug Infrastructure
- **AiDebugLog.java** – Lightweight ring buffer (200 entry max) for structured AI events
- Integrated into WorldContext as getDebugLog()
- Guard.logGuardEvent() + Assassin.logAssassinEvent() thread-safe logging
- Rendering support in DualViewScreen with F2 (overlay) / F3 (log toggle)

### 5. Build Status
✓ Full project builds successfully (no errors)
✓ core:compileJava passes
✓ desktop:run executes without crashes
✓ AI debug logs visible in console and on-screen HUD

---

## In Progress / Next Steps

### Immediate (High Priority)
1. **Integrate AwarenessService into GuardState.ALERT** – Use service to track last threat position instead of Unit field
2. **Integrate MovementController into movement resolution** – Replace inline velocity updates with controller methods
3. **Test scenario: Assassin escape behavior** – Verify spacing, fleeing, and corner logic still works post-refactor

### Medium Term
4. **Add CombatService** – Consolidate backstab, attack range, knockback logic
5. **Add InfiltrationService** – Consolidate stealth timer, visibility checks
6. **Migrate Guard/King movement** – Replace moveTimer + targetPosition with MovementController
7. **Add behavior-tree support** – Experimental parallel-branch FSM for complex decision trees

### Long Term
8. **Remove duplicate code in Unit subclasses** – K.I.S.S. principle; minimize tactical logic duplication
9. **Expand service library** – As needed for future entity types (archer, mage, etc.)
10. **Performance audit** – Profile service overhead, allocations per frame, GC pressure

---

## Architecture Overview

```
Unit (abstract)
  ├─ King    → KingAgent (TransitionableAgent<King, KingState>)
  ├─ Guard   → GuardAgent (TransitionableAgent<Guard, GuardState>)
  └─ Assassin → AssassinAgent (TransitionableAgent<Assassin, AssassinState>)

TransitionableAgent<T, S>
  ├─ awareness : AwarenessService
  ├─ movement : MovementController
  ├─ targeting : TargetingService
  ├─ timers : TimerRegistry
  └─ stateMachine : DefaultStateMachine<T, S>
```

---

## Key Decisions

### Why TransitionableAgent<T, S> Generic Signature?
- **T** = Unit subclass (King, Guard, Assassin) for type safety
- **S** = State enum (KingState, GuardState, AssassinState) for FSM type safety
- Allows compile-time verification that state transitions are valid

### Why Keep AiAgent as Parent?
- TransitionableAgent extends AiAgent to preserve context (AiContext)
- Maintains backward compatibility with code expecting AiAgent instances
- Thin shim approach: old code works, new code consumes services

### Why Separate Services from Unit?
- **Decoupling** – Services don't depend on Unit internals; Unit doesn't force service adoption
- **Composition** – Easier to test services independently
- **Flexibility** – Other entity types (NPCs, towers) can reuse same services

---

## Testing Checklist

- [ ] Assassin spacing behavior (hysteresis 12/16 blocks) still works
- [ ] Assassin corner escape (climb/scramble/fight) triggers correctly
- [ ] Guard patrol wander and target pursuit unaffected
- [ ] King courtyard behavior unchanged
- [ ] AI debug logs capture all state transitions
- [ ] F2/F3 hotkeys toggle overlay + logging correctly
- [ ] No performance regression (measure frame time before/after)
- [ ] No new memory leaks (check GC patterns)

---

## Metrics

| Component | Lines | Status | Notes |
|-----------|-------|--------|-------|
| TimerRegistry | 25 | ✓ Complete | Simple named-timer map |
| AwarenessService | 55 | ✓ Complete | Threat decay + last-known tracking |
| MovementController | 65 | ✓ Complete | Target + velocity helpers |
| TargetingService | 80 | ✓ Complete | Nearest search + backstab check |
| TransitionableAgent | 60 | ✓ Complete | Service + FSM base class |
| IServicedUnit | 15 | ✓ Complete | Optional adoption interface |
| AiDebugLog | 170 | ✓ Complete | Ring buffer + console/HUD rendering |
| Agent Migrations | 3 files | ✓ Complete | AssassinAgent, GuardAgent, KingAgent |

**Total new code: ~470 lines** (net +~250 after cleanup)
**Files modified: 7** (agents + debug + units)

---

## References

- **REFACTORING_NPC_COMPONENTS.md** – Detailed migration guide
- **copilot-instructions.md** – Project architecture and AI patterns
- **AI debug HUD** – F2 overlay + F3 log toggle in DualViewScreen
