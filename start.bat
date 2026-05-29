@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0"

title AI Architect Launcher

echo ====================================
echo   AI ARCHITECT — STARTUP
echo ====================================
echo.

REM --- Admin check ---
>nul 2>&1 net session
if %errorlevel% neq 0 (
    echo [REQUEST] Administrator privileges needed. Restarting...
    powershell -Command "Start-Process cmd -ArgumentList '/c \"%~f0\" %*' -Verb RunAs"
    exit /b
)

REM ── Check Prerequisites ──────────────────────────────────────
echo [1/5] Checking prerequisites...

where java >nul 2>&1
if errorlevel 1 ( echo ERROR: Java not found & pause & exit /b 1 )

where mvn >nul 2>&1
if errorlevel 1 ( echo ERROR: Maven not found & pause & exit /b 1 )

where python >nul 2>&1
if errorlevel 1 ( echo Python not found — worker will be skipped & set PYTHON_OK=0 ) else ( set PYTHON_OK=1 )

where node >nul 2>&1
if errorlevel 1 ( echo Node.js not found — frontend will be skipped & set NODE_OK=0 ) else ( set NODE_OK=1 )

echo   OK

REM ── Check .env ───────────────────────────────────────────────
echo [2/5] Checking config...

if not exist "ai-architect-backend\.env" (
    if exist "ai-architect-backend\.env.example" (
        copy "ai-architect-backend\.env.example" "ai-architect-backend\.env" >nul
        echo   Created ai-architect-backend\.env from template
        echo   IMPORTANT: Add your API keys to ai-architect-backend\.env
    )
)

echo   OK

REM ── Start Databases ──────────────────────────────────────────
echo [3/5] Starting databases...

REM Start PostgreSQL service
powershell -Command "Get-Service postgresql* -ErrorAction SilentlyContinue | Where-Object { $_.Status -ne 'Running' } | Start-Service -ErrorAction SilentlyContinue"

REM Start Memurai (Windows Redis)
powershell -Command "Get-Service Memurai -ErrorAction SilentlyContinue | Where-Object { $_.Status -ne 'Running' } | Start-Service -ErrorAction SilentlyContinue"

echo   Databases ready
timeout /t 3 /nobreak >nul

REM ── Configure Database ───────────────────────────────────────
echo [3b/5] Setting up database...

set PSQL_PATH=
for /f "delims=" %%f in ('dir /s /b "%ProgramFiles%\PostgreSQL\*\bin\psql.exe" 2^>nul') do if not defined PSQL_PATH set "PSQL_PATH=%%f"

set PGPASSWORD=ai_architect
if defined PSQL_PATH (
    >nul 2>&1 "%PSQL_PATH%" -U postgres -c "SELECT 1"
    if !errorlevel! equ 0 (
        >nul 2>&1 "%PSQL_PATH%" -U postgres -c "CREATE DATABASE ai_architect"
        >nul 2>&1 "%PSQL_PATH%" -U postgres -c "CREATE USER ai_architect WITH PASSWORD 'ai_architect'"
        >nul 2>&1 "%PSQL_PATH%" -U postgres -c "GRANT ALL PRIVILEGES ON DATABASE ai_architect TO ai_architect"
        >nul 2>&1 "%PSQL_PATH%" -U postgres -d ai_architect -c "GRANT ALL ON SCHEMA public TO ai_architect"
        echo   Database configured
    ) else (
        echo   [WARN] PostgreSQL unreachable — check pg_hba.conf
    )
) else (
    echo   [WARN] psql.exe not found — create database manually
)
set PGPASSWORD=

REM ── Start Backend ────────────────────────────────────────────
echo [4/5] Starting Java backend...

powershell -NoProfile -Command "Get-Content '%~dp0ai-architect-backend\.env' | Where-Object { $_ -match '^[^#=]+=.+' } | ForEach-Object { $k,$v = $_ -split '=',2; [System.Environment]::SetEnvironmentVariable($k.Trim(), $v.Trim()) }" >nul 2>&1
start "AI-Architect-Backend" cmd /k "title AI Architect Backend && cd /d "%~dp0ai-architect-backend" && echo Starting Spring Boot... && mvn spring-boot:run"

REM ── Start Python Worker ──────────────────────────────────────
if %PYTHON_OK%==1 (
    echo [4b/5] Starting Python worker...
    if exist "ai-architect-python\main.py" (
        pushd ai-architect-python
        if not exist ".venv" (
            echo   Creating venv...
            python -m venv .venv >nul
            call .venv\Scripts\activate.bat
            pip install -r requirements.txt -q >nul
        )
        call .venv\Scripts\activate.bat
        python -m playwright install chromium >nul 2>&1
        popd
        start "AI-Architect-Python" cmd /k "title Python Worker && cd /d "%~dp0ai-architect-python" && call .venv\Scripts\activate.bat && echo Starting Python worker... && python main.py"
    ) else (
        echo   Python worker not found — skipping
    )
)

REM ── Start Frontend ───────────────────────────────────────────
if %NODE_OK%==1 (
    echo [5/5] Starting frontend...
    if exist "ai-architect\package.json" (
        cd ai-architect
        if not exist "node_modules" (
            echo   Installing dependencies...
            call npm install --silent
        )
        start "AI-Architect-Frontend" cmd /k "title AI Architect Frontend && cd /d "%~dp0ai-architect" && echo Starting React dev server... && npm run dev"
        cd ..
    ) else (
        echo   Frontend not found — skipping
    )
)

echo.
echo ====================================
echo   AI ARCHITECT IS STARTING
echo ====================================
echo.
echo   Backend API:   http://localhost:8080/api
if %PYTHON_OK%==1 if exist "ai-architect-python\main.py" echo   Python Worker: http://localhost:8081
if %NODE_OK%==1 if exist "ai-architect\package.json" echo   Frontend:      http://localhost:5173
echo.
echo   Press any key to stop all services...
pause >nul

REM ── Cleanup ──────────────────────────────────────────────────
echo Stopping services...
taskkill /f /fi "WINDOWTITLE eq AI Architect Backend*" >nul 2>&1
taskkill /f /fi "WINDOWTITLE eq Python Worker*" >nul 2>&1
taskkill /f /fi "WINDOWTITLE eq AI Architect Frontend*" >nul 2>&1
echo Done.
