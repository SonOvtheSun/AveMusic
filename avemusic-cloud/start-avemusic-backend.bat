@echo off
setlocal EnableExtensions EnableDelayedExpansion
chcp 65001 >nul
title AveMusic Backend Launcher

rem ============================================================
rem Place this file in the Maven parent project root:
rem   avemusic-cloud\start-avemusic-backend.bat
rem
rem Double-click:
rem   Build the Maven project, then start all services.
rem
rem Fast mode:
rem   start-avemusic-backend.bat fast
rem ============================================================

cd /d "%~dp0"
set "ROOT=%CD%"

set "FILE_MODULE=avemusic-file-service"
set "USER_MODULE=avemusic-user-provider"
set "MUSIC_MODULE=avemusic-music-provider"
set "GATEWAY_MODULE=avemusic-gateway"

set "FILE_PORT=8090"
set "USER_PORT=20881"
set "MUSIC_PORT=20882"
set "GATEWAY_PORT=8080"

set "WAIT_SECONDS=120"
set "SKIP_BUILD=0"

if /I "%~1"=="fast" set "SKIP_BUILD=1"

echo.
echo ============================================================
echo                AveMusic Backend Launcher
echo ============================================================
echo Project root: %ROOT%
echo.

where java >nul 2>&1
if errorlevel 1 (
    echo [ERROR] java was not found.
    echo Install JDK 17 and configure JAVA_HOME and Path.
    goto :failed
)

if exist "%ROOT%\mvnw.cmd" (
    set "USE_MAVEN_WRAPPER=1"
    set "MAVEN_DISPLAY=%ROOT%\mvnw.cmd"
) else (
    where mvn >nul 2>&1
    if errorlevel 1 (
        echo [ERROR] Neither mvnw.cmd nor mvn was found.
        goto :failed
    )
    set "USE_MAVEN_WRAPPER=0"
    set "MAVEN_DISPLAY=mvn"
)

echo [CHECK] Maven: %MAVEN_DISPLAY%

call :check_module "%FILE_MODULE%"
if errorlevel 1 goto :failed

call :check_module "%USER_MODULE%"
if errorlevel 1 goto :failed

call :check_module "%MUSIC_MODULE%"
if errorlevel 1 goto :failed

call :check_module "%GATEWAY_MODULE%"
if errorlevel 1 goto :failed

if not defined AVEMUSIC_FILE_INTERNAL_TOKEN (
    set "AVEMUSIC_FILE_INTERNAL_TOKEN=avemusic-local-dev-internal-token-change-before-production"
    echo [INFO] AVEMUSIC_FILE_INTERNAL_TOKEN is not set.
    echo [INFO] A local development token will be used for this run.
) else (
    echo [CHECK] AVEMUSIC_FILE_INTERNAL_TOKEN is configured.
)

set "JAVA_TOOL_OPTIONS=%JAVA_TOOL_OPTIONS% -Dfile.encoding=UTF-8"

if "%SKIP_BUILD%"=="0" (
    echo.
    echo [BUILD] Running Maven install...
    echo [BUILD] Use the fast argument next time to skip this step.
    echo.

    if "%USE_MAVEN_WRAPPER%"=="1" (
        call "%ROOT%\mvnw.cmd" -DskipTests install
    ) else (
        call mvn -DskipTests install
    )

    if errorlevel 1 (
        echo.
        echo [ERROR] Maven build failed. No service was started.
        goto :failed
    )

    echo.
    echo [OK] Maven build completed.
) else (
    echo [FAST] Maven install was skipped.
)

echo.
echo [START] Starting file service and providers...

call :start_if_needed "AveMusic File Service" "%FILE_MODULE%" %FILE_PORT%
call :start_if_needed "AveMusic User Provider" "%USER_MODULE%" %USER_PORT%
call :start_if_needed "AveMusic Music Provider" "%MUSIC_MODULE%" %MUSIC_PORT%

echo.
echo [WAIT] Waiting for backend services...

call :wait_port %FILE_PORT% "%FILE_MODULE%"
if errorlevel 1 goto :provider_failed

