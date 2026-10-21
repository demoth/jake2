# Cake Viewer

![screenshot](screenshots/md2-viewer.png)
![screenshot](screenshots/bsp-viewer.png)

This small app can display .md2, .bsp, .pcx, .wal, .tga and .sp2 files and play .wav sounds
(provided as an argument).
It is part of the `cake` module and has its own LWJGL3 entrypoint.

### How to run
Run from IDE using `org.demoth.cake.lwjgl3.Lwjgl3ViewerLauncher.main` and pass a file path as the program argument.

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
- Displays `.sp2` sprites at each frame's declared dimensions, resolving
  referenced images through the same resource lookup as model skins.
- Plays `.wav` sounds, including Quake II's 8-bit PCM files.
- Displays a grid and axes for models and maps.

For MD2 models, press Space to toggle animation playback and Left/Right to step
frames. Press Escape to exit.

SP2 sprites start paused on the first frame. Press Space to play/pause the looping
animation at 10 frames per second, or Left/Right to step frames with wraparound.
SP2 files do not specify a playback rate. Controls are also shown in the window.

WAV files play once on opening. Press Space to replay, S to stop, and Escape to
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
