# Castle War Game Manual

## Overview
Castle War is a multi-perspective 2.5D block simulation rendered with LibGDX. You inspect two procedurally generated castles, their battlefield, and underground layers using interchangeable view modes: Top-Down, Side Scroller, Isometric, and First-Person. This manual documents player controls and core mechanics.

## View Modes
1. Top-Down: Orthographic X/Y slice stack. Navigate vertical layers to reveal interiors and underground.
2. Side Scroller: Orthographic X/Z strip showing the nearest solid block along a selectable Y slice (depth). Fades distant blocks.
3. Split View: Simultaneous Top + Side panels (left/right) for synchronized spatial awareness.
4. Isometric: Rotatable pseudo-3D projection of all vertical layers with opacity falloff (focused layer full, others 50%).
5. First-Person (experimental): Free-fall spawn for Player entity; currently limited interaction.

## Controls (All Views Unless Noted)
- , (comma): Move active top-down layer down (includes underground depths).
- . (period): Move active top-down layer up.
- [ : Decrement side slice (Y) / iso side slice.
- ] : Increment side slice (Y) / iso side slice.
- / : Toggle between Top-Down and Side Scroller (if allowed in window options).
- m : Toggle split view (disabled while Isometric active).
- i : Enter/exit Isometric (stores previous mode & split state; disables split during iso).
- - : Zoom out (Side or Isometric depending on active view).
- = : Zoom in (Side or Isometric).
- Arrow Keys: Pan side camera (X/Z plane) in Side Scroller panel.
- WASD: Pan iso camera when Isometric active.
- Mouse Left (hold & drag in Iso): Rotate isometric projection horizontally.
- Mouse Left (click): Attempt unit selection in current view space.
- c : Toggle compass HUD.
- x : Toggle X-Ray mode (shows entities through occluding layers).
- F : Toggle First-Person view (captures/releases mouse cursor). Spawns Player if absent.
- TAB: Cycle focused unit (for keyboard accessibility).
- ENTER: Select currently focused unit.

## Mode-Specific Behavior
Top-Down: Renders all layers from underground start up to currentLayer with desaturation on lower slices. Blue horizontal guide indicates current side slice.
Side Scroller: Projects slice sideViewSlice, finds nearest non-air block per column; applies depth-based fading. Top layer guide (horizontal line) marks currentLayer.
Split View: Left = Top, Right = Side. Keys affect their respective panels; selection depends on click region.
Isometric: Full-stack rendering; rotation with mouse drag; WASD pan; -/= zoom; shares layer & slice navigation; entities rendered with opacity rules; awareness icons displayed.
First-Person: Perspective camera; Player updated each frame; other UI minimal.

## Entities & Mechanics
Teams: WHITE and BLACK.
Spawn Summary:
- Kings: Each castle center (z=1). Provide focal point & entourage guards.
- Guards: ENTourage (follow king) + PATROL (battlements). Patrol guards start at battlementLevel.
- Assassins: Spawn near battlefield edges (one per team); target enemy king and infiltration gate vector; scan for guards; can be selected.
- Player: Spawn when entering First-Person (above world then falls). Limited interaction currently.
Entity Visibility: Controlled by currentLayer (top view) and sideViewSlice (side view) unless X-Ray enabled.
Focus & Selection: TAB cycles focus; ENTER selects; mouse click selects nearest entity under cursor per view geometry.
Awareness Icons: Rendered above units indicating scanning/combat states (implementation in UnitRenderer).

## World Generation
GridWorld: 3D array width × depth × height; underground simulated for negative Z queries as dirt/stone.
Castles: Built from CastleLayout blueprints (dimensions, stair geometry, battlements). Battlefield gap enforced (>= MIN_BATTLEFIELD_WIDTH). Gates face battlefield; drawbridges span moats.
Vertical Structure: interiorLevels scaled; battlementLevel = interiorLevels + 1; roofLevel = battlementLevel + 1; turretTopLevel = roofLevel + 2.
Mountains: Perimeter mountains carved using sine/cosine height modulation; block type MOUNTAIN_ROCK.
Moats & Drawbridges: Procedural water ring with noise; bridges placed at gate.
Stair Systems: Spiral shafts in corner towers; continuous multi-floor ramp with headroom clearance.
Rooms & Courtyards: Branched rooms carved around central shaft per floor; courtyard grass clearing on ground layer.

## Rendering Details
Block Size: 10 px square.
Layer Opacity: Focused layer 100%; lower layers 50% (top-down & iso).
Side Depth Fade: Each block behind slice dims (fade factor 0.12 capped at ~0.6).
Isometric Projection: Rotation accumulates; tile width = blockSize; tile height = blockSize * 0.5; vertical block height = blockSize * 0.6.
Compass HUD: Suppressed in solo side view (ambiguous North); toggle via c.
Overlay Bubble: Dynamic instruction text updated per mode & window options.

## Accessibility & UX
- Keyboard-only interaction: TAB / ENTER enable navigation of entity list.
- X-Ray aids tracking units when occluded.
- Compass ensures spatial orientation after rotations.
- Split view provides immediate spatial correlation between layer and slice guides.

## Window Options (DualViewScreen.Options)
- allowViewToggle: Enables / key.
- allowSplitView: Enables m key.
- updatesSimulation: Governs which window advances simulation tick.
- overlayLabel: Label prefix for HUD.

## Known Limitations
- First-Person lacks collision & full controls.
- Some castle interior connectivity (spiral landings vs branched floors) simplified.
- No block editing yet.

## Future Mechanics (Roadmap Highlights)
- Block placement/removal & castle editing.
- Additional animated entities and patrol AI expansion.
- Lighting & underground visual enhancements.
- Coordinate readouts and selection highlighting.

Enjoy exploring the layered battlefield.
