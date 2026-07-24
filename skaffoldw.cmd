@echo off
setlocal

set SKAFFOLD_VERSION=v2.12.0
set SKAFFOLD_URL=https://github.com/GoogleContainerTools/skaffold/releases/download/%SKAFFOLD_VERSION%/skaffold-windows-amd64.exe
set SKAFFOLD_DIR=%~dp0.skaffold
set SKAFFOLD_EXE=%SKAFFOLD_DIR%\skaffold.exe

if not exist "%SKAFFOLD_DIR%" (
    mkdir "%SKAFFOLD_DIR%"
)

if not exist "%SKAFFOLD_EXE%" (
    echo Downloading Skaffold %SKAFFOLD_VERSION%...
    powershell -Command "Invoke-WebRequest -Uri '%SKAFFOLD_URL%' -OutFile '%SKAFFOLD_EXE%'"
    echo Download complete!
)

if "%~1"=="install" (
    goto :eof
)

"%SKAFFOLD_EXE%" %*
endlocal
