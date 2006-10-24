#!/bin/bash

# $Id: Jake2.sh,v 1.5 2006-10-24 23:00:43 cawe Exp $

cd `dirname $0`

export LD_LIBRARY_PATH=lib/joal/linux:lib/lwjgl/linux
CP=build:resources:lib/jogl/jogl.jar:lib/joal/joal.jar:lib/lwjgl/lwjgl.jar:lib/lwjgl/lwjgl_util.jar

exec java -Xmx100M -Djava.library.path=lib/jogl/linux:lib/joal/linux:lib/lwjgl/linux -cp $CP jake2.Jake2 $*
