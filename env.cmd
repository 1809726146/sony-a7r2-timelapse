@echo off
rem 打开一个已配置好 JDK 8 / Android SDK 的 cmd 窗口
setlocal
set "DEV=%~dp0"
set "JAVA_HOME=%DEV%toolchain\jdk8"
set "ANDROID_HOME=%DEV%toolchain\sdk"
set "ANDROID_SDK_ROOT=%ANDROID_HOME%"
set "PATH=%JAVA_HOME%\bin;%ANDROID_HOME%\platform-tools;%ANDROID_HOME%\build-tools\25.0.2;%PATH%"
echo JAVA_HOME   = %JAVA_HOME%
echo ANDROID_HOME= %ANDROID_HOME%
echo.
java -version
echo.
cmd /k
