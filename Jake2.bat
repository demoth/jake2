@echo off
SET PATH=lib\joal\windows;%PATH%
SET CP=build;resources;lib/jogl/jogl.jar;lib/joal/windows/joal.jar;lib/lwjgl/lwjgl.jar;lib/lwjgl/lwjgl_util.jar
java -Xmx100M -Dsun.java2d.noddraw=true -Djava.library.path=lib/jogl/windows;lib/joal/windows;lib/lwjgl/windows -cp %CP% jake2.Jake2