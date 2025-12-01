# Castle War – AI Agent Instructions

## Project Overview
**Castle War** is a LibGDX-based 2D grid simulation with procedurally generated castles, multi-perspective rendering, and AI-driven unit behavior. The engine focuses on clarity over 3D complexity—two castles face each other across a battlefield, rendered via top-down, side-scroller, isometric, and first-person views using `ShapeRenderer`.

**Tech Stack:** Java 21, LibGDX 1.12.1, Gradle 8.7 (multi-module: `core` + `desktop`)

---

## Architecture & Critical Data Flow

### Top-Level Bootstrapping
- **CastleWarGame** extends LibGDX `Game`; creates a **WorldContext** (shared world state) and passes it to `DualViewScreen` with an `Options` profile.
- **WorldContext** owns the `GridWorld` (3D block array), procedural castle generation, entity list, and castle boundary metadata.
- **DualViewScreen** handles input, manages camera state per view, calls `GridRenderer` and `UnitRenderer`, and advances the simulation clock if permitted by `Options.updatesSimulation`.

### World Representation
- **GridWorld**: Dynamic 3D array (width × depth × height). X = horizontal, Y = depth, Z = vertical.
  - Block states: `AIR`, `GRASS`, `DIRT`, `STONE`, `CASTLE_WHITE/BLACK`, `CASTLE_WHITE/BLACK_FLOOR`, `CASTLE_WHITE/BLACK_STAIR`, `DOOR`, `WINDOW`, `WATER`, `MOUNTAIN_ROCK`.
  - Negative Z queries return `DIRT/STONE` (simulated underground) without expanding the array.
- **Procedural Generation** (WorldContext):
  - Two castles built from `CastleLayout` blueprints (width, height, interiorLevels, stair geometry, wall type).
  - Spiral staircases in corner towers connect floors; branched rooms on each level.
  - Moats with drawbridges, gates facing the battlefield gap.
  - Perimeter mountains carved with sine/cosine elevation modulation.
  - Battlefield corridor enforced (≥180 blocks) between gates; tracked via `battlefieldStartX/EndX`.

### Entity System
- **Entity** (abstract): Position (Vector3), velocity, team affiliation. Subclasses: `King`, `Guard`, `Assassin`, `Player`, `Streaker`.
- **Unit** (abstract base for interactive units): Extends Entity; holds AI agent, rendering metadata, awareness state.
- **AiContext**: Lightweight bridge to `WorldContext` (grid, entity list); threaded through AI agents to avoid tight coupling.

---

## Rendering Pipeline

### Views & Layers
- **Top-Down**: Orthographic X/Y slices. Renders all layers from underground to `currentLayer`, desaturating lower slices (50% opacity).
- **Side Scroller**: Orthographic X/Z slice at a fixed Y (`sideViewSlice`). Shows first non-air block per column; applies depth-based color fading (0.12 per layer, capped at 0.6).
- **Split View**: Side-by-side panels (vertical divider). Top panel = top-down, right = side view. Shares layer/slice controls.
- **Isometric**: Pseudo-3D projection with accumulated rotation. All layers rendered; focused layer at 100% opacity, others at 50%. Tile geometry: width = `blockSize`, height = `blockSize * 0.5`, vertical = `blockSize * 0.6`.
- **First-Person**: Perspective camera; Player entity updated each frame.

### Rendering Classes
- **GridRenderer**: Wraps `ShapeRenderer` + `SpriteBatch`; draws blocks as colored rectangles per view mode. Single instance shared across all windows.
- **UnitRenderer**: Renders entities (Kings, Guards, Assassins) with team colors, awareness icons, and view-specific geometry.
- **Constants**: `BLOCK_SIZE = 10f` (pixels), layer opacity = 100%/50%, side fade = 0.12.

---

## Input & Control Conventions

### Core Controls (Synced in GAME_MANUAL.md)
- **Layer Navigation**: `,` / `.` adjust top-down layer; `[` / `]` adjust side slice.
- **View Toggling**: `/` switches between top-down ↔ side scroller (if `allowViewToggle`).
- **Split View**: `m` toggles (disabled during isometric).
- **Isometric**: `i` enters/exits; stores prior mode/split state.
- **Entity Selection**: Mouse click (geometry-aware per view), `TAB` cycles focus, `ENTER` selects.
- **Isometric Extras**: WASD pan, mouse drag rotate (horizontal accumulation), `-`/`=` zoom.
- **Compass HUD**: `c` toggle visibility.
- **First-Person**: `F` toggle; spawns Player if absent; mouse cursor captured/released.
- **X-Ray**: `x` toggle (shows entities through occluding layers).

### Implementation Pattern
- **DualViewScreen** processes input via `Gdx.input.isKeyPressed()` and mouse events.
- All input state updates happen in `handleInput()` before render; toggles are debounced with `keyDown` tracking.
- New controls must update the overlay instructions text dynamically (e.g., hide `/` hint if `allowViewToggle = false`).

---

## Configuration & Customization

### SimulationConfig
Located in `simulation/` package. Centralized config for:
- World dimensions (`setWorldWidth/Depth/Height()`).
- Castle dimensions (`setCastleWidth/Depth/Levels()`).
- Spawn counts and positions.
- Clamps castle size to fit world with edge margins + battlefield gap.

### Window Options (DualViewScreen.Options)
- `initialViewMode`: TOP_DOWN, SIDE_SCROLLER, ISOMETRIC, or FIRST_PERSON.
- `allowViewToggle`: Enable `/` key to swap view modes.
- `allowSplitView`: Enable `m` key to toggle split view.
- `updatesSimulation`: Only one window should advance the clock (prevents double-updates in multi-window scenarios).
- `overlayLabel`: Prefix for HUD instructions ("Primary", "Detached Side", etc.).

