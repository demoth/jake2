# Ingame Runtime

## Responsibilities

`Game3dScreen` receives gameplay server messages and owns one screen's entity,
prediction, rendering, audio, and HUD runtime. `Cake` owns the connection, queued
commands, and [input scheduling](../../input/README.md).
[Configuration](../../config/README.md) owns configstrings and asset references;
[BSP](../../assets/bsp/README.md) and [MD2](../../assets/md2/README.md) own their
loading/rendering pipelines. [Effects](effects/README.md) and
[HUD layouts](hud/README.md) have separate runtimes.

## Server Updates and Frame Reconstruction

```text
ConfigStringMessage -> GameConfiguration.applyConfigString()
Precache -> Game3dScreen.precache(): map/assets, BSP renderer and controllers

FrameHeader + PlayerInfo + PacketEntities -> ClientEntityManager reconstructs frame
  -> valid packet-entity update: compute visible entities, dispatch entity events,
     notify ClientPrediction of the reconstructed server frame

Render frame -> predict movement and update interpolated view
  -> update listener/loop sounds, world visibility, lightstyles, sky, and effects
  -> collect replicated trails and dynamic lights -> draw world presentation
```

`ClientEntityManager` keeps current/previous frames, entity continuity, and visible
model/sprite/beam buckets. `ClientPrediction` reconciles acknowledged movement
commands with server state and supplies local movement/view smoothing. Entity-event
sounds run after valid packet-entity reconstruction, rather than on every draw.

## World Presentation

`WorldPresentationRenderer` sequences drawing through a frame context supplied by
`Game3dScreen`. The screen retains camera updates, entity material/lighting setup,
and postprocess resources; the renderer coordinates their use.

The submission order is opaque/warp world batches; sky and entity models, beams,
and sprites; translucent world batches; particles and transient model effects;
late depth-hack entities such as the view weapon; then HUD and postprocess present.
Entity models and sprites each separate opaque and translucent submissions.
`HudOverlayRenderer` composes crosshair, statusbar, optional extra layout/inventory,
and timed overlays in legacy order (`SCR_DrawStats`, `SCR_DrawLayout`,
`CL_inv.DrawInventory`).

`ReplicatedEntityEffectCollector` derives projectile trails and dynamic lights from
replicated `EF_*` flags using render-time interpolation. Linked player weapons
(`modelindex2 == 255`) select `CS_MODELS` `#...` entries using `skinnum >> 8`;
`cl_vwep 0` forces weapon index zero. `EF_POWERSCREEN` adds the armor-effect MD2
model at frame zero with translucent green shell flags.

Shell effects are mapped to `RF_SHELL_*` by `ClientEntityManager`.
`Game3dScreen.applyMd2EntityLighting()` supplies shell color, `highlightStrength`,
and `highlightRimPower` through `Md2CustomData`; the MD2 shader renders a Fresnel rim
approximation in one pass. See the MD2 guide for shader/animation details.

## Sound Dispatch

`IngameSoundMessageHandler` translates server updates into `CakeAudioSystem`
playback requests:

- `SoundMessage`: resolves the configstring sound with the emitting entity index
  and forwards origin, channel, volume, attenuation, and timing information.
- `WeaponSoundMessage`: dispatches weapon sounds, muzzle dynamic lights, and
  applicable login/logout bursts.
- Packet-entity events: play respawn, footstep, landing/fall, and player-teleport
  sounds, with respawn/teleport particle feedback.
- Entity `sound` fields: synchronise looping sounds each world render frame.

Player-dependent `*` sounds are resolved through configuration at playback;
[path selection and fallbacks](../../config/README.md#player-sound-resolution)
belong to `PlayerConfiguration`. Legacy counterparts are
`CL_parse.ParseStartSoundPacket`, `CL_fx.ParseMuzzleFlash`, and `CL_fx.EntityEvent`.

Footsteps currently have no `cl_footsteps` gate. `EV_OTHER_TELEPORT` has no explicit
entity-event sound branch.

## Cinematic Presentation

Server data with `playerNumber == -1` selects cinematic presentation; other player
numbers select the world runtime. Networking and normal server-message processing
continue in either mode.

`CinematicPresentationController` owns `.pcx` pictures and `.cin` decoding/display,
centred over a black background. Streamed cinematic audio uses a dedicated
`AudioDevice`, separate from entity/event sounds. EOF or an immediate input action
after the skip guard produces a one-shot `nextserver <spawncount>` command.

## Runtime Controls

- `r_post_vignette` / `r_post_vignette_strength`: control postprocess treatment of
  `player_state_t.blend`.
- `r_underwater_warp`: toggles underwater screen warping.
- `r_dlights`: toggles dynamic-light contribution.
- `r_particles`: sets the global particle budget; zero disables particles.

Shared brightness controls (`vid_gamma`, `gl3_intensity`, `gl3_overbrightbits`) are
covered in the BSP guide. `cl_maxfps` controls command cadence, as described in the
input guide.

## Rendering Diagnostics

`r_bsp_batch_debug` (default `0`) prints throttled world/entity/sprite/beam/particle
submission stats and cinematic diagnostics. For world batching and visibility
internals, see the [BSP pipeline guide](../../assets/bsp/README.md).

To profile representative maps and views:

1. Enable draw-call and texture-binding graphs with `set r_debug_drawcalls 1` and
   `set r_debug_texturebindings 1`.
2. Enable submission statistics with `set r_bsp_batch_debug 1`.
3. Use `set cl_showfps 1` for average FPS, `2` for minimum/maximum/average FPS, or
   `3` to include a frame-time line.

Filesystem lookup diagnostics (`fs_files`, `fs_find`, `fs_mounts`, `fs_overrides`)
are documented in the [shared VFS guide](../../../../../../../../../../qcommon/vfs/README.md).

## Lifetime

`Game3dScreen.dispose()` releases screen-owned renderers, framebuffers, shaders,
audio, cinematic media, effects, HUD, and entity runtime, then resets prediction.
Configuration assets are released separately through `unloadConfigAssets()`.
`Cake` normally performs both operations, but can defer configuration unloading
across a map transition so shared assets remain referenced until the new screen
has acquired them.
