Jake2 README
============

![jake2 screenshot](info/screenshots/jake2-screenshot-1.png "Jake 2")

[Screenshots](info/screenshots/Screenshots.md)

Jake2 is a port of the GPL'd Quake2 engine from id Software. Jake2 is
distributed under the terms of the GPL (see LICENSE).

The original engine core and server components are implemented in Java.
The new `cake` client is developed in Kotlin using libGDX with an LWJGL3 backend
for graphics, input, and audio. Older client code relied on LWJGL (version 2) and OpenAL directly.

Jake2 is still under development. Feel free to send bug report if you find one.

Currently, Jake2 supports every Java supported platform.

Requirements:

 * jdk version 11 to build and run Jake2

Please note that there was an issue with more recent java versions (e.g., 17) on Linux with LWJGL 2 natives (used by the legacy client). The new `cake` client uses LWJGL3, which generally has better compatibility with modern Java versions.


Documentation & Info
--------------------
 * [Overview](info/Overview.md)
 * [BSP file format](info/BSP.md)
 * [Networking](info/Networking.md)

What is Cake? Important note about client side code
---------------------------------------------------

Jake2 client in its vanilla form is archived in `jake2-legacy` branch and will not be maintained.
Even though one of the core ideas of this project was to reuse and improve (rather than reimplement from scratch) - 
the client side code was particularly hard to maintain and develop. 
Firstly, because it is written on top of the unmaintained and obsolete second LWJGL version,
which is incompatible with the current LWJGL version.
Secondly, jake2 rendering is implemented in OpenGL immediate mode and had to be rewritten on a modern shader pipeline anyway.
And lastly, there is a libGDX framework that can provide a lot of useful functionality, and ditch a lot of code (yes, `Menu.java`, I am looking at you)

The `client` and `fullgame` modules are excluded from the build.
The new `cake` module is introduced for the client side part (WIP). Cake is a fresh implementation of the Jake2 client part on the modern libraries: LWJGL3 and libGDX.

Installation and running
------------------------

The primary way to run Jake2 is by building from source, particularly for the new `cake` client.

**Running the `cake` client (LWJGL3/Desktop):**
- Use the Gradle task: `./gradlew cake:lwjgl3:run`
- To build a distribution of the `cake` client, you can use tasks like `cake:lwjgl3:jar` to create a runnable JAR, or `cake:lwjgl3:jpackageImage` to create a native bundle (requires Java 14+). Check the `cake/lwjgl3/build.gradle` file for more details on packaging.

**Running the server:**
- (TODO: Verify the exact Gradle task for running the server. It might be a task like `./gradlew server:run` if a dedicated server module exists, or the main `run` task might default to the server if no client is specified.)

**Building from source (general):**
- `gradlew build`: Compiles all modules and runs checks.
- `gradlew distZip`: This task's content might have changed. It traditionally created a distribution of the `fullgame` module. Its current output should be verified. (TODO: Verify what `distZip` produces now).

**If you run Jake2 from an IDE (e.g., IntelliJ IDEA, Eclipse):**
  1. Import the project as a Gradle project.
  2. To run the `cake` client, find the `Lwjgl3GameLauncher` class in the `cake.lwjgl3` module (`org.demoth.cake.lwjgl3.Lwjgl3GameLauncher`) and run its `main` method.
  3. Native library paths for the `cake` client are typically managed automatically by libGDX and its LWJGL3 backend when run through Gradle or an IDE that understands Gradle projects. If you encounter issues, ensure the working directory is set correctly (usually the project's `assets/` directory or the root of the `cake/lwjgl3` module for asset loading).
  (TODO: Add specific instructions for server-side execution from IDE if necessary).

Installation of Quake2 data:
----------------------------

Jake can autodetect steam installation.
If it doesn't (when you have a non-steam version or when you install it to a custom location) pass basedir parameter to the game

`+set basedir "/home/demoth/.local/share/Steam/steamapps/common/Quake 2"`

If you want to have the latest experimental features you can grab the latest
Jake2 sources from GIT.

Compatibility
-------------

Jake2 is not compatible with the mods that have custom logic (game.dll).

Jake2 is network compatible with other clients, like Yamagi, Q2pro etc.

Jake2 is compatible with all map/models/sounds/textures as id Quake 2.

Goals of the project
--------------------

While Jake2 is a hobby project it is important to keep at least high level goals in mind:

  1. Maintain project runnable on modern machines and operating systems
  2. Bring the codebase up to contemporary expectations.
  3. Expand features of jake2 to bring more fun playing/modding it

At the moment of writing the goal N1 is achieved and goad N2 is in progress.

Roadmap
-------

With accordance to the goals we can put a list of more concrete steps:

  * gather game state in classes. at the moment state of the process is scattered across many static fields.
  * code cleanup - move to modern libraries (filesystem, network, logging) (Partially addressed in `cake` client for its specific needs)
  * file formats - support modern file formats (zip, image, audio, video, models?) (libGDX in `cake` helps with image/audio)
  * support "brother projets" content: q2 mission packs, q1? q3? hl?
  * replace q2 ui code (not 3d) with a modern UI library (e.g., libGDX's Scene2D, which is being used in the `cake` client, replacing old UI code like `Menu.java`)

### Crazy ideas area (proceed with caution):

  * add script support: graalvm support many scripting jvm languages, with little overhead. implement monster, items behavior with scripts
  * change 3d rendering code to something more recent (like yq2 gl3, q2xp?...)
  * implement mmo-like replayable campaign for q2:
 npc, quests, character progression, item & monster randomization, trading
  * jake2 game engine suit (unreal like):
full set of tools to support game development and creation of new games.
3d editor, entitity editor, scripting, and so on
  * procedural map generation
  * utilize kotlin multiplatform support and create jake2 clients for various platforms (desktop, web, android, ios)

3rd party components
--------------------

The Jake2 project utilizes several key third-party libraries:

*   **libGDX**: A cross-platform game development framework. The new `cake` client is built upon libGDX. (https://libgdx.com/)
*   **LWJGL3 (Lightweight Java Game Library 3)**: Used by libGDX for accessing native APIs for graphics (OpenGL), audio (OpenAL), and input on desktop platforms. (https://www.lwjgl.org/)
*   **Kotlin**: The `cake` client is primarily written in Kotlin, a modern programming language that runs on the JVM. (https://kotlinlang.org/)
*   **OpenAL**: An audio library, typically used via libGDX in the `cake` client for sound playback. (https://www.openal.org/)

The legacy parts of Jake2 also used LWJGL (version 2) and OpenAL directly.

Use github issue tracker for bug reports and feedback.

have fun!

Authors
-------
Daniil Bubnov <bubnov.d.e@gmail.com> - current maintainer (2019+)

## bytonic Software
Developed the project in 2003 - 2015
 * Holger Zickner <hoz@bytonic.de>
 * Carsten Weiße <cwei@bytonic.de>
 * Rene Stoeckel  <rst@bytonic.de>

## Contributors

 * David Sanders - lwjgl support
 * Kenneth Russell	(Sun Microsystems) - jogl improvements
 * Scott Franzyshen	- 3rd Person Camera, CD Player emulation (MP3), Console Patch
