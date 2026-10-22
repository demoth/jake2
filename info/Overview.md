# Jake2 overview

The project consists of several modules:

  * `game` - game related logic: monsters, items, weapons, game rules
  * `server` - server runtime: keeps master game state, sends game state updates to clients, receives client input 
  (over the network) and routes it to the `game`
  * `client` - (Deprecated) client is responsible for handling updates from the server, drawing picture, playing sounds,
  getting inputs from human and sending them over the net to the `server` (excluded from the build)
  * `cake` - new reimplementation of Jake2 client with libGDX engine
  
In addition to that there are several auxiliary modules:

  * `qcommon` - a common module with necessary data classes, utility functions, file formats, etc
  * `dedicated` - **executable** for the dedicated server - contains `server` and `game` modules.
  * `cake-client` - **executable** for the remote-only Cake client
  * `cake-fullgame` - **executable** combining Cake with the `server` and `game` modules for singleplayer and hosting
  * `maptools` - set of tools (WIP) for map -> bsp pipeline

# Module `game`

Game module handles all the gameplay code like movement, monsters behavior, weapons code, triggers etc.

This is a usual place for the modders to start in the original quake version.
The famous mission packs and mods are actually the `game` + resources.

All game entities (`GameEntity.java`) are instantiated, updated and disposed in this module.

The `game` and `server` modules are interacting via the `GameImports` and `GameExports` interfaces.

# Module `server`

Server manages:

* connected clients - array of client states
* bsp model - provide collisions/visibility functions to the `game` module
* networking code - sends/receives updates

# Module `cake`

This module is the client facing interactive application and is responsible for gathering input, rendering the level and entities and playing sounds.

# Module `client` (deprecated)

This module is kept for history and reference and is not part of the active build.
Where it makes sense, it is possible to move an original file or class (preserving the history) into the new `cake` module and refactor later.


# Notable patterns

Some patterns observed in the project. Not necessarily bad.

## State management

The structure design of the quake 2 is basically a set of global variables, and a set of functions.
You can see functions changing state of some global static field very often. 
Apart from the problem of uncontrolled state scope and visibility, such approach makes jake2 very hard to redesign, refactor or introduce any substantial changes.

## Entity representation

`GameEntity` holds fields used by different entity kinds, including monsters, items
and debris. Existing character/animation experiments do not replace the general
entity representation.

## Behavior serialization

During entity serialization (when the game is saved) along with the field values the behavior should be saved.
To achieve that, so called adapters(`EntUseAdapter`, `EntTouchAdapter`, etc) are used.
These are "named" functions - an interface with String ID (see `jake2.game.adapters.SuperAdapter`).
When such property is serialized - the Id is written to the file.
During deserialization the id is read from the file and corresponding adapter is looked up in the registry.
This requires the registry to be built beforehand and kept consistent between save/load.


## Threading model

Cake fullgame updates the client and embedded server on the libGDX application
thread. Multiple game instances run within that server process; this does not
imply concurrent mutation of engine state. UDP sockets use nonblocking
`DatagramChannel` reads. AssetManager can perform loader work on its loader thread,
so not all application I/O and computation happen on the simulation thread.

The hosting lifecycle is documented in [the Cake guide](../cake/README.md).
