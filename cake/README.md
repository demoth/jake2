# Cake Client

Cake is the new Jake2 client implementation built on libGDX with the LWJGL3 desktop backend.

## Modules

### `core`

Main game client logic: rendering, audio, input, HUD, networking integration, and gameplay screen flow.

### `cake-client`

Remote-only desktop launcher for `core`.

### `cake-fullgame`

Desktop application containing Cake, server, and game modules. Migrated from the
legacy `fullgame` module, retaining `build.gradle.kts` and the Java entrypoint
`jake2.fullgame.Jake2` history. Run with `./gradlew :cake:cake-fullgame:run`.

`EmbeddedCakeHost` owns an embedded server, started after the client applies its
selected profile. Console hosting automatically attaches the local player through
loopback; hosting menus are subsequent work. The remote-only launcher reports `REMOTE_ONLY`.

### `desktop-common`

Shared game window configuration, video platform, JVM restart helper and desktop
icons. Both game launchers use `CakeDesktopLauncher`; the model viewer shares
the restart helper and icons while retaining its own window configuration.

### `cake-modelviewer`

Standalone desktop launcher for the Cake model viewer.

### [`engine-tools`](engine-tools/README.md)

Standalone desktop tooling for engine asset generation workflows.

## Building and running

### Running

From repository root:

- `./gradlew :cake:cake-client:run` - runs the desktop client launcher directly.
- `./gradlew :cake:cake-modelviewer:run --args="/absolute/path/to/file.md2"` - runs the model viewer launcher directly.

Main desktop entrypoint:

- [`Lwjgl3GameLauncher.kt`](cake-client/src/main/kotlin/org/demoth/cake/lwjgl3/Lwjgl3GameLauncher.kt)

### JVM distributions

Build portable ZIPs with Java 25:

```sh
./gradlew :cake:cake-client:distZip :cake:cake-modelviewer:distZip
./gradlew :cake:cake-fullgame:distZip
```

Each launcher's `build/distributions/` contains a ZIP with `bin/` launch scripts
and `lib/` application/dependency JARs. Extract the entire ZIP and run
`bin/cake-client`, `bin/cake-fullgame`, or `bin/cake-modelviewer` (the `.bat` script on Windows).
Pass an asset path to the model viewer; Quake 2 game data remains external.

For a local installation, use `:cake:cake-client:installDist` or
`:cake:cake-modelviewer:installDist`; scripts are under the corresponding
`build/install/<application>/bin/` directory. JARs under `build/libs/` are normal
project JARs and need the distribution classpath. Use `distZip` instead of the
former custom `dist` task; standalone fat JARs are no longer produced.

The existing launcher helper supplies macOS first-thread startup by restarting
the JVM when needed. ZIPs require Java; bundled-runtime images and native
executables use the separate Beryx and Graal tasks.

Configuration cache is enabled for ordinary builds, JVM distributions, and run
tasks. Beryx 2.0.1 still requires `--no-configuration-cache` for
`runtime`, `runtimeZip`, `jpackageImage`, and `jpackage`, for example:

```sh
./gradlew :cake:cake-client:runtimeZip --no-configuration-cache
./gradlew :cake:cake-client:jpackageImage --no-configuration-cache
```

`runtimeZip` writes `build/image.zip`; `jpackageImage` writes an application
under `build/jpackage/`. Use the corresponding model-viewer tasks for that app.
These outputs bundle the project Java toolchain and target the build machine's
OS and architecture. Installers remain disabled.

### Native executables

Graal support is opt-in. Enable the native plugin and helpers when building an
experimental native executable:

```sh
./gradlew -PenableGraalNative=true :cake:cake-client:nativeCompile
./gradlew -PenableGraalNative=true :cake:cake-modelviewer:nativeCompile
```

These tasks require GraalVM 25 or newer with Native Image. Set `GRAALVM_HOME` to
that installation; Gradle can continue running on the regular project JDK.
Native resources are detected from the runtime classpath, including repository
assets and dependency libraries. Ordinary JVM builds and ZIPs omit the Graal helpers.
Fullgame native-image support is not verified; embedded game reflection and save
serialization need additional reachability coverage before that is supported.

## Hosting boundary

`CakeHost` lives in core and is injected into `Cake` by the launcher. Its request
captures the profile, map, mode, difficulty, client count and hostname. The host
owns validation and reports a typed start result and session state. All host
operations belong to the application thread. `stop` must be idempotent, and pause
requests must not pause multiplayer. Core has no dependency on server or game.

Fullgame exposes Singleplayer (difficulty and New Game) and Multiplayer → Host Game
(map text, deathmatch/coop, player count, hostname, and coop difficulty). Disconnect
before starting another session. Thin builds disable these actions. Availability hints are deferred to future tooltips.
Host Game uses the same request/start path as the console; server-side validation
and player-count normalization remain authoritative.

On profile selection, Cake registers only `alias newgame ...` from the VFS-selected
`default.cfg`; it does not apply the file's other settings/bindings. New Game prepares
the embedded filesystem and server commands, queues `newgame`, then connects the
local client when the alias starts a map or cinematic. Missing definitions disable
New Game. Startup failures return feedback to the setup menu. Map discovery, custom
port/password/rules controls, and save browsing remain deferred.

