# Console Rework Plan

## Goal

Replace the current `TextArea`-based console output with a custom console widget that:

- keeps Scene2D for stage/layout/input routing,
- owns its own line-based scrollback,
- renders only visible rows,
- supports severity coloring,
- adds simple command/cvar prefix completion,
- adds command history navigation,
- optionally supports mouse text selection and copy later.

This document settles the target state before implementation.

## Current Problems

### Output surface

Current `ConsoleStage` uses a `TextArea` as the output widget. That causes several mismatches:

- output is treated like an editor instead of a log view,
- `Tab` focus traversal can move focus to output,
- large amounts of appended text cause repeated relayout and string churn,
- output is plain text only and has no severity model.

### Input UX

Current input only handles Enter. Missing features:

- simple prefix completion on `Tab`,
- history navigation on `Up` / `Down`.

### Logging path

Current `Com.Printf` goes through `Cmd.ExecuteFunction("console_print", msg)` and reaches the UI as a plain string.

Consequences:

- UI receives no structured severity,
- output rendering is coupled to command dispatch,
- the output widget cannot make informed styling decisions.

## Research Notes

### Current Jake2 / old client

Existing Jake2 already contains simple completion and history logic in the deprecated client:

- `client/src/main/java/jake2/client/Key.java`
- `qcommon/src/main/java/jake2/qcommon/exec/Cmd.java`
- `qcommon/src/main/java/jake2/qcommon/exec/Cvar.java`

Useful takeaways:

- `Tab` completion for commands and cvars already exists conceptually.
- `Up` / `Down` history behavior is already established in the project.
- The old behavior is a good parity target for v1.

### yquake2

Files checked:

- `/Users/daniil.bubnov/IdeaProjects/quake/quake2/yquake2/src/client/cl_keyboard.c`

Interesting ideas:

- very small, understandable `Tab` completion path,
- direct `Up` / `Down` history recall,
- follow-up special completion for `map` after generic command completion,
- line-based console scrollback instead of pixel scrolling.

Why it matters:

- confirms that line-based scrolling is a natural fit for a Quake-style console,
- confirms that simple generic completion first, specialized completion later, is a sane sequence.

### q2pro

Files checked:

- `/Users/daniil.bubnov/IdeaProjects/quake/quake2/q2pro/src/client/console.c`

Interesting ideas:

- console owns its own scrollback ring and prompt logic,
- `Tab` completion and history are handled inside the console itself,
- paste support and extra search/navigation exist but are not required for v1,
- page navigation is line-based and explicit.

Why it matters:

- reinforces that the console should own its own model and scroll position,
- reinforces that a custom widget is a better fit than forcing a generic text editor widget.

### quetoo

Files checked:

- `/Users/daniil.bubnov/IdeaProjects/quake/quake2/quetoo/src/common/console.c`

Interesting ideas:

- completion is structured around autocomplete providers,
- multiple matches extend to the longest common prefix,
- matches can be printed in a readable grouped format.

Why it matters:

- this is a good shape for future extensibility,
- but the full provider-based system is more than v1 needs.

## Settled Decisions

### 1. Keep Scene2D, replace only the output widget

We will keep:

- `Stage`,
- `Table`,
- `TextField`,
- `InputMultiplexer`,
- skin-based colors and panel chrome.

We will replace:

- output `TextArea` with a custom Scene2D widget.

Rationale:

- Scene2D is still the right tool for layout, focus, keyboard routing, and integration with the rest of Cake.
- The problem is not Scene2D itself. The problem is using an editable text widget for console output.

### 2. No `ScrollPane` in v1

Question raised: why do we need a `ScrollPane` if the widget only draws visible lines?

Decision:

- we do **not** need a `ScrollPane` in v1.

Rationale:

- if the widget owns `topLine` and clips/draws only visible rows, `ScrollPane` adds little value,
- line-based scrolling is simpler than pixel scrolling for a Quake-style console,
- internal scrolling will make optional text selection easier to reason about later than mixing `ScrollPane` drag behavior with custom selection logic.

What `ScrollPane` would have provided:

- generic scrollbars,
- generic clipping,
- generic drag/wheel behavior.

Why we are skipping it now:

- we only need line scrolling,
- we already need a custom widget for performance and severity coloring,
- adding `ScrollPane` would not remove the need for custom scroll state.

Possible future revisit:

- if we later want draggable scrollbars or fully generic mouse scroll behavior, we can revisit this.

### 3. Severity-aware console events

We will introduce structured severity in the console path.

Initial mapping:

- `Com.Printf` / `Com.Println` => `INFO`
- `Com.Warn` => `WARN`
- `Com.Error` => `ERROR`

Notes:

- `Com.Error` also drives engine error handling and may throw; UI append behavior must not change engine semantics.
- `Com.Printf` is currently the main print path used across the codebase, so the implementation should cover it even if `Println` is the cleaner API name to think about conceptually.

### 4. Completion scope for v1

Completion in v1 will be intentionally small:

- commands and cvars only,
- prefix match only,
- single match completes and appends a space,
- multiple matches extend to the longest common prefix,
- if multiple matches still remain, print or show the candidates.

Explicitly out of scope for v1:

- map name completion,
- file/path completion,
- command-specific subcommand completion,
- quoting-aware advanced token completion.

### 5. History scope for v1

History in v1:

- submitted commands only,
- `Up` navigates older entries,
- `Down` navigates newer entries,
- preserve the current draft when the user starts browsing history,
- returning to the newest position restores that draft.

Explicitly out of scope for v1:

- persistent history across restarts,
- search through history,
- duplicate collapsing policy beyond basic sanity.

