# Cake Client

Cake is the new Jake2 client implementation built on libGDX with the LWJGL3 desktop backend.

## Modules

### `core`

Main game client logic: rendering, audio, input, HUD, networking integration, and gameplay screen flow.

### `cake-client`

Desktop platform launcher/wrapper for `core`.

### `cake-modelviewer`

Standalone desktop launcher for the Cake model viewer.

### `engine-tools`

Standalone desktop tooling for engine asset generation workflows.

## Running

From repository root:

- `./gradlew :cake:cake-client:run` - runs the desktop client launcher directly.
- `./gradlew :cake:cake-modelviewer:run --args="/absolute/path/to/file.md2"` - runs the model viewer launcher directly.

Main desktop entrypoint:

- [`Lwjgl3GameLauncher.kt`](cake-client/src/main/kotlin/org/demoth/cake/lwjgl3/Lwjgl3GameLauncher.kt)

## JVM distributions

Build portable ZIPs with Java 21:

```sh
./gradlew :cake:cake-client:distZip :cake:cake-modelviewer:distZip
```

Each launcher's `build/distributions/` contains a ZIP with `bin/` launch scripts
and `lib/` application/dependency JARs. Extract the entire ZIP and run
`bin/cake-client` or `bin/cake-modelviewer` (the `.bat` script on Windows).
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
tasks. The current Beryx runtime plugin requires `--no-configuration-cache` for
`runtime`, `runtimeZip`, `jpackageImage`, and `jpackage`, for example:

```sh
./gradlew :cake:cake-client:jpackageImage --no-configuration-cache
```

## Native executables

Graal support is opt-in. Enable the native plugin and helpers when building an
experimental native executable:

```sh
./gradlew -PenableGraalNative=true :cake:cake-client:nativeCompile
./gradlew -PenableGraalNative=true :cake:cake-modelviewer:nativeCompile
```

These tasks require GraalVM with Native Image; the current configuration requires
version 23.0 or newer. Ordinary JVM builds and ZIPs omit the Graal helpers.

## BSP World Renderer

Cake now uses a dedicated Q2PRO-inspired world BSP batch renderer by default (no legacy per-face world `ModelBatch` path).

Runtime diagnostics:

- `r_bsp_batch_debug` (default `0`) - prints throttled world/entity/sprite/beam/particle submission stats.

Q2PRO references for the target path:

- `q2pro/src/refresh/world.c` (`GL_DrawWorld`, `GL_WorldNode_r`)
- `q2pro/src/refresh/tess.c` (`GL_AddSolidFace`, `GL_DrawSolidFaces`, `GL_Flush3D`)

Profiling workflow:

1. Enable debug graphs:
   - `set r_debug_drawcalls 1`
   - `set r_debug_texturebindings 1`
2. Enable `r_bsp_batch_debug 1` and capture representative maps/views.
3. Enable gameplay fps overlay (Yamagi-style):
   - `set cl_showfps 1` (avg fps)
   - `set cl_showfps 2` (min/max/avg fps)
   - `set cl_showfps 3` (adds frame-time line)

Screenshot output:

- `screenshot` command writes PNG files to `$HOME/.cake/<mod>/scrnshot/`.
- Shared VFS diagnostics commands are available in Cake too:
  - `fs_files`
  - `fs_mounts`
  - `fs_overrides`

### Bitmap Font Tool

Generate a libGDX bitmap font (`.fnt` + `.png`) from a TTF:

- `./gradlew :cake:engine-tools:run --args="--ttf /absolute/path/MyFont.ttf --out /absolute/output/dir --name font --size 21"`

Notes:

- Uses libGDX default glyph set: `FreeTypeFontGenerator.DEFAULT_CHARS`.
- Output is intended for manual copy into engine assets.

## Client Overview

### [`Cake.kt`](core/src/main/kotlin/org/demoth/cake/Cake.kt)

App-level client entrypoint and lifecycle owner:

- initializes commands/cvars/network state,
- initializes and owns shared asset/input systems,
- switches between menu/console/game contexts,
- creates and disposes game screens.

### [`Game3dScreen.kt`](core/src/main/kotlin/org/demoth/cake/stages/ingame/Game3dScreen.kt)

In-game screen (per-level lifecycle) responsible for:

- world/entity/effects rendering,
- HUD integration,
- frame-level input consumption,
- server message processing for active gameplay.

## Module Docs

Detailed docs for specific Cake subsystems:

- [`assets`](core/src/main/kotlin/org/demoth/cake/assets/README.md)
- [`input`](core/src/main/kotlin/org/demoth/cake/input/README.md)
- [`ui`](core/src/main/kotlin/org/demoth/cake/ui/README.md)
- [`stages/ingame/hud`](core/src/main/kotlin/org/demoth/cake/stages/ingame/hud/README.md)
- [`stages/ingame/effects`](core/src/main/kotlin/org/demoth/cake/stages/ingame/effects/README.md)
