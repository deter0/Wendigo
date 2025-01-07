@echo off

echo Running Program.

java.exe -Dsun.java2d.ddforcevram=true  -Dsun.java2d.ddscale=true -Dsun.java2d.translaccel=true  -cp bin/ Wendigo

echo Execution Finished.
