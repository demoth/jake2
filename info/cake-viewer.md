# Cake Viewer

![screenshot](screenshots/md2-viewer.png)
![screenshot](screenshots/bsp-viewer.png)

This small app can display .md2, .bsp, .pcx, .wal, .tga and .sp2 files, play .wav
sounds, and browse .pak archives.
It is part of the `cake` module and has its own LWJGL3 entrypoint.

Start without arguments to see "Drop a resource to view". Drop a resource file
onto the viewer window to open it or replace the current preview.
If several files are dropped, the first is opened. Switching stops sound playback
and resets animation and camera controls. Missing or unsupported files leave the
current preview open and show an error. A resource that fails during loading clears
the preview and shows an error; another file can still be dropped afterward.

PAK archives open a folder tree on the left and the selected folder's contents on
the right in a wrapping grid, sorted with folders first. Select a folder in the
tree or double-click its tile to browse it. Click a file to show its logical path
and size in bytes in a fixed-height footer; long paths are ellipsized so selection
does not shift the grid. Visible PCX, WAL and TGA tiles load image previews progressively;
unavailable images show a placeholder. Double-click an image to fit it to the
preview area, preserving its aspect ratio and enlarging small images. Use the
wheel over the pane to zoom around the pointer and left-drag to pan. Resizing
retains relative zoom and pan; reopening an image restores the initial fit.
"Back to folder" restores the grid
and its scroll position. Loaded thumbnails stay available while scrolling in the
same folder and across full-preview/Back navigation. Leaving the folder or archive
releases them; there is no persistent thumbnail cache. Preview tiles use plain
images with a flat hover/selection highlight. Browsing reads archive metadata without extraction;
visible thumbnails and full previews materialize resources on demand. A failed
resource preview shows an error and reopens the archive at its root folder.

Click a WAV tile to play once while keeping the folder grid visible. Double-click
to show duration and Play/Pause/Loop/Stop controls without restarting playback.
Play starts or resumes single playback, Pause holds position, Loop starts or
resumes repeated playback, and Stop resets. Space toggles pause/resume while
retaining loop mode; S stops. Clicking the tile again restarts single playback.
Selecting another tile, Back to folder, changing
folders, or opening another resource stops playback and unloads the sound.

Click an SP2 tile to play its animation once in the tile. Double-click to open
the larger preview without restarting playback. Like images, it initially fits
the pane and supports pointer-anchored wheel zoom and left-drag pan. Frames are
centred on a fixed canvas with a shared pixel scale; frame changes retain zoom
and pan. The same frame sizing is used for tile animation. It shows frame index/count and
dimensions, with Previous/Next, Play, Pause, Loop and Stop controls. Play resumes
or restarts a completed animation; Stop resets to the first frame. Frame stepping
pauses playback. Space toggles play/pause and Left/Right steps frames. Back or
changing selection releases the sprite and its frame textures.

Double-click an MD2 tile to view its first frame, automatically centred and framed
in the preview pane. Drag with the left mouse button to orbit around the model;
use the wheel over the pane to zoom. Resizing adjusts the preview resolution and
framing. The preview shows frame index/count, the stored frame name, and skin
texture count. MD2 and sprite previews share Play/Pause/Loop/Stop and Previous/Next
controls: Play resumes or restarts a completed animation, Pause holds its current
pose, Loop repeats, Stop resets to frame one, and stepping pauses and wraps.
The skin selector shows loaded skin indices and filenames; changing it preserves
animation and camera position. Single-skin models show a disabled selector, and
generated fallback textures are labelled "Default skin". Selection covers the
first 12 skins supported by the renderer.

Double-click a BSP tile to inspect the world and static brush geometry. Click the
pane to focus it, use WASD to fly and Q/E to move down/up, and left-drag to look
around in place. Clicking outside the pane releases movement focus. The camera
starts at player eye height, preferring an unnamed level start over named return
entrances; maps without a spawn start at the world bounds' centre. Movement has
no collision. The pane shows surface and total inline brush model counts.
Trigger brushes and area portals are hidden in both archive and loose previews.
The preview has no gameplay entities or sky rendering.

### Client Content Browser

The client/fullgame main menu also has **Content browser**. It uses the active
profile's resolved VFS snapshot and menu theme, with tree/grid navigation and a
fixed footer showing logical path, size and winning source layer/package or
directory. The top Back button returns to the main menu. It shares `cl_ui_size` and
refreshes its snapshot on entry or profile/style changes. Empty profiles have
an explanatory message. The client splitter uses each theme's vertical separator
as its handle; libGDX provides dragging. The standalone viewer retains its
existing native engine handle.

