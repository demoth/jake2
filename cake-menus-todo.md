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
- Done: dedicated game configuration/profile edit stage.
  - Clicking current profile now opens a dedicated profile editor stage.
  - Added two-pane layout: profile list (left) and editable profile form (right).
  - Added `Create New Profile`, `Autodetect`, `Save`, and `Back` actions.
- Next: first-run setup/guided profile bootstrap flow.
- Done: `Disconnect` main menu action and connection-aware menu state.
- Done: profile switching locked to disconnected state (UI + command guard).
- Verify/keep: profile `basedir` seeds VFS via `applyGameProfile` (`fileResolver.basedir` -> `CakeVfsAssetSource.configure`).

## Profile Edit Refinements (2026-03-07)
- Keep current behavior: selecting a profile in the left list applies it immediately (no deferred apply on `Back`).
- Layout adjustments:
  - left panel should be packed/minimal width (no fixed wide column),
  - right panel should consume remaining horizontal space.
- Move `Back` button to the left panel under the profile list/actions.
- Keep right-side form simple and stacked (`label` then `textfield` rows).
- Implement explicit checked/toggled state for the selected profile button in the list.
- `Autodetect` should be a no-op when detection fails (do not overwrite fields).

## Menu Messaging Refactor (2026-03-09)
- Introduced a typed menu messaging layer to decouple stages from `Cake` callback wiring:
  - `MenuIntent` (UI -> app actions)
  - `MenuSignal` + `MenuStateSnapshot` (app -> UI state)
  - `MenuEventBus` (in-memory FIFO, frame-drained)
- Added `MenuController` to process intents and produce menu state snapshots.
- Migrated both `MainMenuStage` and `ProfileEditStage` to bus-driven interaction/state.
- Removed stage-specific lambda callback coupling for main/profile screens.
- Added tests for:
  - bus FIFO/limit/state behavior
  - controller intent-to-state transitions for profile flow.

## Profile Layout Stabilization (2026-03-09)
- Made profile screen column sizing explicit:
  - left panel remains packed/content-driven,
  - right panel expands to take remaining width.
- Updated profile editor form rows so text fields use `growX + fillX`.
- Wrapped the profile list into a bounded vertical `ScrollPane` to prevent long lists from distorting the stage layout.

## Reference Notes (for ideas)
- Yamagi: classic robust multiplayer split (`join/start/player setup`).
- Q2Pro: strong data-driven menu definitions (`q2pro.menu`) and flexible menu scripting.
- KMQuake2: practical options category split and useful join compatibility controls.

## Definition of Done (this track)
- New user can start Cake and join a remote server without editing files.
- Autodetect/setup handles missing content gracefully.
- Singleplayer/host paths are disabled clearly (not broken).
- Architecture has an explicit extension point for future integrated wrapper module.

## Client Stability Track (2026-03-13)

### Goal
- Make Cake survive missing or malformed client resources without process-level crashes.
- Replace legacy `Com.Error`-driven client flow in the `cake/` module with typed Kotlin exceptions and explicit recovery boundaries.
- Make missing-resource behavior configurable:
  - strict mode: fail fast with a typed `MissingResourceException`
  - fallback mode: use engine-owned placeholder assets where supported

### Current Findings (codebase)
- `CakeFileResolver.resolve(...)` currently returns `null` for missing assets.
- LibGDX synchronous loaders in Cake declare non-null `FileHandle` parameters, so a missing resolver result becomes a generic Kotlin NPE instead of a domain error.
- The main offenders are:
  - `cake/core/src/main/kotlin/org/demoth/cake/assets/CakeFileResolver.kt`
  - `cake/core/src/main/kotlin/org/demoth/cake/assets/CakeTextureLoaders.kt`
  - `cake/core/src/main/kotlin/org/demoth/cake/assets/Md2ModelLoader.kt`
- Missing-resource handling is inconsistent today:
  - some call sites pre-check `assetManager.fileHandleResolver.resolve(path) == null`
  - some call `tryAcquireAsset(...)` and absorb `GdxRuntimeException`
  - some paths still rely on generic top-level exception recovery in `Cake`
- Cake already has a generic drop-to-console boundary in `Cake.render()`, but it is still coupled to legacy `Com.Error`/`longjmpException` behavior.
- Cake still contains at least one direct `Com.Error` call in network/message handling (`Server disconnected` path in `Cake.kt`).

### Reference Notes (verified)
- Yamagi/Q2Pro still use `ERR_DROP` / `ERR_DISCONNECT` as a client-frame recovery mechanism, then disconnect back to a stable console/client shell.
- Reference renderers also keep engine-owned fallback textures instead of crashing on every missing texture:
  - Yamagi GL1 keeps `r_notexture`
  - Q2Pro reuses placeholder images and keeps a notion of missing-image diagnostics
- The Cake equivalent should preserve the same high-level behavior, but using typed exceptions and Kotlin recovery boundaries instead of legacy `Com.Error` in the client module.

### Decisions
- Introduce a domain exception: `MissingResourceException`.
- `cake/` must stop using `Com.Error` for expected client-side resource/runtime recovery.
- Missing resources must be handled generically at two boundaries:
  - startup / connection / precache / screen init
  - per-frame `Game3dScreen.render`
- Recovery target for non-fatal client failures is:
  - disconnect client state if needed
  - leave the app running
  - return to console/menu shell
  - print a concise warning/error describing the missing resource and phase
- Add a new cvar: `fs_debug_loaders`.

