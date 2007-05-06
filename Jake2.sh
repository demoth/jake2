#!/bin/bash

cd `dirname $0`

# for lwjgl
#LIB=lib/lwjgl/linux
#CP=build:resources:lib/lwjgl/lwjgl.jar:lib/lwjgl/lwjgl_util.jar

# for jogl and joal
LIB=lib/jsr231/linux:lib/joal/linux
CP=build:resources:lib/jsr231/jogl.jar:lib/joal/joal.jar:lib/jsr231/gluegen-rt.jar

exec java -Xmx100M -Djava.library.path=$LIB -cp $CP jake2.Jake2 $*
