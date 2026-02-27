@echo off
:: setup-dev.bat — Obtain SweetHome3D.jar required for building the plugin.
::
:: Tries in order:
::   1. Already exists in lib\ -> nothing to do
::   2. Copies from an installed Sweet Home 3D (fastest, works offline)
::   3. Downloads from SourceForge using curl (built-in on Windows 10+)
::
:: Usage:
::   scripts\setup-dev.bat

setlocal

set DEST=lib\SweetHome3D.jar
set SH3D_VERSION=7.5
set SOURCEFORGE_URL=https://unlimited.dl.sourceforge.net/project/sweethome3d/SweetHome3D/SweetHome3D-%SH3D_VERSION%/SweetHome3D-%SH3D_VERSION%.jar

:: 1. Already present
if exist "%DEST%" (
    echo [OK] %DEST% already exists -- nothing to do.
    exit /b 0
)

if not exist lib mkdir lib

:: 2. Try known installation paths
for %%P in (
    "%ProgramFiles%\Sweet Home 3D\SweetHome3D.jar"
    "%ProgramFiles(x86)%\Sweet Home 3D\SweetHome3D.jar"
) do (
    if exist %%P (
        echo Found Sweet Home 3D at: %%P
        copy %%P "%DEST%" >nul
        echo [OK] Copied to %DEST%
        exit /b 0
    )
)

:: 3. Download from SourceForge (curl is built-in on Windows 10+)
echo Sweet Home 3D installation not found. Downloading SweetHome3D-%SH3D_VERSION%.jar from SourceForge...
echo URL: %SOURCEFORGE_URL%
curl -L --progress-bar "%SOURCEFORGE_URL%" -o "%DEST%"
if errorlevel 1 (
    echo [ERROR] Download failed. Please download SweetHome3D.jar manually:
    echo   %SOURCEFORGE_URL%
    echo and place it at: %DEST%
    exit /b 1
)
echo [OK] Downloaded to %DEST%
endlocal
