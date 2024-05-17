# Model viewer for Cake engine

![screenshot](cake-model-viewer-screenshot.png)

This small app can display .md2 and .pcx files (provided as an argument)

To build run `gradle :cake-model-viewer:nativeBuild` (requires graalvm jdk 17)

You will find the executable in `cake-model-viewer/build/native/nativeCompile`

## List of feature
 - renders .md2 model in the 1st animation frame with the first referenced skin (searches in the same folder as the .mdl file)
 - can also render a .pcx image
 - TODO: change skins
 - TODO: render animation
 - TODO: display .md2 information - like number of triangles, vertices, frames, etc
 - TODO: add grid
 - TODO: add axes

## How to associate .md2 files with Cake model viewer?

### Linux instruction:

1. Create a new MIME type description file.
   Create a new file in the `~/.local/share/mime/packages` directory. 
   You can name it something like `quake-md2.xml.`:


    <?xml version="1.0" encoding="UTF-8"?>
    <mime-info xmlns="http://www.freedesktop.org/standards/shared-mime-info">
      <mime-type type="application/x-quake-md2">
        <comment>Quake II Model</comment>
        <glob pattern="*.md2"/>
      </mime-type>
    </mime-info>

Then update database `update-mime-database ~/.local/share/mime`

2. Define the desktop entry in this file.
You can add an application file here `~/.local/share/applications/cake-md2-viewer.desktop`:
    

    [Desktop Entry]
    Version=1.0
    Name=Cake Model Viewer
    Exec=/path/to/the/executable %f
    Icon=quake
    Terminal=false
    Type=Application
    MimeType=application/x-quake-md2
    
Then update database `update-desktop-database ~/.local/share/applications`

3. Associate the MIME Type with Your Application


    xdg-mime default cake-md2-viewer.desktop application/x-quake-md2

