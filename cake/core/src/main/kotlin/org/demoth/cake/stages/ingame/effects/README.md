# Client Effects Runtime

## Overview
This package owns transient client-side effects produced by server effect messages.

Owned here:
- `MuzzleFlash2Message` behavior (sound + muzzle smoke/flash models).
- `TEMessage` subclass behavior currently decoded by `qcommon`.
- Effect-local asset precache/unload for models, sprites, and sounds not guaranteed by configstrings.
- Runtime lifetime/update/render of temporary visual effects.

Not owned here:
- Replicated entity state reconstruction and interpolation (`ClientEntityManager`).
- Generic world/entity rendering (`Game3dScreen`).
- Replicated `.sp2` world/projectile entity rendering (handled in ingame render path, not this package).
- Dynamic lights and particle systems (not implemented in cake yet).

## Key Types
- `ClientEffectsSystem` - entry point for TE/muzzle effect dispatch.
- `ClientTransientEffect` - lifecycle contract for active transient effects.
- `AnimatedModelEffect` - timed MD2 model effect.
- `AnimatedSpriteEffect` - timed `.sp2` billboard effect.
- `LineBeamEffect` - timed procedural beam placeholder.
- `EffectAssetCatalog` - effect-owned preloaded models/sprites/sounds.
- `MuzzleFlash2Profiles` - grouped behavior mapping for `MZ2_*`.

## Data / Control Flow
```text
Cake.parseServerMessage
  -> Game3dScreen.processMuzzleFlash2Message / processTempEntityMessage
  -> ClientEffectsSystem.processMuzzleFlash2Message / processTempEntityMessage
  -> spawn ClientTransientEffect(s) + play positional sounds
  -> Game3dScreen.render loop: effectsSystem.update -> effectsSystem.render
```

Legacy counterparts:
- `client/CL_fx.ParseMuzzleFlash2`
- `client/CL_tent.ParseTEnt`

## Invariants
- `ClientEntityManager` remains source-of-truth for replicated entity pose; effects only read it.
- All transient effects are owned/disposed by `ClientEffectsSystem`.
- `precache()` must be called before gameplay effect dispatch.
- Effect assets loaded by this package are unloaded by this package only.
- Positional attenuation for both server `SoundMessage` and effects uses `SpatialSoundAttenuation`.
- Behavior coverage is limited to TE subclasses currently parsed in `ServerMessage.parseFromBuffer`.

## Decision Log
Newest first.

### Decision: Use `.sp2` sprite effect for `TE_BFG_EXPLOSION`
- Context: Legacy temp-entity BFG explosion uses `sprites/s_bfg2.sp2`, while cake used MD2 placeholder.
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

### Decision: Handle TE behavior in `ClientEffectsSystem` using effect primitives
- Context: TE messages include multiple visual families (explosions, impact flashes, trails, beams).
- Options considered:
1. Ad-hoc logic in `Game3dScreen`.
2. Centralized TE dispatch in effects subsystem with reusable primitives.
- Chosen Option & Rationale: Option 2 keeps message behavior cohesive and rendering concerns localized.
- Consequences: some TE visuals remain approximations until particles/dlights parity work.
- Status: accepted.
- References: commit `1dbf9201`.
- Definition of Done: parsed TE subclasses (`Point*`, `Trail`, `Splash`, `Beam*`) route through dedicated handlers.

### Decision: Add procedural beam primitive for rail/BFG trail visuals
- Context: rail and BFG laser trail visuals were missing; no sprite/particle runtime path existed.
- Options considered:
1. Wait for full sprite/particle pipeline.
2. Add short-lived procedural line-beam effect now.
- Chosen Option & Rationale: Option 2 restored gameplay readability quickly.
- Consequences: visuals are intentionally approximate and may be replaced later.
- Status: accepted.
- References: commits `aa9db99b`, `172f6cf0`.
- Definition of Done: rail trail renders blue beam + rail sound; BFG laser TE renders green beam.

### Decision: Share one attenuation rule across server sounds and effects
- Context: duplicated attenuation logic diverges easily.
- Options considered:
1. Keep local attenuation implementations.
2. Extract shared utility.
- Chosen Option & Rationale: Option 2 using `SpatialSoundAttenuation`.
- Consequences: attenuation changes affect both paths consistently.
- Status: accepted.
- References: commit `c92d0250`.
- Definition of Done: both `Game3dScreen` and `ClientEffectsSystem` call `SpatialSoundAttenuation.calculate`.

## Quirks & Workarounds
- Transparency is globally incomplete in current cake rendering.
- Why: world/water/translucent parity is not finished yet.
- How to work with it: expect smoke/explosion/sprite blending differences from legacy in edge cases.
- Removal plan: align global translucent pipeline before fine-tuning each effect family.

- `TE_BFG_LASER` and rail trail still use `LineBeamEffect` placeholder instead of canonical particle/sprite legacy visuals.
- Why: canonical particle effect runtime is still incomplete.
- How to work with it: treat current visuals as compatibility placeholders.
- Removal plan: replace with dedicated renderer once particle parity lands.

## How to Extend
1. Add asset paths in `EffectAssetCatalog` for new effect-owned resources.
2. Add or update behavior mapping in `MuzzleFlash2Profiles` for `MZ2_*` additions.
3. Add/update TE subtype branch in `ClientEffectsSystem`.
4. Prefer new `ClientTransientEffect` implementations over monolithic branches.
5. Keep replicated state ownership in `ClientEntityManager`; effects should read but not mutate it.

## Open Questions
- Should TE beams eventually reuse `BeamRenderer` instead of per-effect generated models?
- Should `MuzzleFlash2Profiles` include explicit light-color/radius data before dynamic lights are implemented?
- Should translucent effect ordering be moved to explicit sortable queues when more sprite effects are added?
