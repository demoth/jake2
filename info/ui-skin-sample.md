# Cake UI skin sample

An internal screen for comparing engine and active-game styles with one layout and shared fixture data. Track support through **elements → styles → widgets → screens** in the [skin inventory](../assets/ui/INVENTORY.md).

## Open and switch themes

| Command | Action |
| --- | --- |
| `ui_sample` | Open the last selected mode; initially Engine |
| `ui_sample engine` | Open or switch to engine styling |
| `ui_sample game` | Open or switch to the active profile's resolved style |
| `ui_sample off` | Close and restore the previous menu/game view |

- Available in client/fullgame; the screen also has Engine, Game and Close buttons.
- Escape closes it; the global console remains available.
- Game mode identifies engine fallback when game styling is unavailable.
- Switching themes preserves input, selections, slider values, tree expansion and scroll positions.
- The sample needs no server connection or media playback; profile changes close it before replacing styles.

## Current gallery coverage

| Area | Specimens |
| --- | --- |
| Text and buttons | Mixed text, action/disabled buttons, current `x` checkbox toggle |
| Inputs | Editable, hint, password and disabled fields |
| Selection | Selectable list; enabled/disabled dropdowns |
| Scrolling | Thick panel with scrolling content; independently scrolling tree |
| Tree | Expanded/collapsed branches, selection and hover |
| Horizontal controls | Live slider, linked progress and disabled slider |
| Dividers | Horizontal separator above the bottom group; vertical separator between tree and scrolling panel |

All widgets and captions use the selected theme on a neutral backdrop. Broader target widgets and state coverage are listed in the [target widget inventory](../assets/ui/INVENTORY.md#target-widgets-for-cake-screens).

## Styling decisions

| Role | Current rule |
| --- | --- |
| Proportions | Fixed Quake II conchars artwork is the reference; engine artwork can have higher resolution. |
| Engine menus | Author at 3× Quake resolution and display at 1/3 scale; raw console/viewer/diagnostic skin resources retain native size. |
| Typography | Compare visible cap height: engine menu cap 24 → 8 units; preserve natural glyph widths and line spacing. |
| Fonts and atlas | Keep bitmap fonts separate from UI artwork; use named drawable roles rather than requiring matching atlas layouts. |
| Thick panels | Containers and action buttons; game glyphs 1–9, logical insets 8. |
| Thin panels | Checkbox toggles, inputs, lists and dropdown surfaces; game glyphs 18–26, logical insets 4. |
| Dropdown popup | Match the closed field's thin frame; avoid an extra nested list background. |
| Selection | Game text, lists, dropdowns, trees and browser tiles share the green fill from conchars glyph 11. |
| Tree marks | Game glyph 13 points right when collapsed and rotates down when expanded. |
| Slider/progress | Share track glyphs 128–130 and knob 131; Sound options use a 0–1 slider in 0.05 steps. |
| Separator/handle | Game glyphs 29–31 have fixed caps and a stretchable middle; rotate for vertical use. Engine uses its gray line. |
| Scroll/split | Game vertical scroll thumbs and client splitter handles reuse the vertical separator; the game scroll track has no backdrop. |

- Engine menu font: Share Tech Mono generated at size 34, cap height 24, line height 38, displayed at 1/3 scale.
- Raw engine font remains size 21 / line height 24; HUD metrics remain native.
- Matching line heights, square glyphs and identical text wrapping are not requirements.
- Current game fallbacks: engine caret and horizontal scrollbar artwork; standalone viewer keeps its native engine splitter.

## Scaling and ownership

- Both sample modes use one `UiViewport` and follow `cl_ui_size` live: **default 720, minimum 640; lower values make controls larger**.
- `cl_ui_size` retains its vertical meaning, with a 4:3 reference width; other aspect ratios extend the available area.
- The compact gallery fits one reference screen; viewport scaling does not mutate shared fonts.
- The sample owns its stage/state and borrows host styles; closing it preserves the engine Skin and game assets.
- 3D browser panes size their render targets from actual screen pixels, including virtual UI scaling and high-resolution displays.

## Automated captures

```sh
./gradlew :cake:cake-client:run --args='+set cfg_autosave 0 +set vid_hidden 1 +set vid_fullscreen 0 +set vid_windowed_width 1440 +set vid_windowed_height 900 +set cl_ui_size 720 +ui_sample_capture both /tmp/cake-ui'
```

- Syntax: `ui_sample_capture <engine|game|both> <directory>`; keep it last in startup commands.
- Waits two rendered frames per mode, writes `engine.png` / `game.png`, then exits; existing files are replaced.
- Captures before console/diagnostic overlays, using the active profile and its actual fallback behavior.
- `cfg_autosave 0` prevents automatic profile-setting writes; `vid_hidden 1` hides the window. Both are session-only controls.
- A graphics context/display is still required; failures produce a nonzero exit.
- Quote paths with spaces inside the argument string: `+ui_sample_capture both "/tmp/cake UI"`.

## Content browser

- **Implemented:** active-profile VFS browsing, winning-source metadata, image thumbnails/full previews, WAV audition, SP2 animation, MD2 models and BSP maps.
- Client controls use the active menu theme; standalone viewer uses native engine styling.
- Escape follows preview → current folder → parent folder → root exit/menu.
- Preview failures and navigation release owned resources independently of game assets.
- `ContentBrowserStage` handles layout/navigation; `ContentPreview` owns client media; `SceneResourcePreview` shares 3D rendering and disposal between hosts.
- Controls and format details: [Cake Viewer guide](cake-viewer.md). Ownership details: [UI architecture](../cake/core/src/main/kotlin/org/demoth/cake/ui/README.md).

## Review and remaining work

- Compare both modes with identical fixtures/resolution; inspect actual interaction states, clipping, dropdowns, borders and narrow/wide/4K layouts.
- Record reviewed coverage, gaps and intentional differences against inventory IDs; a screenshot alone does not establish interactive support.
- Existing checks cover styles, viewport scaling, playback and resource ownership; real-content probes cover both hosts and their failure/cleanup paths.

Deferred work:

- Dropdown arrow: SelectBoxStyle has no dedicated slot; compose it into backgrounds with a reserved inset.
- Game horizontal scrollbar styling and unused engine-atlas cleanup.
- Broader palette/typography specimens, optional stock widgets and dedicated screen-composition fixtures, including console/HUD compatibility.
- Content override inspection and native-build verification remain separate work.
