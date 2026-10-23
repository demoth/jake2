# Cake Desktop Video

Video settings are normally archived cvars in the active profile. Changes require a full application restart.
Windowed windows can always be resized; cameras, UI and scene buffers follow
the current size immediately. The latest valid windowed size is saved with the profile, unless a
different size is explicitly pending restart. Fullscreen dimensions never replace windowed dimensions.

| Cvar | Default | Meaning |
|---|---|---|
| `vid_fullscreen` | `0` | `0` windowed, `1` fullscreen |
| `vid_windowed_width` / `vid_windowed_height` | `1024` / `768` | Window content dimensions in OS window coordinates |
| `vid_fullscreen_mode` | `desktop` | Desktop display mode or a supported `WIDTHxHEIGHT@HZ` identifier |
| `vid_fullscreen_modes` | Detected at startup | Read-only list of supported primary-monitor modes; query in the console |
| `vid_depthbits` | `24` | Requested window and scene depth precision: `16`, `24`, `32` |
| `vid_samples` | `4` | Requested window and scene MSAA samples: `0`, `2`, `4`, `8`; `0` disables |
| `vid_hidpi` | `Pixels` | `Pixels` or `Logical` coordinate units for rendering/UI/input |
| `vid_vsync` | `1` | Vertical synchronization |
| `vid_hidden` | `0` | Start with an invisible window for automation; session-only, not archived |

## Startup commands

Client and fullgame accept Quake-style `+set name value` settings and
`+command arguments`. Settings are applied before profile discovery, then reapplied
after profile config so command-line overrides win. Latched settings take effect
before window creation. Other startup commands run after client commands are
registered.

`+set cfg_autosave 0` disables automatic profile config/history saves and first-run
profile persistence for this process. It still reads existing configuration;
explicit `writeconfig` and profile editing actions retain their normal meaning.
The control defaults to `1` and is not archived. Existing filesystem read helpers
may create empty directories; it is a save policy, not a read-only filesystem mode.

For repeatable UI snapshots, see the
[automated sample capture command](../../info/ui-skin-sample.md#automated-captures).

For example, query `vid_fullscreen_modes`, then select an advertised mode:

```text
set vid_fullscreen 1
set vid_fullscreen_mode 1920x1080@144
writeconfig
quit
```

Modes are selected on the primary monitor. An unavailable or malformed identifier falls back to
the desktop mode with a console warning; the requested value remains saved. Display mode changes
depend on the operating system/compositor. Windowed dimensions use `vid_windowed_width` and
`vid_windowed_height`.

HiDPI changes coordinate units rather than selecting a display resolution. Logical and pixel
coordinates can match, including under X11/XWayland, so switching units may have no visible effect.
Scene buffers and screenshots use physical framebuffer dimensions in both modes.

The scene has its own depth attachment and optional multisample target, resolved before
postprocessing. Scene depth `32` requests floating-point depth; RGBA remains 8/8/8/8 and window
stencil remains 0. Unsupported scene configurations fall back with warnings, first disabling MSAA,
then trying depth24/16; if none works, rendering falls back to the window framebuffer. Actual window
formats are driver-selected. `vid_info` prints requested settings, actual display/buffer attributes,
logical/framebuffer dimensions, and pending restart values. Camera near/far planes remain 1/4096;
depth precision can improve z-fighting but does not fix coincident geometry.

An opt-in OpenGL test uses a hidden desktop window to check depth, MSAA resolve and buffer
recreation (requires a graphical session):

```sh
CAKE_VIDEO_GL_TESTS=1 ./gradlew :cake:core:test --tests '*SceneFrameBufferGlTest'
```
