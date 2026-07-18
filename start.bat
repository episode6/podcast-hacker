@echo off
rem Build and launch the PodcastHacker desktop app on Windows.
setlocal
cd /d "%~dp0"

call gradlew.bat :desktopApp:createDistributable
if errorlevel 1 exit /b %errorlevel%

rem packageName is "PodcastHacker-SNAPSHOT" for snapshot builds, "PodcastHacker" for releases
set "APP_DIR=desktopApp\build\compose\binaries\main\app"
for /d %%D in ("%APP_DIR%\PodcastHacker*") do (
    start "" "%%D\%%~nxD.exe"
    exit /b 0
)
echo No app found in %APP_DIR% >&2
exit /b 1
