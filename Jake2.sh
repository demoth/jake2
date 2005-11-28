#!/bin/bash

cd `dirname $0`

export LD_LIBRARY_PATH=lib/joal/linux
CP=build:resources:lib/jogl/jogl.jar:lib/joal/linux/joal.jar:lib/lwjgl/lwjgl.jar:lib/lwjgl/lwjgl_util.jar

exec java -Xmx100M -Dlib/jogl/linux:lib/joal/linux:lib/lwjgl/linux -cp $CP jake2.Jake2
