@echo off
echo Building Jira Crawler...
call mvn clean compile

if %ERRORLEVEL% NEQ 0 (
    echo Build failed!
    pause
    exit /b 1
)

echo.
echo Starting Jira Crawler...
echo.

if "%1"=="" (
    call mvn exec:java
) else (
    call mvn exec:java -Dexec.args="%*"
)

pause