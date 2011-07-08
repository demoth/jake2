#!/bin/sh

export LD_LIBRARY_PATH=lib/osx
export DYLD_LIBRARY_PATH=lib/osx
CP=lib/jake2.jar:lib/jogl.jar:lib/joal.jar:lib/gluegen-rt.jar;lib/jl1.0.1.jar

exec java -Xmx100M -Djava.library.path=lib/osx -cp $CP jake2.Jake2
