# Client Effects Runtime

## Overview
This package owns transient client-side effects produced by server effect messages.

Owned here:
- `MuzzleFlash2Message` behavior (sound + muzzle smoke/flash models).
- `TEMessage` subclass behavior that is currently decoded by `qcommon`.
- Effect-local asset precache/unload for models and sounds not guaranteed by configstrings.
- Runtime lifetime/update/render of temporary visual effects.

Not owned here:
- Replicated entity state reconstruction and interpolation (`ClientEntityManager`).
- Generic world/entity rendering (`Game3dScreen`).
- Dynamic lights and particle systems (not implemented in cake yet).
- Sprite (`.sp2`) projectile rendering.

## Key Types
- `ClientEffectsSystem` - entry point that receives and executes effect messages.
- `ClientTransientEffect` - lifecycle contract for runtime transient effects.
- `AnimatedModelEffect` - timed MD2 model effect with frame interpolation.
- `LineBeamEffect` - timed procedural beam for trail-like effects.
- `EffectAssetCatalog` - effect-owned preloaded models/sounds.
- `MuzzleFlash2Profiles` - grouped behavior mapping for `MZ2_*` flash types.

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
- All transient effects are owned and disposed by `ClientEffectsSystem`.
- `precache()` must be called before gameplay effect dispatch.
- Effect assets loaded by this package are unloaded by this package only.
- Positional attenuation for both server `SoundMessage` and effects uses `SpatialSoundAttenuation`.
- Behavior coverage is limited to TE subclasses currently parsed in `ServerMessage.parseFromBuffer`.

## Decision Log
Newest first.

### Decision: Keep effects out of `ClientEntityManager`
- Context: explosion models and temp effects could be hosted in entity manager, but that mixes replicated state and transient VFX ownership.
- Options considered:
1. Extend `ClientEntityManager` to own effect entities.
2. Keep `ClientEntityManager` read-only for server state and add dedicated effects subsystem.
- Chosen Option & Rationale: Option 2. It preserves clean boundaries and reduces hacks around server-state lifecycle.
- Consequences: effects require read accessors (`getEntitySoundOrigin`, `getEntityAngles`) but no mutation hooks.
- Status: accepted.
- References: commits `b24eaeeb`, `7eded747`, thread message confirming this boundary.
- Definition of Done: no effect instance is stored in `ClientEntityManager` and effect message handlers delegate to `ClientEffectsSystem`.

### Decision: Implement muzzleflash2 behavior via data profiles
- Context: `MZ2_*` constants are numerous and mostly grouped by identical behavior in legacy client.
- Options considered:
1. Large switch inside runtime handler.
2. Data map (`MuzzleFlash2Profiles`) + small runtime handler.
- Chosen Option & Rationale: Option 2 for maintainability and lower risk when extending/remapping sounds.
- Consequences: dynamic light color/radius is deferred; mapping currently focuses on sound + smoke/flash model behavior.
- Status: accepted.
- References: commit `7eded747`, legacy `CL_fx.ParseMuzzleFlash2`.
- Definition of Done: each handled flash type resolves through `MuzzleFlash2Profiles` and uses entity pose + `M_Flash.monster_flash_offset`.

### Decision: Handle TE behavior in `ClientEffectsSystem` using effect primitives
- Context: TE messages include multiple visual families (explosions, impact flashes, trails, beams).
- Options considered:
1. Ad-hoc logic in `Game3dScreen`.
2. Centralized TE dispatch in effects subsystem with reusable primitives.
- Chosen Option & Rationale: Option 2 keeps message behavior cohesive and rendering concerns localized.
- Consequences: some TE visuals are approximations until particles/dlights/sprites are implemented.
- Status: accepted.
- References: commit `1dbf9201`.
- Definition of Done: parsed TE subclasses (`Point*`, `Trail`, `Splash`, `Beam*`) route through dedicated handlers in `ClientEffectsSystem`.

### Decision: Add procedural beam primitive for rail/BFG trail visuals
- Context: rail and BFG laser trail visuals were missing; no sprite/particle path existed.
- Options considered:
1. Wait for full sprite/particle pipeline.
2. Add short-lived procedural line-beam effect now.
- Chosen Option & Rationale: Option 2 to restore gameplay readability quickly.
- Consequences: visuals are intentionally approximate and may need replacement when canonical effect renderers are added.
- Status: accepted.
- References: commits `aa9db99b`, `172f6cf0`.
- Definition of Done: rail trail renders blue beam + rail sound; BFG laser TE renders visible green beam.

### Decision: Share one attenuation rule across server sounds and effects
- Context: duplicated attenuation logic diverges easily.
- Options considered:
1. Keep local attenuation implementations.
2. Extract shared utility.
- Chosen Option & Rationale: Option 2 using `SpatialSoundAttenuation`.
- Consequences: changes in attenuation behavior now affect both paths consistently.
- Status: accepted.
- References: commit `c92d0250`.
- Definition of Done: both `Game3dScreen` and `ClientEffectsSystem` call `SpatialSoundAttenuation.calculate`.

## Quirks & Workarounds
- Transparency is globally incomplete in current cake rendering.
- Why: world/water/translucent pipeline parity is not finished yet.
- How to work with it: expect smoke/explosion materials to look opaque or off compared with legacy.
- Removal plan: align global translucent material pipeline before tuning individual effects.

- `TE_BFG_LASER` and rail trail use `LineBeamEffect` instead of canonical particle/sprite implementations.
- Why: sprite + particle effect runtime is not available yet.
- How to work with it: treat current visuals as compatibility placeholders.
- Removal plan: replace with dedicated renderer once sprite/particle support lands.

- BFG projectile/explosion now use `.sp2` sprite rendering path.
- Why it changed: sprite model support was added in the runtime render pipeline and effect catalog.
- Remaining gap: particle-heavy legacy companions around BFG visuals are still approximated.

## How to Extend
1. Add asset paths in `EffectAssetCatalog` if new behavior needs non-configstring models/sounds.
2. Add behavior mapping in `MuzzleFlash2Profiles` for `MZ2_*` additions.
3. Add or update TE subtype handler branch in `ClientEffectsSystem`.
4. Prefer new `ClientTransientEffect` implementations over monolithic handler logic.
5. Keep `ClientEntityManager` read-only from this package.

## Open Questions
- Should TE beams eventually reuse the existing `BeamRenderer` infrastructure instead of per-effect generated models?
- Should `MuzzleFlash2Profiles` include explicit light-color/radius data now, even before dynamic lights are implemented?
- For TE styles currently represented by placeholders, which ones are priority for full legacy parity (particles vs sprite paths)?
