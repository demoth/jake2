# Cake Multiplayer Menu TODO

## Goal
- Add a vanilla-style `Multiplayer` submenu to Cake.
- Keep `Host Game` visible but disabled for now.
- Implement a working `Join Game` screen for manual remote connect.
- Keep the implementation aligned with the existing Cake menu bus/controller architecture.

## Approved Scope
- Main Menu
  - `Multiplayer` opens a dedicated submenu instead of directly executing `connect`.
- Multiplayer Menu
  - `Join Game` enabled.
  - `Host Game` visible but disabled.
  - `Player Setup` enabled once the dedicated screen lands.
  - `Back` returns to the main menu.
- Join Game
  - `Host name` field.
  - `Port` field.
  - `Join` button.
  - `Back` button.
  - Inline status/error message.
- Player Setup
  - `Name` field -> `name`
  - `Password` field -> `password`
  - `Model` selector
  - `Skin` selector
  - `Handedness` selector -> `hand`
  - `Save` applies staged changes and persists them through profile config
  - `Back` discards unsaved edits
  - No 3D preview or icon preview in this slice

## Current Findings
- Cake already has a working manual connect command:
  - `connect <address>` in `cake/core/src/main/kotlin/org/demoth/cake/Cake.kt`
  - It disconnects first, sets `servername`, and drives the existing handshake flow.
- Address parsing already supports:
  - `host`
  - `host:port`
  - via `netadr_t.fromString(..., PORT_SERVER)`
- Default server port is `27910`.
- Current menu architecture is bus/controller based:
  - `MenuIntent`
  - `MenuStateSnapshot`
  - `MenuController`
  - `MenuEventBus`
- Current menu rendering only supports:
  - `MAIN`
  - `PROFILE_EDIT`
- Current `MainMenuStage` still hardcodes `Multiplayer` to `connect 127.0.0.1`.
- Player-related userinfo cvars already exist and are archived:
  - `name`
  - `password`
  - `skin`
  - `hand`
  - `gender`
- `skin` must stay in legacy `model/skin` format for compatibility with existing servers and clients.

## Constraints / Decisions
- Keep `Host Game` visible but disabled until implemented.
- Do not implement server discovery/address book in this slice.
- Prefer backend validation before dispatching `connect`.
- On successful `Join`, close the menu immediately and let the existing network flow continue.
- On validation failure, stay on the join screen and show an inline message.
- Player Setup writes only on `Save`.
  - `Back` discards unsaved edits.
- Player Setup should not try to reimplement legacy model preview in this slice.
- Derive `gender` from the chosen `skin` on save for legacy compatibility.
- Keep implementation small and local:
  - add menu state/intents first,
  - then add stages,
  - then add backend actions.

## Required Code Changes

### 1. Menu Model
- Extend `MenuScreen` with:
  - `MULTIPLAYER`
  - `JOIN_GAME`
- Extend menu snapshot state with:
  - multiplayer screen state
  - join game form state

### 2. Menu Intents
- Add intents for:
  - opening multiplayer
  - opening join game
  - returning from join game to multiplayer
  - submitting join request

### 3. Menu Backend
- Add a backend method for manual join submission.
- Backend responsibilities:
  - trim host/port
  - reject blank host
  - accept blank port as default port
  - reject invalid/non-numeric/out-of-range port
  - compose final address string
  - optionally validate using `netadr_t.fromString`
  - dispatch existing `connect`

### 4. UI Stages
- Add `MultiplayerMenuStage`
  - `Join Game`
  - `Host Game (future)` disabled
  - `Player Setup`
  - `Back`
- Add `JoinGameStage`
  - host text field
  - port text field
  - join
  - back
  - status label
- Add `PlayerSetupStage`
  - name text field
  - password text field
  - model selector
  - skin selector
  - handedness selector
  - save
  - back
  - status label

### 5. Cake App Routing
- Extend `MenuView`
- Instantiate and dispose new stages
- Route input/rendering to the active multiplayer/join stages
- Replace current main-menu multiplayer action with `OpenMultiplayer`

### 6. Tests
- Menu controller transitions:
  - main -> multiplayer
  - multiplayer -> join
  - join -> multiplayer
  - multiplayer -> player setup
- Join validation:
  - blank host rejected
  - blank port uses default port
  - invalid port rejected
  - valid host/port triggers backend connect path
- Player setup validation:
  - invalid name rejected
  - invalid password rejected
  - blank name normalizes to `unnamed`
  - model/skin selection normalizes against discovered catalog

## Delivery Slices

### Slice 1
- Add this todo file.
- No code changes.

### Slice 2
- Add multiplayer menu screen/state/routing.
- Main menu `Multiplayer` opens submenu.
- `Host Game` and `Player Setup` disabled.
- `Join Game` kept disabled until the actual join screen lands.

### Slice 3
- Add join game screen/state/backend join action.
- Wire `Join` to existing `connect` command path.
- Close the menu immediately on successful `Join`.

### Slice 4
- Add tests.
- Run targeted validation/build.

### Slice 5
- Add Player Setup menu screen and backend wiring.
- Persist player-facing cvars via the profile-local config path.
- Keep previews out of scope for now.

## Progress
- Done:
  - Approved multiplayer menu scope.
  - Analyzed current connect path and menu architecture.
  - Recorded implementation plan in this file.
  - Added multiplayer submenu shell:
    - main menu now routes into a dedicated multiplayer screen
    - menu state/view routing now supports `MULTIPLAYER`
    - `Host Game` and `Player Setup` are visible but disabled
  - Added join game flow:
    - multiplayer menu now opens a dedicated join screen
    - join screen captures host and port
    - join submit validates host/port and reuses the existing `connect` path
    - successful join closes the menu immediately
  - Added controller coverage for:
    - multiplayer/join screen transitions
    - blank-host rejection
    - invalid-port rejection
    - default-port behavior
    - validated join dispatch
  - Added player setup flow:
    - multiplayer menu now opens a dedicated player setup screen
    - player setup edits `name`, `password`, `skin`, `hand`, and derived `gender`
    - model and skin options are discovered from mounted player assets
    - save writes through the profile-local config path
  - Added controller coverage for:
    - opening player setup
    - invalid userinfo validation
    - normalized player setup save dispatch
- Next:
  - Decide whether `rate` belongs in Player Setup or later Options.
  - Add preview support only if it can stay isolated from menu-state logic.
