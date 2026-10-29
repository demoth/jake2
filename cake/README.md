# Cake Client

Cake is the new Jake2 client implementation built on libGDX with the LWJGL3 desktop backend.

## Modules

### `core`

Main game client logic: rendering, audio, input, HUD, networking integration, and gameplay screen flow.

### `cake-client`

Remote-only desktop launcher for `core`.

### `cake-fullgame`

Desktop application containing Cake, server, and game modules, with Singleplayer
and Host Game menus. Core accesses hosting through `CakeHost`; only
`cake-fullgame` depends on the server and game modules.

### `desktop-common`

Shared game window configuration, video platform, JVM restart helper and desktop
icons. Both game launchers use `CakeDesktopLauncher`; the viewer shares
the restart helper and icons while retaining its own window configuration.

The icons in `desktop-common/icons` and `desktop-common/src/main/resources` are
the default libGDX Liftoff placeholders, pending Cake branding.

### `cake-viewer`

Standalone desktop launcher for the Cake Viewer.
Opens MD2 models, BSP maps, and PCX/WAL images; see the [viewer guide](../info/cake-viewer.md).

### [`engine-tools`](engine-tools/README.md)

Standalone desktop tooling for engine asset generation workflows.

## Building and running

### Running

From repository root:

- `./gradlew :cake:cake-client:run` - runs the remote-only client.
- `./gradlew :cake:cake-fullgame:run` - runs the client with singleplayer and hosting support.
- `./gradlew :cake:cake-viewer:run --args="/absolute/path/to/file.md2"` - runs the viewer launcher directly.

Main desktop entrypoint:

- [`Lwjgl3GameLauncher.kt`](cake-client/src/main/kotlin/org/demoth/cake/lwjgl3/Lwjgl3GameLauncher.kt)

### JVM distributions

Build portable ZIPs with Java 25:

```sh
./gradlew :cake:cake-client:distZip :cake:cake-viewer:distZip
./gradlew :cake:cake-fullgame:distZip
```

Each launcher's `build/distributions/` contains a ZIP with `bin/` launch scripts
and `lib/` application/dependency JARs. Extract the entire ZIP and run
`bin/cake-client`, `bin/cake-fullgame`, or `bin/cake-viewer` (the `.bat` script on Windows).
Pass an asset path to the viewer; Quake 2 game data remains external.

For a local installation, use `:cake:cake-client:installDist` or
`:cake:cake-viewer:installDist`; scripts are under the corresponding
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
under `build/jpackage/`. Use the corresponding viewer tasks for that app.
These outputs bundle the project Java toolchain and target the build machine's
OS and architecture. Installers remain disabled.

### Native executables

Graal support is opt-in. Enable the native plugin and helpers when building an
experimental native executable:

```sh
./gradlew -PenableGraalNative=true :cake:cake-client:nativeCompile
./gradlew -PenableGraalNative=true :cake:cake-viewer:nativeCompile
./gradlew -PenableGraalNative=true :cake:cake-fullgame:nativeCompile
```

These tasks require GraalVM 25 or newer with Native Image. Set `GRAALVM_HOME` to
that installation; Gradle can continue running on the regular project JDK.
Native resources are detected from the runtime classpath, including repository
assets and dependency libraries. Ordinary JVM builds and ZIPs omit the Graal helpers.
All three launchers use the shared native configuration and explicit reflection
metadata for audio and Scene2D skin/pool classes. The `game` module supplies
type registrations for its callback preload list; keep these aligned with
`GameExportsImpl.preloadclasslist`. The `qcommon`, `server`, and `game` modules
also register their save DTOs for Jackson reflection.
Native compilation, basic startup, local map transitions, and console save/load
are verified on Linux ARM64. Multiplayer hosting still needs native runtime
validation.

## Playing and hosting

`cake-client` connects to remote servers. `cake-fullgame` also provides Singleplayer
and Host Game menus, using the selected profile's game data and mod.

Hosting automatically connects the local player. Disconnect before switching
profiles or starting another session.

Switching profiles resets built-in gameplay startup settings before loading the
selected profile's config. Video and client preferences are not reset. Initial
startup preserves command-line overrides; loading a save restores its saved
latched settings, including difficulty.

Console equivalents:

- `map <map>` / `gamemap <map>` — start a local game or change maps while hosting.
- `disconnect` — disconnect and stop the local game, retaining the idle Host.
- `killserver` — stop the hosted game, retaining the idle Host.

For multiplayer, set `coop`, `deathmatch`, and `maxclients` before starting the
map, for example `set coop 1; set deathmatch 0; set maxclients 4; map base1`.
The Host Game menu configures these settings for you.

Saves are stored in `~/.cake/<profileId>/save/`. Console `load <slot>` restores a
game even while disconnected. `save <slot>` requires an active game.

Fullgame's Singleplayer submenu contains New Game, Load Game and Save Game.
New Game opens difficulty selection. The submenu stays accessible during play,
with each action enabled when eligible; Back from these screens returns there.
Escape goes back through the menus and returns to gameplay from the main menu.
Saved games refresh automatically whenever Load Game or Save Game is opened.
Each screen shows a scrolling checkpoint list alongside preview space and save
details, with actions along the bottom. Manual saves from the menu include a
thumbnail of the scene without menus (up to 320×240). Missing or unreadable
thumbnails show a placeholder; image capture/write failures leave the save usable
and display a warning. Autosaves and console saves do not capture thumbnails.
Replacing a checkpoint clears its old contents, including any previous thumbnail.
New Save creates a checkpoint; Save asks before replacing the selected manual save.
Delete asks before removing the selected checkpoint. Autosave is
listed for loading and reserved against manual overwrite. Save requires a living,
spawned local player. Loading is available from startup and during singleplayer;
the singleplayer respawn action opens Load Game automatically.

`menu_loadgame` and `menu_savegame` open those screens from the console. Errors
remain visible in the menu so an invalid save or failed restore can be retried.
Deathmatch does not create autosaves. Coop save/load remains console-only.

## Demo playback

Both `cake-client` and `cake-fullgame` play protocol-34 client recordings (`.dm2`)
without running a game server. Select a profile containing the recording's maps,
models, and sounds, then open the client console:

- `demomap example` — play `demos/example.dm2` from the active profile's game data.
- `demomap demos/folder/example.dm2` — play a nested demo, including archive entries.
- `demopause` — toggle pause; opening the menu or suspending the application also pauses playback.
- `demostop` or `disconnect` — stop and return to the console.

EOF and playback errors return to the console. Starting another demo replaces the
current session. The camera follows the recorded player; movement and mouse look
do not control playback. Opening the console leaves playback running.
Base-game and expansion temporary effects use Cake's rendering pipeline;
visual fidelity varies by effect family.

Protocol-26 demos shipped with older Quake II releases, extended/rerelease
protocols, and cinematic recordings are unsupported. For a compatible test file,
record a session in Yamagi using `record example`, then `stop`, and copy its
`demos/example.dm2` into the selected profile's game-data directory.

The hidden-window playback integration tests accept an external recording through
`CAKE_TEST_DEMO=/absolute/path/example.dm2`, alongside `CAKE_HOST_GAME_TESTS=1`
and `CAKE_TEST_BASEDIR`. They run the untouched recording and controlled view
updates in both host configurations; game content and recordings stay outside git.

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
