@echo off

java -cp ./build;./lib/Raylib-J-0.5.2.jar -Djava.library.path=./lib Wend || exit /b

echo Program execution finished.