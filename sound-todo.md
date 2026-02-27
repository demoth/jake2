# Sound TODO (Cake vs Yamagi/Quetoo)

Date: 2026-02-27

## Purpose

Track remaining gaps between Cake audio and reference Quake II behavior:

- Yamagi (`../quake/quake2/yquake2`)
- Quetoo (`../quake/quake2/quetoo`)

## Implemented so far

- Centralized audio facade:
  - `cake/core/src/main/kotlin/org/demoth/cake/audio/CakeAudioSystem.kt`
  - `cake/core/src/main/kotlin/org/demoth/cake/audio/FireAndForgetCakeAudioSystem.kt`
- Unified one-shot playback request model with:
  - `entityIndex`, `channel`, `timeOffsetSeconds`, `origin`, `attenuation`, `baseVolume`
- `SoundMessage.timeOffset` delayed scheduling support.
- Explicit channel override baseline for `CHAN_* != CHAN_AUTO` via `(entity, channel)` keys.
- Per-frame listener-based respatialization for tracked active channels.
- Non-spatial exceptions for:
  - `ATTN_NONE`
  - local player entity sounds
- Looped entity sound baseline (`entity_state.sound`):
  - per-frame sync start/update/stop
  - static-style attenuation and respatialization
- Transition lifecycle cleanup:
  - stop audio on disconnect / serverdata reset / map transition

## Pending (current priority)

### 1) Channel semantics parity hardening

Status:

- Baseline override behavior is in place.
- Remaining parity details (under channel pressure/high concurrency) are not fully verified.

TODO:

- Verify and align replacement/stealing behavior vs legacy mixers under stress.
- Add targeted diagnostics to inspect active channels and replacements.

### 2) One-shot tracking parity gaps

Status:

- Explicit-channel sounds are tracked/respatialized.
- `CHAN_AUTO` one-shots are still fire-and-forget after start.

TODO:

- Decide whether to keep `CHAN_AUTO` fire-and-forget or add bounded short-lived tracking for closer parity.
- Add regression checks for moving-source one-shot behavior.

### 3) Entity event coverage completion

Status:

- Implemented: footsteps + fall variants.
- Pending: respawn/teleport entity events.

References:

- Cake: `Game3dScreen.playEntityEventSounds`
- Yamagi/legacy: `client/src/main/java/jake2/client/CL_fx.java` (`EV_ITEM_RESPAWN`, `EV_PLAYER_TELEPORT`)

TODO:

- Add `EV_ITEM_RESPAWN` sound + particle dispatch.
- Add `EV_PLAYER_TELEPORT` sound + particle dispatch.
- Validate mappings against `qcommon/Defines` event constants.

### 4) Cross-cutting quality tasks

TODO:

- Add Cake audio cvars parity set (at least `s_volume`, `cl_footsteps`; split volumes optional).
- Add audio debug commands/diagnostics:
  - active channels
  - loaded sounds
  - backend state
- Add integration tests around `SoundMessage` handling:
  - `timeOffset`
  - explicit channel override
  - sexed (`*`) sound resolution

## Deferred (future, not current priority)

- Music/streaming subsystem parity.
- Environmental filters/effects:
  - occlusion
  - underwater filtering
  - reverb
- Doppler behavior parity.

## References

- Server send path: `server/src/main/java/jake2/server/SV_SEND.java`
- Packet model: `qcommon/src/main/java/jake2/qcommon/network/messages/server/SoundMessage.java`
- Cake ingest path: `cake/core/src/main/kotlin/org/demoth/cake/stages/ingame/Game3dScreen.kt`
- Legacy reference (events/particles): `client/src/main/java/jake2/client/CL_fx.java`
