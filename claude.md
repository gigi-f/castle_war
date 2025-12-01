# Castle War: Agent Context

## Project Overview

**Castle War** is a LibGDX-based 2D/3D grid simulation featuring procedurally generated castles, multi-perspective rendering, and AI-driven unit behavior. Two castles face each other across a procedurally-generated battlefield, rendered via top-down, side-scroller, isometric, and first-person views using `ShapeRenderer`.

- **Stack:** Java 21, LibGDX 1.12.1, Gradle 8.7
- **Structure:** Multi-module build with `core` (shared logic) and `desktop` (LWJGL3 launcher)
- **Architecture:** Singleton `WorldContext` owns mutable state; multiple windows reference it (only one advances simulation clock)

---

## Key Architecture Concepts

### World Representation
- **GridWorld:** Dynamic 3D array (X × Y × Z) representing the game world
  - X = horizontal, Y = depth, Z = vertical
  - Negative Z queries return underground blocks without expanding the array
  - Block types: `AIR`, `GRASS`, `DIRT`, `STONE`, castles, doors, windows, water, mountains

### Entity System
- **Entity:** Abstract base for all interactive objects (position, velocity, team)
- **Unit:** Extends Entity; has AI agent and rendering metadata
- **Types:** `King`, `Guard` (with GuardType variants), `Assassin`, `Player`, `Streaker`
- **Teams:** `Team.WHITE` or `Team.BLACK`

### Rendering
- **GridRenderer:** Draws blocks as colored rectangles per view mode (shared instance)
- **UnitRenderer:** Renders entities with team colors and awareness icons
- **Views:** Top-down, side-scroller, isometric (pseudo-3D), first-person (perspective camera)
- **Constants:** `BLOCK_SIZE = 10f` pixels; 100%/50% layer opacity; side fade 0.12 per layer

### AI & Behavior
- **AiAgent interface:** `update(entity, delta, aiContext)` called each frame
- **Agents:** `KingAgent`, `GuardAgent`, `AssassinAgent`
- **Steering:** Uses `gdx-ai` steering behaviors for pathfinding/pursuit
- **AiContext:** Lightweight bridge to WorldContext (grid, entity list) to decouple AI from internals

### Procedural Generation
- Castles built from `CastleLayout` blueprints (spiral staircases, branched rooms per level)
- Moats with drawbridges; gates facing battlefield
- Perimeter mountains with sine/cosine elevation
- Battlefield corridor enforced (≥180 blocks); tracked via `battlefieldStartX/EndX`

---

## Build & Execution

```bash
./gradlew build          # Full build
./gradlew desktop:run    # Run immediately
./gradlew clean          # Clean artifacts
./gradlew test           # Run JUnit 5 tests
```

**Working directory:** Desktop launcher operates from `core/assets/` (texture loading)

---

## Input Controls (Canonical Reference: GAME_MANUAL.md)

Core input handling in `DualViewScreen.handleInput()`:
- **Layer Navigation:** `,` / `.` (top-down layer); `[` / `]` (side slice)
- **View Toggling:** `/` (top-down ↔ side scroller, if enabled)
- **Split View:** `m` (toggle; disabled during isometric)
- **Isometric Mode:** `i` (enter/exit); WASD pan, mouse drag rotate, `-`/`=` zoom
- **Entity Selection:** Mouse click (geometry-aware), `TAB` cycle, `ENTER` select
- **Compass HUD:** `c` toggle
- **First-Person:** `F` (spawns Player if absent; captures mouse)
- **X-Ray:** `x` (show entities through occluding layers)

All input state updates happen in `handleInput()` before render; toggles are debounced with `keyDown` tracking.

---

## Configuration

**SimulationConfig** (in `simulation/` package) centralizes:
- World dimensions: `setWorldWidth/Depth/Height()`
- Castle dimensions: `setCastleWidth/Depth/Levels()`
- Spawn counts and positions
- Automatic clamping of castle size to fit world with margins + battlefield gap

