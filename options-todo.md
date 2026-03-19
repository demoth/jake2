# Cake Options TODO

## Goal
- Replace the current dead `Options` button in Cake with a usable options flow.
- Keep the first implementation thin-client scoped and incremental.
- Avoid painting the project into a corner around launcher-owned video settings and runtime cvar behavior.

## Current Cake State
- `Options` in `cake/core/src/main/kotlin/org/demoth/cake/stages/MainMenuStage.kt` is still a stub.
- Cake already has profile-local config persistence for archived cvars and key bindings.
- The desktop launcher owns initial window creation in `cake/lwjgl3/src/main/kotlin/org/demoth/cake/lwjgl3/Lwjgl3GameLauncher.kt`.
- Current launcher video behavior is hardcoded at startup:
  - `useVsync(true)`
  - `setForegroundFPS(Lwjgl3ApplicationConfiguration.getDisplayMode().refreshRate)`
  - `setResizable(false)`
  - `setFullscreenMode(Lwjgl3ApplicationConfiguration.getDisplayMode())`
- Shared `Cvar` is currently a storage/update primitive, not a behavior binding system:
  - `name`
  - `string`
  - `value`
  - `flags`
  - `modified`
  - `latched_string`
- There is no built-in per-cvar update callback or listener mechanism today.

## Reference Findings

### 1. Vanilla / Jake2 Legacy
- Legacy Jake2 options in `client/src/main/java/jake2/client/Menu.java` follow the original Quake II shape:
  - one mixed `Options` screen
  - audio + controls + crosshair on the same page
  - actions for `customize controls`, `reset defaults`, `go to console`
- Yamagi keeps the same overall shape in `../quake/quake2/yquake2/src/client/menu/menu.c`, but modernizes some entries:
  - OGG music
  - sound backend selection
  - pause-on-minimize behavior

### 2. Category Hub
- KMQuake2 splits `Options` into a root hub plus focused submenus:
  - `ui_options.c`
  - `ui_options_sound.c`
  - `ui_options_controls.c`
  - `ui_options_screen.c`
  - `ui_options_effects.c`
  - `ui_options_interface.c`
- Q2Pro does the same conceptually, but with data-driven menu scripts in `../quake/quake2/q2pro/src/client/ui/q2pro.menu`.
- In both KMQuake2 and Q2Pro, the root `Options` page is mostly navigation, not a giant form.

### 3. Modern Outlier
- Quetoo uses a more application-like UI architecture:
  - controller code in `../quake/quake2/quetoo/src/cgame/default/ui/settings/OptionsViewController.c`
  - declarative layout in `../quake/quake2/quetoo/src/cgame/default/ui/settings/OptionsViewController.json`
- This is useful as a design reference, but it is not a near-term fit for Cake’s current menu/stage architecture.

## Common Ground Across References
- Sound volume is always present.
- Music volume or music enable is always present.
- Mouse sensitivity is always present.
- Invert mouse is always present.
- Always run / freelook style movement controls are common.
- Crosshair or HUD/screen controls are common.
- Key rebinding entry point is common.
- Video settings are always present, either directly or via submenu.
- Reset defaults is common.

## Main Differences Across References
- The main difference is structure:
  - old clients use one long mixed options page
  - newer/more extended clients use an options hub with categorized submenus
- More advanced clients add specialized categories:
  - effects
  - screen/HUD
  - downloads
  - address book
  - interface
- More advanced clients also introduce settings that are not just direct 1:1 cvar toggles:
  - quality presets
  - grouped effect policies
  - restart/reload-required video changes

## Recommendation For Cake
- Do not copy the old single-screen legacy menu.
- Do not jump straight to a Quetoo-style UI framework.
- Follow the KMQuake2 / Q2Pro direction:
  - an `Options` hub screen
  - a few focused submenus

## Proposed Cake v1 Shape
- `Options`
  - `Video`
  - `Sound`
  - `Controls`
- Optional later categories:
  - `Screen`
  - `Effects`

## First Fields Worth Shipping

### Video
- fullscreen mode
- resolution
- vsync
- maybe menu/UI scale later

### Sound
- master/effects volume
- music volume

### Controls
- mouse sensitivity
- invert mouse
- always run
- freelook

