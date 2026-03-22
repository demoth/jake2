# UI Stages

## Overview
This package owns non-ingame Scene2D stages for shell UI:
- main menu and menu sub-screens,
- developer console stage package (`stages.console.ConsoleStage`).

It does not own:
- gameplay runtime/HUD orchestration (`stages/ingame`),
- skin asset definitions (`assets/ui/uiskin.json`, `assets/ui/uiskin.atlas`),
- global input mode toggling (`org.demoth.cake.Cake`),
- menu state reduction and backend actions (`org.demoth.cake.ui.menu`).

## Key Types
- `MainMenuStage` - Root shell menu for startup/profile/options entry points.
- `ProfileEditStage` - profile selection, creation, autodetect, and editing flow.
- `MultiplayerMenuStage` - multiplayer hub with `Join Game`, disabled `Host Game`, `Player Setup`, and `Back`.
- `JoinGameStage` - manual remote connect form (`host`, `port`, validation feedback).
- `PlayerSetupStage` - staged player-facing userinfo edits (`name`, `password`, `model`, `skin`, `hand`).
- `OptionsMenuStage` - hub for cvar-backed options sections.
- `OptionsSectionStage` - generic cvar table for one options prefix/section.
- `stages.console.ConsoleStage` - Command input and output overlay stage.
  - Detailed console package behavior is documented in `stages/console/README.md`.

## Data / Control Flow
```text
Cake.create()
  -> Scene2DSkin.defaultSkin loaded from ui/uiskin.json
  -> menu controller / event bus initialized
  -> MainMenuStage(viewport)
  -> ProfileEditStage(viewport)
  -> MultiplayerMenuStage(viewport)
  -> JoinGameStage(viewport)
  -> PlayerSetupStage(viewport)
  -> OptionsMenuStage(viewport)
  -> OptionsSectionStage(viewport)
  -> stages.console.ConsoleStage(viewport)

Cake.updateInputHandlers(...)
  -> active UI processor is menu stage OR console stage OR game screen

Menu stage interaction
  -> MenuIntent posted to MenuEventBus
  -> MenuController reduces state / calls backend
  -> Cake swaps active MenuView and syncs stage widgets from MenuStateSnapshot

Join Game save path
  -> JoinGameStage submits host/port
  -> MenuController validates and dispatches backend join request
  -> Cake reuses existing connect path and closes the menu on success

Player Setup save path
  -> PlayerSetupStage stages edits locally
  -> MenuController validates and normalizes `model/skin`
  -> Cake writes userinfo cvars and derived `gender`
  -> profile-local config persists through archived cvars

Options section edit path
  -> OptionsSectionStage edits opted-in cvars directly
  -> `CVAR_LATCH` settings show pending restart state
  -> profile-local config persists canonical cvar names and binds

Console Enter key
  -> Cbuf.AddText(input)
  -> Cbuf.Execute()
  -> append output to ConsoleBuffer / ConsoleOutputWidget
```

## Invariants
- Stages are app-owned singletons for a running `Cake` instance.
- Console execution is Enter-key driven, but the console also owns completion/history/scroll keys through `ConsoleInputController`.
- Menu navigation state lives in `org.demoth.cake.ui.menu`, not inside individual stage widgets.
- Multiplayer and options flows are routed through dedicated screens instead of ad-hoc button commands.
- `Player Setup` writes on `Save` only; `Back` discards staged edits.
- `Join Game` validates before dispatching `connect`.
- Options screens only expose opted-in canonical cvar names; player identity cvars stay in `Player Setup`.
- Stage visuals depend on skin resource names; renaming in `uiskin.json` requires stage updates.

## Decision Log
Newest first.

### Decision: shell UI uses dedicated menu screens behind a shared controller
- Context: profile setup, multiplayer, player setup, and options outgrew a single main-menu command list.
- Options considered:
1. Keep direct button handlers in each stage with no shared state model.
2. Route non-console shell UI through `MenuIntent` / `MenuController` / `MenuStateSnapshot`.
- Chosen Option & Rationale: Option 2 keeps stage code mostly dumb, centralizes validation/navigation, and makes new submenus cheap to add.
- Consequences: `Cake` owns more menu stage instances and must sync them against the current snapshot.
- Status: accepted.
- Definition of Done: main menu, profile edit, multiplayer, join game, player setup, and options all route through the shared menu controller.
- References: multiplayer menu and options implementation threads.

### Decision: Main menu buttons use table-level uniform sizing
- Context: varying text labels produced uneven button widths.
- Options considered:
1. Per-button fixed width values.
2. Table defaults with `uniformX().fillX()`.
- Chosen Option & Rationale: Option 2, less brittle and content-driven.
- Consequences: all menu entries in the same column share the widest computed width.
- Status: accepted.
- Definition of Done: `Single player`, `Multiplayer`, `Settings`, and `Exit` render with the same width.
- References: thread section about "make buttons on the MainMenuStage same width".

### Decision: Console frame rendering uses skin drawable (`console-panel`) with `Stack`
- Context: console needed chamfer panel framing without switching to custom widget code.
- Options considered:
1. Table background assignment.
2. `Stack` with drawable image + content actor.
- Chosen Option & Rationale: Option 2 for explicit layering control in Scene2D.
- Consequences: actor add order in `Stack` is now behavior-critical.
- Status: tentative.
- Definition of Done: panel border does not overlap text content and panel remains visible for both output/input blocks.
- References: thread section about console panel inset and border overlap.

### Decision: Console command execution remains direct (`Cbuf.AddText` + `Cbuf.Execute`)
- Context: console is intended as a thin UI bridge to existing command pipeline.
- Options considered:
1. Buffered async command dispatch.
2. Immediate execution in key listener.
- Chosen Option & Rationale: Option 2 preserves current engine command semantics.
- Consequences: command exceptions are caught and appended to output text.
- Status: accepted.
- Definition of Done: pressing Enter executes one command and clears input.
- References: thread section around console styling changes (behavior preserved).

## Quirks & Workarounds
- `Stack` draw order is back-to-front by add order; last added child is drawn on top.
- Why: Scene2D `Stack` rendering model.
- How to work with it: add background image before text controls when you want chrome behind text.
- Removal plan: introduce a dedicated `ConsolePanel` widget that encodes layer order.

- Console spacing can appear as "margins" even when stage padding is small.
- Why: panel texture + nine-patch split/pad + widget internals all contribute.
- How to work with it: tune content table padding and the underlying skin atlas split/pad together.
- Removal plan: standardize console panel drawable with explicit split/pad values and document it in `assets/ui/README.md`.

## How to Extend
1. Add new shell screens by extending `org.demoth.cake.ui.menu` first: screen enum, snapshot state, intents, controller/backend handling.
2. Add or extend the corresponding stage only after the state flow exists.
3. Keep menu width consistency by leaving `defaults().uniformX().fillX()` intact where used.
4. Keep player-facing userinfo editing in `PlayerSetupStage`; keep local client cvars in options sections.
5. Add console commands via `Cmd.AddCommand` in `stages.console.ConsoleStage` (or central command registration if shared).
6. For visual changes, update skin assets first, then stage layout paddings.

## Open Questions
- Should `rate` join the current `Player Setup` screen or remain outside the initial userinfo flow?
- If player preview returns later, can it stay isolated from menu-state logic and asset discovery concerns?
- Console-specific follow-ups now live in `stages/console/README.md`.