Visible PCX, WAL and TGA tiles now load aspect-fit image thumbnails progressively,
using the same loaders as the standalone viewer. Other tiles show their file
extension; failed images show "Unavailable". Scrolling retains folder thumbnails;
changing folders, leaving the browser, profile changes and shutdown release them. Listing
reads metadata only; image loading materializes visible package entries on demand.
Double-click an image to open its aspect-preserving full preview, with dimensions
and a pan/zoom hint. Wheel zooms around the pointer; left-drag pans. Resizing
retains relative zoom/pan, and reopening starts fitted again. Back to folder or
Escape restores the folder grid and its scroll position; another Escape leaves
the browser. Failed images show a dismissible inline error. The preview borrows
the folder-cache texture, so returning to the grid retains it; there is no second
image load or cache. Audio, sprite and model/map previews remain later client
slices. Browsing does not change theme.

### How to run
Run from IDE using `org.demoth.cake.lwjgl3.Lwjgl3ViewerLauncher.main`, optionally passing a file path as the program argument.

### How to build
Build the viewer desktop app with `./gradlew :cake:cake-viewer:build`.

### Native build
Alternatively, you can build a native (so you have easier time to associate with the files in OS)
`./gradlew -PenableGraalNative=true :cake:cake-viewer:nativeCompile`
You will find the executable in `cake/cake-viewer/build/native/nativeCompile`

## List of features

- Renders `.md2` models with frame stepping and interpolated animation playback.
- Resolves referenced skins using the opened file's folder and parent, with detected
  game-data fallback and an adjacent-file fallback for game-relative skin paths.
  Models can render with a default skin when their referenced skins are missing.
- Renders `.bsp` maps and `.pcx` / `.wal` / `.tga` images.
- BSP previews reuse the engine world renderer for opaque and translucent world
  surfaces, with all surfaces enabled for free-flight inspection, static light
  styles at full intensity, and no gameplay dynamic lights.
- Displays `.sp2` sprites at each frame's declared dimensions, resolving
  referenced images through the same resource lookup as model skins.
- Plays `.wav` sounds, including Quake II's 8-bit PCM files.
- Uses the shared engine skin for a Scene2D information panel showing the filename,
  image dimensions, current/total SP2 frames with frame dimensions, MD2 frame
  and loaded skin texture counts (including the fallback texture when used),
  sound duration in seconds (or "unknown" when unavailable), and BSP world surface
  and inline brush model counts from the loaded render data.
- Displays a grid and axes for models and maps.

For MD2 models, press Space to toggle animation playback and Left/Right to step
frames. Playback runs once by default; archive previews also offer explicit Loop.
MD2 animation interpolates smoothly between frames, while stepping selects an
exact frame. These playback rules also apply to loose model previews. Press
Escape to exit.

Loose SP2 sprites start paused on the first frame. Press Space to play/pause a
single animation at 10 frames per second, or Left/Right to step frames with
wraparound. Archive previews also offer explicit looping. SP2 files do not
specify a playback rate. Controls are also shown in the window.

WAV files play once on opening. Press Space to pause/resume, S to stop, and Escape to
close. These controls are also shown in the window.

## How to associate .md2 files with Cake Viewer?

### Linux instruction:

1. Create a new MIME type description file.
   Create a new file in the `~/.local/share/mime/packages` directory. 
   You can name it something like `quake-md2.xml.`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<mime-info xmlns="http://www.freedesktop.org/standards/shared-mime-info">
<mime-type type="application/x-quake-md2">
  <comment>Quake II Model</comment>
  <glob pattern="*.md2"/>
</mime-type>
</mime-info>
```

Then update database `update-mime-database ~/.local/share/mime`

2. Define the desktop entry in this file.
You can add an application file here `~/.local/share/applications/cake-viewer.desktop`:

```
 [Desktop Entry]
 Version=1.0
 Name=Cake Viewer
 Exec=/path/to/the/executable %f
 Icon=quake
 Terminal=false
 Type=Application
 MimeType=application/x-quake-md2
```
    
Then update database `update-desktop-database ~/.local/share/applications`

3. Associate the MIME Type with Your Application


Run `xdg-mime default cake-viewer.desktop application/x-quake-md2`
