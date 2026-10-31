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
  -> collect replicated trails
  -> reset frame lights, collect explosion and replicated-entity lights
  -> draw world and entities, then translucent world
  -> effectsSystem.renderParticles(): dedicated particle pass
  -> effectsSystem.render(): transient models/sprites/beams through ModelBatch
  -> effectsSystem.renderLasers(): palette lasers through the shared BeamRenderer
```

`ClientTransientEffect.update(nowMs, deltaSeconds)` returns false when an instance
expires; the system removes and disposes it in that update loop. Update and render
run on the render thread. `ExplosionAnimation` uses libGDX `Animation` for shared explosion
frame, skin, and alpha rules. `ExplosionMd2Effect` and `ExplosionSpriteEffect`
apply that state to their visuals. Model effects reuse the
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
provides the underlying controller/emitter. The stock `PointSpriteParticleBatch`
and `BillboardParticleBatch` support configurable depth/blending, but their default
shader does not implement Cake's gamma correction, circular cutout or size rule.
Direct replacement would also require splitting the mixed mode/blend channels into
separate batch inputs. The existing drawing adapter retains these engine-specific
requirements; simulation, storage and lifetime remain libGDX-owned. GPU resources
are created on the first particle draw, so dispatch/update/teardown require no GL.

`r_particles` supplies the live emission budget: zero or negative values disable
emission and drawing, while positive values limit new spawns to the remaining
validated budget up to `MAX_PARTICLES`. Overflow emits are dropped. Lowering the
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

## Persistent Beams and Lightning

`BeamEffects` owns cable/grapple and lightning state for the screen. Cable messages
refresh by source entity, so parasite/medic/grapple updates replace their previous
beam. Lightning identifies a source/destination pair, allowing multiple targets
from the same source. Each message refreshes a 200 ms lifetime; expired slots are
reclaimed before admitting new beams. State is bounded to 32 beams and geometry to
128 model segments per beam.

After the player view is updated, beam geometry is rebuilt from current camera
state. Beams originating from the viewed player follow that camera, with the view
height adjustment and transmitted grapple offset. Other origins retain the recorded
world position. This works for live and recorded views without waiting for another
beam message. Lightning uses its dedicated full-bright model and handles short bolts
from the target end to avoid extending through the Tesla mine. Its roll advances
with presentation time and freezes when that clock pauses.

`BeamModelRenderer` reuses libGDX `ModelInstance` pools and submits them through the
existing `ModelBatch` pass. It owns no model assets; model loading/unloading remains
with the effect asset catalog. Missing optional expansion models are skipped. Tesla
sound requests use the source entity and weapon channel, resolved through the shared
audio system. Screen teardown clears beam state and instance pools before unloading
catalog assets. Resource-free tests cover state, geometry, sound requests, pooled
instance transforms/reuse and borrowed model ownership using empty libGDX models.

## Player and Monster Heatbeams

`HeatbeamEffects` owns up to 32 heatbeams, refreshed by source entity with a 200 ms
lifetime. Local player beams follow the presented camera direction and interpolated
replicated gun offset. The `hand` setting shifts the muzzle right, left or to the
center; model/projection mirroring is not used. Remote player and monster beams
retain their recorded world endpoints. All views, including demos, supply the same
presentation context.

Heatbeam segments reuse the shared model-instance pools with the optional
`models/proj/beam/tris.md2` asset. Player and remote beams select frames 1 and 2,
respectively, use full-bright lighting and roll with presentation time. Segment
counts are capped at 128 per beam. Models share the world/view-weapon camera
projection, so they require no separate weapon-FOV correction.

`HeatbeamParticles` emits short pulses through the libGDX particle runtime: up to
five moving rings near the local muzzle, or a plasma shell around a remote origin.
Pulses are limited to one per source every 50 ms, live for 75 ms and obey the shared
particle budget. Refreshing messages or rendering repeatedly at a paused timestamp
does not add duplicate pulses. Screen teardown clears heatbeam state, model pools
and the shared particle runtime. Synthetic-camera and particle tests cover these
paths without game assets or a window.

## Steam and Sustained Emission

`SteamEffects` uses the shared libGDX particle runtime for instantaneous steam and
repeated steam bursts. Messages supply particle count, normal, palette range and
magnitude. Magnitude controls forward speed, lateral spread and spawn jitter;
particles fade over short lifetimes with a gentle downward acceleration.
Heatbeam impact sparks and steam use this same recipe and retain their impact sound.

Each screen owns at most 32 steam sustains. Reusing an identifier replaces its
parameters and refreshes its duration. `SustainScheduler` emits immediately on the
next effects update, then on a 100 ms cadence until expiry. A stalled frame emits
at most one burst per active sustain, skipping missed bursts. Repeated updates at
a paused presentation time do not emit again. Nonpositive durations cancel an
identifier. Clearing sustains stops future emission; existing particles finish
fading, or are disposed with the screen's particle runtime during teardown.

## Other Expansion Effects

`ExpansionEffects` routes flashlight messages to entity-keyed white dynamic lights
with a 100 ms lifetime. Repeated messages update the same light through
`DynamicLightSystem`, including its normal high-FPS visibility extension. Force
walls emit falling palette-colored particles along their segment; bubble trail 2
emits a denser rising trail with the impact sound at its start. Segment sampling
has a work cap and all particles obey the shared budget.

`ShellEffects` uses `SustainScheduler` and the libGDX particle runtime for expanding
widow beam-out and nuke shells. Widow shells expand toward a 45-unit radius over
2.1 seconds and refresh by wire identifier. Nuke shells expand toward 200 units
over one second; their messages have no identifier, so independent explosions
receive local IDs outside the wire's signed-short range. The two kinds share a
32-sustain capacity. Particle pulses run at 50 ms intervals, last 75 ms and use
family-specific palettes, with counts tuned for the existing particle budget.
Paused clocks do not emit again, stalls skip missed pulses, and clearing sustain
state stops future emission while existing particles finish fading.

Tests use synthetic messages, clocks and palette values with the real light and
particle runtimes to exercise refresh, motion, sound requests, coexistence,
expiry, pause, resource limits and cleanup. No installed content or window is needed.

## Impacts and Point Bursts

`ImpactEffects` supplies particle recipes for blood, gunshot, spark, shotgun and
blaster families, including blue-hyperblaster impacts. Spawn positions scatter
along the supplied normal, while velocities scatter independently; blaster
variants also carry directed velocity and their own palette ranges. Chainfist
smoke uses particles only and rises without downward acceleration. Welding sparks
add a short yellow light without a flash model. The outer controller retains model
flashes and dynamic lights.

Splash, laser, welding and tunnel effects preserve their transmitted counts,
including zero, subject to the shared particle budget. Laser/welding particles
vary within their palette range and fall; tunnel particles retain the supplied
color and accelerate upward. Gunshot/bullet impacts select a ricochet on three
of sixteen sound rolls; spark splashes weight their three sounds 1:1:2. Sounds
remain independent of whether the particle budget permits emission.

`PointParticles` adds the boss teleport's tall radial column and tracker
explosion's black particle cloud, alongside their existing sound/light components.
Other point/trail families retain dedicated recipes: rail helix/core, green BFG
cloud, teleport bursts and outward widow splash. Ordinary bubbles rise at a
steady velocity, and diagnostic trails remain stationary. These are Cake
approximations, rather than pixel-perfect replicas of reference emission.

## Explosions and Palette Lasers

`ExplosionEffect` owns animation state and contributes a frame light whose radius
follows the animation alpha. Lights are collected after the frame-light reset;
expiry removes both the visual and its light. Missing optional visual assets still
retain the animated light. MD2 explosions select frame pairs, skins and translucency
from the shared state; sprite explosions select its current frame. Materials use
standard libGDX alpha blending. `TE_EXPLOSION1_BIG`, `TE_EXPLOSION1_NP` and
`TE_PLAIN_EXPLOSION` do not emit explosion particles.

`LaserEffects` keeps up to 64 independent BFG laser pulses for 100 ms. Each pulse
chooses a palette color from `0xD0..0xD3`, with diameter 4 and opacity 0.3; its color
stays stable when presentation time pauses. The screen-owned `BeamRenderer` shares
cached libGDX cylinder models with RF_BEAM entities, avoiding per-pulse GPU resource
allocation. Update expires pulses even when no draw occurs. Screen teardown clears
laser state and disposes the shared renderer.
Synthetic tests cover animation, lights, materials, transforms, expiry and ownership
without game assets or a window.

## Visual Behaviour and Current Limits

Particle families select colours from `q2palette.bin`. Useful compatibility anchors:

- Point impacts use the corresponding legacy blood/spark/shotgun palette ranges.
- Explosion-family and blue-hyperblaster particles use `0xE0..0xE7`.
- Splash types select from `{0x00,0xE0,0xB0,0x50,0xD0,0xE0,0xE8}`.
- Rail trails use spiral particles (`0x74..0x7B`) and core particles (`0x00..0x0F`).

Fallback colours keep effects visible when palette data is unavailable.
`TE_BFG_EXPLOSION` uses the translucent `sprites/s_bfg2.sp2` explosion path;
`TE_BFG_LASER` uses the shared palette-beam renderer. Parasite/medic and grapple
beams use pooled segment models rebuilt from persistent beam state. Particle
patterns and procedural geometry remain visual approximations of the reference;
base-game effects, live play, pause and map/session transitions still need visual
verification.

Source references are `client/CL_fx.ParseMuzzleFlash2`, `CL_fx.LogoutEffect`,
`CL_tent.ParseTEnt`, Yamagi's `src/client/cl_tempentities.c` / `cl_effects.c` /
`cl_particles.c`, and Q2PRO's `src/client/tent.c` / `effects.c` in the sibling checkouts.
The comparison targets the classic effects, rather than Q2PRO's extended protocol.
For example, Q2PRO also presents `TE_RAILTRAIL2`; Cake follows Yamagi's supported
set and rejects that type and `TE_FLAME`. Yamagi plays a rocket sound for
`TE_PLAIN_EXPLOSION`, while Q2PRO leaves it silent; Cake retains Yamagi's sound.

## Protocol-34 Presentation Coverage

All 54 supported types have a presentation path. Names below omit `TE_`; wire IDs
are included to make the boundary explicit. Optional missing assets, disabled
particles/lights and exhausted budgets can still suppress their respective output.

| Types (wire IDs) | Presentation owner and components |
| --- | --- |
| GUNSHOT (0), SHOTGUN (4), BULLET_SPARKS (14) | `ImpactEffects` particles; controller smoke/flash; probabilistic ricochet for gunshot/bullet sparks |
| BLOOD (1), GREENBLOOD (26), MOREBLOOD (42) | `ImpactEffects` blood palettes and counts |
| BLASTER (2), BLASTER2 (30), FLECHETTE (55) | `ImpactEffects` directed particles and sound; controller model/skin and animated light |
| RAILTRAIL (3) | Controller helix/core particles and rail sound |
| EXPLOSION1 (5), EXPLOSION2 (6), ROCKET_EXPLOSION (7), GRENADE_EXPLOSION (8), ROCKET_EXPLOSION_WATER (17), GRENADE_EXPLOSION_WATER (18), PLASMA_EXPLOSION (28) | `PointParticles` cloud; controller animated model/light and dry/water sound |
| SPARKS (9) | `ImpactEffects` spark particles |
| SPLASH (10) | `ImpactEffects` transmitted count/palette and spark-splash sound |
| BUBBLETRAIL (11) | Controller rising bubble trail |
| SCREEN_SPARKS (12), SHIELD_SPARKS (13), ELECTRIC_SPARKS (46) | `ImpactEffects` palette particles and impact sound |
| LASER_SPARKS (15), TUNNEL_SPARKS (29) | `ImpactEffects` transmitted count/color, falling/rising particles |
| PARASITE_ATTACK (16), MEDIC_CABLE_ATTACK (19), GRAPPLE_CABLE (24) | `BeamEffects` refreshed cable state and pooled segment models |
| BFG_EXPLOSION (20) | Controller animated sprite and green light |
| BFG_BIGEXPLOSION (21) | Controller green particle cloud |
| BOSSTPORT (22) | `PointParticles` teleport column; controller unattenuated teleport sound |
| BFG_LASER (23) | `LaserEffects` independent pulses; shared `BeamRenderer` geometry |
| WELDING_SPARKS (25) | `ImpactEffects` particles; controller light-only flash |
| BLUEHYPERBLASTER (27) | `ImpactEffects` directed particles; second position field is interpreted as a direction |
| LIGHTNING (33) | `BeamEffects` source/destination-keyed segments and entity-associated Tesla sound |
| DEBUGTRAIL (34) | Controller stationary diagnostic particles |
| PLAIN_EXPLOSION (35), EXPLOSION1_BIG (53), EXPLOSION1_NP (54) | Controller model/light and sound, without explosion particles |
| FLASHLIGHT (36), FORCEWALL (37), BUBBLETRAIL2 (41) | `ExpansionEffects` keyed light, falling wall particles, or rising bubbles plus impact sound |
| HEATBEAM (38), MONSTER_HEATBEAM (39) | `HeatbeamEffects` refreshed attached/world beams; `HeatbeamParticles` rings/shell |
| STEAM (40), HEATBEAM_SPARKS (43), HEATBEAM_STEAM (44) | `SteamEffects` instant/sustained bursts; controller sound for heatbeam impacts |
| CHAINFIST_SMOKE (45) | `ImpactEffects` rising smoke particles |
| TRACKER_EXPLOSION (47) | `PointParticles` black cloud; controller negative light and disruption sound |
| TELEPORT_EFFECT (48), DBALL_GOAL (49), WIDOWSPLASH (52) | Controller teleport burst or outward widow shell particles |
| WIDOWBEAMOUT (50), NUKEBLAST (51) | `ShellEffects` bounded, timed expanding particle pulses |

`TempEntityPresentationTest` exercises every type through the real dispatcher with
synthetic messages and no optional assets. It checks visible-effect state, paused
updates, expiry without drawing, and a fresh session after teardown. Family tests
cover the detailed particle/beam geometry, timing, sounds, budgets and material
requests; `TemporaryEntityCodecTest` independently checks wire layouts and bounded
rejections. These tests do not validate GPU output or audible playback.

User-run visual checks still needed after the final changes:

- Base-game gunshot/shotgun/blaster/rail impacts, dry/water explosions and BFG effects.
- Expansion beams, welding and chainfist smoke in live play; the three Rogue demos
  were already reported playable and visually good before the final audit fixes.
- Pause/resume during sustained effects, followed by a map change and disconnect/
  reconnect, checking for frozen leftovers, missing assets or duplicated effects.

## Assets and Lifetime

Call `precache()` before gameplay effect dispatch. `EffectAssetCatalog` preloads
listed MD2 models, SP2 sprites, and sounds that configstrings may not supply.
Files absent from raw resolver lookup are skipped; catalog lookups return null for
unloaded assets. Present files still pass through the normal loaders.

The catalog acquires one AssetManager reference per present asset, including
already loaded assets, and releases that reference during disposal. Repeated
precache/dispose calls do not acquire/release twice. This keeps config assets and
overlapping screens independent during map transitions.

`ClientEffectsSystem.dispose()` disposes remaining transient instances, particle
meshes/shaders, its sprite renderer, and the asset catalog. Shared model/sprite
assets belong to the catalog/`AssetManager`, rather than each effect instance.
