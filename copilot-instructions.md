## Castle War – Copilot Instructions
UPDATE THIS FILE AFTER EVERY PROMPT!!!

### Core vision (current build)
- Deliver a readable, layered castle sandbox rendered on a 2D grid. The player can inspect tall castles, moats, and underground layers from both a top-down slice view and a side-scroller profile.
- Focus on clarity and navigation tools (layer stepping, zoom, split view) rather than voxel/3D rendering. All gameplay happens inside `DualViewScreen` using LibGDX’s `ShapeRenderer`.
- Maintain smooth UX: instant key responses, minimal allocations per frame, and obvious visual cues when switching layers or views.

### Key features & controls
- **Views**
  - `/` toggles between solo top-down mode and solo side view.
  - `m` toggles split mode, showing top view on the upper half and side view on the lower half (both always live).
- **Layer navigation**
  - `,` steps downward through Z layers (includes mirrored underground levels equal in depth to the sky height).
  - `.` steps upward until the top of the constructed world (currently 32 blocks above ground).
- **Side view extras** (active in solo or split mode):
  - Arrow keys pan the orthographic camera.
  - `-` / `=` zoom out / in (clamped to [0.5×, 3×]).
  - Side rendering shows the first non-air block along the camera slice and fades distant layers for depth.
- **Moving cube** travels between the two castle gates to demonstrate synchronization between views.

### Architecture overview
- Modules:
  - `core`: all gameplay logic, rendering, and input (`com.castlewar.*`).
  - `desktop`: LWJGL3 launcher only—keep it free of game logic for future targets.
- Important packages/classes:
  - `com.castlewar.CasteWarGame` – configures the world (currently 80×48×32) and boots `DualViewScreen`.
  - `com.castlewar.world.GridWorld` – simple `(x,y,z)` block array with mirrored subterranean layers.
  - `com.castlewar.renderer.GridRenderer` – wraps a shared `ShapeRenderer` and color palette.
  - `com.castlewar.screens.DualViewScreen` – builds the castles, handles input, runs both viewports, and owns the overlay.
  - `com.castlewar.entity.MovingCube` – minimal entity showcasing animation between castles.

### World representation
- Blocks are stored in a fixed array sized by `SimulationConfig` (currently 80×48×32). Negative Z values are synthesized as dirt/stone to represent underground without expanding the array.
- Block states include: `AIR`, `GRASS`, `DIRT`, `STONE` (moats), `CASTLE_WHITE/BLACK`, and matching `_FLOOR` variants for interior levels.
- Two castles are procedurally built per run, now doubled to 14×14 footprints with six interior floors, battlements, roofs, and turrets that reach `CASTLE_TURRET_TOP_LEVEL`.
- A moat/bridge surround is carved automatically, and underground depth mirrors sky height to keep layer navigation symmetric.

### Rendering pipeline
- Everything is rendered with `ShapeRenderer` rectangles; no meshes/shaders are involved.
- Top-down view draws every layer from the deepest visible slice up to the currently selected layer, desaturating lower slices.
- Side view projects the selected Y slice and looks “through” air to show the nearest solid block, fading color by distance.
- Split view simply repurposes the same cameras and `Viewport`s with custom screen bounds (upper half for top view, lower half for side view).
- Overlay text (BitmapFont/SpriteBatch) always summarizes controls for the active mode.

### Coding guidelines
- Target Java 21 (matches Gradle settings). Favor LibGDX helpers such as `MathUtils` for clamps/lerp.
- Keep render/update loops allocation-free; reuse shared `ShapeRenderer` and avoid creating new fonts/batches.
- When modifying controls, update the overlay text and ensure split mode, solo modes, and underground bounds all stay in sync.
- Any new world content should route through `GridWorld` and the existing block enums so both views stay consistent.

### Roadmap / TODO reminders
- Short term: interactive castle editing (placing/removing blocks), highlight stairs/elevators between layers, add more animated entities for depth perception.
- Mid term: texture/gradient improvements, lighting cues for underground, and UI labels that show absolute coordinates.
- Long term: return to voxel/mesh rendering once the 2D UX is perfected; keep current code ready for incremental upgrades (e.g., replacing `ShapeRenderer` with batches of sprites).
