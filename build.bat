@echo off

echo Building file with Java:

"./jdk-18/bin/javac.exe" -version || exit /b
"./jdk-18/bin/javac.exe" -cp ./lib/Raylib-J-0.5.2.jar ./src/*.java -d ./build || exit /b

echo Build successful!
