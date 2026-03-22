# Console Stage Package

## Overview

This package owns the Cake developer console overlay.

It keeps Scene2D for:

- stage lifecycle
- layout and skin integration
- one-line command input

It owns custom console-specific behavior for:

- bounded line-based scrollback
- severity-aware output rendering
- visible-row-only drawing
- command history
- command/cvar prefix completion
- output scrolling

It does not own:

- engine command execution semantics (`Cbuf`, `Cmd`, `Cvar`)
- global console visibility/input-mode routing (`org.demoth.cake.Cake`)
- shell menu state (`org.demoth.cake.ui.menu`)

## Key Types

- `ConsoleStage` - overlay stage composition and engine-console sink wiring.
- `ConsoleBuffer` - bounded logical entry buffer with severity-tagged entries.
- `ConsoleOutputWidget` - custom Scene2D widget that wraps, clips, colors, and scrolls visible console rows.
- `ConsoleInputController` - console-specific keyboard behavior layered on top of `TextField`.

## Data / Control Flow

```text
Com.Printf / Com.Warn / Com.Error
  -> Com.ConsoleSink
  -> ConsoleStage.appendOutput(...)
  -> ConsoleBuffer.append(...)
  -> ConsoleOutputWidget redraws visible wrapped rows

Console input key handling
  -> ConsoleInputController
  -> Enter: echo + Cbuf.AddText/Cbuf.Execute
  -> Tab: command/cvar prefix completion
  -> Up/Down: history navigation with draft restore
  -> PageUp/PageDown / wheel: output scrolling
```

## Invariants

- `ConsoleBuffer` stores logical entries, not view-specific wrapped rows.
- `ConsoleOutputWidget` owns wrapping and `topLine`; the buffer does not know about pixel offsets or width.
- Output rendering is clipped to widget bounds and only draws visible rows.
- Input focus stays on the `TextField`; output is not part of focus traversal.
- Completion scope is intentionally small: commands and cvars only.
- History is session-local and stores submitted commands only.

## Current Behavior

- Severity colors map from `Com.ConsoleLevel` to skin colors.
- Consecutive same-severity appends coalesce until a newline boundary.
- Multiple completion matches extend to the longest common prefix and print a readable candidate list.
- Output scrolling is line-based, not `ScrollPane`-based.
- Mouse wheel scrolling is attached directly to `ConsoleOutputWidget`.
- `clear` clears the logical scrollback buffer.

## Open Follow-Ups

- Add optional output text selection/copy only if it does not complicate wrapping/scrolling excessively.
- Decide whether styling-only commands such as `console_print` should remain stage-owned or move to a more central console registration point.
- Decide whether the current `Stack`-based panel chrome should become a reusable console-specific widget.
