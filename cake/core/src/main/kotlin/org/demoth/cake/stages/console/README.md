# Developer Console

## Commands and history

- Enter echoes and runs the command.
- Tab completes command and cvar names. Shared prefixes expand and multiple matches are listed.
- Up and Down browse history and restore the draft when returning to the newest entry.
- Page Up, Page Down, and the mouse wheel scroll output. `clear` clears it.
- Each profile keeps up to 500 history entries in `$HOME/.cake/<profileId>/console-history.txt`. A leading space runs a command without saving it to history.

## Engine messaging contract

| Function | Level | Description | Cake console style | Stdout color |
|---|---|---|---|---|
| `Com.Print` | `PRINT` | Gameplay and command output. Always shown. | Default | Default |
| `Com.Error(message)` | `ERROR` | Reports an operation failure. The caller handles recovery. | Red | Red |
| `Com.Warn` | `WARN` | Recoverable problems and rejected requests. | Yellow | Yellow |
| `Com.Info` | `INFO` | Significant lifecycle events. | Default | Default |
| `Com.Debug` | `DEBUG` | Occasional developer detail. | Muted | Gray |
| `Com.Trace { ... }` | `TRACE` | Frequent diagnostics. Builds the message only when enabled. | More muted | Dim default |

Choose the level from context the caller already has. Keep the existing level
when a finer distinction would add behavior solely for logging. Use single
quotes around interpolated values in diagnostic prose.

## Log levels

All levels except print are diagnostic log levels.

- `loglevel` defaults to `INFO`. From least to most verbose, the levels are `ERROR`, `WARN`, `INFO`, `DEBUG`, and `TRACE`.
- Subsystem overrides use `GLOBAL` to follow `loglevel`.
- Click the gear to open log settings. Escape dismisses the open selector or the popover.
- Changing **Global** in the popover sets `loglevel` and resets every subsystem override to `GLOBAL`.
- Threshold changes apply to new output in the process, including an embedded server. `developer` controls developer features such as automation.

## Logging subsystems

Choose the subsystem that owns the operation. For example, Cake's packet handling uses `Network`, its render statistics 
use `Render`, and its missing-texture fallback uses `Client`.

| Subsystem | Cvar | Scope |
|---|---|---|
| Client | `loglevel_client` | Cake input, audio, asset loading, demos, profiles, and client session state. |
| FileSystem | `loglevel_filesystem` | Virtual filesystem lookup, mounts, and package indexing. |
| Game | `loglevel_game` | Gameplay code in the `game` module. |
| Network | `loglevel_network` | Transport, packets, handshakes, snapshot deltas, downloads, and rcon across client and server. |
| Render | `loglevel_render` | Cake rendering, including graphics modes, framebuffers, screenshots, and cinematic frame upload. |
| Server | `loglevel_server` | Dedicated and embedded hosting, including game instances, map transitions, saves, and client slots. |
| Common | `loglevel_common` | Shared engine code such as commands, cvars, basically everything that didn't fit into other systems.|

## Player message priority

`msg` filters server print messages before they reach the HUD and console.

- `0` accepts every print priority.
- `1` accepts death, critical, and chat messages. This is the default.
- `2` accepts critical and chat messages.
- `3` accepts chat only.
- `4` suppresses all server print priorities.

Center prints use a separate message type and are independent of `msg`.

## Colors

The log settings labels use the Cake console style for each row's effective level.

`colorterminal` is an archived boolean that defaults to `true`. Stdout colors require
an interactive terminal with a nonempty `TERM` other than `dumb`. Pipes and files
receive plain text.
