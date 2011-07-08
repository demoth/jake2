@echo off
SET PATH=lib\windows;%PATH%
SET CP=lib/jake2.jar;lib/jogl.jar;lib/joal.jar;lib/gluegen-rt.jar;lib/jl1.0.1.jar
start javaw -Xmx100M -Dsun.java2d.noddraw=true -Djava.library.path=lib/windows -cp %CP% jake2.Jake2