@echo off
cd /d "%~dp0"
nginx.exe -t
if errorlevel 1 (pause & exit /b 1)
start "" nginx.exe
