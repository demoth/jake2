# Rendering TODO (Cake)

This document tracks rendering parity work against legacy Jake2 and Yamagi GL3.

## Goal

Reach practical Quake2 gameplay parity for world/entity/effects lighting and transparency while keeping the code incremental and testable.

## Runtime Controls

- `vid_gamma` (default `1.2`)  
  Legacy/Yamagi reference: `vid_gamma`, consumed as `1.0 / vid_gamma` in GL3 shader path.
- `gl3_intensity` (default `1.5`)  
  Legacy/Yamagi reference: `gl3_intensity`.
- `gl3_overbrightbits` (default `1.3`)  
  Legacy/Yamagi reference: `gl3_overbrightbits` (0 => effective multiplier `1`).
- `r_dlights` (default `1`)  
  Enables/disables dynamic light contribution.
- `r_particles` (default `1`)  
  Enables/disables transient particle rendering.
- `r_md2_legacy_shadedots` (default `0`)  
  Enables strict alias-style MD2 shadedot response (`SHADEDOT_QUANT = 16` yaw buckets).

## Master Feature List

- [x] Translucent model rendering (`RF_TRANSLUCENT`, smoke fade).
- [x] World surface representation refactor (stable per-face records).
- [x] BSP visibility (`PVS` + `areabits`).
- [x] Animated BSP surfaces (`nexttexinfo`, `SURF_FLOWING`).
- [x] Transparent BSP surfaces (`SURF_TRANS33`, `SURF_TRANS66`).
- [x] Static BSP lightmaps + animated lightstyles (`CS_LIGHTS`, up to 4 style slots).
- [x] Inline BSP entity lightmaps (per-face UV2 + style slots, legacy exclusions preserved).
- [x] Dynamic lights (muzzle, temp effects, `EF_*` replicated emitters).
- [x] MD2 lighting (no longer fullbright by default).
- [x] Optional strict MD2 alias-shading parity mode (`r_md2_legacy_shadedots`).
- [x] Particles (transient runtime for TE/effect bursts).

## Implementation Notes (Legacy + Yamagi Cross-Check)

### Inline BSP entity lightmaps

- Legacy path:
  - Jake2: `R_DrawBrushModel -> GL_RenderLightmappedPoly`
  - Yamagi GL3: `GL3_DrawBrushModel -> RenderLightmappedPoly`
- Cake implementation:
  - Inline models emitted as per-face mesh parts.
  - Eligible faces (`!SURF_TRANS33`, `!SURF_TRANS66`, `!SURF_WARP`) carry UV2 + per-style lightmap textures.
  - Runtime style weights driven by `CS_LIGHTS` in same slot model as world.
- Behavior difference:
  - Non-lightmapped inline faces still use diffuse modulation fallback (expected; matches legacy exclusions).

### Dynamic lights

- Legacy path:
  - Jake2/Yamagi: `CL_AllocDlight`, `CL_RunDLights`, `CL_AddDLights`
  - Effect producers in `CL_fx`, `CL_tent`, and entity `EF_*` light branches in `CL_ents`.
  - Yamagi GL3 applies dlights in brush shader path (`gl3_surf.c`, `gl3_shaders.c`).
- Cake implementation:
  - `DynamicLightSystem` with keyed transient lights + one-frame effect lights.
  - 32 ms visibility extension replicated (high-FPS flash preservation).
  - Sources:
    - `MuzzleFlash2Profiles` (+ style-specific overrides),
    - temp entities in `ClientEffectsSystem`,
    - replicated `EF_*` lights in `Game3dScreen`.
  - BSP shader consumes up to 8 strongest lights per frame.

### MD2 lighting

- Legacy path:
  - Jake2 `Mesh.R_DrawAliasModel` and Yamagi `GL3_DrawAliasModel`:
    - point light sample (`R_LightPoint` / `GL3_LightPoint`),
    - `RF_FULLBRIGHT`, `RF_MINLIGHT`, `RF_GLOW`, shell-color overrides.
- Cake implementation:
  - `BspEntityLightSampler` approximates leaf-local baked-lightstyle contribution from BSP data.
  - Adds dynamic-light contribution from `DynamicLightSystem`.
  - Applies `RF_FULLBRIGHT`, `RF_MINLIGHT`, `RF_GLOW`, and shell-color overrides before shader.
  - MD2 decode resolves `lightnormalindex` via `Globals.bytedirs` and stores per-frame normals in a normal VAT.
  - MD2 shader interpolates VAT normals and applies directional term from yaw-derived shade vector.
  - Optional legacy mode quantizes yaw to 16 buckets and uses `dot(currentFrameNormal, shadeVector) + 1`.
  - MD2 shader also multiplies by per-entity light tint and shared gamma/intensity controls.
- Behavior difference:
  - Default Cake mode uses continuous Lambert dot on interpolated normals.
  - Legacy toggle mode matches quantized shadedot behavior more closely, but still differs from legacy immediate/fixed-function submission details.

### Particles

- Legacy path:
  - Jake2: `CL_fx` / `CL_newfx` particle spawners + renderer particle submission.
  - Yamagi: dedicated particle renderer path (`cl_particles.c` + refresh backend).
- Cake implementation:
  - `EffectParticleSystem` adds transient world-space particles (TE impacts/splashes/explosions).
  - Integrated in `ClientEffectsSystem.update/render`.
  - Controlled by `r_particles`.
- Behavior difference:
  - Initial renderer uses lightweight translucent particle primitives, not legacy palette/indexed particle sprites.

### Brightness controls (world + MD2)

- Legacy/Yamagi references:
  - `vid_gamma`, `gl3_intensity`, `gl3_overbrightbits` update shader-side uniforms.
- Cake implementation:
  - BSP and MD2 shaders now apply shared controls.
  - `gl3_overbrightbits <= 0` behaves as multiplier `1`.

## Legacy References Used

- Jake2 legacy client:
  - `client/src/main/java/jake2/client/CL_ents.java`
  - `client/src/main/java/jake2/client/CL_fx.java`
  - `client/src/main/java/jake2/client/CL_tent.java`
  - `client/src/main/java/jake2/client/render/fast/Light.java`
  - `client/src/main/java/jake2/client/render/fast/Mesh.java`
- Yamagi:
  - `../yquake2/src/client/cl_lights.c`
  - `../yquake2/src/client/cl_effects.c`
  - `../yquake2/src/client/refresh/gl3/gl3_surf.c`
  - `../yquake2/src/client/refresh/gl3/gl3_mesh.c`
  - `../yquake2/src/client/refresh/gl3/gl3_shaders.c`
  - `../yquake2/src/client/refresh/gl3/gl3_main.c`

## Remaining Follow-ups (Non-Blocking)

- Particle renderer quality/performance parity (palette-accurate visuals, batching).
- Optional: revisit BSP `GL_NONE` culling once full winding + plane-side parity is guaranteed.
