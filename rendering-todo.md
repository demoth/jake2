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
- `r_particles` (default `4096`)  
  Global particle budget (`0` disables particles; positive values cap live particles, clamped to `MAX_PARTICLES` parity target).
- `r_bsp_batch_world` (default `0`)  
  Guards the in-progress Q2PRO-style world BSP batching path during parity migration.

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
- [x] Particle pipeline parity: use palette colors
- [x] Particle pipeline parity: enforce global particle budget cap (`MAX_PARTICLES` parity target: 4096)
- [x] Particle pipeline parity: batch particle rendering (avoid one draw submission per particle)
- [x] Particle pipeline parity: switch particle primitive from cubes to camera-facing billboards/points
- [x] Particle pipeline parity: align particle brightness controls with reference path (gamma-only; no particle intensity/overbright)
- [x] Player weapon muzzleflash dynamic lights (`MZ_*`) are missing for several weapons (notably shotgun/machinegun)
- [x] `RF_GLOW` pulse uses server-stepped time instead of continuously advancing client render time
- [x] Replicated `EF_*` dynamic light origins are sampled from non-interpolated entity positions
- [ ] Optional non-legacy enhancement: smooth lightstyle interpolation between 100ms ticks
- [ ] entity Shells are not implemented
- [ ] Postprocessing is missing: full screen blend (player_stat_t.blend), under water shader (RDF_UNDERWATER)
- [ ] Optimize number of draw calls per frame (bsp rendering is too expensive now)

### BSP Draw-Call Optimization Migration (Q2PRO-style)

Current migration target follows Q2PRO brush-world batching ideas:

- `q2pro/src/refresh/world.c` (`GL_DrawWorld`, `GL_WorldNode_r`, `GL_MarkLeaves`)
- `q2pro/src/refresh/tess.c` (`GL_AddSolidFace`, `GL_DrawSolidFaces`, `GL_DrawFace`, `GL_Flush3D`)
- `q2pro/src/refresh/surf.c` (`LM_BeginBuilding`, `LM_BuildSurface`, `GL_UploadLightmaps`)

Planned phases:

- [x] Step 1: runtime guardrail + profiling baseline workflow (`r_bsp_batch_world`).
- [x] Step 2: lightmap atlas pages (replace per-face lightmap textures).
- [x] Step 3: precomputed batched world geometry/ranges/chunking metadata.
- [x] Step 4: dedicated opaque world batch renderer (outside `ModelBatch`).
- [x] Step 5: move world animation/lightstyle/flowing to draw-command state.
- [x] Step 6: translucent world pass parity on batched path.

## Implementation Notes (Legacy + Yamagi Cross-Check)

### Inline BSP entity lightmaps

- Legacy path:
  - Jake2: `R_DrawBrushModel -> GL_RenderLightmappedPoly`
  - Yamagi GL3: `GL3_DrawBrushModel -> RenderLightmappedPoly`
- Cake implementation:
  - Inline models emitted as per-face mesh parts.
  - Eligible faces (`!SURF_TRANS33`, `!SURF_TRANS66`, `!SURF_WARP`) carry UV2 + per-style lightmap atlas slots (`0..3`) packed into shared pages.
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
- Known gap:
  - `Game3dScreen.collectEntityEffectDynamicLights` currently samples `entity.current.origin` (via `getEntityOrigin`) instead of interpolated render origin (`prev/current + lerpfrac`), causing server-tick stepping on moving lights.

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
### Minor Visual Gap Investigation (2026-02-28)

- `MZ_*` muzzleflash dlights (shotgun/machinegun missing):
  - Reference:
    - Jake2 `CL_fx.ParseMuzzleFlash`, Yamagi `cl_effects.c` (`MZ_MACHINEGUN`, `MZ_SHOTGUN`, `MZ_CHAINGUN*`) allocate/update keyed dlight and set color/radius.
    - Jake2/Yamagi `CL_LogoutEffect` (`MZ_LOGIN`, `MZ_LOGOUT`, `MZ_RESPAWN`) particle burst.
  - Cake:
    - implemented in `Game3dScreen.processWeaponSoundMessage` via `spawnWeaponMuzzleFlashLight` with legacy color/radius/lifetime mapping.
    - login/logout/respawn burst implemented via `ClientEffectsSystem.emitLoginLogoutRespawnEvent`.
    - login/logout/respawn muzzle dlight lifetime aligned to short flash parity (`1ms` + `32ms` minimum visibility extension in `DynamicLightSystem`), not `1000ms`.
  - Status:
    - closed on 2026-02-28
  - Difficulty: `S`
  - Coupling: `Low` (mostly `Game3dScreen` + `DynamicLightSystem`; optional helper profile map).

- `RF_GLOW` smooth pulse:
  - Reference:
    - Jake2 `Mesh.java`, Yamagi `gl3_mesh.c`: `scale = 0.1 * sin(r_newrefdef.time * 7)`.
  - Cake:
    - implemented via `interpolatedClientTimeSeconds()` in `Game3dScreen.applyMd2EntityLighting`, matching legacy continuous render-time pulse.
  - Status:
    - closed on 2026-02-28
  - Difficulty: `S`
  - Coupling: `Low` (time source wiring + one lighting function).

