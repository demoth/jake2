# Developer Console

## Responsibilities

The console uses Scene2D for stage layout, skin integration, and single-line input.
It owns scrollback rendering, completion, command history, and scrolling. Engine
command semantics remain in `Cbuf`, `Cmd`, and `Cvar`; `Cake` controls console
visibility and input routing.

- `ConsoleStage` connects the engine console sink, widgets, and command execution.
- `ConsoleBuffer` stores bounded logical entries with severity, independently of
  display width and wrapping.
- `ConsoleOutputWidget` wraps, clips, colours, and draws only visible rows; it owns
  output scroll position.
- `ConsoleInputController` handles submission, completion, and history navigation.
- `ConsoleCommandHistory` stores bounded submitted history and restores the draft
  input when navigating back from history.

## Output and Commands

```text
Com.Printf / Com.Warn / Com.Error
  -> Com.ConsoleSink -> ConsoleStage.appendOutput -> ConsoleBuffer
  -> ConsoleOutputWidget: wrap and draw visible rows

Input -> ConsoleInputController
  -> Enter: echo and execute through Cbuf.AddText / Cbuf.Execute
  -> Tab: command/cvar prefix completion
  -> Up/Down: command history
  -> PageUp/PageDown: output scrolling
```

Command exceptions are caught and displayed in the console. Severity maps to skin
colours; consecutive same-severity appends coalesce until a newline boundary.
`clear` clears scrollback. Mouse-wheel scrolling is attached to the output widget;
scrolling is line-based, without a `ScrollPane`.

Completion covers command and cvar names. Multiple matches extend the longest
common prefix and print the candidate list. Input focus stays on the text field;
output is excluded from focus traversal.

## History and Presentation

Submitted commands persist to `$HOME/.cake/<profileId>/console-history.txt`, capped
to the latest 500 entries and restored on subsequent launches. Commands starting
with a space execute without being recorded. Profile history storage is wired by
`Cake`.

Output and input panels use the `console-panel` drawable behind their content in
Scene2D stacks. Children draw in insertion order, so the background must be added
first. Panel texture, nine-patch split/pad values, and widget padding all contribute
to the visible spacing.
