# Legacy Jake2 client

The original client (developed by bytonic.de) and its fullgame launcher have been removed from the active
source tree. Use the `legacy-jake2-client` tag to browse or build them; they are no
longer maintained. The active applications live under `cake/`.

Historical references to `client/` paths and `CL_*` symbols in Cake source
comments refer to the implementation on the `legacy-jake2-client` tag.

## Why Cake replaced it

The original client used LWJGL2 and OpenGL immediate-mode rendering. Moving to
LWJGL3 and a modern shader pipeline required substantial changes. Cake uses
libGDX to provide rendering, input, audio, asset pipeline, and UI infrastructure, replacing the
legacy client implementation while preserving Quake II network compatibility.

See the [Cake guide](../cake/README.md) for the current applications.
