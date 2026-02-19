# Rendering TODO (Cake)

This document tracks the rendering roadmap for Cake and the dependency order for implementation.

## Goal

Bring Cake world/entity rendering closer to Quake2 behavior parity while keeping changes incremental and testable.

## Current Reality (Why features are blocked)

- BSP world geometry is currently built as texture-grouped model parts in `cake/core/src/main/kotlin/org/demoth/cake/assets/BspLoader.kt`.
- This is a good bootstrap path, but it drops per-surface/leaf runtime structure needed for:
  - visibility/PVS traversal,
  - lightmap handling,
  - transparent-surface passes,
  - animated texture chains and flowing/warp behavior.

## Master Feature List

- [ ] World surface representation refactor (prerequisite for most BSP features)
- [ ] BSP visibility / PVS / areabits culling
- [ ] Static BSP lightmaps
- [ ] Dynamic lights (muzzle flashes, explosions, effect/entity lights)
- [ ] Transparent BSP surfaces (water, glass windows)
- [ ] Animated BSP surfaces (computer screens/buttons + flowing/warp)
- [ ] Translucent model rendering (smoke and other RF_TRANSLUCENT model cases)

## Coupling Summary

- Hard-blocked by current BSP split-by-texture path:
  - BSP visibility / PVS
  - Static lightmaps
  - Transparent BSP surfaces
  - Animated BSP surfaces
- Partially blocked:
  - Dynamic lights (full parity needs world/lightmap integration; approximate overlays can exist earlier)
- Not blocked:
  - Translucent model rendering for non-BSP models/effects

## Implementation Order (Recommended)

1. [ ] Translucent model rendering (quick win, isolated)
2. [ ] World surface representation refactor
3. [ ] BSP visibility / PVS / areabits culling
4. [ ] Animated BSP surfaces
5. [ ] Transparent BSP surfaces
6. [ ] Static BSP lightmaps (+ lightstyles)
7. [ ] Dynamic lights (full world interaction)

## Phase Details

### 1) Translucent model rendering

- Scope:
  - Ensure `RF_TRANSLUCENT` + entity alpha affect MD2/model-backed drawables, not only sprites/beams.
  - Fix smoke parity (`models/objects/smoke/tris.md2`) and similar transient model effects.
- Done when:
  - Smoke/flash-related translucent models render with expected alpha layering in-game.

### 2) World surface representation refactor (core prerequisite)

- Scope:
  - Keep BSP surface-level records at runtime (face, texinfo, flags, leaf/node ownership).
  - Preserve links needed later for PVS, texture animation, transparent passes, lightmaps.
  - Avoid rebuilding the world as coarse texture-only chunks.
- Done when:
  - Runtime can iterate world by logical surfaces/leaves, not only grouped texture parts.

### 3) BSP visibility / PVS / areabits

- Scope:
  - Use view origin -> leaf -> cluster to build visible set each frame.
  - Apply server-provided `areabits` gate.
  - Cull non-visible world surfaces/leaves.
- Done when:
  - Large same-texture areas no longer render globally; visibility changes with position/doors.

### 4) Animated BSP surfaces

- Scope:
  - Implement texinfo `nexttexinfo` chain animation (screen/button style textures).
  - Implement flowing texture behavior where applicable.
- Progress:
  - [x] Texinfo `nexttexinfo` chain animation in world runtime (`R_TextureAnimation` parity path).
  - [ ] `SURF_FLOWING` UV scrolling behavior.
- Done when:
  - Animated monitor/button textures and flowing surfaces advance over time like legacy behavior.

### 5) Transparent BSP surfaces

- Scope:
  - Add transparent world-surface pass ordering for `SURF_TRANS33`/`SURF_TRANS66`.
  - Include water/window rendering path with correct depth/blend handling.
- Done when:
  - Water and glass-like surfaces render with expected transparency and ordering.

### 6) Static BSP lightmaps (+ lightstyles)

- Scope:
  - Add lightmap data path (UV2/lightmap sampling or equivalent).
  - Respect BSP lightmap offsets/styles and animated lightstyles (`CS_LIGHTS`).
- Done when:
  - World is no longer fullbright; map baked lighting and style changes are visible.

### 7) Dynamic lights

- Scope:
  - Reintroduce gameplay/effects dlights (muzzle, explosions, projectile/entity effects).
  - Integrate with world/model rendering path after lightmap foundation exists.
- Done when:
  - Dynamic light events visibly affect scene lighting in expected locations/colors/intensities.

## Notes for Execution

- Keep each phase shippable with feature flags/cvars where useful.
- Prefer parity-first behavior and then optimize.
- When unsure about behavior details, cross-reference old client code paths:
  - `client/src/main/java/jake2/client/render/fast/Surf.java`
  - `client/src/main/java/jake2/client/render/fast/Light.java`
  - `client/src/main/java/jake2/client/render/fast/Model.java`
  - `client/src/main/java/jake2/client/CL_fx.java`
  - `client/src/main/java/jake2/client/CL_tent.java`
