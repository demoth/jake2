# Ingame Runtime

## Overview
This package owns gameplay-time client runtime orchestration:
- server message handling for world/frame/sound updates,
- replicated entity reconstruction and visibility,
- frame rendering and gameplay-time sound dispatch.

It does not own:
- effect-specific TE/muzzle logic (`stages/ingame/effects`),
- HUD script parser/runtime internals (`stages/ingame/hud`),
- configstring storage and asset ownership (`org.demoth.cake.GameConfiguration`).

## Key Types
- `Game3dScreen` - main ingame runtime entry point and server-message dispatcher.
  - Also owns presentation-mode routing (`WORLD` vs `CINEMATIC`) while `Cake` keeps shared networking/input orchestration.
  - Delegates frame drawing to mode-specific runtimes (`WorldPresentationRuntime`, `CinematicPresentationRuntime`) and delegates cinematic media/skip ownership to `CinematicPresentationController`.
- `CinematicPresentationController` - owns cinematic media decode/render lifecycle and skip command policy.
  - Supports static `.pcx` pictures and `.cin` streamed frames (centered/letterboxed over black background) while gameplay/network message handling stays in `Game3dScreen`.
  - Streams `.cin` audio chunks to a dedicated runtime `AudioDevice` (separate from entity/event SFX path).
  - EOF or guarded input skip produces `nextserver <spawncount>` command parity.
  - Emits throttled `cinematic_debug` diagnostics when `r_bsp_batch_debug 1`.
- `HudOverlayRenderer` - composes timed HUD overlays and gameplay layouts.
  - Handles crosshair + statusbar layout + optional extra layout/inventory branches.
  - Keeps legacy ordering/semantics of `SCR_DrawStats`, `SCR_DrawLayout`, and `CL_inv.DrawInventory`.
- `IngameSoundMessageHandler` - central sound/event dispatch for server sound packets.
  - Handles `SoundMessage`, `WeaponSoundMessage`, packet-entity event sounds, and loop-sound sync.
  - Keeps legacy behavior for `CL_parse.ParseStartSoundPacket`, `CL_fx.ParseMuzzleFlash`, and `CL_fx.EntityEvent`.
- `ReplicatedEntityEffectCollector` - applies replicated `EF_*` visual side effects from packet entities.
  - Collects dynamic lights and replicated projectile trails using render-time interpolation rules.
  - Keeps legacy `CL_ents.AddPacketEntities` behavior for light/trail branches.
- `WorldPresentationRenderer` - orchestrates one gameplay-world render frame.
  - Preserves pass ordering across world batches, entities/sprites/beams, effects, HUD, and postprocess present.
  - Uses callback joints to keep `Game3dScreen` ownership for subsystems not yet extracted.
- `ClientEntityManager` - frame/entity reconstruction, continuity, and visible buckets.
- `ClientPrediction` - movement prediction and view smoothing.
- `BspWorldBatchRenderer` - dedicated world BSP renderer (opaque/warp/translucent passes).
- `BspWorldVisibilityMaskTracker` - batch-mode PVS/areabits visibility mask tracker (no NodePart mutation).
- `GameConfiguration` (`org.demoth.cake`) - configstring-backed asset ownership/lookup for map assets and generic sounds.
- `PlayerConfiguration` (`org.demoth.cake`) - player slot/inventory state and variation-aware model/icon/sound resolution.

Legacy counterparts:
- `client/CL_ents`, `client/CL_fx`, `client/CL_parse`, `client/sound/lwjgl/LWJGLSoundImpl`.