## The Real Apply Problem
- A form bound to cvars is not enough.
- The important question is when and how changing a cvar actually updates behavior.

## Required Setting Classes

### 1. Immediate Apply
- Safe to apply during runtime with no restart.
- Examples:
  - sound/music volume
  - mouse sensitivity
  - invert mouse
  - always run
  - freelook
  - UI-only scale/opacity settings once Cake owns them directly

### 2. Recreate / Reconnect / Reload
- Changes may require rebuilding local runtime state, but not a full process restart.
- Candidates:
  - some renderer toggles
  - screen/HUD setup if cached into render helpers
  - effect toggles if subsystems cache derived values instead of polling cvars

### 3. Full App Restart
- These are launcher-owned today, not Cake-runtime-owned:
  - fullscreen
  - resolution
  - vsync
  - MSAA / backbuffer config
  - possibly resizable / HiDPI policy
- These cannot be honestly implemented as normal in-game cvar changes without changing launcher ownership.

## Validating the Restart Ideas

### Require Restart
- This is the safest first implementation for launcher-owned video settings.
- It is easy to explain in the UI.
- It avoids introducing fragile process/control flow before the options screen exists.
- Recommendation:
  - mark video settings as `restart required`
  - save them immediately to profile config
  - prompt the user to restart Cake

### Exit Cake And Let Launcher Restart
- This is a valid future direction, but it is not a cheap first step.
- To do it cleanly, the launcher must become a real owner of application lifecycle, not just `new Lwjgl3Application(...)`.
- It would need:
  - a restart contract from Cake to launcher
  - persisted window/video config source of truth
  - possibly reconnect context if reconnect-after-restart is desired
  - careful UX around unsaved state and active connections
- Recommendation:
  - do not make this a prerequisite for the first options implementation

## Validating the Cvar Callback Idea
- The direction is good.
- The scope should stay incremental.

### What Is Good About It
- It gives cvars real behavior, not just storage.
- It removes ad hoc polling or scattered “read cvar every frame” patterns.
- It makes option forms easier to connect to runtime systems.

### What Needs Care
- Not every cvar should own a direct side-effect callback.
- Some cvars are:
  - network-facing userinfo
  - launcher-owned startup config
  - server/game latched values
- These should not all be flattened into one “set value -> callback” model.

### Example Caveat
- `fov` in the current Quake II model is not a purely local Cake camera setting.
- It is a legacy userinfo cvar and feeds server/player-state logic.
- So `Cvar.Register("cl_fov", update = { old, new -> camera.fov = new })` is a good shape for a future client-only cvar, but it is not a drop-in model for the existing `fov` cvar.

## Recommended Incremental Cvar Evolution
- Keep the current cvar storage and flags model intact.
- Add optional metadata/behavior in layers instead of replacing everything.

### Suggested Direction
- Introduce a higher-level registration API for Cake-owned cvars only.
- Give each registered cvar an apply policy:
  - `IMMEDIATE`
  - `RECREATE_RUNTIME`
  - `RESTART_APP`
- Allow an optional update hook for `IMMEDIATE` and maybe `RECREATE_RUNTIME`.
- Keep plain legacy cvars working exactly as they do today.

### Example Shape
- `registerCakeCvar(name, defaultValue, flags, applyPolicy, onChange?)`
- This should sit on top of `Cvar`, not replace `Cvar` all at once.

## Recommendation For Options Work
- Use the options screen as the moment to start revisiting cvars, but do it incrementally.
- First:
  - build the `Options` hub + `Sound` + `Controls`
  - use plain cvar persistence with direct Cake-side apply where trivial
- Then:
  - add minimal `Video`
  - mark launcher-owned settings as `restart required`
- After that:
  - introduce Cake-owned cvar registration metadata and update hooks for selected local settings
  - avoid broad cvar redesign before there is at least one working options flow using it

## Immediate Next Steps
1. Add `Options` hub screen.
2. Add `Sound` submenu with runtime-safe settings.
3. Add `Controls` submenu with runtime-safe settings.
4. Decide the persisted schema/source of truth for launcher-owned video settings.
5. Add `Video` submenu with `restart required` labeling first.
6. Revisit cvar registration for a small set of Cake-local settings after the menu flow exists.
