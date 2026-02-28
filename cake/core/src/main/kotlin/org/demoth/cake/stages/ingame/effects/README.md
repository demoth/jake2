# Client Effects Runtime

## Overview
This package owns transient client-side visual/audio effects produced by server effect messages.

Owned here:
- `MuzzleFlash2Message` behavior (sound + local model/smoke + dynamic light).
- `WeaponSoundMessage` special burst helper for `MZ_LOGIN`/`MZ_LOGOUT`/`MZ_RESPAWN` particle effect parity.
- `TEMessage` subclass behavior decoded by `qcommon`.
- Effect-local asset precache/unload for models, sprites, and sounds not guaranteed by configstrings.
- Runtime lifetime/update/render of temporary visual effects.
- Temp-effect dynamic light spawning and transient particle bursts.

Not owned here:
- Replicated entity state reconstruction/interpolation (`ClientEntityManager`).
- Core world/entity drawing (`Game3dScreen`).
- Player weapon muzzleflash (`WeaponSoundMessage` / `MZ_*`) dynamic lights (owned by `Game3dScreen`).
- Continuous replicated entity `EF_*` lights (owned by `Game3dScreen.collectEntityEffectDynamicLights`).

## Key Types
- `ClientEffectsSystem` - entry point for TE/muzzle effect dispatch.
- `ClientTransientEffect` - lifecycle contract for active transient effects.
- `AnimatedModelEffect` - timed MD2 model effect.
- `AnimatedSpriteEffect` - timed `.sp2` billboard effect.
- `LineBeamEffect` - timed procedural beam placeholder.
- `EffectParticleSystem` - transient particle runtime for impact/explosion bursts.
- `EffectAssetCatalog` - effect-owned preloaded models/sprites/sounds.
- `MuzzleFlash2Profiles` - grouped behavior mapping for `MZ2_*`, including per-profile dynamic light defaults.

## Data / Control Flow
```text
Cake.parseServerMessage
  -> Game3dScreen.processWeaponSoundMessage / processMuzzleFlash2Message / processTempEntityMessage
  -> ClientEffectsSystem handlers
  -> spawn ClientTransientEffect(s) + particles + transient dlights + positional sounds
  -> Game3dScreen.render loop: effectsSystem.update -> effectsSystem.renderParticles -> effectsSystem.render
```

Legacy counterparts:
- `client/CL_fx.ParseMuzzleFlash2`
- `client/CL_fx.LogoutEffect`
- `client/CL_tent.ParseTEnt`
- `../yquake2/src/client/cl_effects.c`

## Invariants
- `ClientEntityManager` remains source-of-truth for replicated entity pose; effects only read it.
- All transient effects are owned/disposed by `ClientEffectsSystem`.
- `precache()` must be called before gameplay effect dispatch.
- Effect assets loaded by this package are unloaded by this package only.
- Positional attenuation for both server `SoundMessage` and effects uses `SpatialSoundAttenuation`.
- Dynamic-light output obeys runtime toggle (`r_dlights`).
- Particle runtime obeys `r_particles` live-budget cvar (`0` disables; budget clamped to `MAX_PARTICLES=4096`) and drops overflow emits.
- Point-impact TE particle families (`TE_BLOOD`, `TE_GUNSHOT`, spark/screen/shield/shotgun/electric/heatbeam variants) use legacy palette index ranges.
- Explosion-family particle branches (`TE_EXPLOSION*`, rocket/grenade/plasma/plain explosion temp entities) use legacy explosion palette range (`0xE0..0xE7`).
- `TE_BLUEHYPERBLASTER` uses the legacy blaster palette range (`0xE0..0xE7`) like `CL_BlasterParticles`.
- `TE_SPLASH` uses the legacy splash palette table mapping (`{0x00,0xE0,0xB0,0x50,0xD0,0xE0,0xE8}`).

## Decision Log
Newest first.

### Decision: Use a dedicated particle renderer pass and skip temporary batching fixes
- Context: per-particle `ModelBatch` submissions caused extreme draw-call growth in high-count effects (rail, explosions).
- Options considered:
1. Temporary stopgaps (`one material per effect instance`, ad-hoc cap-first strategy).
2. Move directly to a dedicated dynamic-VBO particle renderer.
- Chosen Option & Rationale: Option 2. It directly solves draw-call scaling and keeps architecture aligned with future sprite particles.
- Consequences:
  - particle rendering is split from model/sprite effects (`renderParticles` vs `render`),
  - simulation and rendering responsibilities are separated (`EffectParticleSystem` vs `ParticleRenderer`),
  - no dependency on per-particle `ModelInstance`/`Material` state.
- Status: accepted.
- References: commits `c1c3c3bc`, `24bcc6a1`, `bc3d1ec6`.
- Definition of Done: particle draw submissions scale with bucket count, not with live particle count.

### Decision: Tune particle visuals for vanilla-like sharpness and distance behavior
- Context: initial dedicated renderer produced oversized and overly smoothed particles with weak distance attenuation.
- Options considered:
1. Keep soft edge fade and constant point size.
2. Use distance-attenuated point sizing with sharp circular cutout.
- Chosen Option & Rationale: Option 2 for closer Quake/Yamagi visual behavior and gameplay readability.
- Consequences:
  - point shader uses camera-distance size attenuation,
  - extra edge smoothing was removed; only circular cutout remains,
  - alpha sorting retained for translucent buckets.