## Data / Control Flow
```text
ConfigStringMessage -> GameConfiguration.applyConfigString
Precache -> GameConfiguration.loadAssets (models/images/sounds + player-variation preload)

FrameHeader + PacketEntities + PlayerInfo
  -> ClientEntityManager reconstruct frame
  -> Game3dScreen.computeVisibleEntities
  -> IngameSoundMessageHandler.playEntityEventSounds
  -> ReplicatedEntityEffectCollector.collectTrails/collectDynamicLights
  -> WorldPresentationRenderer.render

World BSP surfaces are rendered by `BspWorldBatchRenderer` with dedicated opaque/warp/translucent passes.
Visibility is driven by `BspWorldVisibilityMaskTracker` (PVS + areabits) and fed directly into the batched renderer.

SoundMessage
  -> Game3dScreen.processSoundMessage
  -> GameConfiguration.getSound(soundIndex, entityIndex)
  -> regular sound OR variation-specific '*' resolution
```

## Invariants
- Player entity index mapping for variation lookup is `entityIndex in 1..MAX_CLIENTS` -> client slot `entityIndex - 1`.
- Only default variation constants are hardcoded: `male/grunt`.
- Non-default variation names must come from `CS_PLAYERSKINS`; do not hardcode model names.
- `*` config sounds are not loaded in `loadSoundConfigResource`; they are resolved at playback.
- Variation sound fallback order must remain deterministic and cached per `(variation model, sound name)`.
- Linked player weapon pass (`modelindex2 == 255`) uses `skinnum >> 8` as weapon model index from `CS_MODELS` `#...` entries; `cl_vwep 0` forces index `0`.
- `EF_POWERSCREEN` emits a companion MD2 pass (`models/items/armor/effect/tris.md2`) with frame `0` and translucent green shell flags.
- Entity-event sounds are triggered after a valid packet-entity reconstruction.
- Cinematic control path (`playernum == -1`) must keep normal client message processing alive and may emit `nextserver <spawncount>` on guarded user skip input.

## Runtime Controls
- `vid_gamma` - shared brightness control for world/MD2 lighting.
- `gl3_intensity` - legacy-style brightness multiplier for lit geometry.
- `gl3_overbrightbits` - legacy-style overbright control (`0` disables extra boost).
- `r_dlights` - toggles dynamic-light contribution.
- `r_particles` - global particle budget (`0` disables particles).
- `r_bsp_batch_debug` - throttled batching/cinematic diagnostics.
- `r_post_vignette` / `r_post_vignette_strength` - postprocess blend control for `player_state_t.blend`.
- `r_underwater_warp` - toggles postprocess underwater screen warping.

## MD2 Shell Highlight (Fresnel Rim)
- Purpose: provide a clear shell-like visual emphasis for MD2 entities without introducing an extra shell mesh/render pass.
- Current approach: single-pass Fresnel rim highlight in MD2 shader (`assets/shaders/md2.vert`, `assets/shaders/md2.frag`).
- Effect-to-render mapping:
1. `ClientEntityManager.resolveEntityRenderFx` maps gameplay shell effects (`EF_QUAD`, `EF_PENT`, `EF_DOUBLE`, `EF_HALF_DAMAGE`) to shell render flags (`RF_SHELL_*`).
2. `Game3dScreen.applyMd2EntityLighting` resolves shell color from `RF_SHELL_*` and writes highlight uniforms via `Md2CustomData`.
3. `Md2Shader` forwards those values as shader uniforms; fragment shader applies Fresnel rim term based on view direction and world normal.
- Notes:
1. This is an intentional approximation, not an exact legacy shell pipeline replica.
2. Visual tuning knobs are `highlightStrength` and `highlightRimPower` in `Game3dScreen.applyMd2EntityLighting`.

## Decision Log
Newest first.

### Decision: Resolve player sounds via `PlayerVariation` abstraction
- Decision: Use `PlayerVariation` (`model`, `skin`, sounds) as the conceptual unit.
- Context: Player sounds are variation-specific and cannot be treated as plain global sounds.
- Options Considered:
1. Keep legacy "sexed sound" naming/logic and hardcode known variation names.
2. Resolve variation identity from `CS_PLAYERSKINS` and keep only default constants.
- Chosen Option & Rationale: Option 2. It avoids hardcoded non-default variation names and matches server-provided variation data.
- Consequences: Variation lookup depends on player configstrings; invalid/missing entries fallback to `male/grunt`.
- Status: accepted.
- Definition of Done: No hardcoded non-default variation names in Cake core; variation sound/model/icon lookup uses `CS_PLAYERSKINS`.
- References: this thread ("PlayerVariation terminology and naming constraints").

