@echo off
gradle clean build --no-daemon
if errorlevel 1 exit /b 1
echo.
echo Built JARs:
dir build\libs