- Moving dynamic light interpolation (`EF_*`):
  - Reference:
    - Jake2/Yamagi `CL_AddPacketEntities` compute interpolated `ent.origin` first, then call `V.AddLight(ent.origin, ...)`.
  - Cake:
    - implemented in `Game3dScreen.collectEntityEffectDynamicLights` via `interpolatedEntityRenderOrigin(...)` (same `RF_FRAMELERP`/`RF_BEAM` and lerp rules as packet-entity rendering).
  - Status:
    - closed on 2026-02-28
  - Difficulty: `S-M`
  - Coupling: `Medium` (needs shared/central interpolated origin access to keep parity across trail/light paths).

- Lightstyle smoothing (non-legacy option):
  - Reference:
    - Jake2 `CL_fx.RunLightStyles`, Yamagi `CL_RunLightStyles` are ticked at `cl.time / 100` with no sub-tick interpolation.
  - Cake:
    - `refreshLightStyles` is already legacy-equivalent (`currentTimeMs / 100` + discrete pattern sample).
  - Difficulty: `M`
  - Coupling: `Medium-High` (touches world surface modulation + inline surfaces + entity light sampler expectations; should likely be a toggle because it intentionally diverges from legacy behavior).

### Verified MD2 parity differences (yamagi vs Cake)

- Geometry submission:
  - yamagi streams MD2 draw commands (`glcmds`) at render time (triangle strips/fans).
  - Cake pre-decodes MD2 into static triangle lists (with decode-time winding swap).
- Interpolation stage:
  - yamagi lerps positions/normals on CPU each draw.
  - Cake lerps via VAT in the vertex shader.
- Directional alias shading:
  - yamagi uses quantized yaw + `lightnormalindex` lookup through `anormtab`.
  - Cake uses decoded per-frame normals (`bytedirs`) with quantized shade vector in shader.
  - Cake also clamps directional term to `0.70..1.99` to match yamagi alias range.
- Entity light sample:
  - yamagi `GL3_LightPoint` samples BSP lightmap recursively and adds dlights using `(intensity - distance) / 256`.
  - Cake samples leaf-local baked data and applies an approximate dynamic attenuation model.
- Dark-scene fallback:
  - yamagi can reach black entity lighting.
  - Cake currently enforces a small floor (`0.1`) in the BSP entity light sampler.
- Translucent alias alpha:
  - yamagi scales translucent alias alpha by `0.666`.
  - Cake generic MD2 path uses entity alpha directly (`0.666` is only applied for explosion effect path).
- Brightness pipeline:
  - yamagi alias shading applies `gl3_overbrightbits` in addition to gamma/intensity.
  - Cake MD2 shader currently applies gamma/intensity only.
- Current parity summary:
  - visuals are already close; the remaining noticeable gap is primarily missing MD2 `overbrightbits` handling.

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
  - Yamagi/Q2PRO expose `cl_particles` as on/off toggle, not as a budget-size cvar.
  - Cake uses `r_particles` as runtime budget cap in `EffectParticleSystem.emitBurst`; overflow spawn requests are dropped, and the budget is clamped to `MAX_PARTICLES=4096`.
- Palette mapping status:
  - Cake now maps point-impact particle families to legacy palette ranges (blood/gunshot/sparks/screen-shield/shotgun/electric/heatbeam/chainfist smoke).
  - Impact palette path no longer carries per-effect fallback color branches (aligned with legacy index-driven behavior).
  - Cake now maps explosion-family temp-entity particles to legacy explosion palette range (`0xE0..0xE7`).
  - Cake now maps `TE_SPLASH` via the legacy splash table (`{0x00,0xE0,0xB0,0x50,0xD0,0xE0,0xE8}`).
  - Cake now maps `TE_BLUEHYPERBLASTER` to the legacy blaster palette range (`0xE0..0xE7`).
  - Cake now maps `TE_RAILTRAIL` using legacy palette indices (`0x74..0x7B` spiral and `0x00..0x0F` core).
- Rendering cost:
  - Yamagi GL3 streams all particles into one dynamic VBO and issues one `glDrawArrays(GL_POINTS, ...)`.
  - Cake now streams particles through a dedicated dynamic VBO renderer (outside `ModelBatch`) and issues bounded draw submissions by particle blend bucket.
- Transparency ordering:
  - Cake submits particle buckets unsorted (matching legacy-style particle submission behavior).
- Materials/state:
  - Cake no longer needs per-particle materials/instances; particle render state is encoded in streamed vertex data.
- Primitive/render style:
  - Yamagi uses point sprites with circular edge fade (and optional square mode).
  - Cake now uses point sprites with camera-distance size attenuation and sharper (non-smoothed) circular cutout.
- Brightness controls:
  - Yamagi/Q2PRO particle shading applies gamma correction and does not apply particle intensity/overbright scaling.
  - Cake particle shaders now match this behavior (gamma-only correction).

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

- Particle renderer quality/performance parity follow-ups (further draw/sorting tuning).
- External particle editor/import format support is intentionally deferred until runtime parity/stability goals are complete.
- Optional: revisit BSP `GL_NONE` culling once full winding + plane-side parity is guaranteed.
