# Cake Core Configuration

## Responsibilities

`GameConfiguration` stores server configstrings and owns the map/gameplay assets
loaded for a `Game3dScreen`. Each configuration has its own `PlayerConfiguration`,
which holds the local player index, inventory, and player name/icon/model/sound
lookups. `Config` pairs a raw configstring value with an optional loaded resource.

Frame/entity reconstruction belongs to `ClientEntityManager`; rendering is
orchestrated by `Game3dScreen` and its presentation components. HUD layout execution
belongs to [the HUD package](stages/ingame/hud/README.md).

## Configuration Flow

```text
ConfigStringMessage -> GameConfiguration.applyConfigString(index, value)
  -> store the configstring
  -> CS_PLAYERSKINS: invalidate that client's cached model/skin resolution
  -> CS_MODELS: invalidate cached player weapon-model names
  -> load supported resources immediately if the screen is already precached

Game3dScreen.precache()
  -> GameConfiguration.loadMapAsset() + loadAssets(...)
  -> model/sound/image/sky loading
  -> PlayerConfiguration.preload(): player models and variation-specific sounds

Runtime lookups
  -> GameConfiguration: map and configstring resources
  -> gameConfig.playerConfiguration: player state and variation-dependent assets
```

The local `playerIndex` is zero-based and is either `UNKNOWN_PLAYER_INDEX` or in
`0 until MAX_CLIENTS`. Sound lookups instead receive a server entity index: players
occupy `1..MAX_CLIENTS`.

Sounds whose configstring starts with `*` depend on the emitting player's model
variation. Generic sound loading skips them; `PlayerConfiguration` preloads known
variations and resolves them at playback through
`GameConfiguration.getSound(soundIndex, entityIndex)`.

## Asset Ownership

Each `GameConfiguration` contributes one `AssetManager` reference for each asset
path it acquires. Repeated acquisition within the same configuration reuses that
reference. `unloadAssets()` releases tracked references and clears resource handles,
player caches, and the local player index.

This allows old and new configurations to overlap during map transitions: releasing
one configuration's reference does not unload an asset still owned by another.
Player assets use the same ownership helpers, rather than separate accounting in
`PlayerConfiguration`.

`acquireAsset` loads and returns an asset or propagates a failure.
`tryAcquireAsset` is best-effort: it catches missing-resource and libGDX loading
failures, records and logs the failure, and returns `null`. Repeated attempts for
that path return `null` until the failure records are cleared during unload.
Callers must therefore handle absent optional resources.

Resolver policies, download-cache precedence, and synthetic player model/skin keys
are described in [the assets package](assets/README.md).

## Application Behaviour

A missing-resource failure that reaches `Cake.render()` is recovered by
disconnecting, disposing the game screen, and showing the console. This includes
missing-resource exceptions wrapped in another runtime exception; unrelated runtime
exceptions are rethrown. Best-effort acquisition failures handled locally do not
trigger this recovery.

`Cake` also owns the optional `pics/conback.pcx` background outside the game screen,
so it can be shown before gameplay. Applying a profile reloads it through
`applyGameProfile(...)`; it is drawn while there is no game screen. An absent
background is silently skipped, and a background loading failure is logged without
terminating the application.
