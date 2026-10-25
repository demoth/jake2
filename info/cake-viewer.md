# Cake Viewer

View Quake II resources standalone, or open **Content browser** from the client/fullgame main menu.

## Open resources

- **Standalone:** pass a file path at launch or drop a file onto the window; the first dropped file replaces the current preview.
- **Client:** browse the active profile's resolved content, using its theme and `cl_ui_size`.
- Supported files: **PAK, PCX, WAL, TGA, WAV, SP2, MD2 and BSP**.

## Browser navigation

- Select a tree folder or double-click its tile to enter it; files show path and size, plus the winning source in the client.
- Image thumbnails load as they become visible; double-click a file for its full preview.
- **Back to folder** restores the grid and scroll position.
- **Escape:** close preview → parent folder → leave at root (close viewer / return to client menu).
- The client's top **Back** button returns directly to the menu; Escape closes loose-resource and empty standalone views directly.
- Changing selection/folders stops the previous playback; leaving a folder releases its thumbnails.
- Failed previews show an error; select or open another resource to recover.

## Previews and controls

| Format | Behavior and controls |
| --- | --- |
| PCX / WAL / TGA | Aspect-fit preview; wheel zooms around the pointer, left-drag pans. Resize retains zoom/pan; reopening resets the fit. |
| WAV | Click a tile to play once; double-click opens controls without restarting. **Space** pauses/resumes; **S** stops. Supports Quake II's 8-bit PCM WAVs. |
| SP2 | Click a tile to animate once; double-click opens controls without restarting. Frames stay centred on a fixed canvas; wheel zooms and left-drag pans. Playback runs at 10 fps. |
| MD2 | Loose files and browser entries share an automatically framed preview: left-drag orbits and wheel zooms. Shows frame names and a skin selector; generated skins are labelled **Default skin**. |
| BSP | Double-click to open; click the pane to focus, **WASD** flies, **Q/E** moves down/up, left-drag looks around. Click outside to release movement focus. |

- **WAV/SP2/MD2 controls:** Play, Pause, Loop and Stop; sprite/model previews also have Previous/Next.
- **SP2/MD2 keyboard:** Space toggles playback; Left/Right step, pause and wrap. Stop resets to frame one; completed playback can restart.
- **Loose files:** WAV plays on opening; SP2/MD2 start paused. Fly navigation is reserved for BSP maps; grid and origin overlays are removed.
- **BSP limits:** static world/brush geometry only; no collision, gameplay entities or sky. Trigger and area-portal brushes are hidden.
- Metadata includes image/frame dimensions, sound duration, model skin counts and map surface/brush counts.

## Run and build

- **IDE entrypoint:** `org.demoth.cake.lwjgl3.Lwjgl3ViewerLauncher.main` (optional file-path argument).
- **Desktop build:** `./gradlew :cake:cake-viewer:build`
- **Native build:** `./gradlew -PenableGraalNative=true :cake:cake-viewer:nativeCompile`
- **Native executable:** `cake/cake-viewer/build/native/nativeCompile/`

<details>
<summary>Linux: associate MD2 files with the native viewer</summary>

1. Create `~/.local/share/mime/packages/quake-md2.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<mime-info xmlns="http://www.freedesktop.org/standards/shared-mime-info">
  <mime-type type="application/x-quake-md2">
    <comment>Quake II Model</comment>
    <glob pattern="*.md2"/>
  </mime-type>
</mime-info>
```

2. Create `~/.local/share/applications/cake-viewer.desktop`, replacing the executable path:

```ini
[Desktop Entry]
Version=1.0
Name=Cake Viewer
Exec=/path/to/the/executable %f
Icon=quake
Terminal=false
Type=Application
MimeType=application/x-quake-md2;
```

3. Refresh the databases and set the association:

```sh
update-mime-database ~/.local/share/mime
update-desktop-database ~/.local/share/applications
xdg-mime default cake-viewer.desktop application/x-quake-md2
```

</details>

<details>
<summary>Screenshots</summary>

![MD2 viewer](screenshots/md2-viewer.png)
![BSP viewer](screenshots/bsp-viewer.png)

</details>
