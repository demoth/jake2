# Shared Viewer and in-game browser UX study

Method: dual-agent (A: `/root/viewer_design_study` · B: `/root/viewer_evidence_study`).
Assessment A reviewed design independently; Assessment B traced source, native
screenshots, and detector evidence. Their findings were synthesized after A
completed. This document records the study and the accepted scope below.

**Recommendation:** keep one shared browser/preview interaction model, with
host-supplied skins and density. Reclaim space by removing repeated information
and using compact contextual controls. Keep the current browsing topology first;
evaluate List mode and focus-preview as separate follow-ups.

## Review material

- [Before/after layout board](../../build/design-studies/viewer-shared-ux/study-board.png)
- [Interactive layout study](../../build/design-studies/viewer-shared-ux/proposal.html)
- [Current in-game browser](../../build/design-studies/viewer-shared-ux/game-browser.png)
- [Existing native resource captures](viewer-engine-ui-review.md)

The interactive study changes host context, resource type, window size, folder
visibility, and Grid/List. Play/Pause, Loop, frame stepping, Help and Info operate
on the sketch only. Asset imagery is cropped from current captures. The game
treatment is a layout schematic, not a pixel-accurate conchars skin. No live
renderer or application behavior is changed by these illustrations.

## Findings

### P1: information ownership is unclear

The standalone header occupies about 169 px at both 1024×768 and 800×600. It
contains filename, resource details, shortcut instructions, an empty error row,
and frame time. Models, sprites, sounds, and maps then repeat some metadata or
instructions in their shared preview widgets. Removing frame time alone saves
only one row; the repeated information and empty reserved row also need to go.

Use one compact identity bar, roughly 40–48 native pixels tall. Show filename or
archive/profile context there. Put dynamic frame/status information with the
transport. Static dimensions can appear once beside identity or in a quiet
status line. Put technical counts in Info and shortcuts/camera gestures in Help
or tooltips. Error UI is inserted only when an error exists; hiding its label
must not leave its table row behind. Frame time belongs outside the normal
layout, optionally in an opt-in diagnostic overlay.

### P1: the transport shows commands but not their state

MD2/sprite present six equal buttons: Previous, Play, Pause, Loop, Stop, Next.
Sound has four. The current controls clear their checked state; Loop means
"start/resume looping playback", and pressing Play disables looping. This needs
a deliberate behavior change, not merely a new label.

Propose one shared transport vocabulary:

| Control | Proposed contract |
| --- | --- |
| Play/Pause | One stable-width control; resume the same position after pausing |
| Stop | Stop and return to the first frame/time zero |
| Loop | Visible on/off setting; toggling it does not start playback; Stop retains it for the current resource |
| Frame − / Frame + | MD2/sprite only; pause and step one frame, preserving existing wrap behavior |
| Position | One frame count/name beside stepping; sound/cinematic show useful playback state |
| Skin | MD2 only, compact labeled selector near the preview; avoid a full-width control |

Opening a different resource starts with Loop off. Cinematic gets Play/Pause and
Stop, without invented looping/stepping capabilities. A future seek bar needs
backend work: the current sound API does not expose seeking, and CIN uses a
sequential decoder. Do not add a decorative scrubber in this layout pass.

For every animated resource, including sound, put transport below the preview.
Keep frame stepping and its readout as one wrapping group. The proposed sound
card was rejected during review: sound uses the same bottom transport as other
media. Keep existing fast in-browser audition available.

### P1: toolbar density is inherited from full action buttons

The standalone skin uses native Share Tech Mono and thick frame artwork; the
game uses conchars/menu units. Native action buttons are roughly 72 px high,
while the standalone thin control frame has a 48 px minimum. This explains much
of the apparent mismatch in the shared browser's plus/minus controls.

Give secondary toolbar actions a compact style using existing thin/text control
treatments. Keep the selected thick frame for ordinary action buttons. Copy
drawable metrics for the compact variant; do not mutate the shared Skin or
globally shrink the Viewer font. Target roughly 48–56 px native toolbar rows,
then check the game skin separately in its own units.

The 800×600 layout illustration leaves 464 px for a loose model viewport,
compared with approximately 149 px in the current capture. This is a wireframe
space budget, not a claim about a completed Scene2D implementation. Actual
drawable minimums will determine the final dimensions.

### P2: browser structure is sound, but its visual hierarchy is weak

Both hosts already use the same tree, grid, location bar, preview slot, and
selection footer. Keep that structure. Showing folders in both a tree and a
listing is familiar navigation; it is not itself a reason to remove either.

Recommended shared anatomy:

1. Small host bar: archive/file in standalone; profile and Back to menu in-game.
2. Compact location toolbar above the listing. Back to folder appears here in
   preview mode, in the same position for both hosts. An Up action can make the
   existing parent-folder behavior discoverable.
3. Resizable folder tree plus listing; start nearer a useful name width than an
   unconditional 30% of a wide viewport. Keep selection and the splitter.
4. One selected-item status area. Full selected name/path remains discoverable;
   file provenance and demo metadata stay available in the in-game host.
5. Compact thumbnail-size controls, grouped and labeled so plus/minus are not
   mistaken for zooming the resource.

Grid remains useful for textures/images. List mode is the best optional next
addition for folders, configs, sounds, and long names. It should be implemented
once in ContentBrowser and offered in both hosts. Keep the view choice explicit;
avoid silently changing modes on every folder. Two-line grid captions and a
visible file type are also worth testing. The current selected-file metadata
must not be discarded merely to make a cleaner screenshot.

### P2: preserve the interaction contract while cleaning the presentation

