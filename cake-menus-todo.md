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
- Layout adjustments:
  - left panel should be packed/minimal width (no fixed wide column),
  - right panel should consume remaining horizontal space.
- Move `Back` button to the left panel under the profile list/actions.
- Keep right-side form simple and stacked (`label` then `textfield` rows).
- Implement explicit checked/toggled state for the selected profile button in the list.
- `Autodetect` should be a no-op when detection fails (do not overwrite fields).

## Runtime Content Style Refactor (2026-03-26)
- Pull HUD style ownership out of `Game3dScreen` and up into `Cake`.
  - `Game3dScreen` should receive a ready-to-use HUD/content style and should not need to know `gameName`.
- Keep runtime content-style switching semantics:
  - effective style follows selected profile while disconnected,
  - effective style can switch to server-provided mod context while connected,
  - but style objects should be recreated only when the effective style key actually changes.
- Menu stages will switch style by being rebuilt, not by attempting in-place Scene2D skin mutation.
  - this is acceptable because current menu state is controller-owned and purely local widget state is not valuable enough to preserve.
- Console remains explicitly on engine-owned styling and is out of scope for content-style switching.
- Profile editor behavior change:
  - selecting a profile in the list only loads it into the editor,
  - applying/switching the active profile is now a separate explicit action,
  - this avoids background/style churn on every list click.
- Progress:
  - explicit profile apply is implemented,
  - HUD style ownership is moved from `Game3dScreen` to `Cake`,
  - runtime style reuse is keyed by effective resolver context so identical reconnects do not recreate the same HUD style.
  - `MainMenuStage` now consumes the shared content style and is rebuilt when the effective style changes.
  - broader menu-stage theming is still pending; for now this slice is intentionally limited to the main menu.

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

## Video Mode Bootstrap (2026-03-21)
- Goal:
  - Make `vid_*` cvars actually control launcher-owned window settings for the next app start.
  - Keep this phase restart-based; no live window recreation is required yet.
- Decision:
  - Do not move full VFS/libGDX asset initialization before `Cake()`.
  - Move only a small startup bootstrap earlier:
    - `Cmd.Init()`
    - `Cvar.Init()`
    - register aliases
    - register `vid_*` cvars with defaults
    - resolve active Cake profile
    - read and execute `.cake/<profileId>/config.cfg`
  - The launcher then reads `vid_*` from `Cvar` and builds `Lwjgl3ApplicationConfiguration` before creating `Cake()`.
- Initial `vid_*` scope:
  - `vid_fullscreen`
  - `vid_width`
  - `vid_height`
  - `vid_vsync`
- Follow-up implementation notes:
  - Pass a small startup context into `Cake` so profile/config startup work is not duplicated.
  - `CVAR_LATCH` is acceptable for restart-required client settings, but current persistence must be adjusted:
    - when `latched_string` exists, profile config save should persist that pending value
    - next launch should use that pending value during early bootstrap
  - Full game-data VFS can remain Cake-owned and initialized later through `CakeFileResolver`.
- Progress:
  - Added shared Cake startup bootstrap for:
    - command/cvar initialization
    - common Cake cvar registration
    - early `ClientBindings` creation so `bind` / `unbindall` are available during config execution
    - selected-profile resolution
    - profile config execution before launcher window creation
  - Added first real video mode cvars:
    - `vid_fullscreen`
    - `vid_width`
    - `vid_height`
    - `vid_vsync`
  - Updated the LWJGL3 launcher to read bootstrapped `vid_*` values before constructing `Cake()`.
  - Updated archive persistence so restart-latched `vid_*` values are written using their pending value.
  - Added regression coverage for:
    - idle `vid_*` latch behavior
    - archive write preferring `latched_string`

## Video Apply Paths (future)
- Phase-1 path:
  - Save `vid_*` changes in Options.
  - Mark them as pending restart.
  - Apply them on the next app launch through the early startup bootstrap.
- Future no-restart path candidates:
  - In-process live switch:
    - attempt runtime `Gdx.graphics` mode changes directly
    - likely viable for fullscreen/windowed switch, size changes, vsync
    - requires explicit success/failure handling and rollback to prior mode on failure
  - Controlled app restart through launcher:
    - Cake requests restart after saving pending `vid_*`
    - launcher catches a typed restart signal/exception
    - launcher creates a new `Lwjgl3ApplicationConfiguration` from current cvars and restarts Cake
    - if restart originated from Options, launcher can pass a resume target so Cake returns to the same menu section
  - Confirmation/rollback flow:
    - after applying a new video mode, show a timed confirmation dialog
    - if user confirms, keep the new mode
    - if user cancels or timeout expires, restore the previous mode/settings and return to a known-safe menu state