### Decision: Resolve `*` sounds at playback using emitting entity
- Decision: Keep `*` sounds unresolved during generic config-sound loading and resolve them dynamically on playback.
- Context: Legacy behavior treats `*pain*.wav`, `*jump1.wav`, `*death*.wav`, `*fall*.wav` as variation-dependent.
- Options Considered:
1. Pre-resolve `*` sounds into fixed assets at config load.
2. Resolve when sound is played, using entity/player variation.
- Chosen Option & Rationale: Option 2 for behavior parity and correct per-entity variation mapping.
- Consequences: Sound API now accepts `entityIndex` for variation-aware lookup.
- Status: accepted.
- Definition of Done: `Game3dScreen.processSoundMessage` uses `getSound(soundIndex, entityIndex)` and no missing warnings for available variation sounds.
- References: this thread; legacy `LWJGLSoundImpl.RegisterSexedSound`.

### Decision: Handle fall/footstep via entity events in ingame runtime
- Decision: Handle gameplay-significant entity-event sounds in `Game3dScreen.playEntityEventSounds`.
- Context: Fall/footstep, item respawn, and teleport feedback are emitted as entity events, not only as `SoundMessage`.
- Options Considered:
1. Keep only `SoundMessage` path.
2. Add dedicated entity-event sound dispatch after packet entity reconstruction.
- Chosen Option & Rationale: Option 2, matching old `CL_fx.EntityEvent` behavior.
- Consequences: `EV_OTHER_TELEPORT` remains an explicit follow-up instead of being guessed into the current mapping.
- Status: accepted.
- Definition of Done: footsteps, fall variants, item respawn, and player teleport feedback are audible without relying on `SoundMessage`.
- References: this thread; legacy `client/CL_fx.EntityEvent`.

### Decision: Remove `cl_footsteps` parity gating for now
- Decision: Footstep event playback is currently unconditional.
- Context: User requested cvar parity to be implemented separately.
- Options Considered:
1. Keep old-client `cl_footsteps` gate now.
2. Remove gate and defer cvar parity.
- Chosen Option & Rationale: Option 2 to keep this feature slice focused.
- Consequences: Footsteps cannot yet be toggled via cvar in Cake runtime.
- Status: accepted.
- Definition of Done: `EV_FOOTSTEP` path does not read a footsteps cvar.
- References: this thread ("I removed the gating - cvars parity will be implemented separately").

## Quirks & Workarounds
- Generic fall files exist outside variation folders (`sound/player/fall1.wav`, `fall2.wav`).
- Why: legacy content mixes variation-specific and generic player sound layouts.
- How to work with it: keep fallback candidates that include both variation paths and generic `sound/player/<name>`.
- Removal plan: none; this is compatibility behavior.

- Variation sound candidate matching is path-order dependent.
- Why: first existing candidate wins and result is cached.
- How to work with it: modify candidate ordering only with explicit parity checks.
- Removal plan: if path rules are formalized in shared loader, centralize ordering there.

## How to Extend
1. To add new entity-event sounds, extend `Game3dScreen.playEntityEventSounds` and reuse `playEntityEventSound`.
2. Keep `GameConfiguration` as the only place that resolves variation-specific sound paths.
3. If adding new variation fields, extend `PlayerVariation` and keep defaults limited to `male/grunt`.
4. Add tests or manual parity checks for both `SoundMessage` and entity-event paths.

## Open Questions
- Should `EV_OTHER_TELEPORT` map to an explicit entity-event sound path in Cake runtime?
- Should variation-sound resolution move into a reusable helper shared with other runtime systems?
