# Rendering TODO (Cake)

Stable rendering/runtime architecture now lives in:

- `cake/core/src/main/kotlin/org/demoth/cake/assets/README.md`
- `cake/core/src/main/kotlin/org/demoth/cake/stages/ingame/README.md`
- `cake/core/src/main/kotlin/org/demoth/cake/stages/ingame/effects/README.md`

This file is intentionally reduced to active rendering follow-ups only.

## Active Follow-Ups

- Replace render-frame-driven client sends with an always-decoupled command/update cadence.
  - Chosen direction: adopt the Q2Pro-style split concept, but keep Cake single-threaded for now.
  - `render()` keeps running every LibGDX frame.
  - local input/view updates run every LibGDX frame.
  - usercmd finalization and packet send run on a fixed cadence controlled by `cl_maxfps`.
  - We explicitly do not keep a dormant synchronous/`cl_async 0` path for Cake.
  - `cl_maxfps` stays for legacy config familiarity, but in Cake it means client command/update cadence, not render FPS.

- Optional non-legacy enhancement: smooth lightstyle interpolation between 100 ms ticks.
  - Keep legacy-discrete behavior as the baseline.
  - If implemented, make it a deliberate opt-in rather than the default.

- Widen static-image cinematic/end-screen support beyond `.pcx`.
  - Target formats: `.tga`, `.png`, `.jpg`.
  - Reuse the existing texture loader pipeline where practical instead of building a parallel image path.

- Keep validating renderer parity under stress scenes:
  - dense dynamic-light overlap
  - high particle pressure
  - cinematic/control-plane transitions

## Deferred

- Additional non-legacy renderer polish beyond parity-driven work.
- Any larger renderer rewrite that is not required for gameplay/readability parity.

## References

- Legacy Jake2 renderer/runtime: `client/`
- Yamagi GL3 reference: `../quake/quake2/yquake2`
- Q2PRO batching reference: `../quake/quake2/q2pro`
