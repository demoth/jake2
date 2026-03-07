# Cake Menus TODO

## Goal
- Make Cake usable from first launch without manual config-file edits.
- Prioritize thin-client flow (join remote servers) before local hosting/singleplayer.
- Keep v1 implementation intentionally small and migration-friendly.

## Scope

### In Scope (now)
- Thin client usability from startup to joining a server.
- Game profile setup and selection (`id`, `basedir`, optional `gamemod`).
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
- Main menu has a minimum shell in `cake/core/src/main/kotlin/org/demoth/cake/stages/MainMenuStage.kt`:
  - current profile display + selector
  - `Singleplayer` and `Host Game` disabled placeholders
- Cake startup resolves selected/default profile and applies it to resolver in
  `cake/core/src/main/kotlin/org/demoth/cake/Cake.kt` (`applyGameProfile` -> `fileResolver.basedir/gamemod`).
- Shared FS already has Steam autodetect logic in `qcommon/src/main/java/jake2/qcommon/filesystem/FS.java` (`autodetectBasedir`).
- Cake already exposes VFS diagnostics and mount/index metadata via `VfsDebugCommands`, which can later power an asset explorer UI.

## UX Targets
- First launch with Steam install: auto-detect content and land in usable menu flow.
- First launch without detected content: guided setup to choose `basedir`.
- Switching mod/profile should not require manual file edits.
- Join remote multiplayer server entirely from UI.
- Profile-local writable state lives under `.cake/<profileId>`.

## Decisions (2026-03-06)
- `profiles.json` will contain:
  - schema `version`
  - `selectedProfileId`
  - `profiles[]`
- Minimal profile shape for now:
  - `id` (alphanumeric only, unique, used as folder name)
  - `basedir`
  - optional `gamemod`
- No separate display name yet (can be added later).
- Keep implementation simple:
  - small Kotlin data classes (single-line style where practical)
  - Jackson serialization/deserialization
- Near-term install model supports one game installation root (`basedir`) per profile.
- Future multi-install support will extend profile schema (additional roots) when needed.

## Screen Plan (thin client)
- Startup Gate
  - Resolve active profile.
  - Validate content availability.
  - Route to main menu or first-run setup.
- First-Run Setup
  - Auto-detect result + manual browse/edit path.
  - Validate and save profile.
- Main Menu
  - `Disconnect` (enabled only when connected/in-game)
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
- Game profile switching is allowed only while disconnected (menu/offline state).
- During active connection/game:
  - profile selector UI is disabled/read-only
  - show short hint that profile changes require disconnect first.

## Delivery Plan

### Phase 1: Startup/Foundation
- Introduce launcher-level game profile model (separate from runtime `GameConfiguration`):
  - `profiles.json` root model with `version`, `selectedProfileId`, `profiles`
  - `GameProfile(id, basedir, gamemod?)`
- Implement startup resolver:
  - CLI overrides (if any) > saved profile > autodetect > setup screen.
- Wire Steam autodetect into Cake startup path.
- Remove hard dependency on `System.getProperty("basedir")` for normal startup flow.
- Route writable profile data into `.cake/<profileId>/...` (configs/screenshots/saves).

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

## Next Implementation Steps (immediate)
1. Profile schema + persistence
- Finalize `profiles.json` v1 models and Jackson mapping.
- Ensure default bootstrap behavior:
  - if no file exists, create default profile record
  - persist `selectedProfileId`
- Validate `id` (alphanumeric), `basedir` existence, and `gamemod` directory-token safety.

2. Startup integration
- Load selected profile during Cake boot.
- Resolve effective game configuration from selected profile (`basedir`, optional `gamemod`).
- Use existing Steam autodetect as fallback when profile file is absent or invalid.

3. Profile-scoped writable paths
- Introduce profile data root helper (`.cake/<profileId>`).
- Point config/screenshot/save outputs to profile root.

4. UI increment (minimum usable shell)
- Main menu: add current profile switch entry + multiplayer/options/exit.
- Keep singleplayer/host visible but disabled with thin-client note.
- Add basic profile list/select screen before full editor UX.

5. Tests
- Unit tests for profile read/write/default bootstrap/validation.
- Startup resolver tests for precedence and fallback paths.

## Progress (2026-03-06)
- Done: profile schema + persistence.
  - Added `.cake/profiles.json` with `version`, `selectedProfileId`, `profiles[]`.
  - Added profile validation (`id`, `basedir`, `gamemod`).
- Done: startup integration.
  - Cake now loads selected profile on startup.
  - If no profile exists, Cake bootstraps a default profile (`id=default`) with basedir resolution:
    - JVM `basedir` property
    - Steam autodetect paths
    - fallback `"."`
- Done: profile-scoped writable paths.
  - Screenshots and save metadata now write under `.cake/<profileId>/...`.
- Done: tests for new profile store and updated save store behavior.
- Done: minimum UI increment.
  - Main menu now shows current profile and allows profile selection from existing profiles.
  - Singleplayer and Host Game are visible but disabled as future thin-client-incompatible modes.
- Next: dedicated game configuration/profile edit screens (list/add/edit UX instead of menu-level selector only).
- Done: `Disconnect` main menu action and connection-aware menu state.
- Done: profile switching locked to disconnected state (UI + command guard).
- Verify/keep: profile `basedir` seeds VFS via `applyGameProfile` (`fileResolver.basedir` -> `CakeVfsAssetSource.configure`).

## Reference Notes (for ideas)
- Yamagi: classic robust multiplayer split (`join/start/player setup`).
- Q2Pro: strong data-driven menu definitions (`q2pro.menu`) and flexible menu scripting.
- KMQuake2: practical options category split and useful join compatibility controls.

## Definition of Done (this track)
- New user can start Cake and join a remote server without editing files.
- Autodetect/setup handles missing content gracefully.
- Singleplayer/host paths are disabled clearly (not broken).
- Architecture has an explicit extension point for future integrated wrapper module.
