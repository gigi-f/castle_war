## Castle War – Copilot Instructions
UPDATE THIS FILE AFTER EVERY PROMPT!!!

### Core vision (current build)
- Deliver a readable, layered castle sandbox rendered on a 2D grid. The player can inspect tall castles, moats, and underground layers from both a top-down slice view and a side-scroller profile.
- Focus on clarity and navigation tools (layer stepping, zoom, split view) rather than voxel/3D rendering. All gameplay happens inside `DualViewScreen` using LibGDX’s `ShapeRenderer`.
- Maintain smooth UX: instant key responses, minimal allocations per frame, and obvious visual cues when switching layers or views.

### Key features & controls
- **Windows & layouts**
  - Desktop launcher now opens **two independent OS windows**: the primary top-focused window plus a side-view companion. Drag, resize, or minimize each separately.
  - The primary window boots with split view already enabled and the side camera fully zoomed out for maximum context—tap `m` if you want to collapse back to a single panel.
  - `/` still swaps perspectives, but only in windows configured to allow it (default: the primary window). The detached side window is locked to the side view.
  - `m` toggles the in-window split view (top on the left, side on the right, both full height). Detached windows ignore this key.
  - `i` toggles a **full-screen isometric view** that shares the same layer/slice controls; split view temporarily disables while this mode is active.
- **Layer navigation**
  - `,` / `.` adjust the top-down layer independently.
  - `[` / `]` adjust the side-view slice independently, even while the top view stays on a different layer.
- **Side view extras** (active in solo, split, or detached mode):
  - Arrow keys pan the orthographic camera.
  - `-` / `=` zoom out / in (clamped to [0.5×, 3×]).
  - Side rendering shows the first non-air block along the camera slice, fades distant layers for depth, and overlays a thin blue horizontal guide matching the currently selected top-down layer.
- **Isometric view** (toggle with `i`)
  - Flipped 180° to mirror the top view and now renders **every** vertical layer simultaneously, keeping the focused layer at full opacity while all others sit at 50%.
  - Shares the comma/period and bracket controls for layer + slice navigation, adds WASD panning plus `-`/`=` zoom controls while active, renders bright gold/brown stair highlights, and now shows the red moving cube gliding between castles.
- **Top view cues**
  - The top-down renderer now includes a matching horizontal blue line that marks the Y-slice currently displayed in the side view, keeping both panels synchronized visually.
- **HUD compass**
  - A translucent compass rose now lives in the top-right corner of every window to keep cardinal directions obvious. Press `c` at any time to hide or reveal it—this works in split, solo, and isometric views alike.
- **Moving cube** travels between the two castle gates to demonstrate synchronization between views.

### Architecture overview
- Modules:
  - `core`: all gameplay logic, rendering, and input (`com.castlewar.*`).
  - `desktop`: LWJGL3 launcher + multi-window bootstrap; backend specifics live here.
- Important packages/classes:
  - `com.castlewar.CasteWarGame` – wires a `DualViewScreen` instance to a `WorldContext` with a chosen `Options` profile (primary vs detached windows).
  - `com.castlewar.world.GridWorld` – simple `(x,y,z)` block array with mirrored subterranean layers.
  - `com.castlewar.renderer.GridRenderer` – wraps a shared `ShapeRenderer` and color palette.
  - `com.castlewar.simulation.WorldContext` – single owner of the grid, castle generation, and shared entities (moving cube). Multiple windows reference it to stay in sync.
  - `com.castlewar.screens.DualViewScreen` – consumes `WorldContext`, handles input, runs both viewports, and owns the overlay.
  - `com.castlewar.entity.MovingCube` – minimal entity showcasing animation between castles.

### World representation
- Blocks are stored in a dynamically-sized array controlled by `SimulationConfig` (defaults: 80×48×32). Change `setWorldWidth/Depth/Height()` before creating `WorldContext` to adjust terrain size—cameras and rendering adapt automatically. Negative Z values are synthesized as dirt/stone to represent underground without expanding the array.
- Block states include: `AIR`, `GRASS`, `DIRT`, `STONE` (moats), `CASTLE_WHITE/BLACK`, matching `_FLOOR` variants for interior levels, plus dedicated `_STAIR` blocks that receive special rendering passes.
- Two castles are procedurally built per run from `CastleLayout` blueprints computed dynamically from `SimulationConfig`. Use `setCastleWidth/Depth/Levels()` to control footprint and vertical scale—`WorldContext.createCastleLayouts()` clamps these to fit the world, respecting edge margins and battlefield spacing. Default footprints: 30×20 with 6 interior levels, enclosing a grass courtyard left open to the sky even under the battlements and roof layers.
- Castles now spawn against wider map edges, leaving a dedicated battlefield corridor of at least ~180 blocks between their gates. `WorldContext` tracks this gap (`battlefieldStartX / battlefieldEndX`) for future encounters or unit placement.
- Each keep now mirrors a believable floor plan: a central gate-fed corridor, a terraced courtyard, a diagonal grand stair that climbs from the front gate up to the upper stories, and at least four interior rooms (two on the first floor, two on the second) arranged north/south and east/west so every space is reachable from the main corridor.
- Castle generation is now blueprint-driven (`WorldContext.getCastleLayouts()`), so future procedural variants can tweak widths, heights, margins, stair geometry, or even inject completely different layouts without touching the rendering code.
- A moat/bridge surround is carved automatically, and underground depth mirrors sky height to keep layer navigation symmetric.

### Rendering pipeline
- Everything is rendered with `ShapeRenderer` rectangles; no meshes/shaders are involved.
- Top-down view draws every layer from the deepest visible slice up to the currently selected layer, desaturating lower slices.
- Side view projects the selected Y slice and looks “through” air to show the nearest solid block, fading color by distance.
- Split view now shares the window **side-by-side** (vertical divider) so each panel keeps full height. Detached windows simply render their single perspective.
- Overlay text (BitmapFont/SpriteBatch) always summarizes controls for the active mode and auto-hides instructions for disabled controls (e.g., detached windows without `'m'`).

### Coding guidelines
- Target Java 21 (matches Gradle settings). Favor LibGDX helpers such as `MathUtils` for clamps/lerp.
- Keep render/update loops allocation-free; reuse shared `ShapeRenderer` and avoid creating new fonts/batches.
- When modifying controls, update the overlay text and ensure split mode, solo modes, and underground bounds all stay in sync.
- Any new world content should route through `GridWorld` and the existing block enums so both views stay consistent.
- Preserve `WorldContext` as the single owner of mutable simulation state. Only one window should advance the simulation clock (controlled via `DualViewScreen.Options.updatesSimulation`) to prevent double updates.

### Roadmap / TODO reminders
- Short term: interactive castle editing (placing/removing blocks), highlight stairs/elevators between layers, add more animated entities for depth perception.
- Mid term: texture/gradient improvements, lighting cues for underground, and UI labels that show absolute coordinates.
- Long term: return to voxel/mesh rendering once the 2D UX is perfected; keep current code ready for incremental upgrades (e.g., replacing `ShapeRenderer` with batches of sprites).
