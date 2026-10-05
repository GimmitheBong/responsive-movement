@echo off
call "%~dp0gradlew.bat" -p "%~dp0." run --console=plain
if errorlevel 1 pause
