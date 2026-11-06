# BSP map tools

Build the command-line distribution with `./gradlew :maptools:installDist`.

```sh
maptools/build/install/maptools/bin/maptools catalog /path/to/map.bsp /path/to/another.bsp > report.json
```

`catalog` writes a JSON array containing map titles, texture paths, worldspawn sky,
player starts, entity classes, changelevel destinations, explicit entity resource
references and the complete parsed entities. It reuses the engine's BSP and entity
readers. BSP bytes are not modified. Invalid headers and out-of-bounds lumps fail
the command rather than producing a partial report.

Use an extracted map file as input. For catalog work, keep the original archive
and its member dates separately; extraction is only for inspection. Resource paths
are lowercased and backslashes normalized for comparison. Changelevel destinations
retain unit (`*`) and spawnpoint (`$`) syntax.

This is static evidence, not a complete dependency resolver: gameplay classes
implicitly load models and sounds, texture animation chains may reference more
textures, skies require their environment images, and custom entities may interpret
other fields as resource paths. Inspect readmes and test maps in the intended game.
An absent sky uses the game's default; the report preserves the missing value.

Existing commands are `info <map.bsp>` and `transparency <map.bsp> [*modelIndex]`.
Run automated checks with `./gradlew :maptools:test`.
