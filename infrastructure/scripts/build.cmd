@echo off
REM Local build helper: runs the Gradle wrapper and keeps the full log readable.
REM Usage: infrastructure\scripts\build.cmd <gradle args...>
setlocal
set LOG_DIR=%TEMP%\companero-build-logs
if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"
set STAMP=%RANDOM%
set LOG=%LOG_DIR%\gradle-%STAMP%.log
call "%~dp0..\..\gradlew.bat" %* --console=plain > "%LOG%" 2>&1
set EXIT_CODE=%ERRORLEVEL%
type "%LOG"
echo.
echo "== exit code: %EXIT_CODE% =="
echo "== log: %LOG% =="
exit /b %EXIT_CODE%
