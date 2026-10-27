# UI Stages

## Responsibilities

This package contains Scene2D menu screens. Navigation, validation, and backend
requests belong to `ui.menu`; `MenuStages` owns menu stage lifetimes and presentation. `Cake` routes input
between the active menu, console, and game screen. Content styles come from [the UI package](../ui/README.md).
The developer console has [its own documentation](console/README.md).

| Screen | Purpose |
|---|---|
| `MainMenuStage` | Main navigation and profile/options entry points |
| `SingleplayerMenuStage` | New Game, Load Game, Save Game and Back |
| `SavedGamesStage` | Scrolling checkpoint list, selected thumbnail/details and confirmed save/delete actions; owns and releases the preview texture on selection, hide and disposal |
| `ProfileEditStage` | Profile selection, creation, autodetection, and editing |
| `MultiplayerMenuStage` | Join Game, optional Host Game, and Player Setup |
| `HostingSetupStage` | Singleplayer difficulty and multiplayer host settings |
| `JoinGameStage` | Manual server address entry and validation feedback |
| `PlayerSetupStage` | Player name, password, model, skin, and handedness |
| `OptionsMenuStage` | Available client option sections |
| `ContentBrowserStage` | Active-profile VFS tree/grid/metadata; borrowed theme, image/WAV/SP2/MD2/BSP previews delegated to ContentPreview; stage owns layout/navigation |
| `OptionsSectionStage` | Editable values for one section of opted-in cvars |

## Menu Flow

```text
Stage interaction
  -> MenuIntent -> MenuEventBus
  -> MenuController validates, updates state, and calls MenuBackend
  -> Cake implements backend actions
  -> MenuStateSnapshot synchronises widgets and selects the active MenuScreen through MenuStages
```

Menu stages present state and collect edits; navigation state lives in `ui.menu`.
`MenuStages` owns their lifetime and rebuilds content-styled stages when Cake
supplies a changed shared style. It handles browser entry/exit, interaction resets,
and active-stage drawing/input selection; navigation decisions stay in `MenuController`. They must not be treated as permanent instances across style changes.

- `cake-client` hides Singleplayer and Host Game entries. `cake-fullgame` exposes
  them and enables hosting when the current profile and session state allow it.
- Join Game validates host/port before using Cake's existing connection path.
- Host Game lists profile BSP maps as `name — title`, reading titles from worldspawn
  metadata and falling back to names. Choices refresh on entry/profile changes;
  no maps disables Start. Deathmatch and Coop share the list.
- Player Setup writes on Save; Back discards staged edits. The backend writes
  userinfo cvars through `ui.menu.PlayerSetup`, including derived gender; Cake
  then saves profile configuration. `PlayerSetup` owns the cached model/skin
  catalog, which Cake invalidates when applying a profile.
- Player Setup previews the draft model/skin on frame zero with orbit/zoom, using
  the shared MD2 pane. It loads on entry and releases its private assets on exit,
  stage rebuild, or profile/game-directory changes. Skin edits preserve the camera;
  failed loads show a local error. Inputs and controls stay on the left, with the preview on the right.
- Sound volume uses the shared horizontal slider over 0–1 in steps of 0.05, with
  a numeric readout. Changes are staged until Apply; other numeric options use
  text fields.
- Options collect edits and submit `SaveOptionsSection` on Apply through the
  controller/backend. Only opted-in canonical cvars are exposed; player identity
  stays in Player Setup. `OptionsSections.kt` enumerates and applies cvar edits;
  Cake saves the profile afterward. Latched values are shown as pending restart values.
- The controller emits submenu enter/exit sounds according to navigation depth.
  Shared button helpers handle hover feedback; Cake plays the sound signals.

## Presentation

Menus and game overlays share a `UiViewport` (`ExtendViewport`).
Its default 4:3 reference area is 960×720; other window aspect ratios extend the
available coordinates while keeping the same scale on both axes. The archived cvar
`cl_ui_size` changes the reference height live (minimum 640); the reference
width is derived as height × 4/3. Lower values make game UI larger.
Form screens use `addCenteredForm` to retain their preferred size within the centered
reference area, with vertical scrolling when necessary. HUD elements can use the full
extended area. The engine console and debug graphs use an unscaled `ScreenViewport`,
independent of `cl_ui_size`. The world and backdrops continue to fill the window.

Stages use the engine Scene2D skin and shared content styles. Skin resource names
are part of their integration: renaming a drawable or style requires updating its
consumers. Layout padding and drawable nine-patch padding both affect spacing.

The separate `DebugGraphStage` displays runtime diagnostics: `r_debug_*` controls
render/GL metrics and `net_debug_*` controls client network metrics.