**Factory methods:**
- `Options.primaryWindow()` – primary with split view + view toggling.
- `Options.detachedSideWindow()` – side-view only; no split/toggle.

---

## AI & Behavioral Patterns

### AI Architecture
- **AiAgent** (interface): `update(entity, delta, aiContext)` called each frame.
- Concrete agents: `KingAgent`, `GuardAgent`, `AssassinAgent`.
- **gdx-ai steering**: `SteerableAdapter` wraps entities for pathfinding/pursuit behaviors.
- Each agent receives lightweight `AiContext` (world grid, entity list) to minimize coupling.

### Team Awareness & Detection
- Entities belong to `Team.WHITE` or `Team.BLACK`.
- Guards patrol battlements; Assassins infiltrate; Kings hold courtyard positions.
- Visibility depends on `currentLayer` (top view) or `sideViewSlice` (side view) unless X-Ray is enabled.

---

## Build & Execution

### Build Commands
```bash
./gradlew build                # Full build (skips tests by default)
./gradlew desktop:run         # Run desktop app immediately
./gradlew clean               # Clean build artifacts
./gradlew test                # Run all tests (JUnit 5)
```

### Multi-Module Layout
- **core/** – Shared game code (entities, rendering, simulation, AI). Produces `core-1.0-SNAPSHOT.jar`.
- **desktop/** – LWJGL3 launcher + multi-window bootstrap. Depends on `core`.
- JDK 17+ required; Gradle 8.7 auto-downloaded via wrapper.

### Working Directory
- Desktop launcher works from `core/assets/` by convention (for texture/asset loading).

---

## Code Style & Performance Guidelines

### Java Conventions
- Target Java 21; use var-args, records, and text blocks where appropriate.
- Allocations per frame should be minimal: reuse `ShapeRenderer`, `SpriteBatch`, and entity lists.
- Prefer LibGDX math utilities (`MathUtils.clamp()`, `MathUtils.lerp()`, etc.) over manual arithmetic.

### Patterns to Follow
1. **SingletonContext**: `WorldContext` is the single owner of mutable state. Multiple windows reference it; only one advances the simulation.
2. **Lightweight Bridges**: `AiContext` decouples AI agents from `WorldContext` internals.
3. **View Geometry**: Each view (top-down, side, isometric) has separate camera + projection logic; share underlying grid.
4. **Block Enums**: New block types added to `GridWorld.BlockState` automatically sync across all views.

### When Adding New Content
- **New Block Type**: Add to `GridWorld.BlockState`, define rendering color in `GridRenderer`, update castle generation if needed.
- **New Entity**: Extend `Entity` or `Unit`; create AI agent if behavioral; register with `WorldContext.entities`.
- **New Input**: Update `DualViewScreen.handleInput()`, synchronize overlay text in `buildOverlayText()`.
- **New View Mode**: Extend `ViewMode` enum; implement camera + projection in `DualViewScreen`; add rendering branch.

---

## Key Files & Navigation

| File | Purpose |
|------|---------|
| `CastleWarGame.java` | Entry point; bootstraps context and screen |
| `WorldContext.java` | Procedural generation, entity list, castle bounds |
| `DualViewScreen.java` | Input, view modes, rendering orchestration (1959 lines) |
| `GridWorld.java` | 3D block array, block state enum |
| `GridRenderer.java` | Block rendering logic per view mode |
| `UnitRenderer.java` | Entity rendering with awareness icons |
| `Entity.java` | Base class for all interactive objects |
| `SimulationConfig.java` | Configuration knobs for world/castle dimensions |
| `AiContext.java` | Lightweight AI agent interface to world |

---

## Testing & Debugging

### Test Conventions
- Unit tests use JUnit 5 (Jupiter).
- Test classes co-located with main code or in `core/src/test/java/`.
- Run via `./gradlew test` or IDE shortcuts.

### Common Debug Workflows
1. **Layer Navigation Issues**: Check `currentLayer` bounds vs. `totalVerticalBlocks`; ensure side/top slice clamping in `DualViewScreen.clampCamera()`.
2. **Entity Visibility**: Verify `currentLayer` / `sideViewSlice` in rendering calls; toggle X-Ray to confirm entity positioning.
3. **Castle Gen Bugs**: Inspect `WorldContext.buildCastles()` spiral staircase and room carving; visualize with top-down view.
4. **Performance**: Profile render loop; check frame allocation patterns in `GridRenderer` and `UnitRenderer.render()`.

---

## Roadmap Notes

**Immediate Priorities:**
- Interactive castle editing (place/remove blocks).
- Highlight stairs/elevators between layers.
- Animated entity patrol expansion.

**Design Principles:**
- Maintain allocation-free render loops.
- Keep `WorldContext` single-owner of simulation state.
- Sync all views to same underlying grid (no duplication).
- Use lightweight bridges (`AiContext`) to decouple subsystems.

---

## Documentation References
- **GAME_MANUAL.md** – Player controls & mechanics (canonical).
- **repo-map.json** – Machine-readable index of enums, bindings, constants.
- **context-index.json** – Lightweight auto-index for prompt compression.

> **Meta Note:** This is a living design document. Challenge unclear specifications and evolve systems toward modular, testable OOP design (SOLID, composition over inheritance). Audit continuously for optimization opportunities and memory leaks.
