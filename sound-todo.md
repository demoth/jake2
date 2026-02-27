# Sound TODO (Cake vs Yamagi/Quetoo)

Date: 2026-02-27

## Purpose

Track gaps between the current Cake audio pipeline and reference Quake II implementations:

- Yamagi (`../quake/quake2/yquake2`)
- Quetoo (`../quake/quake2/quetoo`)

This document focuses on concrete behavior mismatches and implementation tasks.

## High Priority

### 1) Packet channel/time-offset semantics are not implemented in Cake

Status:

- Server/network protocol carries channel-override and start-time offset semantics.
- Cake currently ignores both and does immediate `Sound.play(volume)` calls.

References:

- `server/src/main/java/jake2/server/SV_SEND.java` (`sendchan`, `SND_OFFSET`)
- `qcommon/src/main/java/jake2/qcommon/network/messages/server/SoundMessage.java` (`timeOffset`, `sendchan`)
- `cake/core/src/main/kotlin/org/demoth/cake/stages/ingame/Game3dScreen.kt` (`processSoundMessage`)
- Yamagi: `src/client/sound/sound.c` (`S_PickChannel`, `S_StartSound`, pending playsounds queue)

Impact:

- Entity/channel override rules are broken (`CHAN_AUTO` vs `CHAN_*` behavior drift).
- Server-provided sub-frame timing (`timeOffset`) is lost.
- Overlapping/retrigger behavior can differ from original gameplay feel.

TODO:

- Build a Cake-side sound channel manager keyed by `(entity, channel)`.
- Honor channel override semantics matching `Defines.CHAN_*`.
- Implement delayed scheduling for `SoundMessage.timeOffset` (0..~255 ms encoded).

### 2) Looping entity sounds are missing (`entity_state.sound`)

Status:

- Yamagi/Quetoo add per-frame loop sounds from entity state and cull/update them every frame.
- Cake has no equivalent path for persistent loops.

References:

- Yamagi: `src/client/sound/openal.c` (`AL_AddLoopSounds`)
- Quetoo: `src/cgame/default/cg_entity.c` (`Cg_EntitySound`, `S_PLAY_LOOP | S_PLAY_FRAME`)
- Cake search shows no loop path consuming `entity.current.sound`.

Impact:

- Missing/incorrect ambient and mover loops (doors, trains, world hum, etc.).

TODO:

- Add loop-sound collection from replicated entity state each frame.
- Keep active loop channels alive only while source exists this frame.
- Match legacy attenuation and relative positioning behavior for looped sources.

### 3) One-shot volume-only playback instead of active channel spatialization

Status:

- Cake computes an attenuation scalar once at play time and then loses control of the voice.
- Reference engines keep channels and respatialize them every frame with listener updates.

References:

- Cake: `Game3dScreen.processSoundMessage`, `ClientEffectsSystem.playEffectSound`
- Yamagi: `src/client/sound/openal.c` (`AL_Update`, per-channel respatialization)
- Quetoo: `src/client/sound/s_mix.c` (`S_MixChannels`, listener/source updates)

Impact:

- No proper panning/orientation updates once sound starts.
- Moving source/listener realism is reduced.
- Doppler-capable behavior cannot be reproduced.

TODO:

- Store and update active playing sources, not just fire-and-forget sounds.
- Update listener orientation/velocity each frame.
- Respatialize active non-UI channels every frame.

## Medium Priority

### 4) Environmental filtering/effects gap (occlusion, underwater, reverb)

Status:

- Yamagi includes occlusion traces + underwater/reverb filters in OpenAL path.
- Quetoo includes per-sample occlusion/underwater flags and OpenAL filters.
- Cake currently has only distance attenuation, no environmental filtering.

References:

- Yamagi: `src/client/sound/openal.c` (`AL_Spatialize`, `AL_UpdateUnderwater`, reverb logic)
- Quetoo: `src/cgame/default/cg_sound.c` (`S_PLAY_OCCLUDED`, `S_PLAY_UNDERWATER`)
- Quetoo: `src/client/sound/s_mix.c` (`filter` application)
- Cake: `cake/core/src/main/kotlin/org/demoth/cake/audio/SpatialSoundAttenuation.kt`

Impact:

- Audio depth and readability regress in enclosed/liquid/occluded situations.

TODO:

- Add optional environmental audio layer behind cvars/toggles.
- Implement basic occlusion and underwater low-pass first.
- Revisit reverb after channel manager is in place.

### 5) Music/streaming pipeline parity gap

Status:

- Yamagi has OGG track backend with streaming and control cvars/commands.
- Quetoo has dedicated music subsystem with threaded buffering.
- Cake currently loads/plays only short `Sound` assets, no map music system.

References:

- Yamagi: `src/client/sound/ogg.c`
- Quetoo: `src/client/sound/s_music.c`
- Cake: `cake/core/src/main/kotlin/org/demoth/cake/Cake.kt` (only `Sound` loader setup)

Impact:

- No in-game music playback parity with classic behavior.

TODO:

- Introduce `Music` playback manager in Cake.
- Parse and react to map track/config-driven music changes.
- Add volume/mute/shuffle controls at least to cvar/console level.

### 6) Entity-event sound coverage is incomplete

Status:

- Cake currently handles footsteps + fall variants.
- Item respawn and teleport event sounds are still missing (explicitly noted in comments/docs).

References:

- Cake: `Game3dScreen.playEntityEventSounds` (non-goals comment)
- Yamagi: `src/client/cl_effects.c` (`EV_ITEM_RESPAWN`, `EV_PLAYER_TELEPORT`)
- Quetoo: `src/cgame/default/cg_entity_event.c`

Impact:

- Missing audible gameplay cues.

TODO:

- Add `EV_ITEM_RESPAWN` sound/effect dispatch.
- Add teleport event sound/effect dispatch.
- Validate all event mappings against protocol constants.

## Low Priority

### 7) Audio lifecycle control during transitions is incomplete

Status:

- Cake has TODO to stop sounds/effects on serverdata/reset transitions.
- References explicitly stop all sounds on restarts/deactivation.

References:

- Cake: `cake/core/src/main/kotlin/org/demoth/cake/Cake.kt` (`// todo: stop sounds, effects, etc..`)
- Yamagi: `src/client/sound/sound.c` (`S_StopAllSounds`, `S_Activate`)

Impact:

- Potential lingering audio across map transitions/disconnect/reconnect.

TODO:

- Add centralized stop/reset audio call in client reset path.
- Ensure transition cleanup handles active effects and loop channels.

## Cross-cutting tasks

- Add Cake audio cvars matching key legacy controls:
  - `s_volume` (master)
  - effects/music split (`s_effects_volume`, `s_music_volume`) or Cake equivalents
  - `cl_footsteps` toggle
- Add audio debug commands / diagnostics:
  - active channel list
  - loaded sample list
  - backend info
- Add integration tests around `SoundMessage` handling:
  - channel override behavior
  - `timeOffset` delayed start
  - sexed (`*`) sound fallback resolution

## Suggested implementation order

1. Channel manager + `timeOffset` + looping entity sounds.
2. Per-frame listener/source update and respatialization.
3. Event coverage completion (respawn/teleport).
4. Music subsystem.
5. Environmental effects (occlusion/underwater/reverb), guarded by cvars.
