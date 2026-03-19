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

## Agreed Plan
- Options are cvar-backed.
  - Cvars remain the source of truth for console, config persistence, and options UI.
- Options use a Q2Pro-style hub with submenus.
- `userinfo` cvars are not part of `Options`.
  - They belong to `Player Setup`, like in vanilla Quake II.
- Initial submenu grouping is prefix-based:
  - `s_` -> sound
  - `vid_` -> video/display
  - `in_` -> input
  - `r_` -> rendering/visuals
  - `cl_` -> generic client settings
- Prefix alone is not enough to expose a cvar.
  - A cvar must also opt in via an options-related cvar flag.
- For now, grouping should stay simple:
  - prefix selects the section
  - alphabetical order inside a section
  - no second explicit grouping layer yet
- Cvars with missing or imperfect prefixes can be handled later:
  - canonical rename + legacy alias
  - migration
  - manual cleanup
- Options UI uses canonical prefixed names only.
  - Legacy aliases exist for console/exec/config compatibility, not for UI display.
- Aliases are primarily for compatibility with old script/config/console usage.
  - New code and config writing should prefer canonical names.
- Optional description should be supported, but not required for the first slice.
- Typed widgets are explicitly deferred to the next phase.
  - First slice only needs cvar discovery and basic metadata groundwork.
- `CVAR_LATCH` should be generalized for Cake-facing restart-required client settings.
  - The same flag can represent “applies on next client restart” for Cake-owned option cvars.
  - Implementation still needs to avoid breaking current server/game latch behavior.

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
- Use the options screen as the moment to revisit cvars, but keep the rollout incremental.
- First:
  - build cvar discovery support for opt-in option cvars
  - build the `Options` hub
  - expose `Sound` and `Controls`
- Then:
  - expose minimal `Video`
  - use generalized latch semantics for restart-required settings
- After that:
  - add typed cvar metadata for widget inference and stronger validation

## Validate: Use Cvar As The Backing Structure
- This idea is sound.
- There is no strong reason to invent a separate persistent `Option` model just to drive the UI.
- `Cvar` is already the shared source of truth for:
  - current value
  - persistence through `CVAR_ARCHIVE`
  - command/console interoperability
  - userinfo/serverinfo propagation
- Recommendation:
  - keep `Cvar` / `cvar_t` as the backing data structure
  - extend it with optional metadata needed by Cake options UI
  - avoid a second parallel registry that would drift from real cvar state

## Validate: Reuse Latched Semantics For Client Restart
- The idea remains good.
- Restart-required values are conceptually “latched until a later apply boundary”.
- That matches `latched_string`.
- Agreed direction:
  - reuse `CVAR_LATCH` for Cake-facing restart-required settings
  - treat it as “next client restart” for those cvars
- Implementation note:
  - current `CVAR_LATCH` behavior is still server-oriented in `Cvar.java`
  - Cake/client usage should be generalized carefully so server/game behavior keeps working

## Validate: Make Cvars More Type Safe
- This idea is strong.
- It is probably the most useful long-term improvement for options work.
- It is intentionally not part of the first options PoC.

### Why It Helps
- It makes invalid values harder to set.
- It lets the UI infer controls automatically.
- It gives the console and config system richer validation.
- It reduces duplicated menu code that currently hardcodes slider ranges and yes/no lists.

### Useful Metadata To Add Later
- description/help text
- value kind:
  - boolean
  - integer
  - float
  - enum
  - string
- numeric constraints:
  - min
  - max
  - step
- allowed values for enum-like cvars
- optional on-change/update callback

### What The UI Can Infer Later
- boolean -> checkbox / toggle
- integer or float range -> slider or spinner
- enum -> select box
- constrained string -> select box or validated text field
- unconstrained string -> text field

### Important Constraint
- Do not try to retrofit every legacy cvar in one pass.
- Some cvars are:
  - free-form
  - engine internal
  - command-adjacent
  - not suitable for normal options UI
- So metadata should be optional, not mandatory for all cvars.

## Recommended Incremental Shape
- Keep legacy `FindVar/Get/Set` behavior working.
- Extend `cvar_t` or the registration path with optional metadata.
- Let Cake options render only cvars that opt in through flags.
- Let prefixes provide the first-pass section mapping.

### Good End State
- One registry
- One source of truth
- Console and UI both operate on the same cvars
- UI widgets can be generated mostly from cvar metadata
- Restart-required video settings can be shown honestly without fake live-apply behavior

## Example Direction
- First phase:
  - `register/get cvar + flags + optional description`
- Later phase:
  - `registerCvar(name, defaultValue, flags, description, type, allowedValues, onChange?)`
- The exact API can differ, but the direction is correct:
  - cvars remain the backing model
  - typed metadata drives validation and UI generation
  - latch semantics explain restart-required behavior

## Immediate Next Steps
1. Continue migrating safe local option cvars to canonical prefixed names plus legacy aliases.
2. Keep registering option cvars during Cake startup before profile config load.
3. Add the first genuinely useful `Sound` section entries.
4. Add more `Controls` entries that are runtime-safe.
5. Decide the persisted schema/source of truth for launcher-owned video settings.
6. Add `Video` submenu entries with `restart required` labeling first.

## Progress
- Done:
  - added `CVAR_OPTIONS` and optional cvar description metadata
  - added sorted cvar discovery by `prefix + flag`
  - tagged an initial set of real Cake cvars for options discovery
  - replaced the dead main-menu `Options` button with a real options hub
  - added a generic options section screen driven by discovered cvars
  - added cvar alias support for legacy console/exec/config compatibility
  - switched the first safe local cvars to canonical prefixed names:
    - `sensitivity` -> `in_sensitivity`
    - `crosshair` -> `cl_crosshair`
  - kept legacy names as aliases so old configs/scripts still resolve
  - moved option-cvar registration into Cake startup so the options menu sees canonical entries immediately
  - added a live `Sound` option backed by `s_volume`
  - added a live `Controls` option backed by `in_invert_mouse`
- Current limitations:
  - section membership is still pure prefix-based
  - option editing is generic text-field based for now
  - only a small subset of cvars is currently migrated/opted in
  - launcher-owned video settings still do not have a proper restart/apply flow
  - typed widgets and stronger validation remain the next phase
