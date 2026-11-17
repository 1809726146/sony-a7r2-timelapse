@echo off
rem ============================================================
rem  A7R2App build script  (JDK 8 + Android build-tools 25.0.2)
rem  aapt -> javac -> dx -> package -> zipalign -> apksigner
rem  Keep this file ASCII-only: cmd.exe reads .bat as ANSI.
rem ============================================================
setlocal enabledelayedexpansion

set "APP=%~dp0"
set "DEV=%APP%.."
rem CI can pass JAVA_HOME / ANDROID_HOME / ANDROID_PLATFORM_DIR instead of the local toolchain
if not defined JAVA_HOME set "JAVA_HOME=%DEV%\toolchain\jdk8"
if not defined ANDROID_HOME set "ANDROID_HOME=%DEV%\toolchain\sdk"
if not defined ANDROID_PLATFORM_DIR set "ANDROID_PLATFORM_DIR=%ANDROID_HOME%\platforms\android-10"
set "SDK=%ANDROID_HOME%"
set "BT=%SDK%\build-tools\25.0.2"
set "PLATFORM=%ANDROID_PLATFORM_DIR%\android.jar"
set "PATH=%JAVA_HOME%\bin;%BT%;%SDK%\platform-tools;%PATH%"

if not exist "%JAVA_HOME%\bin\javac.exe" (
    echo [x] JDK 8 not found: %JAVA_HOME%
    goto :fail
)
if not exist "%PLATFORM%" (
    echo [x] android-10 platform not found: %PLATFORM%
    goto :fail
)
if not exist "%BT%\aapt.exe" (
    echo [x] build-tools 25.0.2 not found: %BT%
    goto :fail
)
if not exist "%APP%libs\sony-stubs.jar" (
    echo [x] libs\sony-stubs.jar not found
    goto :fail
)

set "OUT=%APP%build"
set "GEN=%OUT%\gen"
set "OBJ=%OUT%\obj"
set "DEX=%OUT%\classes.dex"
set "APK_RAW=%OUT%\app-raw.apk"
set "APK_ALIGNED=%OUT%\app-aligned.apk"
set "APK_SIGNED=%OUT%\A7R2App-debug.apk"
set "KS=%APP%debug.keystore"
set "KSPASS=android"

if exist "%OUT%" rmdir /s /q "%OUT%"
mkdir "%GEN%" 2>nul
mkdir "%OBJ%" 2>nul

echo [1/7] aapt: generate R.java
"%BT%\aapt.exe" package -f -m -J "%GEN%" -M "%APP%AndroidManifest.xml" -S "%APP%res" -I "%PLATFORM%"
if errorlevel 1 goto :fail

echo [2/7] javac
set "SRC="
for /r "%APP%src" %%f in (*.java) do set "SRC=!SRC! "%%f""
for /r "%GEN%" %%f in (*.java) do set "SRC=!SRC! "%%f""
javac -source 1.6 -target 1.6 -encoding UTF-8 -nowarn -bootclasspath "%PLATFORM%" -classpath "%APP%libs\sony-stubs.jar" -d "%OBJ%" !SRC!
if errorlevel 1 goto :fail

echo [3/7] dx: classes to classes.dex
"%JAVA_HOME%\bin\java.exe" -jar "%BT%\lib\dx.jar" --dex --output="%DEX%" "%OBJ%"
if errorlevel 1 goto :fail

echo [4/7] aapt: package resources
"%BT%\aapt.exe" package -f -M "%APP%AndroidManifest.xml" -S "%APP%res" -I "%PLATFORM%" -F "%APK_RAW%"
if errorlevel 1 goto :fail

echo [5/7] aapt: add classes.dex
pushd "%OUT%"
"%BT%\aapt.exe" add "%APK_RAW%" classes.dex
if errorlevel 1 (
    popd
    goto :fail
)
popd

echo [6/7] zipalign
"%BT%\zipalign.exe" -f 4 "%APK_RAW%" "%APK_ALIGNED%"
if errorlevel 1 goto :fail

echo [7/7] sign with debug keystore, v1 only
if not exist "%KS%" (
    keytool -genkeypair -keystore "%KS%" -storepass %KSPASS% -keypass %KSPASS% -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
    if errorlevel 1 goto :fail
)
"%JAVA_HOME%\bin\java.exe" -jar "%BT%\lib\apksigner.jar" sign --ks "%KS%" --ks-pass pass:%KSPASS% --key-pass pass:%KSPASS% --v1-signing-enabled true --v2-signing-enabled false --out "%APK_SIGNED%" "%APK_ALIGNED%"
if errorlevel 1 goto :fail

echo.
echo [OK] %APK_SIGNED%
"%BT%\aapt.exe" dump badging "%APK_SIGNED%" | findstr /b "package: sdkVersion: targetSdkVersion: launchable-activity:"

rem ---------- [8/8] optional verify ----------
set "PYEXE=%APP%..\toolchain\python\python.exe"
set "VERIFY=%APP%..\tools\verify_v18.py"
if exist "%PYEXE%" if exist "%VERIFY%" (
    echo.
    echo [8/8] verify
    set "PYTHONPATH=%APP%..\tools\pylib"
    "%PYEXE%" "%VERIFY%"
    if errorlevel 1 echo [WARN] verify found problems ^(APK still built^)
)

if not defined NOPAUSE pause
exit /b 0

:fail
echo.
echo [FAIL] build aborted
if not defined NOPAUSE pause
exit /b 1
