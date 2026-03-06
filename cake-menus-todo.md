# Cake Menus TODO

## Goal
- Make Cake usable from first launch without manual config-file edits.
- Prioritize thin-client flow (join remote servers) before local hosting/singleplayer.

## Scope

### In Scope (now)
- Thin client usability from startup to joining a server.
- Game profile setup and selection (`basedir`, `gamemod`).
- Main menu + options + multiplayer/join/player setup screens.
- First-run autodetect + guided setup fallback.

### Out of Scope (now)
- Singleplayer runtime.
- Hosting local server / local coop runtime.

### Future (tracked)
- Add an integrated wrapper module that combines `server + game + cake`.
- This wrapper will enable:
  - Singleplayer mode.
  - Local coop hosting mode.

## Key Findings (codebase)
- Cake main menu is still placeholder-only in `cake/core/src/main/kotlin/org/demoth/cake/stages/MainMenuStage.kt`.
- Cake startup currently seeds VFS path from JVM system property in `cake/core/src/main/kotlin/org/demoth/cake/Cake.kt` (`System.getProperty("basedir")`).
- Shared FS already has Steam autodetect logic in `qcommon/src/main/java/jake2/qcommon/filesystem/FS.java` (`autodetectBasedir`).
- Cake already exposes VFS diagnostics and mount/index metadata via `VfsDebugCommands`, which can later power an asset explorer UI.

## UX Targets
- First launch with Steam install: auto-detect content and land in usable menu flow.
- First launch without detected content: guided setup to choose `basedir`.
- Switching mod/profile should not require manual file edits.
- Join remote multiplayer server entirely from UI.

## Screen Plan (thin client)
- Startup Gate
  - Resolve active profile.
  - Validate content availability.
  - Route to main menu or first-run setup.
- First-Run Setup
  - Auto-detect result + manual browse/edit path.
  - Validate and save profile.
- Main Menu
  - `Multiplayer` (enabled)
  - `Options` (enabled)
  - `Game Configuration` (enabled)
  - `Singleplayer` (disabled, future)
  - `Host Game` (disabled, future)
  - `Exit` (enabled)
- Multiplayer
  - `Join Game` (enabled)
  - `Player Setup` (enabled)
  - `Host Game` (disabled, future)
- Join Game
  - Manual address + server discovery list.
- Player Setup
  - Name/model/skin/handedness/rate preview workflow.
- Options
  - Video, audio, controls/general sections.

## UI Behavior Rules (current phase)
- Keep `Singleplayer` and `Host Game` visible but disabled.
- Disabled entries should clearly show "future/not available in thin-client mode".
- Avoid dead buttons: every enabled entry must navigate or perform a valid action.

## Delivery Plan

### Phase 1: Startup/Foundation
- Introduce launcher-level game profile model (separate from runtime `GameConfiguration`).
- Implement startup resolver:
  - CLI overrides (if any) > saved profile > autodetect > setup screen.
- Wire Steam autodetect into Cake startup path.
- Remove hard dependency on `System.getProperty("basedir")` for normal startup flow.

### Phase 2: Navigation Shell
- Replace placeholder main menu with structured navigation.
- Add `Game Configuration` screen for profile view/edit/select.
- Add `Multiplayer` hub with thin-client-safe actions.
- Add disabled `Singleplayer`/`Host Game` entries.

### Phase 3: Join + Player Setup
- Implement `Join Game` screen flow.
- Implement `Player Setup` screen.
- Ensure profile context (basedir/mod) is respected by asset resolution and UI state.

### Phase 4: Options
- Implement options categories with high-impact settings first:
  - Video (mode/fullscreen/vsync/basic rendering toggles)
  - Audio (master/music)
  - General/controls basics.

### Phase 5: Future Prep
- Mark integrated wrapper module as separate epic.
- Keep interfaces between launcher config and runtime clean to support dual modes later:
  - thin-client mode
  - integrated mode (future).

## Reference Notes (for ideas)
- Yamagi: classic robust multiplayer split (`join/start/player setup`).
- Q2Pro: strong data-driven menu definitions (`q2pro.menu`) and flexible menu scripting.
- KMQuake2: practical options category split and useful join compatibility controls.

## Definition of Done (this track)
- New user can start Cake and join a remote server without editing files.
- Autodetect/setup handles missing content gracefully.
- Singleplayer/host paths are disabled clearly (not broken).
- Architecture has an explicit extension point for future integrated wrapper module.
