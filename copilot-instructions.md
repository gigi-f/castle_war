## Castle War – Copilot Instructions
UPDATE THIS FILE AFTER EVERY PROMPT!!!
### Core vision
- MVP goal: Minecraft-style voxel world rendered in two simultaneous side-by-side viewports—side view (X/Z) and top-down view (X/Y).
- Full 3D world space using `Vector3`: X=east/west, Y=depth/north-south, Z=elevation/height.
- Both cameras share a synchronized focus point but view it from different angles (orthographic projection).
- Interactive camera controls allow independent pan/zoom/rotate per viewport while maintaining synchronized position.

### Architecture & modules
- `core` module houses all cross-platform game logic (voxel system, rendering, input, screens).
- `desktop` module contains only the LWJGL3 launcher/config; avoid game logic here so other targets can be added later.
- Maintain clean packages:
  - `com.castlewar.voxel` – voxel world system (BlockType, Chunk, VoxelWorld, VoxelMeshBuilder, VoxelRenderer).
  - `com.castlewar.input` – camera controllers for independent viewport manipulation.
  - `com.castlewar.simulation` – world configuration (dimensions, future gameplay parameters).
  - `com.castlewar.screens` – LibGDX `Screen` implementations (DualViewScreen with side-by-side layout).

### Voxel world guidelines
- World coordinates: X = east/west, Y = depth/north-south, Z = elevation/height (standard 3D right-handed system).
- Chunk-based storage: 16×16×16 blocks per chunk, stored in HashMap with packed long key.
- Face culling optimization: only render faces adjacent to non-solid blocks (air or chunk boundaries).
- Flat terrain at z=0 for MVP; future: procedural generation with noise functions.
- Block types: AIR (non-solid), GRASS/DIRT/STONE (terrain), CASTLE_WHITE/CASTLE_BLACK (structures).
- Prefer deterministic math utilities from LibGDX (`MathUtils`) over `java.lang.Math` for consistency.

### Rendering & camera controls
- Side-by-side viewport layout: left=side view (X/Z), right=top-down view (X/Y).
- Each viewport has independent OrthographicCamera with shared focus point (Vector3).
- Custom OpenGL shaders for voxel rendering: vertex shader transforms position, fragment shader applies per-vertex color.
- Mesh-based rendering: LibGDX Mesh class with position (vec3) + color (vec4) attributes.
- Camera controls: WASD pan, scroll zoom, Q/E rotate, right-click drag for mouse pan.
- Blue 4px divider line between viewports rendered with ShapeRenderer (2D overlay).

### Coding style
- Target Java 17. Use LibGDX idioms (e.g., `Vector3.mulAdd`, `MathUtils` helpers) and avoid unnecessary allocations inside render/update loops.
- Keep constructors short; move derived behavior into helper methods where possible.
- When adding new functionality, update tests (when introduced) or provide lightweight sanity harnesses.

### Roadmap reminders
- Near-term: block placement/removal with mouse picking, voxel castles (multi-block structures), procedural terrain.
- Mid-term: unit entities that navigate voxel terrain, lighting/shadows, particle effects, greedy meshing optimization.
- Long-term: networked multiplayer, advanced AI behaviors, saving/loading worlds, texture atlases instead of flat colors.
