#!/bin/bash

export LD_LIBRARY_PATH=lib/linux/lwjgl
CP=lib/jake2.jar:lib/lwjgl.jar:lib/lwjgl_util.jar

exec java -Xmx100M -Djava.library.path=lib/linux/lwjgl -cp $CP jake2.Jake2
