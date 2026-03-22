# Sound TODO (Cake vs Yamagi/Quetoo)

Stable architecture and current runtime behavior now live in:

- `sound/README.md`
- `cake/core/src/main/kotlin/org/demoth/cake/stages/ingame/README.md`

This file is intentionally reduced to active parity and diagnostics follow-ups only.

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

TODO:

- Validate `EV_OTHER_TELEPORT` handling expectations and decide if it should map to an explicit entity-event path.

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
