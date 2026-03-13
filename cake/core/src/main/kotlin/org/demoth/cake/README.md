# Cake Core Configuration

## Overview
This package owns runtime configstring storage and config-scoped asset ownership for the ingame client flow.

It does not own:
- frame/entity reconstruction (`stages/ingame/ClientEntityManager`),
- render-loop orchestration (`stages/ingame/Game3dScreen`),
- HUD layout execution internals (`stages/ingame/hud`).

## Key Types
- `GameConfiguration` - map/runtime configstring container with asset ownership tracking.
- `PlayerConfiguration` - player-scoped state and variation resolution (name/icon/model/sounds).
- `Config` - raw configstring value plus optionally loaded resource handle.

## Data / Control Flow
```text
ConfigStringMessage -> GameConfiguration.applyConfigString(index, value)
  -> configstring store update
  -> if CS_PLAYERSKINS: PlayerConfiguration cache invalidation

Game3dScreen.precache -> GameConfiguration.loadMapAsset + loadAssets
  -> model/sound/image/sky preload
  -> PlayerConfiguration.preload (player models + '*' variation sounds)

Runtime lookups
  -> GameConfiguration.getSound(soundIndex, entityIndex)
     -> regular config sound OR PlayerConfiguration.getPlayerSound
  -> HUD/entity/prediction read via gameConfig.playerConfiguration.*
```

## Invariants
- One `GameConfiguration` instance owns one `PlayerConfiguration` instance with identical lifecycle.
- `playerIndex` is either `UNKNOWN_PLAYER_INDEX` or within `0 until MAX_CLIENTS`.
- `CS_PLAYERSKINS` updates must invalidate resolved player model/skin cache for that client.
- `*` sounds are never loaded by generic config sound loading; they are resolved by player variation at use/preload time.
- Asset ownership/refcounting is centralized in `GameConfiguration` (`acquireAsset` / `tryAcquireAsset`).

## Decision Log
Newest first.

### Decision: Handle Cake missing-resource failures by dropping to console
- Context: missing supported client assets should not terminate the process or rely on legacy `Com.Error` flow inside the `cake/` module.
- Options Considered:
1. Keep local one-off recovery and allow generic runtime exceptions to surface.
2. Catch broad runtime failures and always disconnect.
3. Catch typed missing-resource failures and return to a stable console state.
- Chosen Option & Rationale: Option 3. It keeps the stability path intentionally narrow, removes `Com.Error` from Cake app flow, and makes handled failures predictable: disconnect, dispose the active `Game3dScreen`, and show the console.
- Consequences: `Cake.render()` now treats `MissingResourceException` as a recoverable client-content failure, while unrelated runtime errors still surface normally.
- Status: accepted.
- Definition of Done: supported missing-resource failures during startup/precache/render drop to console without killing the app process.
- References: thread decisions around generic missing-resource handling and “drop to console on exception”.

### Decision: Load optional profile background through `applyGameProfile(...)`
- Context: Cake needed a profile/game-specific menu background before `Game3dScreen` exists, and mods commonly override `pics/conback.pcx`.
- Options Considered:
1. Load background only on startup and keep profile switching separate.
2. Let menu stages own the background.
3. Let `Cake` own a small optional background asset and reload it whenever `applyGameProfile(...)` changes resolver context.
- Chosen Option & Rationale: Option 3. It reuses the existing profile-application path for both startup and disconnected profile switches, keeps ownership at app scope, and avoids coupling menu stages to asset lifecycle.
- Consequences: `Cake` now owns one optional background texture/batch, rendered only while `game3dScreen == null`. Missing `pics/conback.pcx` is intentionally silent and non-fatal.
- Status: accepted.
- Definition of Done: startup selected profile and runtime disconnected profile changes both update the optional `conback.pcx` background through the same `applyGameProfile(...)` path.
- References: thread feature request for game background/profile branding via `conback.pcx`.