- Open questions:
  - none for the phase-1 restart-based implementation
  - future no-restart apply will need a concrete launcher-to-Cake restart contract and a UI confirmation flow

## Definition of Done (this track)
- New user can start Cake and join a remote server without editing files.
- Autodetect/setup handles missing content gracefully.
- Singleplayer/host paths are disabled clearly (not broken).
- Architecture has an explicit extension point for future integrated wrapper module.

## Profile Background (2026-03-14)
- Goal:
  - Show an optional profile-specific background image while no `Game3dScreen` is active.
  - Use `pics/conback.pcx` so mod overrides naturally brand the current profile.
- Decisions:
  - Background load/reload is owned by `Cake`, not by `GameConfiguration`.
  - `applyGameProfile(...)` is the single load/reload hook for both:
    - startup selected profile
    - runtime profile switching while disconnected
  - Missing `conback.pcx` is non-fatal and silent.
  - Render only while `game3dScreen == null`.
- Progress:
  - Added Cake-owned background texture/batch state.
  - Added background reload on `applyGameProfile(...)`.
  - Added background rendering in the main loop after clear and before menu/console.

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
  - `resourceKind: ResourceKind`
  - `phase: String` (`resolver`, `loader`, `precache`, `render`, `connection`)
  - optional `cause: Throwable`
- `ResourceKind` should be an enum with explicit resolver policy:
  - `PCX("_missing.pcx", tolerableMissing = false)`
  - `WAL("_missing.wal", tolerableMissing = false)`
  - `MD2("_missing.md2", tolerableMissing = false)`
  - `SP2("_missing.sp2", tolerableMissing = false)`
  - `SOUND(null, tolerableMissing = true)`
- `ResourceKind` exists because fallback routing must be deterministic and not re-inferred ad hoc from string paths at every call site.
- Engine assets are assumed to guarantee the configured fallback files exist.
- Missing fallback-content handling is intentionally out of scope for this track.
- Use it for:
  - resolver misses that should be treated as fatal in strict mode
  - loader dependency misses
  - sound requests where missing content is explicitly tolerated

### `fs_debug_loaders` Policy
- Add `fs_debug_loaders` as the loader-policy cvar.
- Proposed semantics:
  - `0`: strict mode
  - `1`: fallback mode for supported resource classes
- Strict mode behavior:
  - throw `MissingResourceException` for non-tolerable resource kinds
  - return `null` for tolerable missing kinds (for example `SOUND`)
  - let generic Cake/Game3dScreen boundaries disconnect to console
- Fallback mode behavior:
  - substitute engine-owned placeholder assets where a type-specific fallback exists
  - return `null` for tolerable missing kinds with no fallback asset
  - emit `Com.Warn` once per missing path/resource kind
  - continue execution unless fallback cannot be provided
- Initial fallback scope:
  - textures -> `_missing.pcx`, `_missing.wal`
  - md2 models -> `_missing.md2`
  - sprite -> `_missing.sp2`

- Deferred fallback scope:
  - cinematics
- Fallback assets must be engine-owned, mounted predictably, and not depend on mod content being complete.
- Sky fallback is removed from scope:
  - `sky/<name>.sky` is a synthetic key, not a real file format fallback target
  - missing sky side textures should be handled through normal PCX fallback instead

### Resolver / Loader Rules
- Stop treating `null` `FileHandle` as a normal resolver contract inside Cake loaders.
- Implement the policy directly in `CakeFileResolver.resolve(...)`.
- Extract current raw lookup logic into `internal fun tryResolve(...)`.
- `resolve(...)` should:
  - call `tryResolve(...)`
  - if found, return the real handle
  - if missing, infer `ResourceKind` from the requested extension for supported types
  - in strict mode, throw `MissingResourceException` for non-tolerable kinds
  - in fallback mode, return `Gdx.files.internal(<fallback-path>)` when the kind provides a fallback
  - for tolerable-missing kinds (currently `SOUND`), warn and return `null`
  - for unsupported/out-of-scope types, keep current behavior unless and until they are explicitly classified
- Warning policy:
  - keep a small in-memory `Set<Pair<String, ResourceKind>>` in the resolver
  - warn only when the resolver makes a final policy decision for a missing resource
  - do not warn on every raw probe/lookup attempt
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
  - current scope is intentionally narrow: only missing-resource failures with supported fallback policy participate in this path
  - other asset/runtime exceptions remain out of scope for this track and should still surface normally
- Remove the remaining direct `Com.Error` usage from Cake and replace it with normal Kotlin control flow plus the shared recovery boundary.
- Cleanup behavior after a handled client-side missing-resource failure:
  - dispose / clear the active `Game3dScreen`
  - leave the application process alive
  - show the console
  - rely on the already emitted warning/error for user-visible diagnosis

### Implementation Plan
1. Exception model
- Add `MissingResourceException`

