# Cake Sound Subsystem Overview

## Purpose

This directory documents the current Cake runtime audio architecture and the behavior contracts that gameplay code relies on.

The implementation lives in:

- `cake/core/src/main/kotlin/org/demoth/cake/audio/CakeAudioSystem.kt`
- `cake/core/src/main/kotlin/org/demoth/cake/audio/FireAndForgetCakeAudioSystem.kt`

## Core Concepts

### 1) Audio facade boundary

Gameplay/effects code does not call `Sound.play(...)` directly for world sounds.  
It submits requests to `CakeAudioSystem`:

- one-shot / channel-bound sounds: `SoundPlaybackRequest`
- looped entity sounds (`entity_state.sound`): `EntityLoopSoundRequest`

This keeps call sites stable while backend behavior evolves.

### 2) Frame lifecycle contract

Per render frame:

1. `beginFrame(listener)`
2. zero or more `play(...)`
3. one `syncEntityLoopingSounds(...)` with authoritative loop set
4. `endFrame()`

`beginFrame` updates listener transform and respatializes tracked active sounds.

### 3) Entity indexing semantics

`entityIndex` follows server numbering (`1..MAX_EDICTS`), `0` means unbound.

- If a one-shot has `origin != null`, that explicit origin is used.
- If `origin == null` and `entityIndex > 0`, backend may resolve dynamic entity origin.

### 4) Channel semantics (current baseline)

- Explicit channels (`CHAN_* != CHAN_AUTO`) are keyed by `(entity, channel)` and override previous active voice on the same key.
- `CHAN_AUTO` currently behaves as fire-and-forget (no override key).

### 5) Non-spatial exceptions

The backend treats these as non-spatial/full-center pan:

- `attenuation <= 0` (`ATTN_NONE`)
- sounds bound to the local player entity

This preserves expected pickup/UI-like feedback behavior.

### 6) Looped entity sounds

Loops are synchronized from replicated `entity_state.sound` each frame:

- present in frame -> start/update loop
- missing in frame -> stop loop

Current attenuation baseline uses static-style falloff (`ATTN_STATIC`) with per-frame respatialization.

## Current producers

- Server sound packets: `Game3dScreen.processSoundMessage`
- Weapon sound packets: `Game3dScreen.processWeaponSoundMessage`
- Entity events: `Game3dScreen.playEntityEventSounds`
- Temp/effect sounds: `ClientEffectsSystem.playEffectSound`

## Transition lifecycle

`Cake` explicitly stops audio on:

- disconnect
- serverdata reset
- map transition staging

This prevents lingering sounds across reconnect/map changes.

## Known limits

- No strict full parity yet for all channel-pressure/voice-stealing edge cases.
- `CHAN_AUTO` one-shots are not persistently tracked after playback starts.
- Music/streaming, environmental filters, and doppler are deferred for future work.
