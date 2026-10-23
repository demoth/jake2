# Cake UI skin sample specification

The sample will make Cake's supported resources, widget styles, states and screen
usages visible in one place. It supports consistent styling and deliberate visual
decisions across engine and Quake-derived themes. Coverage is tracked in
[the skin inventory](../assets/ui/INVENTORY.md). The basic internal gallery and
Engine/Game switching are implemented. Remaining target widgets and composed
screen cases are planned below.

The primary host is an internal screen in Cake client/fullgame, opened through
a console command. Build the layout once and switch its style source between
Engine and Active game. Both modes use the same fixture data and layout rules.

The [execution plan](#execution-plan) below defines the implementation slices,
their deliverables and completion checks. Slice 1 is complete; slice 2 is next.

## Sample coverage follows the target hierarchy

Use **elements → styles → widgets → screens** throughout the sample. The
[target widget list](../assets/ui/INVENTORY.md#target-widgets-for-cake-screens)
defines its core scope: label, action button, checkbox, text input, selectable
list, dropdown, scroll pane, tree, split pane, panel, content tile and image view.
Composed and specialized widgets cover the browser, resource information,
playback, console output, scene preview, diagnostics and HUD.

The gallery must let a reader trace each target widget to its style roles and
their elements. Screen compositions show how those same widgets are reused.
Start with current implementations, including the `x` checkbox toggle. Stock
skin styles outside the target belong to an optional inspection area; listing
them does not commit Cake screens to using them.

## Coverage and consistency workflow

1. Maintain the target hierarchy and baseline inventory, separating intended
   widget support from existing definitions, Quake coverage and actual usage.
2. Build an internal sample screen with Engine and Active game modes using one
   layout builder. Borrow the host's existing styles through a small adapter.
3. Identify game-derived styling and engine fallback in the same cases; use the
   host's current profile resolution rather than loading another content context.
4. Inspect states and compositions, recording gaps, inconsistent usages and
   intentional differences.
5. Decide which roles should share resources and which need distinct styles.
   Use optional visual variants when comparison helps answer a specific question.
6. Apply agreed consistency changes in small slices and inspect affected usages
   in the sample and real screens.

The sample is a reference and inspection tool. It should work with deterministic
fixtures, without connecting to a server, changing profiles, loading a PAK or
playing media. It uses the already active profile's styles without requiring a
running game session.

## Invocation and screen behaviour

Console commands available in client/fullgame:

| Command | Behaviour |
| --- | --- |
| `ui_sample` | Open the sample in its last selected mode; default to Engine |
| `ui_sample engine` | Open or switch to engine styling |
| `ui_sample game` | Open or switch to the active game's resolved styling |
| `ui_sample off` | Close the sample and restore the previous menu/game view |

Keep this out of ordinary menu navigation. Start with the console command;
a configurable key binding can invoke it later without a dedicated hidden-key
implementation. The screen itself has Engine/Game controls and a Close action.
Escape closes it. Global console access remains available; issuing the command
from the console closes the console so the sample immediately receives input.

The sample temporarily takes menu-like input priority and releases mouse capture.
Apply the host's existing menu/console pause policy, keep network processing
running, and restore the prior view/input routing on close. Wire rendering,
resize, input, pause policy and disposal explicitly in Cake.

The first slice provides labels, action/disabled buttons, the existing checkbox
toggle, editable/hint/password/disabled inputs, a selectable list, dropdowns,
a panel and a scrolling specimen. They fit on one virtual screen, with short
captions rather than resource descriptions. Every label and control, including
Engine/Game/Close, uses the selected style. A common neutral background avoids
mixing either mode with the active profile's backdrop. Game mode explicitly
identifies whole-theme engine fallback and its inherited engine caret/selection.
The panel reuses the selected scroll surface, or text-input surface when absent;
there is no separate panel role in the current game style bundle.

## Two views within the internal sample screen

### Resource and control gallery

The default gallery is a compact widget form. Keep technical resource names in
the inventory; additional element/style inspection cases can be separate views.
Keep sample content and layout rules constant while changing themes so supported
behaviour and visual differences can be understood.

| Area | Inventory coverage | Content and interaction |
| --- | --- | --- |
| Palette | C01–C08 | Named swatches beside text on actual surfaces; include translucent surfaces over light and dark backdrops |
| Typography | F01–F05, W01 | UI font roles, mixed case, numbers, punctuation, long path, wrapping, ellipsis, unsupported-glyph examples; Quake normal/alternate text in compatibility area |
| Surfaces | D01–D06, D11, D21 | Panel and button samples at minimum, normal and large sizes; show nine-patch padding and edges |
| Buttons | W02, W03, W10, W11 | Action, current `x` boolean toggle, and plain browser tile; idle/hover/pressed/disabled, checked on/off, keyboard focus where supported |
| Input | W04 | Empty field with hint, entered text, caret, selection, focused/unfocused, disabled, long value, password masked with `*`; feedback as adjacent text |
| Selection | W05, W06 | List and open dropdown with long choices; selected/unselected, hover, disabled dropdown, scrolling |
| Navigation | W07–W09 | Horizontal/vertical scrolling, tree expanded/collapsed and selected, draggable splitter, overflowing content |
| Extensions | W12–W19 | Separate optional area for native checkbox/radio, icon buttons, sliders, progress, tooltip, window and touchpad |

Show fixed visual specimens of transient states beside a live control. Render
fixed specimens using the same resolved style resources as live widgets, with
explicit state labels. Keyboard focus must be exercised on live controls where
supported; mark missing feedback as a gap. Do not invent hover/press behaviour
in a screenshot and count it as an implemented interaction.

The gallery should report a missing required resource with its name/type rather
than silently substituting it. Intentionally resolved engine fallback in a Quake
theme is valid and should be identified as such. Keep optional extensions separate
so their absence cannot prevent reviewing the required controls.

### Screen compositions

Use selectable scenarios within the same application rather than squeezing every
screen into one crowded view. Fixtures should resemble current Cake usage:

| Scenario | Contents | Styling and support questions |
| --- | --- | --- |
| Menu | Vertical navigation over a neutral backdrop; profile label; enabled and disabled actions | Button proportions, text hierarchy, readability over a backdrop |
| Form | Profile list beside name/path fields, dropdown, `x` boolean toggle, wrapped description, feedback and Apply/Back | Alignment, density, field states, form scrolling |
| Browser | Folder tree, horizontal split, wrapping tiles, long breadcrumb/path, selected and hovered tiles, metadata and Back | Tree affordance, selection, flat idle tiles, thumbnail contrast, resize behaviour |
| Preview controls | Information panel, image placeholder, frame label, Previous/Play/Pause/Loop/Stop/Next, skin selector; sound duration variant | Toolbar spacing, narrow-window wrapping, disabled single-choice selector |
| Console and diagnostics | Info/warning/error lines, clipped/wrapped output, prompt and command field; metric labels and simple graph | Severity contrast, panel padding, caret/selection, dense text |
| Quake compatibility | Normal/alternate glyph samples and numeric fields on representative surfaces | Original glyph colours, font size, digits, explicit engine fallback |

Use fixed generated thumbnails and a neutral scene placeholder; full image/model
loading is unnecessary for a skin prototype. Reuse existing leaf widgets when
they can accept fixtures cheaply. Keep fake playback local to the sample and
reuse its actual button/label styles; no sound or animation backend is required.
An embedded Quake browser composition initially demonstrates resolved styling,
not completed production embedding.

## Theme and viewport controls

Engine mode borrows the host's engine Skin. Active game mode borrows its current
GameUiStyle; today this is Quake-derived where content loads successfully and
engine fallback otherwise. Show the selected mode, effective style and fallback
roles so a game-mode fallback is not mistaken for native game coverage. Named
visual variants can be added later for specific decisions.

Use one layout builder with explicitly supplied resolved styles. Switching modes
rebuilds its actors from the same fixture model, preserving entered text, checked
states, selections and scroll positions where applicable. The fixture content,
layout rules, viewport and scale stay the same; natural size differences caused
by font/drawable metrics remain visible. Two independently maintained screen
implementations would make comparisons unreliable.

The sample owns its Stage, fixture textures and UI state, and borrows host styles.
Closing it does not dispose the engine Skin, active game style or AssetManager-owned
textures. The current implementation closes the sample before profile changes
replace game styling; reopen it to inspect the new profile. Mode controls use the
selected style, consistently with the other widgets.

The sample uses its own UiViewport and follows `cl_ui_size` live. The setting is
the minimum virtual height, with a 4:3 reference width; other aspect ratios extend
the available area. Lower values make elements larger. The existing default is
720 and minimum is 640. Both Engine and Game modes share this mapping, and the
compact form fits within one reference screen at either size. Shared fonts are
not mutated to implement scaling. Video resolution determines the physical area
onto which the virtual screen is mapped; the setting retains its vertical meaning.

Start visual review at 960×720 and 1440×900, plus a narrow 640×720 window and a
wide 1920×1080 window. These are prototype review sizes, not a new minimum window
size contract. Inspect text clipping, scrollability, dropdown placement, tile
reflow and nine-patch borders at the chosen `cl_ui_size`. Include a high-resolution
window to verify that controls scale with the virtual reference height.

## Compact UI atlas and separate font texture

Use conchars as inspiration for a compact sheet of reusable UI primitives:
panel corners/edges/centre, fills, marks and small controls. Fonts stay in their
own bitmap texture and metrics file. The engine already loads Share Tech Mono
separately; old font regions in the UI atlas are not current typography inputs.

Named atlas regions are the runtime interface. A grid can make authoring easier,
but neither a fixed grid size nor a fixed cell order is required for engine art.
The Quake adapter maps legacy cell indices to the same semantic roles. A new
engine atlas should not require a Quake-specific loader.

Proposed atlas roles, to refine against actual style usages:

| Primitive | Uses | Metadata or state requirement |
| --- | --- | --- |
| Neutral fill | Selection, tintable surface, cursor or dim overlay | Opaque neutral pixels for predictable tinting |
| Panel frame | Console, information panel, form/browser surface | Nine-patch splits, content padding, minimum size |
| Control frame | Buttons, fields, dropdown | Shared art or separate resources where roles differ; hover/pressed/focused/disabled treatments |
| Check mark | Optional native checkbox; current boolean toggle may remain text | On/off and disabled appearance |
| Tree controls | Expand/collapse | Distinguishable at the smallest logical size |
| Track and thumb | Horizontal/vertical scrollbars; optional sliders | Fixed borders, stretch direction, minimum thumb size |
| Split handle | Browser split | Useful hit area and visible handle |
| Direction/icon marks | Optional dropdown arrow and later icon buttons | Legible at control size; add only with an actual style consumer |

Keep source artwork size, logical dimensions, padding, hit area and stretch rules
explicit. A 3×3 panel assembled from separate Quake glyph cells can satisfy the
same drawable role as an engine nine-patch region. They need not share texture
packing. Preserve Quake pixel colours where meaningful; multiplying them by an
engine palette colour is a design choice, not a neutral conversion.

The atlas is a resource organization option; adopting it does not require a new
visual style. Use the sample to judge whether existing resources can be shared
consistently and where distinct artwork is justified. Set palette values, atlas
resolution, cell size and icon treatment through concrete usage decisions.

## Completion and evidence

The prototype is ready for coverage and consistency review when the required
inventory has gallery cases and composition cases, live controls can be exercised,
and both engine and Quake coverage can be inspected with visible fallback/gap information.
Optional extensions do not block that milestone.

Save relevant gallery and composition captures with theme name, viewport policy
and dimensions. Record findings against inventory IDs and update the coverage
and consistency tracker only for states and usages actually reviewed. For each
visual decision, record what is shared, what differs intentionally and why.
The sample remains useful as a reference even when the current appearance is kept.

Initial verification should check required resource resolution, independent
theme ownership, theme switching, actual input/selection, overflow and resizing.
Use focused interactive checks; introduce automated tests only for reusable
behaviour such as coverage validation if it becomes part of the implementation.
Do not build a permanent rendering-test framework for the prototype.

## Execution plan

Complete and verify one slice at a time. Estimates below are approximate changed
application/build lines, excluding documentation; revise them against the actual
diff before starting each slice. Later slices depend on what the sample exposes.

### Slice 1 Internal gallery with engine and game modes

**Status: complete.** `UiSkinSample.kt` contains the Stage, layout and small style
adapter; `Cake.kt` owns command registration and host integration. Application
diff: 254 lines added and 4 removed across those two files (258 changed), below
the 280–450 estimate. No build changes, moved code or permanent tests were added.

Verification: client/fullgame compilation and 23 existing style/viewport/menu
tests pass. A temporary hidden-window OpenGL check used real baseq2 content and
an empty profile to exercise both native content styling and engine fallback.
It checked typed-value/selection retention, pointer clicks, popup removal, nested
wheel focus, scroll restoration with clamping, console priority, Escape/Close,
reopening, narrow resize and profile invalidation. A host spy checked pause
requests and continued host updates. Live game-session and native executable
verification remain open; visual consistency decisions remain for review.

Compact layout verification: the current engine form needs 504 virtual units of
height, fitting within the 608 units available after padding at `cl_ui_size 640`.
The game form also fits. A hidden-window check verified all visible labels and
inputs use the selected theme, values survive switching, and changes to
`cl_ui_size` apply live. Both modes were checked at a 3840×2160 framebuffer;
console/close/profile lifetime checks still pass.

User verification: the compact, consistently themed gallery and vertical
`cl_ui_size` scaling were confirmed, and this slice was accepted for commit.
Tree, splitter, tiles, image view and composed screen cases remain for slice 2.

- Add a sample Stage under
  `cake/core/src/main/kotlin/org/demoth/cake/ui/sample/`, with one layout builder,
  a fixture model and a small adapter for engine/game styles.
- Register `ui_sample [engine|game|off]` in `Cake.kt`. Integrate the screen with
  rendering, input priority, cursor capture, menu-like pause policy, resizing,
  Escape/Close and disposal. Restore the previous view when closing it.
- Borrow the engine Skin and current GameUiStyle. Resolve styles on open/switch;
  close the sample when the host replaces profile styling. The sample does
  not own these resources or create a separate content mount.
- Show labels, action buttons, the existing boolean toggle, text input,
  selectable list, dropdown, scroll pane and panel. Include disabled controls, checked/on-off
  examples, password input, hints and long text. Live controls expose transient
  hover, press, caret and selection states.
- Keep specimen captions concise and use the selected style throughout.
  Track technical style/resource mappings in the inventory rather than adding
  descriptions between the controls.

**Deliverable:** open the gallery from the client/fullgame console and switch
Engine/Game without changing layout rules or fixture state.
**Done when:** open/switch/close, console access, input restoration, resize and
resource lifetime work. Game fallback is visible; compare both styles without
joining a server. Mark only demonstrated inventory cases Available.
**Estimate:** 280–450 changed lines across the sample, adapter and Cake host.
Host lifecycle integration is the main uncertainty. Verify it before expanding
the gallery. No standalone launcher or Gradle task is needed for this slice.

### Slice 2 Complete the target widgets and screen compositions

- Add tree, split pane, content tile and image view to complete the 12 target
  widget kinds. Show relevant colours, fonts and drawable primitives beside them.
- Add a form composition and a browser composition. Use fixture VfsEntry values
  with the existing ContentBrowser where practical; use generated placeholder
  textures and callbacks rather than opening archives.
- Add representative information/transport and console specimens using the same
  style resources as their real consumers. Show custom HUD/diagnostic rendering
  in a separate compatibility area as needed to cover the inventory.
- Keep each sample view within one virtual screen, following `cl_ui_size`.
  Keep layout rules and fixture content stable across Engine/Game modes.

**Done when:** every target widget has an inspectable case; required states and
usages have named cases or an explicit gap. Check narrow/wide layouts, overflow,
dropdown placement, tree selection and splitter dragging. Capture the engine
baseline and update inventory coverage; this does not imply visual acceptance.
**Estimate:** 200–350 changed lines, mainly sample fixture/layout construction.
Reassess custom console/HUD reuse before adding adapters for those components.

### Slice 3 Complete game coverage and compatibility cases

- Extend the engine/game adapter to the remaining target roles and composed cases.
  Where the existing content bundle lacks a role, identify engine fallback.
- Expose font metrics, normal/alternate glyph text, unsupported text and HUD
  numeric rendering using the host's current resources. Show factory fallback as
  it actually behaves; independent fallback changes are a separate decision.
- Verify repeated mode switching and profile changes, including a profile that
  falls back to engine styling. Keep the UI atlas and font roles separate.

**Done when:** all target cases can be inspected in both modes; effective style
and remaining gaps are explicit; switching and closing retain correct ownership.
Run existing relevant font/style tests and interactive lifecycle checks.
**Estimate:** 100–200 changed lines beyond the initial adapter, depending on
custom HUD/console reuse. Split work that requires production style changes.

### Slice 4 Record concrete consistency decisions

Review the rendered sample together. For each finding, record the inventory ID,
affected widgets/screens, shared elements, applicable states, intended differences
and the decision. Resolve these initial questions using the visible cases:

- Which text roles share fonts/colours, and which differ for feedback?
- Which buttons/panels/fields share frames, insets and state treatments?
- What implements the checkbox role, and how is its state shown in each theme?
- Which Quake roles are derived and which intentionally use engine fallback?
- Which interaction states lack useful feedback or differ unintentionally?

**Deliverable:** a short decision list and a mapping from target style roles to
resource names for both themes, with explicit gaps. Keep current appearances
where they meet the agreed rules. The atlas grid/packing and optional visual
variants become implementation work only when a decision calls for them.

### Slice 5 Apply the agreed rules to actual consumers

First resolve target roles through the shared Skin-based contract. Then migrate
one consumer group per patch: menu/form widgets, browser and preview controls,
then console/diagnostics where relevant. Reuse standard libGDX style classes;
introduce custom style objects only for concrete custom-widget needs.

Verify each changed group in the sample and in its actual host. Check profile
changes, fallback, viewport behaviour and resource lifetime when those paths are
affected. Update the inventory with the resulting support and remaining gaps.
Estimate each patch after slice 4 establishes its precise changes.

**Completion:** the target widget set is inspectable, engine/Quake support is
explicit, equivalent usages resolve consistent styles, and intentional differences
have recorded reasons. The sample remains the reference for future additions.

Native packaging, optional widget extensions and visual variant loading can be
scheduled separately when needed. No new automated tests are required for static
fixture layout; use focused existing checks and interactive verification. Add
tests when an implemented reusable contract or ownership behaviour merits them.
