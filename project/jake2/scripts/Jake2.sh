#!/bin/sh

exec java -Xmx128M -Djava.library.path=lib/linux -cp lib/jake2.jar:lib/linux/jogl.jar jake2.Jake2