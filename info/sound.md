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
- `CHAN_AUTO` allows overlapping one-shots without a keyed override. All one-shots are tracked until their estimated end time and respatialized each frame.
- `SoundMessage.timeOffset` is honored for delayed one-shot scheduling.

One-shots and entity loops share the `s_maxvoices` budget. Under pressure, a
one-shot replaces the eligible voice with the shortest remaining lifetime;
non-player sounds cannot steal the local player's voices. Loop synchronization
keeps existing loops stable and skips new loops when the pool is full.

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

### 7) Entity-event coverage

Current event-sound coverage includes:

- footsteps and fall variants
- `EV_ITEM_RESPAWN`
- `EV_PLAYER_TELEPORT`

Item respawn and player teleport also trigger their paired visual effect paths from the ingame runtime.

## Current producers

- Server sound packets: `IngameSoundMessageHandler.processSoundMessage`
- Weapon sound packets: `IngameSoundMessageHandler.processWeaponSoundMessage`
- Entity events: `IngameSoundMessageHandler.playEntityEventSounds`
- Temp/effect sounds: `ClientEffectsSystem.playEffectSound`

## Transition lifecycle

`Cake` explicitly stops audio on:

- disconnect
- serverdata reset
- map transition staging

This prevents lingering sounds across reconnect/map changes.

## Known limits

- Voice allocation has regression coverage for channel overrides, voice stealing,
  local-player protection, and stable loops under pressure. This does not establish
  full parity with every legacy mixer edge case.
- One-shot lifetimes use decoded sound durations, with an estimate when the duration
  is unknown.
- Footsteps currently have no `cl_footsteps` gate.
- Music playback, environmental filters, and Doppler are not implemented. Cinematic
  audio streaming is handled separately by `CinematicPresentationController`.