- Status: accepted.
- References: commit `34285409`, `../yquake2/src/client/refresh/gl3/gl3_main.c`, `../yquake2/src/client/refresh/gl3/gl3_shaders.c`.
- Definition of Done: particles become smaller with distance and no longer look over-smoothed.

### Decision: Keep external particle format support out of current scope
- Context: future sprite-particle support is desired, but immediate priority is parity/performance of runtime rendering.
- Options considered:
1. Implement external editor/import format now.
2. Keep runtime format internal for now; defer external support.
- Chosen Option & Rationale: Option 2 to avoid expanding scope before core runtime/parity goals stabilize.
- Consequences:
  - renderer/runtime interfaces remain format-agnostic,
  - sprite backend stays ready for atlas integration without committing to external tooling yet.
- Status: accepted.
- Definition of Done: subsystem remains extensible, but no external particle format is required for current development.

### Decision: Add local transient particle runtime for TE impact/explosion feedback
- Context: many legacy TE branches depended on particles, but Cake had no particle output.
- Options considered:
1. Keep model/sprite-only placeholders.
2. Add a lightweight particle bridge now.
- Chosen Option & Rationale: Option 2. Introduce `EffectParticleSystem` for immediate gameplay readability.
- Consequences: visuals are approximate versus full legacy palette particle renderer.
- Status: accepted.
- References: `client/CL_tent.java`, `client/CL_fx.java`, `../yquake2/src/client/cl_effects.c`.
- Definition of Done: gunshot/spark/splash/explosion TE paths emit visible transient particles when `r_particles>0`.

### Decision: Spawn transient dynamic lights from effect message handlers
- Context: muzzle/explosion effects should light nearby geometry/models in real time.
- Options considered:
1. Handle dynamic lights only from replicated `EF_*` entity flags.
2. Also spawn dynamic lights directly from TE/muzzle handlers.
- Chosen Option & Rationale: Option 2 to match legacy timing and event ownership.
- Consequences: dynamic-light source logic is split between this package (message-driven) and `Game3dScreen` (`EF_*` continuous).
- Status: accepted.
- References: `client/CL_fx.java`, `client/CL_tent.java`, `../yquake2/src/client/cl_lights.c`.
- Definition of Done: muzzle flashes and TE explosions produce visible transient lights with expected color/radius families.

### Decision: Use `.sp2` sprite effect for `TE_BFG_EXPLOSION`
- Context: legacy temp-entity BFG explosion uses `sprites/s_bfg2.sp2`, while Cake used MD2 placeholder.
- Options considered:
1. Keep MD2 placeholder.
2. Add dedicated `.sp2` effect path.
- Chosen Option & Rationale: Option 2 for legacy visual parity and consistent sprite pipeline reuse.
- Consequences: effect runtime now depends on camera-facing sprite renderer and `.sp2` effect asset catalog support.
- Status: accepted.
- References: commit `532590f7`, `client/CL_tent.java` `TE_BFG_EXPLOSION`.
- Definition of Done: `TE_BFG_EXPLOSION` renders from `sprites/s_bfg2.sp2` with translucent alpha behavior.

### Decision: Keep effects out of `ClientEntityManager`
- Context: explosion models and temp effects could be hosted in entity manager, but that mixes replicated state and transient VFX ownership.
- Options considered:
1. Extend `ClientEntityManager` to own effect entities.
2. Keep `ClientEntityManager` read-only for server state and add dedicated effects subsystem.
- Chosen Option & Rationale: Option 2. It preserves clean boundaries and reduces hacks around server-state lifecycle.
- Consequences: effects require read accessors (`getEntitySoundOrigin`, `getEntityAngles`) but no mutation hooks.
- Status: accepted.
- References: commits `b24eaeeb`, `7eded747`.
- Definition of Done: no effect instance is stored in `ClientEntityManager` and effect handlers delegate to `ClientEffectsSystem`.

## Quirks & Workarounds
- `TE_BFG_LASER` and rail trail still use `LineBeamEffect` placeholders.
- Why: canonical beam/particle parity path is still incomplete.
- How to work with it: treat these visuals as readability-first approximations.
- Removal plan: replace with dedicated beam/particle implementation once parity path lands.

- Particle pass uses a dedicated shader/VBO path with point-sprite and billboard backends.
- Point sprites currently drive gameplay effects; billboard backend is maintained for sprite-particle extension.
- Alpha buckets are depth-sorted per frame; additive buckets are submitted unsorted.
- Why: avoid per-particle ModelBatch submissions and keep draw-call count bounded.
- How to work with it: tune burst count/speed/alpha and world-space size ranges in emitters.
- Removal plan: wire billboard backend to atlas/frame sampling once sprite-particle content is introduced.

## How to Extend
1. Add asset paths in `EffectAssetCatalog` for new effect-owned resources.
2. Add/update behavior mapping in `MuzzleFlash2Profiles` for `MZ2_*` additions.
3. Add/update TE subtype branches in `ClientEffectsSystem`.
4. Prefer extending `EffectParticleSystem` for particle-heavy effects instead of spawning many model effects.
5. Keep replicated state ownership in `ClientEntityManager`; effects should read but not mutate it.

## Open Questions
- Which effect families should migrate to billboard sprite mode first once atlas sampling is introduced?
