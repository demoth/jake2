# Automation

## Runtime scripts

Enable `developer 1` to register the `automation_` commands. Registration is
synchronized before startup commands and at frame boundaries; after enabling it
interactively, allow a frame before submitting automation. Disabling it cancels
active work and removes the commands. No extra cvars are needed.

| Command | Action |
|---|---|
| `automation_run <file> [exit]` | Run a local script; `exit` requests application exit after successful completion. |
| `automation_open <screen> [argument]` | Open a `MenuScreen` name in lowercase; `options_section` requires its prefix. `ui_sample` accepts `engine` or `game`. |
| `automation_click <id>` | Click a control through stage input. |
| `automation_await screen <name> [seconds]` | Wait for the presented screen, including `ui_sample` or `console`. |
| `automation_await element <id> <visible\|enabled> [seconds]` | Wait for an element in the active stage and its visible/enabled ancestors. A click also checks its center is exposed. |
| `automation_await game_ready [seconds]` | Wait for active gameplay with a renderable presentation. |
| `automation_frames <count> [seconds]` | Wait for completed draws; zero-sized frames and extra command-buffer passes do not count. |
| `automation_capture <file> [presentation\|final] [seconds]` | Capture a completed frame and wait for the PNG write before advancing. |
| `automation_cancel` | Cancel the current sequence. |

Waits default to 10 seconds. Only one sequence runs at a time. The operation
commands also work individually in the console, starting a short sequence.
Interactive completion leaves the client running. Command and wait failures report
their script location; write/render failures propagate to the caller. Failed
sequences complete exceptionally, and batch cancellation also fails.
Use the `exit` option instead of a trailing global `quit`: commands outside the
script are not sequenced with it.

Scripts reuse engine quoting, macro expansion, cvars, aliases, `//` comments,
newlines, and unquoted semicolons. Ordinary commands run through `Cmd`; commands
that print/reject rather than throw need a following readiness check. Unknown
`automation_` commands fail explicitly. Plain `wait` is rejected: use
`automation_frames` for an actual rendered-frame wait.

Automation owns its queue so waits do not stop global `Cbuf` server/loading work.
Inside an automation script, `exec` reads a local file relative to its containing
script and stays in this queue. Aliases stay here too; recursive expansion is
limited to 32 levels. Global engine `exec` and aliases retain their normal behavior.
Capture paths are also relative to their containing script; interactive paths are
relative to the working directory. Quote paths containing spaces.

The ready-to-run gallery script is `assets/automation/ui-sample.cfg`. From the repo root:

```sh
./gradlew :cake:cake-client:run --args='+set developer 1 +set cfg_autosave 0 +set vid_hidden 1 +set vid_fullscreen 0 +set vid_windowed_width 1440 +set vid_windowed_height 900 +set cl_ui_size 720 +automation_run automation/ui-sample.cfg exit'
```

This writes `build/captures/ui-sample/engine.png` and `game.png` under the repo root
and exits. The script uses filesystem paths; captures stay outside `assets/`.
`cfg_autosave 0` avoids automatic settings writes but still reads profile config;
tests use isolated profiles. A graphics context/display is required.

For a capture in an already running visible client, enter `set developer 1` in
the console, allow a frame for registration, then run:

```text
automation_capture "/tmp/cake-current.png" final
```

The client stays open. `final` includes console/diagnostic overlays; use
`presentation` to omit those overlays. The old `ui_sample_capture` command has
been removed; gallery capture uses the shared script above.

## Tests and standalone hosts

`Cake.automation.runScript(path)` and `runText(text, directory = ...)` return a
pollable `CompletableFuture<Unit>`. Tests may drive to a state without capturing,
then use ordinary Kotlin/JUnit assertions after completion:

```kotlin
HiddenGlTest.run(home = temp) {
    CakeStartupBootstrap.ensureInitialized()
    val startup = CakeStartupBootstrap.bootstrap(arguments = listOf("+set", "developer", "1"))
    val cake = Cake(startup)
    onCleanup(cake::dispose)
    cake.create()
    renderWith(cake::render)
    await(cake.automation.runText("""
        automation_open options
        automation_await screen options
    """.trimIndent()))
    assertEquals(MenuScreen.OPTIONS, cake.presentedMenuScreen)
}
```

`HiddenGlTest` is shared through core's Gradle test fixtures. Its suspendable body
uses Kotlin's standard coroutine support and resumes after rendering, without
blocking GL or invoking assertions inside runner callbacks. It owns the hidden
window, overall timeout, reverse-order cleanup, and temporary home scope. Register
cleanup before initialization; failures are rethrown to JUnit with cleanup
errors suppressed. An early application exit cannot count as scenario completion.
`onCleanup` returns an idempotent release function for fixtures that must close
before the test ends; final cleanup will not dispose them again.

`automation.await(description, timeoutSeconds) { predicate }` supports test-specific
readiness without a runtime expression language. Cancel a returned future or call
`automation.cancel(reason)` to stop its pending capture too.

