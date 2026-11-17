@echo off
rem PMCA-RE launcher for the portable Python in this folder.
rem Must run from the repo root: pmca imports config.py and reads certs/ + updatershell/fdat/.
setlocal
set "DEV=%~dp0"
set "PY=%DEV%toolchain\python\python.exe"
set "REPO=%DEV%pmca-re"

if not exist "%PY%" (
    echo [x] portable python not found: %PY%
    exit /b 1
)
if not exist "%REPO%\pmca-console.py" (
    echo [x] pmca-re not found: %REPO%
    exit /b 1
)

set "PYTHONDONTWRITEBYTECODE=1"
set "PYTHONWARNINGS=ignore"
rem pyusb locates libusb-1.0.dll through PATH
set "PATH=%DEV%toolchain\python;%PATH%"
cd /d "%REPO%"
"%PY%" pmca-console.py %*
