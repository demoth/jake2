# Cake Input Bindings

## Overview
This package owns client input processing in Cake:
- runtime bindings and physical-event dispatch,
- immediate input state and usercmd assembly.

It does **not** own:
- persistence to `config.cfg`,
- per-mod bind profiles.

## Key Types
- `ClientBindings` - Runtime bind table + key/mouse/wheel event dispatcher.
- `InputManager` - Immediate action state + angle sync + `MoveMessage` assembly.

## Data / Control Flow
```text
LibGDX InputProcessor events
  -> input.InputManager.key*/touch*/scrolled
  -> ClientBindings.handle*
       - +commands: Cmd.ExecuteString("+...") / Cmd.ExecuteString("-...")
       - command-style: Cbuf.AddText("...\n")
  -> Cake.render(): Cbuf.Execute() BEFORE sendUpdates()
  -> input.InputManager.gatherInput() builds MoveMessage using immediate action counters
```

## Invariants
- Non-`+` bindings execute on key/button press only.
- `+command` emits once per physical press and must emit matching `-command` on release.
- Held key auto-repeat must not duplicate `+command` activation.
- Wheel binds are transient events (`MWHEELUP`, `MWHEELDOWN`), not held state.
- Input context switch (menu/console/game) must clear held gameplay state.

## Decision Log
### Decision: Execute `Cbuf` before sending client updates
- Context: Cake previously sent usercmd before executing queued commands, causing one-frame latency for command-style binds.
- Chosen Option & Rationale: Reordered render flow to `CL_ReadPackets -> Cbuf.Execute -> sendUpdates` to match legacy behavior and remove avoidable latency.
- Consequences: Command-style bind changes affect the same outgoing frame when queued before send.
- Status: accepted
- Date: unknown
- References: thread decision: “Quake-timed command execution”

### Decision: Keep bindings at `Cake` lifecycle, not `Game3dScreen`
- Context: `Game3dScreen` is recreated on map/reconnect; resetting binds there would lose runtime changes.
- Chosen Option & Rationale: Keep a single `ClientBindings` instance at app scope and inject into each `InputManager`.
- Consequences: Session-stable binds across map transitions; currently not mod-specific.
- Status: accepted
- Date: unknown
- References: thread discussion about ownership and reconnect behavior

### Decision: Split immediate vs command-style behavior
- Context: Input revamp needed parity with bind semantics while preserving usercmd stability.
- Chosen Option & Rationale:
  - immediate controls (`+forward`, `+attack`, `+use`, ...) map to counters used in `gatherInput`,
  - command-style binds (`use ...`, `inven`, `weapnext`, ...) queue commands.
- Consequences: Clear separation of movement/fire state from one-shot actions.
- Status: accepted
- Date: unknown
- References: thread requirement: two input types

### Decision: Runtime-only bindings for now
- Context: Persistence and per-mod config handling are not yet implemented in Cake.
- Chosen Option & Rationale: Keep binds in memory for current session only.
- Consequences: Re-launch resets to defaults; mod-specific binds remain an explicit follow-up.
- Status: accepted
- Date: unknown
- References: thread notes on deferring persistence

## Quirks & Workarounds
- `bind` key-name parsing uses aliases + `Input.Keys.toString` normalization.
  - Work with it: prefer canonical names (`w`, `ctrl`, `mouse1`, `mwheelup`, `enter`, `[`, `]`).
- `bind` commands are registered with replace semantics to avoid leaking of captured state.
- Debug toggle commands still exist (`toggle_skybox`, `toggle_level`, `toggle_entities`) but are intentionally unbound by default.

## Differences with IdTech2 (Quake2)
- Input routing uses LibGDX `InputMultiplexer` (game/menu/console processors), not legacy `key_dest` filtering in the key event layer.
- Decision: Cake will **not** mirror legacy key-destination filtering beyond current InputMultiplexer routing.
- Bind storage is runtime-only in this phase (no `config.cfg` read/write yet).
- Bind scope is session-global; it is not yet per-mod/profile.

## How to Extend
1. Add a new immediate action:
   - Extend `ImmediateAction` in `InputManager`.
   - Consume it in `gatherInput` (movement/button bit).
   - Bind it via defaults in `ClientBindings.installDefaultBindings()` if needed.
2. Add a new default command-style bind:
   - Add `setBindingByName(...)` in `ClientBindings.installDefaultBindings()`.
3. Add a new physical input alias:
   - Extend `keyAliases` in `ClientBindings`.
4. Add persistence (future work):
   - Serialize/restore `ClientBindings.listBindings()` per profile/mod.

## Open Questions
- Should bind sets be stored per mod (`baseq2` vs custom game dirs) once persistence is added?
