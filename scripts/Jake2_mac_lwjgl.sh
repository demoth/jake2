#!/bin/sh

export LD_LIBRARY_PATH=lib/osx/lwjgl
export DYLD_LIBRARY_PATH=lib/osx/lwjgl
CP=lib/jake2.jar:lib/lwjgl.jar:lib/lwjgl_util.jar

exec java -Xmx100M -Djava.library.path=lib/osx/lwjgl -cp $CP jake2.Jake2