2. Loader policy
- Add `fs_debug_loaders`.
- Add `ResourceKind` with:
  - fallback asset path
  - tolerable-missing flag
- Put the missing-resource policy directly into `CakeFileResolver.resolve(...)`.
- Add resolver-local warn-once bookkeeping keyed by `(requestedPath, resourceKind)`.
- Postpone loader signature changes unless the resolver-level fix still leaves nullability gaps.

3. Resolver refactor
- Extract the existing raw lookup flow into `tryResolve(...)`.
- Keep asset probing order unchanged:
  - VFS game data
  - classpath fallback
  - internal engine assets
- Add extension-to-`ResourceKind` inference for the supported types of this track.
- Resolver outcomes become:
  - real asset file
  - internal fallback asset file
  - `null` for explicitly tolerable missing kinds
  - `MissingResourceException` for non-tolerable unresolved kinds in strict mode

4. Loader hardening
- Update `PcxLoader`, `WalLoader`, `Md2Loader`, and other custom loaders to:
  - reject missing input with `MissingResourceException`
  - use placeholder assets when policy allows
  - preserve useful path/cause information

5. Asset acquisition cleanup
- Refactor `GameConfiguration` and nearby callers to stop scattering `resolve(path) == null` checks.
- Route all asset loads through a common path that understands `MissingResourceException` and fallback policy.
- Preserve original logical asset keys for AssetManager ownership/refcounting:
  - request path remains the original missing asset path
  - resolver may physically return a fallback file handle behind that key

6. Recovery boundaries
- Move from the current one-off Cake exception handling to explicit reusable handlers for:
  - startup / connection / precache
  - in-game render
- Replace the last Cake `Com.Error` usage with normal exception-driven disconnect flow.
- Cleanup target for handled missing-resource failures:
  - dispose active gameplay screen state
  - drop back to console
  - do not terminate the app

7. Placeholder content
- Add/verify `_missing.pcx`, `_missing.md2`, and others
- Ensure they can load without depending on optional mod assets.
- Fallback assets live in Cake engine's asset folder.
- Required initial placeholder set:
  - `_missing.pcx`
  - `_missing.wal`
  - `_missing.md2`
  - `_missing.sp2`
- `_missing.sp2` is a real sprite file stored in engine assets and points at `_missing.pcx`.

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
- `ResourceKind` stays explicit and owns:
  - fallback mapping for supported asset classes
  - tolerable-missing policy
- Missing-resource policy is resolver-owned for this track.
- Original logical asset keys are preserved even when the resolver supplies a fallback file handle.
- Malformed assets are out of scope for this resolver-first track.
- Sounds are not fallback-backed, but are classified as tolerable missing via `ResourceKind.SOUND`.
- Cinematics remain out of scope.

### Verification Checklist
- Verify there are no remaining direct `Com.Error` calls in `cake/`.
- Verify no Cake custom loader can produce a generic Kotlin NPE for missing file input.
- Verify connecting to a server with a missing texture/model drops cleanly to console instead of terminating the app.
- Verify render-time missing assets follow the same recovery path as startup/precache misses.
- Verify fallback mode logs the missing path and actual placeholder used.
- Verify strict mode surfaces the original missing path, resource kind, and phase.

### Progress (2026-03-13)
- Done: resolver-owned missing-resource policy.
  - Added `ResourceKind` and `MissingResourceException`.
  - Added `fs_debug_loaders` handling in `CakeFileResolver`.
  - Moved raw lookup into `tryResolve(...)`.
  - Added resolver-local warn-once behavior.
  - Added initial fallback assets:
    - `_missing.pcx`
    - `_missing.wal`
    - `_missing.md2`
    - `_missing.sp2`
- Verified:
  - strict mode throws for missing `md2`
  - fallback mode redirects missing `pcx`
  - missing `sound/*` remains tolerable
- Done: split raw existence probes from policy-aware loads.
  - Added `tryResolveRaw(...)` helper for code paths that are selecting candidates rather than loading.
  - Updated `GameConfiguration`, `PlayerConfiguration`, cinematics, and effect precache to stop probing through policy-aware `resolve(...)`.
  - Actual asset loads now go through the resolver policy, while legacy fallback-selection logic still uses raw existence checks.
- Done: Cake missing-resource recovery boundary.
  - Wrapped the main Cake frame/update path so `MissingResourceException` drops cleanly to console instead of terminating the client.
  - Removed the last direct `Com.Error(...)` call from `cake/`.
  - Handled missing-resource recovery by disposing the active `Game3dScreen` and unloading its config-owned assets through the existing disconnect path.
- Done: loader-side null-file hardening for dependency discovery.
  - `Md2Loader` and `Sp2Loader` now throw `MissingResourceException` instead of silently accepting `null` dependency files.
