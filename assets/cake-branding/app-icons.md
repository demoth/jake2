# Cake desktop icons

| App / directory | Cog | Cake rim | Cake center |
| --- | --- | --- | --- |
| `fullgame` | Strawberry `#F07883` | Highlight Raspberry `#D95769` | Vanilla cream `#FFF3E3` |
| `viewer` | Blueberry `#83B8E5` | Lavender cream `#B5A5DC` | Vanilla cream `#FFF3E3` |
| `client` | Pistachio `#8CCB9B` | Mint cream `#B1DDB5` | Vanilla cream `#FFF3E3` |

All variants use the selected ten-tooth mark, the same proportions, and a
Dark cocoa `#191412` rounded tile. Color distinguishes apps alongside their
window and package names; no shape changes were needed at the exported sizes.

Regenerate from the repository root with Inkscape and Pillow installed:

```sh
python3 assets/cake-branding/generate_app_icons.py
```

The generator reads `assets/cake-branding/app-icons.json` and the selected
`cake-icon-strawberry-10t.json` geometry/tile recipe. Each app owns a generated
SVG, 512px packaging PNG, ICO (16/24/32/48/64/128/256px), and ICNS (up to 1024
physical pixels) in `cake/cake-<app>/icons/`. Its window PNGs at
16/32/64/128/256px are in `cake/cake-<app>/src/main/resources/icons/`.

`cake/desktop.gradle` resolves `project.file('icons')`. Shared launcher code uses
`setWindowIcon(FileType.Classpath, ...)` with identical `icons/icon-<size>.png`
paths for every app. Resource ownership determines which icon is loaded; no
runtime app identity selector is needed. Window titles are ordinary arguments.

The older `assets/icons/logo*` files use Fullgame/Strawberry. Shared-module
icon copies and libGDX placeholder resources were retired. Unreferenced legacy
Jake2 `resources/icon*.png` files are outside the Cake distribution assets.

The generated comparison is `build/captures/design-round-1/slice-3/app-icons.png`.
Linux application images and actual window icon pixels were verified for all
three apps. Windows/macOS containers were decoded and checked; their installers
and desktop presentation still require testing on those platforms.
