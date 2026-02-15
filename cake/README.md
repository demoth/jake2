# Cake Client

Cake is the new Jake2 client implementation built on libGDX with the LWJGL3 desktop backend.

## Modules

### `core`

Main game client logic: rendering, audio, input, HUD, networking integration, and gameplay screen flow.

### `lwjgl3`

Desktop platform launcher/wrapper for `core`.

## Running

From repository root:

- `./gradlew run` - runs the default desktop client launcher.
- `./gradlew :cake:lwjgl3:run` - runs the LWJGL3 launcher directly.

Main desktop entrypoint:

- [`Lwjgl3GameLauncher.kt`](lwjgl3/src/main/kotlin/org/demoth/cake/lwjgl3/Lwjgl3GameLauncher.kt)

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
