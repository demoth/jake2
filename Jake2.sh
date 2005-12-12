#!/bin/bash

# $Id: Jake2.sh,v 1.4 2005-12-12 21:48:55 salomo Exp $

cd `dirname $0`

export LD_LIBRARY_PATH=lib/joal/linux:lib/lwjgl/linux
CP=build:resources:lib/jogl/jogl.jar:lib/joal/linux/joal.jar:lib/lwjgl/lwjgl.jar:lib/lwjgl/lwjgl_util.jar

exec java -Xmx100M -Djava.library.path=lib/jogl/linux:lib/joal/linux:lib/lwjgl/linux -cp $CP jake2.Jake2 $*
