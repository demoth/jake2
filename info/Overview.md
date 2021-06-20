#Jake2 overview

The project consists of several modules:

  * `game` - game related logic: monsters, items, weapons, game rules
  * `server` - server runtime: keeps master game state, sends game state updates to clients, receives client input 
  (over the network) and routes it to the `game`
  * `client` - client is responsible for handling updates from the server, drawing nice picture, playing sounds,
  getting inputs from human and send them over the net to the `server`
  
In addition to that there are several auxiliary modules:

  * `qcommon` - a common module with necessary data classes, utility functions, file formats, etc
  * `dedicated` - **executable** for the dedicated server - contains `server` and `game` modules.
  * `fullgame` - **executable** with all the modules (except `dedicated`), with single/multiplayer modes
  * `maptools` - set of tools (WIP) for map -> bsp pipeline
  
# Module `game`

Game module handles all the gameplay code like movement, monsters behavior, weapons code, triggers etc.

This was a usual place for modders to start in the original quake version.
The famous mission pack and mods are actually the `game` + resources.

All game entities (`SubgameEntity.java`) are created, updated and removed in this module. 

# Module `server`

Server manages:
 * connected clients - array of client states
 * bsp model - provide collisions functions to the `game` module
 * networking code - sends/receives updates

# Notable patterns

Some of the patterns observed in the project. Not necessarily bad.

## State management

The structure design of the quake 2 is basically a set of global variables, and a set of functions.
You can see functions changing state of some global static field very often. 
Apart from the problem of uncontrolled state scope and visibility, such approach makes jake2 very hard to redesign, refactor or introduce any substantial changes.

**state**: in progress - introduce necessary class structure for state 

## God object
https://en.wikipedia.org/wiki/God_object

Quake uses `edict_t` & `SubgameEntity` (introduced in jake2) structures that have all possible fields for every scenario.
Thus, a debris gib, a monster and an ammo clip will have the same class.

**state**: todo - split into components

## Behavior serialization

During entity serialization (when the game is saved) along with the field values the behavior should be saved.
To achieve that, so called adapters(`EntUseAdapter`, `EntTouchAdapter`, etc) are used. 
These are "named" functions - an interface with String ID (see jake2.game.SuperAdapter).
When such property is serialized - the Id is written to the file.
During deserialization the id is read from the file and corresponding adapter is looked up in the registry.
This requires the registry to be build beforehand and kept consistent between save/load.

**state**: ok

## Threading model

Quake2 is single threaded, so as Jake2. All IO operations and computation are done in the same thread.
Although the performance of jake2 is not a problem at the moment (mostly because it uses too little resources),
splitting io and non io operation will improve execution structure.
In future when jake can run multiple map instances in one process
introduction of non-blocking io can improve thread utilization even more.

**state**: todo