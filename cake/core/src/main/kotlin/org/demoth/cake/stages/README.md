# UI Stages

## Overview
This package owns non-ingame Scene2D stages for shell UI:
- main menu (`MainMenuStage`),
- developer console (`ConsoleStage`).

It does not own:
- gameplay runtime/HUD orchestration (`stages/ingame`),
- skin asset definitions (`assets/ui/uiskin.json`, `assets/ui/uiskin.atlas`),
- global input mode toggling (`org.demoth.cake.Cake`).

## Key Types
- `MainMenuStage` - Startup command menu (`connect`, `quit`, placeholders).
- `ConsoleStage` - Command input and output overlay stage.

## Data / Control Flow
```text
Cake.create()
  -> Scene2DSkin.defaultSkin loaded from ui/uiskin.json
  -> MainMenuStage(viewport)
  -> ConsoleStage(viewport)

Cake.updateInputHandlers(...)
  -> active UI processor is menu stage OR console stage OR game screen

Console Enter key
  -> Cbuf.AddText(input)
  -> Cbuf.Execute()
  -> append output line to TextArea
```

## Invariants
- Stages are app-owned singletons for a running `Cake` instance.
- Console submission path is Enter-key only.
- Menu button width uniformity is table-level (`uniformX().fillX()`).
- Stage visuals depend on skin resource names; renaming in `uiskin.json` requires stage updates.

## Decision Log
Newest first.

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
1. Add new menu actions by appending buttons in `MainMenuStage` and enqueueing commands through `Cbuf`.
2. Keep menu width consistency by leaving `defaults().uniformX().fillX()` intact.
3. Add console commands via `Cmd.AddCommand` in `ConsoleStage` (or central command registration if shared).
4. For visual changes, update skin assets first, then stage layout paddings.

## Open Questions
- Should `ConsoleStage` own styling-only commands (`clear`, `console_print`), or should they move to a central command registration component?
- Should console panel rendering be moved from ad-hoc `Stack` composition to a reusable widget to avoid repeated order/padding regressions?
