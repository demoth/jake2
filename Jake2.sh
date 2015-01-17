#!/bin/bash

cd `dirname $0`

# for lwjgl
if [ "$(uname)" == "Darwin" ]; then
    LIB=lib/lwjgl/osx
else
    LIB=lib/lwjgl/linux
fi

CP=build:resources:lib/lwjgl/lwjgl.jar:lib/lwjgl/lwjgl_util.jar:lib/jl1.0.1.jar

exec java -Xmx100M -Djava.library.path=$LIB -cp $CP jake2.Jake2 $*

