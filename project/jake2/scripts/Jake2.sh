#!/bin/sh

export LD_LIBRARY_PATH=lib/linux
CP=lib/jake2.jar:lib/linux/jogl.jar:lib/linux/joal.jar

exec java -Xmx128M -Djava.library.path=lib/linux -cp $CP jake2.Jake2