@echo off
rem Install the built apk through PMCA-RE and report a clear result.
setlocal enabledelayedexpansion
set "APP=%~dp0"
set "PMCA=%APP%..\pmca.cmd"
set "APK=%APP%build\A7R2App-debug.apk"
set "LOGDIR=%APP%build"
set "LOG=%LOGDIR%\install.log"

if not exist "%APK%" (
    echo [x] APK not found: %APK%
    echo     Run 1-build first.
    if not defined NOPAUSE pause
    exit /b 1
)
if not exist "%PMCA%" (
    echo [x] pmca.cmd not found: %PMCA%
    if not defined NOPAUSE pause
    exit /b 1
)
if not exist "%LOGDIR%" mkdir "%LOGDIR%"

echo ==============================================
echo  Connecting to the camera over USB.
echo  Keep the camera powered on and do not unplug.
echo ==============================================
echo.

call "%PMCA%" install -d native -f "%APK%" > "%LOG%" 2>&1
type "%LOG%"

set "RESULT=UNKNOWN"
findstr /c:"Task completed successfully" "%LOG%" >nul 2>&1
if not errorlevel 1 set "RESULT=OK"
if "!RESULT!"=="UNKNOWN" (
    findstr /c:"No devices found" "%LOG%" >nul 2>&1
    if not errorlevel 1 set "RESULT=NOCAM"
)
if "!RESULT!"=="UNKNOWN" (
    findstr /c:"Operation timed out" "%LOG%" >nul 2>&1
    if not errorlevel 1 set "RESULT=TIMEOUT"
)
if "!RESULT!"=="UNKNOWN" (
    findstr /c:"Communication error" "%LOG%" >nul 2>&1
    if not errorlevel 1 set "RESULT=COMMERR"
)
if "!RESULT!"=="UNKNOWN" (
    findstr /c:"does not support apps" "%LOG%" >nul 2>&1
    if not errorlevel 1 set "RESULT=NOAPPS"
)
if "!RESULT!"=="UNKNOWN" (
    findstr /c:"pywintypes.error" "%LOG%" >nul 2>&1
    if not errorlevel 1 set "RESULT=ADMIN"
)
if "!RESULT!"=="UNKNOWN" (
    findstr /c:"Operation not supported or unimplemented" "%LOG%" >nul 2>&1
    if not errorlevel 1 set "RESULT=ADMIN"
)
if "!RESULT!"=="UNKNOWN" (
    findstr /c:"GenericUsbException" "%LOG%" >nul 2>&1
    if not errorlevel 1 set "RESULT=DRIVER"
)

echo.
echo ==============================================
if "!RESULT!"=="OK" (
    echo  RESULT: INSTALL OK
    echo.
    echo  On the camera: MENU -^> Application -^> Application List
    echo  You should see "A7R2" there. If not, power the camera
    echo  off and on once to refresh the list.
) else if "!RESULT!"=="NOCAM" (
    echo  RESULT: NO CAMERA FOUND
    echo.
    echo  - use a USB *data* cable, not a charge-only one
    echo  - try another USB port on the PC
    echo  - the camera must be powered on, not sleeping
    echo  - leave "PC Remote" / airplane mode, use normal shooting mode
) else if "!RESULT!"=="TIMEOUT" (
    echo  RESULT: TIMED OUT while switching modes
    echo.
    echo  Unplug, power-cycle the camera, plug it back in and
    echo  run this script again.
) else if "!RESULT!"=="COMMERR" (
    echo  RESULT: COMMUNICATION ERROR
    echo.
    echo  The camera rejected a step. See the log above.
) else if "!RESULT!"=="NOAPPS" (
    echo  RESULT: THIS CAMERA DOES NOT SUPPORT APPS
    echo.
    echo  Check the compatibility list before going further.
) else if "!RESULT!"=="ADMIN" (
    echo  RESULT: NEED ADMINISTRATOR RIGHTS
    echo.
    echo  Windows blocks raw USB mass-storage commands for normal
    echo  users. Close this window, then right-click the
    echo  "2-....cmd" file you double-clicked and choose
    echo  "Run as administrator".
) else if "!RESULT!"=="DRIVER" (
    echo  RESULT: CAMERA IS BUSY INSIDE WINDOWS
    echo.
    echo  Another program is holding the camera, or a libusb
    echo  driver replaced the normal Windows driver.
    echo  Close Imaging Edge / Photos / Dropbox, unplug and
    echo  replug the camera, then run this script again.
) else (
    echo  RESULT: UNKNOWN - read the log above.
)
echo ==============================================
echo  Full log: %LOG%
if not defined NOPAUSE pause