Standalone fixtures implement `AutomationHost` using their UI and captures.
Call `runner.update()` before updates, `captures.beginFrame()` before drawing,
and `runner.frameRendered()` only after a drawable frame's final capture hook.
Dispose the runner and cancel captures on cleanup. Tests remain responsible for
their own fixture setup, assertions and process lifecycle.

The [standalone menu-stage test](../../../../../../test/kotlin/org/demoth/cake/ui/MenuStagesGlTest.kt)
shows this without a Cake client or server: navigation selects fixture screens,
while backend requests remain intents checked by JUnit. It writes
`cake/core/build/reports/menu-stages-engine.png` and `menu-stages-game.png` through
the shared capture command at physical framebuffer dimensions.

## UI access

`Cake.automationUi` provides GL-thread navigation and ID-based input for runtime
automation and integration tests. Direct driving requires `developer = 1`.

- Use `Actor.name` for IDs unique within a stage, independent of labels and themes.
  Existing save/Mods IDs are retained; gallery controls use `sample-engine`,
  `sample-game`, `sample-close`, `sample-action`, and `sample-disabled`.
- `open(MenuScreen, optionsSection?)` requests normal deferred menu navigation.
  Wait for a frame and inspect `Cake.presentedMenuScreen` before interacting.
- `find(id)` returns null for absence and fails for duplicates; `element(id)`
  requires a match. Lookup always uses the currently presented stage, with console
  taking priority over gallery and menus. Retained actors are invalid after rebuilds.
- `click(id)` sends a left-button down/up through the stage at the control's
  center, using viewport coordinates. Hidden/disabled ancestors and covered,
  clipped, or noninteractive targets fail; scroll the target into view first.
- `Cake.menuState`, `connectionState`, overlay visibility, and
  `isGamePresentationReady` expose read-only observations for waits and ordinary
  Kotlin/JUnit assertions. Menu state alone does not imply that menu is visible.

`AutomationUi` can also wrap a standalone stage provider and navigation callback.

## Capture requests

`Cake.automationCaptures.request(file, layer)` queues a screenshot and returns a
`CompletableFuture<FileHandle>` for polling. It requires `developer = 1`. Requests
become eligible at the next `Cake.render()` start, so a request made during command
processing waits for the following frame's menu pump and draw. Callers must still
wait for asynchronous state such as map/preview loading before requesting capture.

- `CaptureLayer.PRESENTATION` (default) captures after game/menu/gallery drawing,
  before console and diagnostics. `FINAL` includes those overlays.
- Completion follows the PNG write at physical framebuffer dimensions. A write
  failure completes the future exceptionally and propagates out of rendering.
  Poll `isDone` before inspecting the result; never block the GL thread on a future.
- Zero-sized frames leave requests pending. Callers own timeout policy and may
  cancel individual futures or call `cancelAll(reason)`. Disabling developer mode
  cancels pending requests at the next frame/render hook; Cake cancels on disposal.
  Captures never request application exit.

Standalone stage hosts use the same `AutomationCaptures` with explicit hooks:

```kotlin
val result = captures.request(file) // before the next frame
captures.beginFrame()             // once per frame, before updates
stage.act(delta)
stage.draw()
captures.afterDraw(CaptureLayer.PRESENTATION)
check(result.isDone)
result.join()                     // inspect only after completion
```

## Verification

| Usage | Integration check | Capture output, relative to repo root |
|---|---|---|
| Gallery script and screenshot-free assertions | `CakeAutomationGlTest` | `build/captures/ui-sample/{engine,game}.png` from the runtime sample |
| Real save/load, confirmations, failure/recovery and profiles | `CakeSaveMenusGlTest` | `cake/cake-fullgame/build/reports/save-menu-layout.png` |
| Standalone synthetic Mods fixtures, rebuilds and missing archives | `MenuStagesGlTest` | `cake/core/build/reports/menu-stages-{engine,game}.png` |

Run the three integrations with a display and installed Quake II data:

```sh
CAKE_VIDEO_GL_TESTS=1 CAKE_HOST_GL_TESTS=1 CAKE_HOST_GAME_TESTS=1 \
CAKE_TEST_BASEDIR=/absolute/path/to/quake2 \
./gradlew :cake:core:test --tests '*MenuStagesGlTest' \
  :cake:cake-fullgame:test --tests '*CakeAutomationGlTest' --tests '*CakeSaveMenusGlTest'
```

`CAKE_TEST_BASEDIR` contains `baseq2/`; test profiles and saves use temporary homes.
The gallery test creates its own temporary output; the checked-in sample writes
the repo-level files listed above. Runtime game styling follows the active profile
and may resolve to engine fallback. Standalone game-style captures use a synthetic
conchars fixture. Pixel output depends on assets, graphics drivers and presentation
timing; use screenshots alongside state assertions rather than exact-pixel baselines.
