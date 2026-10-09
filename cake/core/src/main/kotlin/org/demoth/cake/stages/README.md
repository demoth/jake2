# UI Stages

## Responsibilities

This package contains Scene2D menu screens. Navigation, validation, and backend
requests belong to `ui.menu`; `Cake` owns stages and routes input between the active
menu, console, and game screen. Content styles come from [the UI package](../ui/README.md).
The developer console has [its own documentation](console/README.md).

| Screen | Purpose |
|---|---|
| `MainMenuStage` | Main navigation and profile/options entry points |
| `ProfileEditStage` | Profile selection, creation, autodetection, and editing |
| `MultiplayerMenuStage` | Join Game, disabled Host Game, and Player Setup |
| `JoinGameStage` | Manual server address entry and validation feedback |
| `PlayerSetupStage` | Player name, password, model, skin, and handedness |
| `OptionsMenuStage` | Available client option sections |
| `OptionsSectionStage` | Editable values for one section of opted-in cvars |

## Menu Flow

```text
Stage interaction
  -> MenuIntent -> MenuEventBus
  -> MenuController validates, updates state, and calls MenuBackend
  -> Cake implements backend actions
  -> MenuStateSnapshot synchronises widgets and selects the active MenuView
```

Menu stages present state and collect edits; navigation state lives in `ui.menu`.
`Cake` owns their lifetime and rebuilds content-styled stages when the shared style
changes. They must not be treated as permanent instances across style changes.

- Join Game validates host/port before using Cake's existing connection path.
- Player Setup writes on Save; Back discards staged edits. The backend writes
  userinfo cvars, including derived gender, and saves profile configuration.
- Options collect edits and submit `SaveOptionsSection` on Apply through the
  controller/backend. Only opted-in canonical cvars are exposed; player identity
  stays in Player Setup. Latched values are shown as pending restart values.
- The controller emits submenu enter/exit sounds according to navigation depth.
  Shared button helpers handle hover feedback; Cake plays the sound signals.

## Presentation

Menus share a 1280×720 `FitViewport`, scaled uniformly and centered in the window.
Form screens use `addCenteredForm` to retain their preferred size within that canvas,
with vertical scrolling when necessary. The backdrop, console and debug overlay use
the full window independently of the menu viewport.

Stages use the engine Scene2D skin and shared content styles. Skin resource names
are part of their integration: renaming a drawable or style requires updating its
consumers. Layout padding and drawable nine-patch padding both affect spacing.

The separate `DebugGraphStage` displays runtime diagnostics: `r_debug_*` controls
render/GL metrics and `net_debug_*` controls client network metrics.
