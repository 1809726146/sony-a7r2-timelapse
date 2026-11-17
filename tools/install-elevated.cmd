@echo off
rem Elevated helper: install the built APK onto the camera.
rem Launched by the agent with Start-Process -Verb RunAs (UAC prompt).
setlocal
cd /d "%~dp0..\A7R2App"
set "NOPAUSE=1"
call "%~dp0..\A7R2App\install-pmca.cmd"
exit /b %errorlevel%