call :wait_port %USER_PORT% "%USER_MODULE%"
if errorlevel 1 goto :provider_failed

call :wait_port %MUSIC_PORT% "%MUSIC_MODULE%"
if errorlevel 1 goto :provider_failed

echo.
echo [OK] File service and providers are ready.
echo [START] Starting Gateway...

call :start_if_needed "AveMusic Gateway" "%GATEWAY_MODULE%" %GATEWAY_PORT%

call :wait_port %GATEWAY_PORT% "%GATEWAY_MODULE%"
if errorlevel 1 (
    echo.
    echo [ERROR] Gateway did not listen on port %GATEWAY_PORT% within %WAIT_SECONDS% seconds.
    echo Check the AveMusic Gateway window.
    goto :failed
)

echo.
echo ============================================================
echo                AveMusic Backend Started
echo ============================================================
echo File Service:   http://127.0.0.1:%FILE_PORT%
echo User Provider:  127.0.0.1:%USER_PORT%
echo Music Provider: 127.0.0.1:%MUSIC_PORT%
echo Gateway:        http://127.0.0.1:%GATEWAY_PORT%
echo.
echo Each service runs in a separate window.
echo Press Ctrl+C in a service window to stop that service.
echo ============================================================
echo.

pause
exit /b 0


:check_module
set "MODULE_NAME=%~1"

if not exist "%ROOT%\%MODULE_NAME%\pom.xml" (
    echo [ERROR] Module pom.xml not found:
    echo         %ROOT%\%MODULE_NAME%\pom.xml
    echo Put this script in the Maven parent project root,
    echo or edit the module names at the top of this file.
    exit /b 1
)

echo [CHECK] Found %MODULE_NAME%
exit /b 0


:start_if_needed
set "SERVICE_TITLE=%~1"
set "SERVICE_MODULE=%~2"
set "SERVICE_PORT=%~3"

call :is_port_open %SERVICE_PORT%

if not errorlevel 1 (
    echo [SKIP] Port %SERVICE_PORT% is already in use. %SERVICE_MODULE% is treated as running.
    exit /b 0
)

echo [START] %SERVICE_MODULE%

if "%USE_MAVEN_WRAPPER%"=="1" (
    start "%SERVICE_TITLE%" /D "%ROOT%\%SERVICE_MODULE%" cmd.exe /k ""%ROOT%\mvnw.cmd" -DskipTests spring-boot:run"
) else (
    start "%SERVICE_TITLE%" /D "%ROOT%\%SERVICE_MODULE%" cmd.exe /k "mvn -DskipTests spring-boot:run"
)

exit /b 0


:wait_port
set "TARGET_PORT=%~1"
set "TARGET_NAME=%~2"
set /a "WAITED_SECONDS=0"

<nul set /p "=[WAIT] %TARGET_NAME% : %TARGET_PORT% "

:wait_port_loop
call :is_port_open %TARGET_PORT%

if not errorlevel 1 (
    echo  READY
    exit /b 0
)

if !WAITED_SECONDS! GEQ %WAIT_SECONDS% (
    echo  TIMEOUT
    exit /b 1
)

<nul set /p "=."
timeout /t 2 /nobreak >nul
set /a "WAITED_SECONDS+=2"
goto :wait_port_loop


:is_port_open
powershell.exe -NoLogo -NoProfile -NonInteractive -Command ^
    "$client = New-Object System.Net.Sockets.TcpClient;" ^
    "try {" ^
    "  $task = $client.ConnectAsync('127.0.0.1', %~1);" ^
    "  if (-not $task.Wait(800)) { exit 1 };" ^
    "  if ($client.Connected) { exit 0 } else { exit 1 }" ^
    "} catch {" ^
    "  exit 1" ^
    "} finally {" ^
    "  $client.Dispose()" ^
    "}" >nul 2>&1

exit /b %errorlevel%


:provider_failed
echo.
echo [ERROR] At least one backend service failed to start.
echo Gateway will not be started.
echo Check the corresponding service window.
goto :failed


:failed
echo.
echo ============================================================
echo                       Startup Failed
echo ============================================================
pause
exit /b 1
