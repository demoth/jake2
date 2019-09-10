Jake2 README
============

Jake2 is a port of the GPL'd Quake2 engine from id Software to Java. Jake2 is
distributed under the terms of the GPL (see LICENSE).

The port was done completely in Java. No native libraries are used for the
game functionality. We use the lwjgl2 for graphics rendering and for sound.

Jake2 is still under development. Send bug reports and feedback to
jake2@bytonic.de.

Currently Jake2 supports Linux, Windows2000/XP and Mac OS X. The Jake2 dedicated
server runs on every Java supported platform.

Requirements:

 * at least JDK 1.8 to build and run Jake2

Installation
------------

from binary distribution:

- unzip the distribution(jake2-some_version.zip)
- go to bin folder
- run jake2 (or jake2.bat on windows)

build from source:

- run `gradle run` to build from source and run jake2
- run `gradle distZip` to build the distribution

installation of Quake2 data:
- if Jake2 does not detect the Quake2 files on startup you have the choice
  to select a baseq2 directory of a Quake2 installation (demo or full version)
  or to download and install the Quake2 demo files

If you want to have the latest experimental features you can grab the latest
Jake2 sources from CVS.

- "cvs -d:pserver:anonymous@jake2.cvs.sourceforge.net:/cvsroot/jake2 login"
- when askesd for password simply press the Enter key
- "cvs -d:pserver:anonymous@jake2.cvs.sourceforge.net:/cvsroot/jake2 co jake2"
- proceed with the build from source instructions

3rd party components
--------------------

Jake2 uses:
lwjgl    Light Weight Java Game Library http://www.lwjgl.org
openal   Audio library                  http://www.openal.org
izpack	 Installer	                    http://www.izforge.com/

Use <jake2@bytonic.de> for bug reports and feedback.

have fun!

bytonic Software
----------------
Holger Zickner <hoz@bytonic.de>
Carsten Weisse <cwei@bytonic.de>
Rene Stoeckel  <rst@bytonic.de>

Contributors
------------
David Sanders       				lwjgl support
Kenneth Russell	(Sun Microsystems)	jogl improvements
Scott Franzyshen					3rd Person Camera, CD Player emulation (MP3), Console Patch
