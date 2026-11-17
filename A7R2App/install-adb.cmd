@echo off
rem Install the built apk over adb (camera must have OpenMemories Tweak with ADB enabled)
rem usage: install-adb.cmd 192.168.1.50
setlocal
if "%~1"=="" (
    echo usage: install-adb.cmd CAMERA_IP
    if not defined NOPAUSE pause
    exit /b 1
)
set "APP=%~dp0"
set "SDK=%APP%..\toolchain\sdk"
set "ADB=%SDK%\platform-tools\adb.exe"
set "APK=%APP%build\A7R2App-debug.apk"

if not exist "%APK%" (
    echo [x] %APK% not found, run build.cmd first
    if not defined NOPAUSE pause
    exit /b 1
)

echo [1/2] adb connect %1:5555
"%ADB%" connect %1:5555
echo [2/2] adb install -r
"%ADB%" install -r "%APK%"
echo.
echo done. check the camera's application list.
if not defined NOPAUSE pause
