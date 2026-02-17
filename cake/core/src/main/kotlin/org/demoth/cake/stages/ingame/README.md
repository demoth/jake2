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
- `ClientEntityManager` - frame/entity reconstruction, continuity, and visible buckets.
- `ClientPrediction` - movement prediction and view smoothing.
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
  -> Game3dScreen.playEntityEventSounds
  -> render

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
- Entity-event sounds are triggered after a valid packet-entity reconstruction.

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
- Decision: Implement `EV_FOOTSTEP`, `EV_FALLSHORT`, `EV_FALL`, `EV_FALLFAR` in `Game3dScreen.playEntityEventSounds`.
- Context: Fall/footstep are emitted as entity events, not only as `SoundMessage`.
- Options Considered:
1. Keep only `SoundMessage` path.
2. Add dedicated entity-event sound dispatch after packet entity reconstruction.
- Chosen Option & Rationale: Option 2, matching old `CL_fx.EntityEvent` behavior.
- Consequences: Event-sound coverage is currently scoped to footstep/fall events.
- Status: accepted.
- Definition of Done: Fall and footstep sounds are audible without relying on `SoundMessage`.
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
- Should remaining legacy entity-event sounds (`EV_ITEM_RESPAWN`, `EV_PLAYER_TELEPORT`) be added in this package or delegated to effects?
- Should variation-sound resolution move into a reusable helper shared with other runtime systems?
