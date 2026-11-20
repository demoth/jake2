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
Com.Print / Com.Info / Com.Debug / Com.Warn / Com.Error
  -> Com.ConsoleSink -> ConsoleStage.appendOutput -> ConsoleBuffer
  -> ConsoleOutputWidget: wrap and draw visible rows

Input -> ConsoleInputController
  -> Enter: echo and execute through Cbuf.AddText / Cbuf.Execute
  -> Tab: command/cvar prefix completion
  -> Up/Down: command history
  -> PageUp/PageDown: output scrolling
```

The sink carries level and display text. Consecutive same-level appends coalesce
until a newline or carriage-return boundary. Debug source attribution and class
prefixes are deferred; no unused source metadata is retained in scrollback.

Command exceptions are caught and displayed in the console. PRINT and INFO use
the main text color. DEBUG and TRACE use progressively muted skin colors. WARN
and ERROR use the warning and error colors.
`clear` clears scrollback. Mouse-wheel scrolling is attached to the output widget;
scrolling is line-based, without a `ScrollPane`.

Completion covers command and cvar names. Multiple matches extend the longest
common prefix and print the candidate list. Input focus stays on the text field;
output is excluded from focus traversal.

## Engine messaging contract

- `Com.Print`: gameplay and requested command output.
- `Com.Info`: sparse lifecycle and significant system-state messages.
- `Com.Debug(message)`: occasional developer diagnostics.
- `Com.Trace { message }`: high-volume diagnostics such as per-packet or per-frame
  output. The message builder runs only when TRACE is enabled.
- `Com.Warn`: recoverable/unexpected conditions and useful anticipated failure feedback.
- `Com.Error(message)`: report an operation failure without throwing or initiating recovery.
  The reporting API takes a message. `Com.Error(code, ...)` still performs
  legacy recovery and must not be confused with the reporting-only overload.

For an ordinary line, write `Com.Warn("Missing texture")`. Com supplies the final
newline. Keep explicit newlines for multiline text and intentional blank lines.
Only Print accepts `endLine = false`
for fragments (`Com.Print("fragment", false)` in Java or Kotlin).
Existing LF, CRLF, or CR endings and
intentional blank lines are preserved. Warn no longer inserts a textual `Warn:`
prefix or duplicates an existing final newline; its severity is carried separately.

Legacy `Error(code, message)` is deprecated. Move recovery into the failure handler
when migrating a caller. Remote command capture sends plain text to the requester
before the console sink and stdout, and the client prints each chunk verbatim.
Gameplay echoes use PRINT locally. The network print priority continues to control
player delivery and HUD presentation.

Enter `loglevel` to see the current diagnostic threshold. Valid values are `ERROR`,
`WARN`, `INFO`, `DEBUG`, and `TRACE`. The default is `INFO`, which includes
warnings and errors. Use `set loglevel TRACE` to collect high-volume diagnostics
and `set loglevel INFO` to return to the default. Changes apply to new messages in
the running process. Gameplay text and command output from `Com.Print` are always
enabled. The `developer` cvar continues to control developer features such as
automation.

The gear beside the command input opens the log levels popover. F4 toggles it,
Tab moves between selectors, and Escape closes it or dismisses an open selector.
Changing **All** sets `loglevel` and clears the Network override. The **Network**
row can select a level or follow the global level. `set loglevel_network TRACE`
collects network diagnostics while other subsystems keep the global threshold.
`set loglevel_network ""` clears the override. These cvars apply to new messages
in the process, including an embedded server. Existing scrollback stays visible.

## History and Presentation

Submitted commands persist to `$HOME/.cake/<profileId>/console-history.txt`, capped
to the latest 500 entries and restored on subsequent launches. Commands starting
with a space execute without being recorded. Profile history storage is wired by
`Cake`.

The scrollback and input use translucent `console-panel` and `console-input`
skin drawables. Their text is drawn at full opacity. Panel texture, nine-patch
split and padding values, and widget padding all contribute to the visible spacing.