### 6. Output selection is optional

Output text selection and copy is desirable, but not required for the first delivery if it substantially increases complexity.

Decision:

- design the widget so selection can be added,
- do not let selection requirements distort the v1 architecture,
- if selection starts to multiply complexity, ship v1 without it.

Reasoning:

- performance, severity coloring, completion, and history are the core console improvements,
- selection adds coordinate-to-row/column mapping, drag state, selection painting, and clipboard integration.

## Target Architecture

### Console event model

Proposed types:

- `ConsoleSeverity`
- `ConsoleEntry`
- `ConsoleBuffer`

Shape:

- `ConsoleSeverity`: `INFO`, `WARN`, `ERROR`, possibly `DEBUG` later.
- `ConsoleEntry`: one logical line or append unit plus severity.
- `ConsoleBuffer`: bounded ring buffer of logical entries.

Important behavior:

- bounded size to prevent unbounded memory growth,
- append cheap enough to tolerate frequent prints,
- resize/wrap cache invalidation handled separately from storage.

### Console output widget

Proposed role:

- a Scene2D widget or actor that renders console rows from `ConsoleBuffer`.

Responsibilities:

- line-based scroll state, for example `topLine`,
- compute visible row range from widget height and font line height,
- render only visible rows,
- clip to widget bounds,
- map severity to colors,
- auto-follow bottom unless the user has scrolled away.

Optional later responsibilities:

- mouse selection,
- copy selected text,
- scrollbar visuals.

Important note:

- the widget should own rendering and scrolling,
- the buffer should not own view-specific state like pixel offsets.

### Wrapping model

We need a clear distinction between:

- logical entries stored in the buffer,
- visual rows drawn in the widget.

Plan:

- buffer stores logical entries,
- widget derives wrapped visual rows for the current width,
- wrapping cache is invalidated on width change or new appended text.

This is necessary because:

- line count on screen depends on widget width,
- performance requires avoiding full re-wrap on every frame when nothing changed.

### Console stage composition

Planned layout:

- output widget in the main console area,
- input `TextField` in the footer,
- same panel framing look as today.

Keyboard focus rules:

- input field is the only normal keyboard focus target,
- output widget is not part of focus traversal,
- `Tab` is reserved for completion, not focus switching.

### Input behavior

`TextField` keeps responsibility for:

- text editing,
- caret movement,
- clipboard paste into input.

Console-specific logic adds:

- `Enter` execute command,
- `Tab` simple completion,
- `Up` / `Down` history,
- `PageUp` / `PageDown` / wheel scrolling for output,
- optional `Home` / `End` scroll-to-top/bottom later.

## Proposed Implementation Sequence

### Phase 1. Console model and event plumbing

- introduce `ConsoleSeverity`,
- introduce bounded `ConsoleBuffer`,
- replace raw output-string accumulation with buffer appends,
- route `Com.Printf` / `Com.Println` / `Com.Warn` / `Com.Error` into severity-aware console appends,
- keep engine error behavior unchanged.

Definition of done:

- console output is no longer stored as one giant `TextArea.text` string,
- structured severity reaches the console layer.

### Phase 2. Custom output widget

- add a custom Scene2D console output widget,
- render only visible rows,
- implement line-based scrolling with wheel and page keys,
- remove output `TextArea`,
- keep existing visual frame/panel style.

Definition of done:

- output scrolls,
- output no longer participates in focus traversal,
- performance no longer degrades with large text due to `TextArea.appendText`.

### Phase 3. Input completion and history

- add command/cvar prefix completion,
- add history buffer and draft restore,
- reclaim `Tab` from focus traversal,
- print ambiguous matches in a readable format.

Definition of done:

- `Tab` completes commands/cvars,
- `Up` / `Down` recall command history,
- focus stays in input while using console controls.

### Phase 4. Severity colors and polish

- map severities to skin colors,
- differentiate echoed commands from engine output if useful,
- verify resize behavior and bottom-follow behavior.

Definition of done:

- warnings and errors are visually distinct,
- output remains readable under resize and log spam.

### Phase 5. Optional text selection

- add mouse drag selection in output widget,
- add copy-to-clipboard,
- keep input paste path working as before.

Definition of done:

- user can select output text and copy it,
- selection does not break scroll behavior or performance.

## Risks / Things To Watch

### `Com.Error` semantics

`Com.Error` does much more than printing. It may:

- print a formatted error block,
- drop/shutdown subsystems,
- throw or terminate.

The console severity work must not change those control-flow semantics.

### Reflow on resize

If the output widget wraps text, width changes can be expensive.

Mitigation:

- cache wrapped rows,
- invalidate only when width actually changes,
- keep buffer logical entries independent from wrapped rows.

### Selection complexity

Selection gets harder when:

- rows are wrapped,
- the widget scrolls by lines,
- selection spans multiple entries.

This is the main reason selection remains optional for v1.

### Thread / timing assumptions

Console updates currently occur on the render-thread-oriented UI path.

If any console append starts happening off-thread later, buffering will need explicit synchronization or marshaling.

## Manual Verification Checklist

When implementation starts, verify:

- opening console still routes input correctly,
- `Tab` no longer changes focus,
- `Up` / `Down` history works and restores draft,
- wheel and page scrolling work,
- large print spam does not tank frame rate,
- warnings and errors are colored differently,
- resize keeps wrapping and scrolling sane,
- optional selection does not break input focus or scrolling.

## Non-Goals For First Pass

- advanced command-specific completion,
- map/file/demo completion,
- persistent command history,
- perfect pixel-precise text selection,
- reworking the whole command system,
- replacing Scene2D.
