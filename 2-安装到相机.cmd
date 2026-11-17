@echo off
chcp 936 >nul
rem ============================================================
rem  STEP 2 : install the APK onto the camera over USB
rem  需要管理员权限（Windows 不允许普通用户直接访问 USB 存储端点）
rem ============================================================
setlocal
set "HERE=%~dp0"
set "PS1=%HERE%tools\install-v40.ps1"
set "APK=%HERE%A7R2App\build\A7R2App-debug.apk"

if not exist "%APK%" (
    echo [x] APK not built: %APK%
    echo     Run 1-build first.
    pause
    exit /b 1
)

net session >nul 2>&1
if errorlevel 1 (
    echo ==============================================
    echo  Administrator rights are required.
    echo  A UAC prompt will appear - click "Yes".
    echo ==============================================
    powershell -NoProfile -Command "Start-Process -Verb RunAs -FilePath 'powershell.exe' -ArgumentList '-NoProfile','-ExecutionPolicy','Bypass','-NoExit','-File','%PS1%'"
    exit /b 0
)

powershell -NoProfile -ExecutionPolicy Bypass -File "%PS1%"
echo.
pause