### `MissingResourceException` Shape
- Add a typed runtime exception in Cake asset/runtime code:
  - `path: String`
  - `phase: String` (`resolver`, `loader`, `precache`, `render`, `connection`)
  - optional `cause: Throwable`
- Use it for:
  - resolver misses that should be treated as fatal in strict mode
  - loader dependency misses
  - malformed fallback asset situations where Cake cannot supply a placeholder

### `fs_debug_loaders` Policy
- Add `fs_debug_loaders` as the loader-policy cvar.
- Proposed semantics:
  - `0`: strict mode
  - `1`: fallback mode for supported resource classes
- Strict mode behavior:
  - throw `MissingResourceException`
  - let generic Cake/Game3dScreen boundaries disconnect to console
- Fallback mode behavior:
  - substitute engine-owned placeholder assets where a type-specific fallback exists
  - emit `Com.Warn` once per missing path/resource kind
  - continue execution unless fallback cannot be provided
- Initial fallback scope:
  - textures -> `_missing.pcx`, `_missing.wal`
  - md2 models -> `_missing.md2`
  - skyboxes -> `_missing.sky`
  - sprite -> `_missing.sp2`

- Deferred fallback scope:
  - sounds - print a warning, play nothing instead
  - cinematics - simply skip with a warning
- Fallback assets must be engine-owned, mounted predictably, and not depend on mod content being complete.

### Resolver / Loader Rules
- Stop treating `null` `FileHandle` as a normal resolver contract inside Cake loaders.
- Replace resolver-driven NPEs with one of two paths:
  - strict: throw `MissingResourceException`
  - fallback: resolve to a placeholder `FileHandle` if the asset type supports it
- Rules for loaders:
  - loaders must validate the incoming file/dependency path before dereferencing
  - loaders must not throw generic `NullPointerException` for missing input
  - loaders should wrap malformed-content errors with path/context when practical
- Rules for `GameConfiguration` / asset acquisition:
  - centralize missing-resource mapping in one asset-acquisition path
  - remove ad hoc `resolve(path) == null` pre-checks from callers where possible
  - keep `tryAcquireAsset(...)` domain-aware instead of relying on generic `GdxRuntimeException` handling

### Recovery Boundaries
- Add explicit generic handlers around:
  - `Game3dScreen` initialization / precache path
  - `Game3dScreen.render`
  - connection/startup transitions in `Cake`
- Boundary behavior:
  - `MissingResourceException` -> warn, disconnect, return to console
  - other recoverable asset/runtime exceptions -> log with context, disconnect, return to console
  - clearly fatal engine/runtime exceptions -> rethrow
- Remove the remaining direct `Com.Error` usage from Cake and replace it with normal Kotlin control flow plus the shared recovery boundary.

### Implementation Plan
1. Exception model
- Add `MissingResourceException`

2. Loader policy
- Fix *Loader signatures, make fun load() receive nullable FileHandle? and hande it explicitly instead of java/kotlin boundary behavior
- Add `fs_debug_loaders`.
- Add a small policy helper in Cake asset loading code that answers:
  - strict vs fallback
  - placeholder path for a given resource kind (should exist!)
  - warn-once bookkeeping

3. Resolver refactor
- Refactor `CakeFileResolver` so missing resources no longer flow into non-null loader parameters as `null`.
- Keep the resolver behavior explicit enough that callers and loaders can distinguish:
  - real asset file
  - placeholder asset file
  - strict missing-resource failure

4. Loader hardening
- Update `PcxLoader`, `WalLoader`, `Md2Loader`, and other custom loaders to:
  - reject missing input with `MissingResourceException`
  - use placeholder assets when policy allows
  - preserve useful path/cause information

5. Asset acquisition cleanup
- Refactor `GameConfiguration` and nearby callers to stop scattering `resolve(path) == null` checks.
- Route all asset loads through a common path that understands `MissingResourceException` and fallback policy.

6. Recovery boundaries
- Move from the current one-off Cake exception handling to explicit reusable handlers for:
  - startup / connection / precache
  - in-game render
- Replace the last Cake `Com.Error` usage with normal exception-driven disconnect flow.

7. Placeholder content
- Add/verify `_missing.pcx`, `_missing.md2`, and others
- Ensure they can load without depending on optional mod assets.
- Fallback assets live in Cake engine's asset folder.

8. Tests
- Add tests for strict/fallback resolver behavior.
- Add loader tests proving missing assets raise `MissingResourceException` instead of NPE.
- Add tests proving fallback mode returns placeholder assets for supported types.
- Add tests for generic boundary behavior:
  - startup/precache failure drops to console
  - render-time failure drops to console
  - app process remains alive

### Required Design Details Before Implementation
- `fs_debug_loaders` stays boolean-like (`0/1`)
- Decide whether unsupported types in fallback mode should:
  - still throw `MissingResourceException`
  - or silently return `null`
- Current recommendation: unsupported types should still throw. Silent `null` keeps the current instability.
  - Decision: follow the missing resource exception path
- Decide whether malformed assets and missing assets share the same recovery path.
  - Decision: MissingResourceException will have a root cause exception which should be enough to know the reason

### Verification Checklist
- Verify there are no remaining direct `Com.Error` calls in `cake/`.
- Verify no Cake custom loader can produce a generic Kotlin NPE for missing file input.
- Verify connecting to a server with a missing texture/model drops cleanly to console instead of terminating the app.
- Verify render-time missing assets follow the same recovery path as startup/precache misses.
- Verify fallback mode logs the missing path and actual placeholder used.
- Verify strict mode surfaces the original missing path, resource kind, and phase.
