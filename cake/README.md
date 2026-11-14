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

Shared desktop window configuration, video platform, JVM restart helper and icon
loading. Each app owns its icons; see the [icon guide](asset-tools/branding/app-icons.md)
for assets and regeneration.

### `cake-viewer`

Standalone desktop launcher for the Cake Viewer.
Opens MD2 models, BSP maps, and PCX/WAL images; see the [viewer guide](../info/cake-viewer.md).

### [`asset-tools`](asset-tools/README.md)

Standalone desktop tooling for engine asset generation workflows.

## Building and running

### Running from sources

From repository root:

- `./gradlew :cake:cake-client:run` - runs the remote-only client.
- `./gradlew :cake:cake-fullgame:run` - runs the client with singleplayer and hosting support.
- `./gradlew :cake:cake-viewer:run --args="/absolute/path/to/file.md2"` - runs the viewer launcher directly.

Main desktop entrypoint:

- [`Lwjgl3GameLauncher.kt`](cake-client/src/main/kotlin/org/demoth/cake/lwjgl3/Lwjgl3GameLauncher.kt)

### JVM distributions

Build ZIPs that require Java 25 to run:

```sh
./gradlew :cake:cake-client:distZip :cake:cake-fullgame:distZip :cake:cake-viewer:distZip
```

ZIPs are under each app's `build/distributions/`. Extract the complete archive
and run `bin/cake-<app>` (`.bat` on Windows).

To bundle Java and a native launcher instead, build on the target OS and architecture:

```sh
./gradlew :cake:cake-client:jpackageImage :cake:cake-fullgame:jpackageImage :cake:cake-viewer:jpackageImage --no-configuration-cache
```

Application images are under each app's `build/jpackage/`.

### GitHub desktop packages

In GitHub Actions, select **Desktop packages → Run workflow**, or push a tag
matching `1.*` or `v1.*`. Download the archives from the run's **Artifacts**
section. Extract the complete archive; Java is bundled.

### Native executables

Experimental GraalVM builds require GraalVM 25 with Native Image. Set
`GRAALVM_HOME` to its installation, then run:

```sh
./gradlew -PenableGraalNative=true :cake:cake-client:nativeCompile
./gradlew -PenableGraalNative=true :cake:cake-viewer:nativeCompile
./gradlew -PenableGraalNative=true :cake:cake-fullgame:nativeCompile
```

## Playing and hosting

`cake-client` is a thin client - it can only connect to remote servers.

`cake-fullgame` also provides Singleplayer and Host Game menus, using the selected profile's game data and mod.

Console commands:

- `map <map>` / `gamemap <map>` — start a local game or change maps while hosting.
- `disconnect` — disconnect and stop the local game, retaining the idle Host.
- `killserver` — stop the hosted game, retaining the idle Host.

Saves are stored in `~/.cake/<profileId>/save/`. Console `load <slot>` restores a
game even while disconnected. `save <slot>` requires an active game.

Autosaves can be loaded but cannot be overwritten manually. Coop save/load uses the console
commands above.

## Demo recording and playback

Both launchers record protocol-34 demos and play classic protocol-26/31/32/34
`.dm2` files. Select a profile containing the required maps, models and sounds.

Console commands:

- `record example` — record the current local or remote game.
- `stop` — finish recording.
- `demomap example` — play `demos/example.dm2`, looking in profile recordings before game data.
- `demopause` — toggle playback pause.
- `demostop` or `disconnect` — stop playback.

Recordings are saved in `~/.cake/<profile-id>/demos/`. Nested names are supported;
existing recordings are not overwritten. Disconnecting also finishes recording.

In the content browser, double-click a demo or press Enter to play it. Opening
the menu pauses playback; closing it resumes. Browser-started playback returns
to the browser when finished or stopped. Extended/rerelease protocols and
cinematic recordings are unsupported.

## Subsystem Guides

- [Desktop video configuration](cake-client/README.md)
- [Configuration and asset ownership](core/src/main/kotlin/org/demoth/cake/config/README.md)
- [Asset resolution and loading](core/src/main/kotlin/org/demoth/cake/assets/README.md)
- [MD2 loading and rendering](core/src/main/kotlin/org/demoth/cake/assets/md2/README.md)
- [BSP loading and rendering](core/src/main/kotlin/org/demoth/cake/assets/bsp/README.md)
- [Input, bindings, and configuration persistence](core/src/main/kotlin/org/demoth/cake/input/README.md)
- [UI styles and menu architecture](core/src/main/kotlin/org/demoth/cake/ui/README.md)
- [Menu screens](core/src/main/kotlin/org/demoth/cake/stages/README.md)
- [Developer automation](core/src/main/kotlin/org/demoth/cake/automation/README.md)
- [Console](core/src/main/kotlin/org/demoth/cake/stages/console/README.md)
- [Ingame runtime](core/src/main/kotlin/org/demoth/cake/stages/ingame/README.md)
- [HUD layouts](core/src/main/kotlin/org/demoth/cake/stages/ingame/hud/README.md)
- [Effects and particles](core/src/main/kotlin/org/demoth/cake/stages/ingame/effects/README.md)
- [Shared virtual filesystem](../info/vfs.md)