Constructing either host implementation has no server-side effects. In fullgame,
`host_start <map> [singleplayer|deathmatch|coop] [skill 0..3] [maxclients]` starts
the server using the active profile. Defaults are singleplayer, skill 1 and
1/8/4 clients for singleplayer/deathmatch/coop. Existing server rules normalize
client counts. Map names omit `maps/` and `.bsp`, e.g. `host_start base1`.
`host_stop`, disconnect, remote join and application exit stop the session.
Profile changes are blocked until the session stops. Successful startup attaches
the local client through the normal connection/precache/spawn protocol.
Use `map <map>` or `gamemap <map>` during hosting to change levels, or `reconnect`
to reattach. `connect localhost`/`connect loopback` attach to an already running
host; use `127.0.0.1:<port>` to join a separate server process on the same machine.

Singleplayer uses only in-process loopback and opens no UDP sockets. Hosted coop
and deathmatch open the server UDP socket while the local player still uses
loopback. Remote joining opens only the client UDP socket and stops any owned
host first. Client reset never closes the server socket; whole-session teardown
clears stale loopback packets and netchan state. Handshake replies must match the
current connection attempt. The protected startup `port` setting remains the
server port source; this step adds no per-session port option. Bind failures
reject hosting and release partially opened channels so a retry is possible.

The server reads the selected profile's base game and optional mod through
EngineVfs. Saves use `~/.cake/<profileId>/save/`; stopping restores the previous
engine write root. A selected mod directory must exist; missing mods cannot silently
fall back to the base game. Both resolvers select the profile's mod, while client
resolver changes remain independent of the hosted EngineVfs.

With a host running and the player spawned, `save <slot>` and `load <slot>` use
that profile's save directory. Slot names must be single directory names (no
path separators, traversal, colon, or control characters); `current` is reserved
for the engine. Different profiles may reuse the same slot name independently.
Stop/disconnect before switching profiles. Loading a save without first starting
a host is deferred.

The existing map initialization path applies game mode and
difficulty and creates autosaves. It also applies pending global cvars, including
video settings; isolating video settings is explicitly deferred as low priority.

Each application frame reads client packets, executes the shared command buffer,
updates the server, then sends client input. Server replies are read on the next
frame. There is no second command loop or server thread. Fractional milliseconds
are retained, `fixedtime` overrides `timescale`, and elapsed simulation time is
capped at 100 ms per frame without retaining stall debt. The server retains its
existing 100 ms simulation scheduler.

Menus, the console and application pause requests pause singleplayer simulation.
Multiplayer continues running. Desktop focus loss/minimization does not suspend
the application loop, and server updates precede any zero-size framebuffer return.
Shutdown releases owned operator commands, clears deferred map commands and closes
sockets; recoverable server drops return to the console. Map transitions preserve
the host, server socket and reconnect traffic.

Hosting lifecycle tests use the retained test maps without external game data.
The optional application-loop check uses an invisible GL window and a temporary
profile: `CAKE_HOST_GL_TESTS=1 ./gradlew :cake:cake-fullgame:test --tests '*CakeHostApplicationTest'`.

The optional gameplay check requires an installed Quake 2 base game and uses
temporary profiles/saves with hidden windows. It checks local spawn/movement,
map transitions, restarts, a separate thin-client process joining hosted coop and
deathmatch, and switching from hosting to a separate UDP server:

```sh
CAKE_HOST_GAME_TESTS=1 CAKE_TEST_BASEDIR=/path/to/Quake2 ./gradlew :cake:cake-fullgame:test --tests '*CakeLocalSessionGlTest'
```

A separate check verifies a mod-only map on both client and server, player-health
restoration through console save/load, and reuse of the same slot under two
profiles. It uses a temporary content directory referencing installed base assets:

```sh
CAKE_HOST_GAME_TESTS=1 CAKE_TEST_BASEDIR=/path/to/Quake2 ./gradlew :cake:cake-fullgame:test --tests '*CakeProfileSaveGlTest'
```

The migrated `GameImportsTest` and map fixtures retain the original server/game
smoke coverage using JUnit 5 and EngineVfs. Legacy renderer debug programs,
the obsolete `Jake2.Init` example and the standalone buffer-allocation experiment
were retired with the launcher replacement; their sources remain in Git history.

## Subsystem Guides

- [Desktop video configuration](cake-client/README.md)
- [Configuration and asset ownership](core/src/main/kotlin/org/demoth/cake/config/README.md)
- [Asset resolution and loading](core/src/main/kotlin/org/demoth/cake/assets/README.md)
- [MD2 loading and rendering](core/src/main/kotlin/org/demoth/cake/assets/md2/README.md)
- [BSP loading and rendering](core/src/main/kotlin/org/demoth/cake/assets/bsp/README.md)
- [Input, bindings, and configuration persistence](core/src/main/kotlin/org/demoth/cake/input/README.md)
- [UI styles and menu architecture](core/src/main/kotlin/org/demoth/cake/ui/README.md)
- [Menu screens](core/src/main/kotlin/org/demoth/cake/stages/README.md)
- [Console](core/src/main/kotlin/org/demoth/cake/stages/console/README.md)
- [Ingame runtime](core/src/main/kotlin/org/demoth/cake/stages/ingame/README.md)
- [HUD layouts](core/src/main/kotlin/org/demoth/cake/stages/ingame/hud/README.md)
- [Effects and particles](core/src/main/kotlin/org/demoth/cake/stages/ingame/effects/README.md)
- [Shared virtual filesystem](../qcommon/vfs/README.md)
