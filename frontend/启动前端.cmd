@echo off
cd /d "%~dp0"
where node >nul 2>nul
if errorlevel 1 (
  echo Please install Node.js 20.19+ or 22.12+ first: https://nodejs.org/
  pause
  exit /b 1
)
node serve.mjs
pause
