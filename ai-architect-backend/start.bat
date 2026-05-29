@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0"

title AI Architect Backend Launcher

echo ============================================
echo   AI Architect Backend - Launcher
echo ============================================
echo.

REM --- Admin check ---
>nul 2>&1 net session
if %errorlevel% neq 0 (
    echo [REQUEST] Administrator privileges needed. Restarting...
    powershell -Command "Start-Process cmd -ArgumentList '/c \"%~f0\" %*' -Verb RunAs"
    exit /b
)

echo [1/4] Checking Chocolatey...
where choco >nul 2>&1
if %errorlevel% neq 0 (
    echo   Installing Chocolatey...
    @"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -ExecutionPolicy Bypass -Command "Set-ExecutionPolicy Bypass -Scope Process -Force; [System.Net.ServicePointManager]::SecurityProtocol = 3072; iex ((New-Object System.Net.WebClient).DownloadString('https://chocolatey.org/install.ps1'))" >nul 2>&1
) else (
    echo   OK
)

echo [2/4] Checking PostgreSQL...
set PSQL_PATH=
where psql >nul 2>&1
if %errorlevel% neq 0 (
    echo   Installing PostgreSQL...
    choco install postgresql16 --params "/Password:ai_architect" -y --force
    for /f "delims=" %%f in ('dir /s /b "%ProgramFiles%\PostgreSQL\*\bin\psql.exe" 2^>nul') do set "PSQL_PATH=%%f"
    if not defined PSQL_PATH (
        for /f "delims=" %%f in ('dir /s /b "%ProgramFiles(x86)%\PostgreSQL\*\bin\psql.exe" 2^>nul') do set "PSQL_PATH=%%f"
    )
) else (
    echo   OK
    for /f "delims=" %%f in ('where psql') do set "PSQL_PATH=%%f"
)

echo [3/4] Checking Redis (Memurai)...
where redis-cli >nul 2>&1
if %errorlevel% neq 0 (
    echo   Installing Redis via Memurai...
    choco install memurai-developer -y --force
) else (
    echo   OK
)

echo [4/4] Starting services...
REM Start PostgreSQL service
powershell -Command "Get-Service postgresql* -ErrorAction SilentlyContinue | Where-Object { $_.Status -ne 'Running' } | Start-Service -ErrorAction SilentlyContinue"
REM Start Memurai/Redis service (Memurai is the modern Windows Redis)
powershell -Command "$svc = Get-Service Memurai -ErrorAction SilentlyContinue; if (!$svc) { $svc = Get-Service Redis -ErrorAction SilentlyContinue }; if ($svc -and $svc.Status -ne 'Running') { Start-Service $svc.Name -ErrorAction SilentlyContinue }"
echo   OK

REM Wait for services to init
timeout /t 5 /nobreak >nul

REM Configure database
echo [INFO] Setting up database...
set PGPASSWORD=ai_architect
if defined PSQL_PATH (
    "%PSQL_PATH%" -U postgres -c "SELECT 1" >nul 2>&1
    if !errorlevel! equ 0 (
        "%PSQL_PATH%" -U postgres -c "CREATE DATABASE ai_architect" >nul 2>&1
        "%PSQL_PATH%" -U postgres -c "CREATE USER ai_architect WITH PASSWORD 'ai_architect'" >nul 2>&1
        "%PSQL_PATH%" -U postgres -c "GRANT ALL PRIVILEGES ON DATABASE ai_architect TO ai_architect" >nul 2>&1
        "%PSQL_PATH%" -U postgres -d ai_architect -c "GRANT ALL ON SCHEMA public TO ai_architect" >nul 2>&1
        echo   Database configured.
    ) else (
        echo   [WARN] Cannot connect to PostgreSQL.
        echo   Try manually: "%PSQL_PATH%" -U postgres
    )
) else (
    echo   [WARN] psql not found - configure database manually
)

echo.
echo ============================================
echo  Launching application...
echo ============================================
echo.

powershell -NoProfile -Command "Get-Content '.env' | Where-Object { $_ -match '^[^#=]+=.+' } | ForEach-Object { $k,$v = $_ -split '=',2; [System.Environment]::SetEnvironmentVariable($k.Trim(), $v.Trim()) }" >nul 2>&1
start "AI-Architect-Backend" cmd /k "title AI Architect Backend && cd /d "%~dp0" && echo Starting Spring Boot... && mvn spring-boot:run"
echo App starting at http://localhost:8080/api
echo.
pause
