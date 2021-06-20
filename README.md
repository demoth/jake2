Jake2 README
============

![jake2 screenshot](info/screenshots/jake2-screenshot-1.png "Jake 2")

[Screenshots](info/screenshots/Screenshots.md)

Jake2 is a port of the GPL'd Quake2 engine from id Software to Java. Jake2 is
distributed under the terms of the GPL (see LICENSE).

The port is implemented completely in Java. No native libraries are used for the
game functionality. We use the lwjgl2 for graphics rendering and for sound.

Jake2 is still under development. Feel free to send bug report if you find one.

Currently, Jake2 supports every Java supported platform.

Requirements:

 * at least JDK 1.8 to build and run Jake2

Please note that there is an issue with more recent java versions on linux with lwjgl natives.


Documentation & Info
--------------------
 * [Overview](info/Overview.md)
 * [BSP file format](info/BSP.md)
 * [Networking](info/Networking.md)

Installation
------------

from binary distribution:

- unzip the distribution(jake2-some_version.zip)
- go to bin folder
- run jake2 (or jake2.bat on windows)

build from source:

- run `gradle run` to build from source and run jake2
- run `gradle distZip` to build the distribution

If you don't have a gradle distribution you can use gradle wrapper, like `./gradlew run`.

Installation of Quake2 data:
----------------------------

The easiest way to run quake is to pass basedir parameter to the game

`+set basedir "/home/daniil/.local/share/Steam/steamapps/common/Quake 2"`

If Jake2 does not detect the Quake2 files on startup you have the choice
to select a baseq2 directory of a Quake2 installation (demo or full version)
or to download and install the Quake2 demo files

If you want to have the latest experimental features you can grab the latest
Jake2 sources from GIT.

Compatibility
-------------

Jake2 is not compatible with the mods that have custom logic (game.dll).

Jake2 is network compatible with other clients, like Yamagi, Q2pro etc.

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
  * code cleanup - move to modern libraries (filesystem, network, logging)
  * file formats - support modern file formats (zip, image, audio, video, models?)
  * support "brother projets" content: q2 mission packs, q1? q3? hl?
  * replace q2 ui code (not 3d) with a library (i.e. nifty-gui)

### Crazy ideas area (proceed with caution):

  * add script support: graalvm support many scripting jvm languages, with little overhead. implement monster, items behavior with scripts
  * change 3d rendering code to something more recent (like yq2 gl3, q2xp?...)
  * implement mmo-like replayable campaign for q2:
 npc, quests, character progression, item & monster randomization, trading
  * jake2 game engine suit (unreal like):
full set of tools to support game development and creation of new games.
3d editor, entitity editor, scripting, and so on

3rd party components
--------------------

Jake2 uses:
lwjgl    Light Weight Java Game Library http://www.lwjgl.org
openal   Audio library                  http://www.openal.org

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
