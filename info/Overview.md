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