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
- [x] Particles (transient runtime for TE/effect bursts).
- [x] Blood/spark hit temp-entities (`TE_BLOOD`, `TE_MOREBLOOD`, `TE_GREENBLOOD`, `TE_SPARKS`).
- [x] Some md2 models have unnesessary shading (explosion models should be fullbright)
- [x] Missing `SplashTEMessage` branches: `TE_LASER_SPARKS`, `TE_TUNNEL_SPARKS`.
- [x] Missing `TrailTEMessage` branches: `TE_BUBBLETRAIL`, `TE_BLUEHYPERBLASTER`, `TE_DEBUGTRAIL`.
- [x] Missing `PointTEMessage` branches: `TE_BFG_BIGEXPLOSION`, `TE_TELEPORT_EFFECT`, `TE_DBALL_GOAL`, `TE_WIDOWSPLASH`.
- [x] Railgun trail has a "beam" like temporary implementation
- [x] Missing particle effects for blaster/rocket/grenade trail
- [x] Fluid surfaces (like water) have incorrect lightmap influence (in quake2 water does not have lightmaps)
- [ ] Particle pipeline parity: use palette colors
- [ ] Particle pipeline parity: enforce global particle budget cap (`MAX_PARTICLES` parity target: 4096)
- [x] Particle pipeline parity: batch particle rendering (avoid one draw submission per particle)
- [x] Particle pipeline parity: switch particle primitive from cubes to camera-facing billboards/points
- [ ] Particle pipeline parity: align particle brightness controls with gamma/intensity pipeline
- [ ] entity Shells are not implemented
- [ ] Postprocessing is missing: full screen blend (player_stat_t.blend), under water shader (RDF_UNDERWATER)
- [ ] Optimize number of draw calls per frame (bsp rendering is too expensive now)

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
  - MD2 shader follows legacy-style shadedots response: quantized yaw buckets and `dot(currentFrameNormal, shadeVector) + 1`.
  - MD2 shader also multiplies by per-entity light tint and shared gamma/intensity controls.
- Behavior difference:
  - Cake keeps modern VAT/shader plumbing, but lighting response now targets legacy alias behavior.

### Particles

- Legacy path:
  - Jake2: `CL_fx` / `CL_newfx` particle spawners + renderer particle submission.
  - Yamagi: dedicated particle renderer path (`cl_particles.c` + refresh backend).
- Cake implementation:
  - `EffectParticleSystem` adds transient world-space particles (TE impacts/splashes/explosions).
  - Integrated in `ClientEffectsSystem.update/renderParticles`.
  - Controlled by `r_particles`.
- Behavior difference:
  - Current renderer uses a dedicated batched pass with two modes: point sprites (active) and camera-facing billboards (backend ready, texture-atlas integration pending).

### Particle Pipeline Review Findings (Yamagi vs Cake)

- Limits:
  - Yamagi enforces `MAX_PARTICLES=4096` at scene submission (`V_AddParticle` drops excess).
  - Cake currently has no global hard cap; high-count effects can exceed legacy budgets.
- Rendering cost:
  - Yamagi GL3 streams all particles into one dynamic VBO and issues one `glDrawArrays(GL_POINTS, ...)`.
  - Cake now streams particles through a dedicated dynamic VBO renderer (outside `ModelBatch`) and issues bounded draw submissions by particle blend bucket.
- Transparency ordering:
  - Cake sorts alpha particle buckets back-to-front before draw; additive buckets remain unsorted.
- Materials/state:
  - Cake no longer needs per-particle materials/instances; particle render state is encoded in streamed vertex data.
- Primitive/render style:
  - Yamagi uses point sprites with circular edge fade (and optional square mode).
  - Cake now uses point sprites with circular edge fade.

### Brightness controls (world + MD2)

- Legacy/Yamagi references:
  - `vid_gamma`, `gl3_intensity`, `gl3_overbrightbits` update shader-side uniforms.
- Cake implementation:
  - BSP and MD2 shaders now apply shared controls.
  - `gl3_overbrightbits <= 0` behaves as multiplier `1`.

### Draw-call estimation (current Cake path)

- Assumption:
  - In libGDX `ModelBatch`, one submitted renderable (effectively one enabled `NodePart`) is one draw call.
  - Current BSP path is intentionally per-face/per-part, so batching opportunities are minimal by design.

- BSP rendering pipeline (`world + inline brush entities`):
  - Estimate formula:  
    `dc_bsp ~= visible_world_surfaces + sum(visible_inline_faces_per_instance) + sky_calls`
  - `visible_world_surfaces` is directly driven by PVS/areabits via `NodePart.enabled`.
  - Inline brush models are emitted per-face; each visible face of each visible inline instance adds one call.
  - `sky_calls` is typically `0..1` (sky model rendered separately when present).
  - Practical range:
    - small indoor view: ~`150..600`
    - open/complex view: ~`800..2500+`

- MD2 rendering pipeline:
  - Estimate formula:  
    `dc_md2 ~= visible_md2_instances + md2_effect_instances`
  - Current `Md2Loader` emits one mesh part for MD2, so each visible MD2 entity is typically one draw call.
  - Translucent sorting does not duplicate calls; it only changes which pass renders the same instance.
  - Shell/minlight/glow/fullbright are shader/material state changes, not extra MD2 geometry passes.
  - Practical range:
    - normal combat scene: ~`5..40`
    - very busy scene (many monsters/gibs/effects): ~`50..120+`

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
