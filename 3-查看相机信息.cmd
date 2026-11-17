@echo off
rem STEP 3 : check what the PC sees on the USB port
setlocal
rem -d native = use the built-in Windows MSC/MTP drivers only.
rem Without it PMCA-RE also probes the same camera through libusb,
rem which fails on an MTP device owned by the Windows driver.
call "%~dp0pmca.cmd" info -d native
echo.
echo ==============================================
echo Expect: Model / serial / firmware lines above.
echo "No devices found" = cable, USB port or camera power.
echo Press any key to close.
pause >nul
