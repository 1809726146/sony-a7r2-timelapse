@echo off
rem STEP 4 : read the app log (SD card / local copies)
rem 在相机上启动过一次 App 之后才会生成 /sdcard/A7R2APP/LOG.TXT
setlocal
set "APP=%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%APP%tools\read-log.ps1"
if errorlevel 1 (
    echo.
    echo [i] 读取失败，原因见上面的提示。
)
if not defined NOPAUSE pause