### Decision: Expose `playerConfiguration` and remove wrapper API in `GameConfiguration`
- Context: player-related config and lookups had outgrown `GameConfiguration` and cluttered map-level concerns.
- Options Considered:
1. Keep all player logic in `GameConfiguration`.
2. Delegate player behavior to a dedicated `PlayerConfiguration` but keep wrapper methods.
3. Delegate player behavior and expose `gameConfig.playerConfiguration` directly.
- Chosen Option & Rationale: Option 3. It clarifies ownership boundaries and removes duplicate forwarding API.
- Consequences: call sites now explicitly depend on `gameConfig.playerConfiguration.*`; API is clearer but slightly longer.
- Status: accepted.
- Definition of Done: ingame call sites (`Game3dScreen`, `ClientEntityManager`, `Hud`) use `gameConfig.playerConfiguration` directly.
- References: thread decision ("expose the playerConfiguration field and remove unnecessary wrappers").

### Decision: Keep asset ownership in `GameConfiguration` after extracting `PlayerConfiguration`
- Context: extracted player logic still needs configstring reads and asset acquisition with refcount tracking.
- Options Considered:
1. Move asset ownership APIs into `PlayerConfiguration`.
2. Add duplicate asset tracking in `PlayerConfiguration`.
3. Keep ownership in `GameConfiguration` and inject owner access into `PlayerConfiguration`.
- Chosen Option & Rationale: Option 3. Single owner prevents refcount drift and preserves map transition behavior.
- Consequences: `PlayerConfiguration` depends on `GameConfiguration` methods (`operator get`, `tryAcquireAsset`).
- Status: accepted.
- Definition of Done: player models/sounds loaded through `PlayerConfiguration` are still unloaded by `GameConfiguration.unloadAssets`.
- References: thread resolution ("limitation (ownership of certain fields), proceed with resolving them").

### Decision: Move `PlayerConfiguration` to package `org.demoth.cake`
- Context: player config lifecycle is tied to `GameConfiguration`, not to `stages/ingame` orchestration classes.
- Options Considered:
1. Keep `PlayerConfiguration` in `stages/ingame`.
2. Move to `org.demoth.cake` beside `GameConfiguration`.
- Chosen Option & Rationale: Option 2 for clearer responsibility and package cohesion.
- Consequences: reduced cross-package leakage; shared config logic is colocated.
- Status: accepted.
- Definition of Done: only `org.demoth.cake.PlayerConfiguration` exists and `GameConfiguration` instantiates it.
- References: thread migration work.

## Quirks & Workarounds
- Quirk: `Cake` owns one optional profile background outside `Game3dScreen`.
  - Why: it must render before gameplay screen creation and survive disconnected profile switches.
  - How to work with it: reload only through `applyGameProfile(...)`; do not add separate startup/profile-switch background paths.
  - Removal plan: none currently; this is the intended ownership boundary.

- Quirk: `PlayerConfiguration` receives a `GameConfiguration` owner reference.
  - Why: it must read configstrings and acquire assets while preserving one ownership/refcount authority.
  - How to work with it: avoid loading player assets through `AssetManager` directly from new code; use `gameConfiguration.tryAcquireAsset`.
  - Removal plan: introduce a narrow owner interface (config read + asset acquire) and inject that instead of full `GameConfiguration`.

- Quirk: player MD2 assets use synthetic keys `<skinPath>|<modelPath>`.
  - Why: `AssetManager` caches by path, but player skins need distinct cache entries per `(model, skin)`.
  - How to work with it: keep separator/key format stable unless both loader and cache semantics are updated together.
  - Removal plan: none currently; compatibility mechanism.

## How to Extend
1. Add new player-scoped lookup/state in `PlayerConfiguration` first; avoid putting player rules back into `GameConfiguration`.
2. If new behavior needs asset loading, route through `GameConfiguration.tryAcquireAsset`.
3. Keep fallbacks deterministic and aligned with legacy path order when touching player model/skin/sound resolution.
4. Update this README Decision Log when changing ownership boundaries or fallback order.

## Open Questions
- Should `PlayerConfiguration` depend on a narrow owner interface instead of concrete `GameConfiguration` to reduce coupling?
