# Cake Input Bindings

## Responsibilities

- `ClientBindings` stores runtime bindings and dispatches keyboard, mouse-button,
  and wheel events. It registers `bind`, `unbind`, `unbindall`, and `bindlist`.
- `InputManager` tracks immediate actions, updates local view angles, and constructs
  `MoveMessage` packets containing movement commands.
- `Cake` owns input-context routing and profile configuration persistence.

## Input Flow

Bindings have two execution paths:

- `+commands`, such as `+forward` and `+attack`, execute immediately through
  `Cmd.ExecuteString`. Pressing activates the action; releasing executes the
  corresponding `-command`.
- Other commands, such as `use shotgun` and `weapnext`, enter `Cbuf` on press.

The main frame loop processes incoming packets and queued commands before updating
local input and running client command steps. This lets queued bindings affect
commands constructed in the same frame.

```text
LibGDX input events
  -> InputManager -> ClientBindings
       -> +command / -command: immediate action state
       -> other commands: Cbuf

Cake.render()
  -> CL_ReadPackets()
  -> Cbuf.Execute()
  -> InputManager.updateLocalInput(): view angles and pending mouse look
  -> fixed client command steps: InputManager.buildMoveMessage() -> transmit
```

Local input updates run every render frame. Movement commands are constructed at
the fixed cadence controlled by `cl_maxfps`, independently of render FPS.

## Lifetime and Persistence

`Cake` owns one `ClientBindings` instance and passes it to each new `InputManager`.
Bindings therefore survive map changes and reconnects, even though the game screen
and its input manager are replaced.

Bindings and archived cvars persist in `$HOME/.cake/<profileId>/config.cfg`.
Cake loads the selected profile configuration at startup. Switching profiles while
disconnected saves the outgoing configuration and loads the selected one. The
generated script uses `unbindall`, `bind`, and cvar assignments; loading executes
it through the command buffer.

Profiles select configurations; there is no separate binding table automatically
keyed by mod. If a selected profile has no configuration file, loading leaves the
existing runtime bindings unchanged. This is Cake-owned persistence rather than
the full legacy `default.cfg -> config.cfg -> autoexec.cfg` startup sequence.

## Behaviour Guarantees

- Non-`+` bindings execute on key/button press only.
- `+command` bindings emit once per physical press and release with a matching
  `-command`; held-key auto-repeat does not reactivate them.
- Wheel bindings are transient: a `+command` receives both press and release
  during the wheel event.
- Switching between game, menu, and console contexts clears held gameplay state.
- Any active immediate action sets `BUTTON_ANY` in the generated movement command,
  preserving intermission progression behaviour.

## Integration Notes

Input routing uses LibGDX `InputMultiplexer` for game, menu, and console contexts
instead of the legacy client's `key_dest` filtering.

Key names are normalised through aliases and `Input.Keys.toString`; examples include
`w`, `ctrl`, `mouse1`, `mwheelup`, `enter`, `[`, and `]`. Binding commands use replace
semantics when registered so recreated owners do not leave captured state behind.

Debug toggle commands still exist (`toggle_skybox`, `toggle_level`, `toggle_entities`)
but are intentionally unbound by default.
