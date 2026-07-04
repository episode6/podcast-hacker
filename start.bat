@echo off
rem Build and launch the PodcastHacker desktop app on Windows.
setlocal
cd /d "%~dp0"

call gradlew.bat :desktopApp:createDistributable
if errorlevel 1 exit /b %errorlevel%

start "" "desktopApp\build\compose\binaries\main\app\PodcastHacker\PodcastHacker.exe"
