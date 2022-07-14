# New cvars
_Add new cvars here_

# New console commands
_Add new console commands here_

 * `listmaps` - prints list of maps from maps.lst

### Testing
 * `spawn <classname>` - spawns new entity in front of the player based on the provided class name. Works only with point entities which have a spawn function.
 * `spawnrandommonster` - spawns random monster in front of the player (no bosses)
 * `parse <entity string>` - parse and spawn a new entity. You can provide multiple key/value pairs, similar to the entity string in a `.bsp` file. Works only with point entities. Can spawn multiple entities but they all will be located at the same coordinates. Example: `parse { "item" "ammo_bullets" "classname" "monster_infantry" }`

### Multi-instance
 * `games` - prints list of game names (Multiinstance feature)
 * `spamgames n` - creates `n` games with `base1` map (Used for testing, to be removed in future)
 * `gamemap <map> <n>` - puts the client with index `<n>` to a separate game with a `<map>`
