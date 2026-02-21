# Rendering TODO (Cake)

This document tracks the rendering roadmap for Cake and the dependency order for implementation.

## Goal

Bring Cake world/entity rendering closer to Quake2 behavior parity while keeping changes incremental and testable.

## Current Reality (Why features are blocked)

- World BSP model (`model 0`) now has stable per-surface mesh parts plus runtime records (`BspWorldRenderData`) with:
  - face/texinfo identity,
  - leaf -> surface mapping,
  - texinfo metadata for animation chains.
- World visibility is driven by PVS + server `areabits`.
- Texinfo `nexttexinfo` animation now works for:
  - world surfaces (legacy global-time cadence),
  - inline brush models (`*1`, `*2`, ...) via entity frame parity.
- Main remaining rendering gaps are now:
  - inline brush-model (`*1`, `*2`, ...) per-face lightmaps (UV2 sampling),
  - dynamic lights integration,
  - optional parity follow-up: non-lightmapped transparent inline surfaces can still use per-part aggregate tinting for readability.

## Master Feature List

- [x] Translucent model rendering (smoke and other `RF_TRANSLUCENT` model cases)
- [x] World surface representation refactor (prerequisite for most BSP features)
- [x] BSP visibility / PVS / areabits culling
- [x] Animated BSP surfaces (`nexttexinfo` + `SURF_FLOWING`)
- [x] Transparent BSP surfaces (`SURF_TRANS33` / `SURF_TRANS66`)
- [x] Static BSP lightmaps + lightstyles
- [ ] Inline BSP entity lightmaps (per-face UV2 + style slots)
- [ ] Dynamic lights (muzzle flashes, explosions, effect/entity lights)

## Coupling Summary

- No major features remain blocked by the old split-by-texture world representation; that prerequisite is complete.
- Remaining strong dependency:
  - Full brush-model lighting parity (doors/platforms/etc.) depends on inline per-face lightmap support.
  - Full dynamic lights parity depends on the lightmap/lightstyle foundation.

## Implementation Order (Recommended)

1. [ ] Inline BSP entity lightmaps (parity-critical prerequisite for full brush lighting)
2. [ ] Dynamic lights (full world interaction)

## Phase Details

### 1) Translucent model rendering

- Scope:
  - Ensure `RF_TRANSLUCENT` + entity alpha affect MD2/model-backed drawables, not only sprites/beams.
  - Fix smoke parity (`models/objects/smoke/tris.md2`) and similar transient model effects.
- Status:
  - [x] Completed.
- Done when:
  - Smoke/flash-related translucent models render with expected alpha layering in-game.

### 2) World surface representation refactor (core prerequisite)

- Scope:
  - Keep BSP surface-level records at runtime (face, texinfo, flags, leaf/node ownership).
  - Preserve links needed later for PVS, texture animation, transparent passes, lightmaps.
  - Avoid rebuilding the world as coarse texture-only chunks.
- Status:
  - [x] Completed.
- Done when:
  - Runtime can iterate world by logical surfaces/leaves, not only grouped texture parts.

### 3) BSP visibility / PVS / areabits

- Scope:
  - Use view origin -> leaf -> cluster to build visible set each frame.
  - Apply server-provided `areabits` gate.
  - Cull non-visible world surfaces/leaves.
- Status:
  - [x] Completed.
- Done when:
  - Large same-texture areas no longer render globally; visibility changes with position/doors.

### 4) Animated BSP surfaces

- Scope:
  - Implement texinfo `nexttexinfo` chain animation (screen/button style textures).
  - Implement flowing texture behavior where applicable.
- Progress:
  - [x] Texinfo `nexttexinfo` chain animation in world runtime (`R_TextureAnimation` parity path).
  - [x] Texinfo `nexttexinfo` chain animation for inline brush models (`*1`, `*2`, ...) using entity frame parity.
  - [x] `SURF_FLOWING` UV scrolling behavior.
- Done when:
  - Animated monitor/button textures and flowing surfaces advance over time like legacy behavior.

### 5) Transparent BSP surfaces

- Scope:
  - Add transparent world-surface pass ordering for `SURF_TRANS33`/`SURF_TRANS66`.
  - Include water/window rendering path with correct depth/blend handling.
- Progress:
  - [x] Surface flags `SURF_TRANS33` / `SURF_TRANS66` now configure per-surface blending + depth-mask behavior.
- Done when:
  - Water and glass-like surfaces render with expected transparency and ordering.

### 6) Static BSP lightmaps (+ lightstyles)

- Scope:
  - Add lightmap data path (UV2/lightmap sampling or equivalent).
  - Respect BSP lightmap offsets/styles and animated lightstyles (`CS_LIGHTS`).
- Progress:
  - [x] BSP lighting lump is parsed and mapped to per-surface/per-inline-part style metadata.
  - [x] World BSP surfaces now use UV2 + per-surface baked lightmap texture sampling in a dedicated brush-surface shader.
  - [x] Runtime applies `CS_LIGHTS` animated style values (100 ms cadence), including multi-style faces (up to 4 BSP lightstyle slots) via shader slot weighting.
- Done when:
  - World is no longer fullbright; map baked lighting and style changes are visible.

### 7) Inline BSP entity lightmaps

- Scope:
  - Move inline brush models from per-part aggregate lightstyle modulation to per-face UV2 lightmap sampling.
  - Keep legacy exclusions: `SURF_TRANS33`, `SURF_TRANS66`, and `SURF_WARP` do not use lightmaps.
  - Preserve up to 4 lightstyle slots per face and runtime `CS_LIGHTS` style weighting (same world shader model).
- Progress:
  - [x] Inline BSP models are now emitted as per-face mesh parts with stable ids.
  - [x] Eligible inline faces now carry UV2 + per-slot baked lightmap textures and shader style-slot weighting.
  - [ ] Validate broad map coverage for inline entities (doors/platforms/func_*) in gameplay.
- Why this is required:
  - Legacy Quake2 renders inline non-transparent/non-warp faces via the same lightmapped surface path as world (`R_DrawBrushModel` -> `GL_RenderLightmappedPoly`).
  - Yamagi GL3 does the same (`GL3_DrawBrushModel` -> `RenderLightmappedPoly`).
- Done when:
  - Doors/platforms/func_* brush entities show per-texel baked shadows (not flat per-part tint), and target_lightramp updates visibly affect eligible inline faces.

### 8) Dynamic lights

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
  - `../yquake2/src/client/refresh/gl3/gl3_surf.c`
  - `../yquake2/src/client/refresh/gl3/gl3_lightmap.c`
