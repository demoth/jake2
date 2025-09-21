# Cake client

Cake is a fresh implementation of the Jake2 client part on the modern libraries: libGDX with the LWJGL 3 backend.

## Modules

### `core`

This is the main module for the client app. All logic for rendering, audio and input is here.

### `lwjgl3`

This is a platform (Desktop LWJGL version 3) wrapper and executable for the `core`. 

## Client overview

### [Cake.kt](core/src/main/java/org/demoth/cake/Cake.kt) 

The entrypoint for the application, it lives for the whole duration of the app. 
It handles the initialization of the network connections, creation/disposal of the game screens and menus.

### [Game3dScreen.kt](core/src/main/java/org/demoth/cake/stages/Game3dScreen.kt)

The actual screen where the game action is happening.
Lives for the duration of the current map/level.
Responsible for 
 - rendering the level, entities, HUD, playing sounds, 
 - gathering session related input (like walking, action buttons)
 - handling the network messages with game/player updates