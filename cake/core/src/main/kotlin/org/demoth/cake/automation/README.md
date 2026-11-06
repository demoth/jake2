# Automation

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
