@echo off
setlocal EnableExtensions
cd /d "%~dp0"

if not exist ".env" (
    echo File .env not found.
    echo Copy .env.example to .env and fill in EMAIL, TOKEN, REPOSITORY_URL and CSV_PATH_TO_EXPORT.
    exit /b 1
)

for /f "usebackq eol=# tokens=1,* delims==" %%A in (".env") do (
    if not "%%A"=="" set "%%A=%%B"
)

if "%EMAIL%"=="" (
    echo EMAIL is not set in .env
    exit /b 1
)
if "%TOKEN%"=="" (
    echo TOKEN is not set in .env
    exit /b 1
)
if "%REPOSITORY_URL%"=="" (
    echo REPOSITORY_URL is not set in .env
    exit /b 1
)
if "%CSV_PATH_TO_EXPORT%"=="" (
    echo CSV_PATH_TO_EXPORT is not set in .env
    exit /b 1
)

where mvn >nul 2>&1
if errorlevel 1 (
    echo Maven ^(mvn^) is not in PATH. Install Maven and try again.
    exit /b 1
)

echo Starting bb-comments-statistics...
call mvn compile exec:java
exit /b %ERRORLEVEL%
