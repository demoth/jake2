# Client Effects Runtime

## Responsibilities and Entry Points

`ClientEffectsSystem` owns temporary visual effects and dispatches their positional
sounds and dynamic lights. It reads replicated poses from `ClientEntityManager`;
effect instances are kept separately from server entity state.

- `Game3dScreen` forwards typed `TEMessage` subclasses and `MuzzleFlash2Message`
  (monster muzzle flashes) to this system.
- `MuzzleFlash2Profiles` maps `MZ2_*` values to sounds, smoke/flash behaviour, and
  dynamic-light defaults. Muzzle positions use entity pose and
  `M_Flash.monster_flash_offset`.
- `IngameSoundMessageHandler` calls particle helpers for respawn/teleport events
  and player login/logout/respawn bursts. Player `WeaponSoundMessage` sounds and
  muzzle lights remain in that handler.
- `ReplicatedEntityEffectCollector` requests interpolated projectile trails here,
  while collecting continuous entity `EF_*` lights itself.

[Ingame orchestration](../README.md) owns frame ordering, core world/entity drawing,
and audio playback through `CakeAudioSystem`. Effects spawn transient lights into
`DynamicLightSystem`; their contribution follows `r_dlights`.

## Update and Draw Flow

```text
Server effect messages / entity-event and trail callbacks
  -> ClientEffectsSystem: dispatch effect family
  -> spawn model/sprite/beam instances, particles, lights, and sound requests

WorldPresentationRenderer frame
  -> effectsSystem.update(): simulate particles, advance/expire effect instances
  -> collect replicated trails and lights
  -> draw world and entities, then translucent world
  -> effectsSystem.renderParticles(): dedicated particle pass
  -> effectsSystem.render(): transient models/sprites/beams through ModelBatch
```

`ClientTransientEffect.update(nowMs, deltaSeconds)` returns false when an instance
expires; the system removes and disposes it in that update loop. Update and render
run on the render thread. `AnimatedModelEffect` and `AnimatedSpriteEffect` provide
timed animation; `ExplosionMd2Effect` and `ExplosionSpriteEffect` apply legacy
explosion frame, skin, and alpha rules. Model effects reuse the
[MD2 pipeline](../../../assets/md2/README.md); sprite effects use `Sp2Renderer`.

## Particle Simulation and Rendering

`EffectParticleSystem` routes burst and explicit particle emission through
`GdxParticleRuntime`. A libGDX `ParticleController` and `RegularEmitter` own particle
channels, lifetime progression and removal. Cake supplies a small influencer for
message-specific velocity, three-axis acceleration and alpha endpoints, while
`ParticleRenderer` consumes the resulting channels in the existing world draw pass.

The presentation clock drives controller updates, including demo pause. Existing
particles advance to an emission's timestamp before new particles are added, so
short pulses survive their first frame. Emission copies supplied vectors and colors;
callers can reuse scratch values. Motion uses physical acceleration and stable
constant-acceleration integration rather than reproducing reference particle math.

The runtime is tested directly with synthetic emissions and clocks, without GL,
game data or effect assets. libGDX's [3D particle framework](https://libgdx.com/wiki/graphics/3d/3d-particle-effects)
provides the underlying controller/emitter; the choice of stock particle batches
is deferred until the world-pass depth, blending and color requirements are checked.

`r_particles` supplies the live emission budget: zero or negative values disable
emission and drawing, while positive values limit new spawns to the remaining
budget. Overflow emits are dropped; there is no hard upper clamp. Lowering the
budget does not immediately remove existing particles; they continue to age out.

`ParticleRenderer` uploads dynamic mesh data rather than creating a `ModelInstance`
per particle. It submits up to four unsorted buckets: alpha/additive point sprites,
then alpha/additive billboards, with one draw per nonempty bucket. Depth testing is
on and depth writes are off during this pass.

Gameplay emitters use point sprites with distance-dependent size and a sharp
circular cutout. The billboard backend exists but has no sprite-atlas/frame sampling
integration. Particle colour receives gamma correction without the geometry
intensity/overbright multipliers. Shader sources are
`assets/shaders/particle_point.vert/.frag` and `particle_billboard.vert/.frag`,
loaded through `AssetManager` and the [shared resolver](../../../assets/README.md).

## Visual Behaviour and Current Limits

Particle families select colours from `q2palette.bin`. Useful compatibility anchors:

- Point impacts use the corresponding legacy blood/spark/shotgun palette ranges.
- Explosion-family and blue-hyperblaster particles use `0xE0..0xE7`.
- Splash types select from `{0x00,0xE0,0xB0,0x50,0xD0,0xE0,0xE8}`.
- Rail trails use spiral particles (`0x74..0x7B`) and core particles (`0x00..0x0F`).

Fallback colours keep effects visible when palette data is unavailable.
`TE_BFG_EXPLOSION` uses the translucent `sprites/s_bfg2.sp2` explosion path;
`TE_BFG_LASER` remains a procedural `LineBeamEffect` approximation. Parasite/medic
and grapple beams use repeated segment models. Visual parity varies by effect
family; unsupported styles inside recognised TE message classes can fall through
silently. The decoded expansion message classes with no presentation implementation
are currently ignored explicitly.

Legacy references are `client/CL_fx.ParseMuzzleFlash2`, `CL_fx.LogoutEffect`,
`CL_tent.ParseTEnt`, and `../yquake2/src/client/cl_effects.c`.

## Assets and Lifetime

Call `precache()` before gameplay effect dispatch. `EffectAssetCatalog` preloads
listed MD2 models, SP2 sprites, and sounds that configstrings may not supply.
Files absent from raw resolver lookup are skipped; catalog lookups return null for
unloaded assets. Present files still pass through the normal loaders.

The catalog records paths visited during precache, reuses already loaded assets,
and unloads its tracked paths during disposal. This tracking is separate from
configuration asset ownership.

`ClientEffectsSystem.dispose()` disposes remaining transient instances, particle
meshes/shaders, its sprite renderer, and the asset catalog. Shared model/sprite
assets belong to the catalog/`AssetManager`, rather than each effect instance.