**Window Options** (`DualViewScreen.Options`):
- `initialViewMode`: View to start with
- `allowViewToggle`: Enable `/` key
- `allowSplitView`: Enable `m` key
- `updatesSimulation`: Only one window should set this `true`
- `overlayLabel`: HUD prefix ("Primary", "Detached Side", etc.)

Factory methods: `Options.primaryWindow()` and `Options.detachedSideWindow()`

---

## Key Files & Navigation

| File | Lines | Purpose |
|------|-------|---------|
| `CastleWarGame.java` | ~50 | Entry point; bootstraps context and screen |
| `WorldContext.java` | ~300 | Procedural generation, entity list, castle bounds |
| `DualViewScreen.java` | ~1959 | Input, view modes, rendering orchestration |
| `GridWorld.java` | ~200 | 3D block array, block state enum |
| `GridRenderer.java` | ~400 | Block rendering per view mode |
| `UnitRenderer.java` | ~200 | Entity rendering with awareness icons |
| `Entity.java` | ~100 | Base for all interactive objects |
| `SimulationConfig.java` | ~150 | Configuration for world/castle dimensions |
| `AiContext.java` | ~50 | Lightweight AI agent interface to world |

See `repo-map.json` for machine-readable index of enums, bindings, and constants.

---

## Code Patterns & Conventions

### When Adding New Content
- **New Block Type:** Add to `GridWorld.BlockState`, define color in `GridRenderer`, update castle gen if needed
- **New Entity:** Extend `Entity` or `Unit`; create AI agent if behavioral; register with `WorldContext.entities`
- **New Input:** Update `DualViewScreen.handleInput()` and `buildOverlayText()`
- **New View Mode:** Extend `ViewMode` enum; implement camera + projection in `DualViewScreen`

### Performance & Memory
- Allocations per frame should be minimal: reuse `ShapeRenderer`, `SpriteBatch`, entity lists
- Prefer LibGDX math utilities (`MathUtils.clamp()`, `MathUtils.lerp()`, etc.)
- Profile render loop; check frame allocation patterns in `GridRenderer` and `UnitRenderer.render()`

### Java Style
- Target Java 21; use var-args, records, text blocks where appropriate
- Prefer composition over inheritance; follow SOLID principles
- Single-owner pattern: `WorldContext` is the only owner of mutable simulation state

---

## Testing & Debugging

**Test Setup:** JUnit 5 (Jupiter); tests co-located with main code or in `core/src/test/java/`

**Common Debug Workflows:**
1. **Layer Navigation:** Check `currentLayer` bounds; ensure side/top slice clamping in `DualViewScreen.clampCamera()`
2. **Entity Visibility:** Verify `currentLayer` / `sideViewSlice` in rendering; toggle X-Ray to confirm positioning
3. **Castle Gen:** Inspect `WorldContext.buildCastles()` spiral staircase and room carving; visualize with top-down view
4. **Performance:** Run `./gradlew test` to validate changes; profile render loop allocation patterns

---

## Documentation & Progressive Disclosure

For task-specific or project-specific documentation:
- **GAME_MANUAL.md:** Player controls and game mechanics (canonical reference)
- **repo-map.json:** Machine-readable enum, binding, and constant index
- **context-index.json:** Lightweight auto-index for prompt compression

When facing complex tasks, ask Claude to review these files before proceeding if they seem relevant to the task at hand.

---

## Agent Guidelines

- This codebase follows **object-oriented design with SOLID principles** and **single-owner pattern for mutable state**
- Leverage existing patterns (e.g., view/rendering separation, lightweight bridges) when adding features
- Use **git diffs** to validate behavior preservation during refactoring
- Consult `GAME_MANUAL.md` when working with input or user-facing mechanics
- Favor **deterministic tools** (formatters, linters) over manual guidance; set up VS Code tasks or git hooks as needed