Current selection auditions WAV/SP2; double-click opens the preview. Preserve
this in the first pass and make playing/looping state visible. A switch to
explicit-only playback is a separate product decision. Returning to the folder
must retain selection, scroll, and thumbnail size. Escape should continue to
leave the current level, then defer to its host: menu return or standalone exit.

Enter activation currently depends on an optional host callback, making the
standalone route differ from the in-game route. A shared activation handler
should support previewable files in both; demos retain their in-game-specific
Play demo action. Space/frame shortcuts should act on the active preview and
respect keyboard focus. Focus traversal and recovery need runtime verification.

## One UX with appropriate host differences

| Shared | Host-specific |
| --- | --- |
| Browser topology, selected/open distinction, restore-on-back behavior | PAK/archive root versus profile/VFS root |
| Control order and playback vocabulary | Engine versus game fonts, borders, colors, physical scaling |
| Resource preview widgets and contextual metadata | In-game source/layer and demo metadata |
| Keyboard routing and focus rules | Back to menu versus desktop window/drop/file integration |

Do not fix this by copying the complete game skin into standalone or globally
scaling engine UI to one third. The intent is compatible workflows with two
appropriate visual treatments. A permanent three-column tree/grid/inspector
would consume more space at 800×600 and is not recommended.

## Implementation sequence after design agreement

1. Compact the standalone-only header; remove timing and duplicate metadata/help;
   collapse empty errors. This produces the largest immediate gain without
   changing browser navigation.
2. Update shared frame/audio/cinematic transports and their compact styles
   together. Implement the explicit Play/Pause/Loop contract in controllers;
   verify both hosts and their shortcuts, rather than changing labels alone.
3. Compact the shared location/footer controls and improve name readability.
   Capture both skins and the same folder/preview/back flows at native and
   narrow sizes. Keep current audition and navigation continuity.
4. If needed, separately add shared List/Grid and optional focus-preview/folder
   collapse. These are shown as study alternatives, not required initial scope.

No seek bar, search system, persistent three-pane inspector, OS thumbnails, or
new border artwork is required. Progress knob, separator, and scrollbar shape
refinements remain deferred as previously agreed.

## Evidence and review limits

Strengths: shared components already exist; navigation keeps context; asset
inspection has working pan/zoom/orbit, frame stepping, and skin selection.

The assessment is based on native screenshots plus source behavior, with a fresh
in-game browser capture at 1440×900 and `cl_ui_size 720`. Interaction timing,
focus order, accessibility, and altered playback contracts are not yet tested
in a native implementation. The HTML sketch was checked at 1024×768 and 800×600;
its buttons/selectors stay inside the frame, related frame controls wrap as a
group, and Play/Pause, Loop retention, frame wrapping and Help passed sketch
checks. These are prototype checks, not application tests.

The CLI detector attempt returned `[]` (zero findings, no rules/locations).
The detector has no useful native Kotlin/Scene2D layout coverage here; this is
not evidence of UI correctness. Browser DOM overlays are inapplicable to the
actual application. The browser was used only to inspect the separate study.

| Nielsen heuristic | Score / 4 | Main observation |
| --- | --- | --- |
| System status | 2 | Frame/state exists, but playing/looping is weakly expressed |
| Real-world match | 2 | Familiar words with surprising Loop semantics |
| Control/freedom | 3 | Stop, pause, frame step, Back and Escape exist |
| Consistency | 2 | Shared widgets; duplicated host chrome and transport differences |
| Error prevention | 3 | Read-only browsing, bounded controls, prior-skin recovery |
| Recognition | 2 | Labels help; long names and repeated instructions compete |
| Efficiency | 2 | Shortcuts exist; Enter behavior differs between hosts |
| Minimalism | 1 | Large header, repeated details, equal-weight controls |
| Error recovery | 2 | Recovery exists; some messages lack useful next steps |
| Help | 2 | Shortcut hints exist without a clear help hierarchy |
| Total | 21 / 40 | Directional expert review, not a measured usability test |

Power users lose viewport space during repeated inspection; new users cannot
easily distinguish Loop mode from a momentary command; keyboard/low-vision
users need runtime focus/state verification rather than assumptions from a
screenshot. No mobile persona is imposed on this desktop/game tool.

The strongest recommendation is to simplify the existing shared structure
before adding new browsing features. The artifact retains Cake's typography,
palette, assets and controls rather than introducing a new visual identity.

References supporting the interaction direction:
[GNOME contextual header bars](https://developer.gnome.org/hig/patterns/containers/header-bars.html),
[browsing and back navigation](https://developer.gnome.org/hig/patterns/nav/browsing.html),
[toggle buttons for binary settings](https://developer.gnome.org/hig/patterns/controls/buttons.html),
[Blender's Play/Pause and frame-step convention](https://docs.blender.org/manual/en/4.5/render/output/animation_player.html).
These are interaction references, not a requirement to adopt GTK or Blender UI.

Questions skipped: priorities, compatibility constraints and study-only scope
were explicit. The recommendation preserves audition-on-selection. Loop's
independent-setting behavior is an explicit proposed change for design review.

## Accepted implementation pass

Keep the current grid representation; no List mode or compact sound card.
Use a compact identity header with Info/Help, compact browser controls, a narrower
initial folder sidebar, and two-line captions that preserve file extensions.
Share bottom playback controls across hosts, retaining host skins; place model
skin selection above its viewport. Loop is independent of Play/Pause and survives
Stop. No seek bar, waveform, new drawable system, or deferred scrollbar/knob work.

Native review captures: `build/captures/design-round-1/slice-5/viewer/`.
