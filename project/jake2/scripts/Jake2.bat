@echo off
SET PATH=lib\windows;%PATH%
java -Xmx128M -Dsun.java2d.noddraw=true -Djava.library.path=lib/windows -cp lib/jake2.jar;lib/jogl.jar;lib/windows/joal.jar jake2.Jake2