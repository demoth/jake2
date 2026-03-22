# Console Follow-Ups

The settled console architecture and current implementation now live in:

- `cake/core/src/main/kotlin/org/demoth/cake/stages/console/README.md`

This file is intentionally reduced to active follow-up items only.

## Open Follow-Ups

- Add output text selection/copy only if it stays simple with the current wrapped-row and line-scroll model.

- Decide whether styling-only console commands should remain local to `ConsoleStage`:
  - `clear`
  - `console_print`

- Decide whether the current `Stack` + `console-panel` framing should become a reusable console-specific widget.

## Deferred

- advanced command-specific completion
- map/file/demo completion
- persistent command history
- perfect pixel-precise text selection
- reworking the whole command system
- replacing Scene2D
