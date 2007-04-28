@echo off

REM for lwjgl
REM SET LIB=lib/lwjgl/windows
REM SET CP=build;resources;lib/lwjgl/lwjgl.jar;lib/lwjgl/lwjgl_util.jar

REM for jogl and joal
SET LIB=lib/jsr231/windows;lib/joal/windows
SET CP=build;resources;lib/jsr231/jogl.jar;lib/joal/joal.jar;lib/jsr231/gluegen-rt.jar
java -Xmx100M -Dsun.java2d.noddraw=true -Djava.library.path=%LIB% -cp %CP% jake2.Jake2