# Castle War Simulation

A LibGDX-powered voxel world simulation with dual-view rendering. The display is split into two side-by-side viewports: the left shows a side view (x/z axes) while the right shows an overhead tactical map (x/y axes). Both cameras look at the same synchronized focal point in 3D space but from different angles.

## Features

- 🧊 **Voxel-based world** – Minecraft-style block granularity with chunk-based storage (16×16×16) for efficient rendering
- 🎥 **Dual-view rendering** – side-by-side viewports with independent camera controls sharing a synchronized focus point
- 🕹️ **Interactive cameras** – WASD to pan, mouse scroll to zoom, Q/E to rotate, right-click drag for quick pan
- ⚡ **Optimized rendering** – Face culling (only visible faces rendered), mesh-based approach with custom shaders
- 🧱 **Extensible scaffolding** – clean Gradle multi-module layout (`core` + `desktop`) ready for additional platforms

## Project layout

```
castle_war/
├── core/                  # Shared game + simulation code
│   ├── assets/            # LibGDX assets root (currently empty)
│   └── src/main/java/com/castlewar
│       ├── voxel/         # Voxel world system (chunks, blocks, renderer)
│       ├── input/         # Camera controllers for each viewport
│       ├── screens/       # DualViewScreen with side-by-side layout
│       └── simulation/    # World configuration
├── desktop/               # LWJGL3 launcher module
├── gradle/                # Wrapper files (Gradle 8.7)
├── build.gradle           # Root Gradle settings
└── README.md
```

## Prerequisites

- JDK 17+
- (Optional) A GPU capable of running OpenGL 3.2+ (standard PC hardware works fine)

Everything else (Gradle 8.7 wrapper + dependencies) is included.

## Run it

```bash
./gradlew desktop:run
```

When the window appears:

- **Left viewport** shows the **side view** (x/z axes) - you'll see the flat voxel terrain from the side
- **Right viewport** shows the **top-down view** (x/y axes) - overhead view of the same terrain
- **Blue divider line** separates the two viewports
- Both cameras share a synchronized focus point in 3D space

### Controls

- **WASD** - Pan the camera (moves the shared focus point)
- **Mouse Scroll** - Zoom in/out
- **Q/E** - Rotate around the focus point
- **Right-click + Drag** - Quick pan with mouse

## Architecture

### Voxel System
- **BlockType** enum defines 6 block types: AIR, GRASS, DIRT, STONE, CASTLE_WHITE, CASTLE_BLACK
- **Chunk** class stores 16×16×16 blocks with dirty flag for efficient mesh regeneration
- **VoxelWorld** manages chunks via HashMap with flat terrain generation
- **VoxelMeshBuilder** implements face culling (only renders visible block faces)
- **VoxelRenderer** uses OpenGL meshes with custom vertex/fragment shaders

### Rendering Pipeline
1. Each chunk tracks if it's "dirty" (modified since last mesh build)
2. On render, dirty chunks rebuild their mesh with face culling optimization
3. Adjacent solid blocks don't render interior faces (major performance gain)
4. Each viewport renders the same world with its own camera perspective
5. Depth testing enabled for proper 3D occlusion

## Next ideas

- Add block placement/removal with mouse picking
- Implement castles as voxel structures (multi-block constructions)
- Introduce unit entities that navigate the voxel terrain
- Add procedural terrain generation (hills, valleys, noise-based)
- Implement lighting system with ambient occlusion
- Add particle effects for block placement/destruction

Have fun building voxel worlds! 🧊�
