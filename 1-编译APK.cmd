@echo off
rem STEP 1 : build the APK on this PC
setlocal
set "NOPAUSE=1"
call "%~dp0A7R2App\build.cmd"
echo.
echo ==============================================
echo STEP 1 finished. Press any key to close.
echo The APK is in A7R2App\build\A7R2App-debug.apk
pause >nul
