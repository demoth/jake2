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

The module currently provides the hosting composition boundary only: its
`EmbeddedCakeHost` reports `NOT_READY` and rejects start requests. It launches
the existing Cake client; local server lifecycle, networking and hosting menus
are subsequent work. The remote-only launcher reports `REMOTE_ONLY`.

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

TSK-16 only installs this boundary; it does not yet call host operations from the
frame loop. Constructing either current host implementation has no server-side
effects. Server construction and socket policy belong to the later integration.

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
