@echo off
:: ============================================================
:: AI ARCHITECT — FULL BUILD SCRIPT
:: Builds: Java JAR → Python venv → React → Tauri .exe
:: ============================================================

title AI Architect Build
color 0B

echo.
echo  ====================================
echo   AI ARCHITECT — BUILD
echo  ====================================
echo.

set BUILD_DIR=%~dp0build
set FRONTEND_DIR=%~dp0ai-architect
set BACKEND_DIR=%~dp0ai-architect-backend
set PYTHON_DIR=%~dp0ai-architect-python

:: ── Check prerequisites ──────────────────────────────────────
echo [CHECK] Verifying build tools...

where java    >nul 2>&1 || (echo ERROR: Java 21+ required & pause & exit /b 1)
where mvn     >nul 2>&1 || (echo ERROR: Maven required & pause & exit /b 1)
where node    >nul 2>&1 || (echo ERROR: Node.js required & pause & exit /b 1)
where python  >nul 2>&1 || (echo WARNING: Python not found - worker will be skipped)
where cargo   >nul 2>&1 || (echo ERROR: Rust/Cargo required. Install from https://rustup.rs & pause & exit /b 1)
where cargo   >nul 2>&1 && (
    cargo tauri --version >nul 2>&1 || npm install -g @tauri-apps/cli@latest
)

echo  OK
echo.

:: ── Build Java backend JAR ────────────────────────────────────
echo [1/5] Building Java backend...

cd /d "%BACKEND_DIR%"
call mvnw.cmd clean package -DskipTests -q
if errorlevel 1 (
    echo ERROR: Java build failed
    pause & exit /b 1
)

:: Find the built JAR
for /f "delims=" %%f in ('dir /b /s target\*.jar ^| findstr /v "original"') do set JAR_PATH=%%f
echo  Built: %JAR_PATH%
echo.

:: ── Setup Python worker ───────────────────────────────────────
echo [2/5] Setting up Python worker...

cd /d "%PYTHON_DIR%"
if not exist ".venv" (
    python -m venv .venv
    call .venv\Scripts\activate
    pip install -r requirements.txt -q
    playwright install chromium --with-deps
    echo  Python worker ready
) else (
    echo  Python worker already set up
)
echo.

:: ── Build React frontend ──────────────────────────────────────
echo [3/5] Building React frontend...

cd /d "%FRONTEND_DIR%"
if not exist "node_modules" (
    call npm install --silent
)
call npm run build
if errorlevel 1 (
    echo ERROR: Frontend build failed
    pause & exit /b 1
)
echo  Frontend built → dist/
echo.

:: ── Copy resources into Tauri ─────────────────────────────────
echo [4/5] Staging Tauri resources...

set TAURI_RES=%FRONTEND_DIR%\src-tauri\resources
mkdir "%TAURI_RES%\backend"    2>nul
mkdir "%TAURI_RES%\python-worker" 2>nul

:: Copy JAR
copy "%JAR_PATH%" "%TAURI_RES%\backend\ai-architect-backend.jar" >nul
echo  Copied backend JAR

:: Copy .env template
copy "%BACKEND_DIR%\.env.example" "%TAURI_RES%\backend\.env" >nul
echo  Copied .env template

:: Copy Python worker (excluding __pycache__, .venv)
robocopy "%PYTHON_DIR%" "%TAURI_RES%\python-worker" /E /XD __pycache__ .venv /XF "*.pyc" /NP /NJH /NJS
echo  Copied Python worker
echo.

:: ── Build Tauri app ───────────────────────────────────────────
echo [5/5] Building Tauri desktop app...

cd /d "%FRONTEND_DIR%"
call npx tauri build
if errorlevel 1 (
    echo ERROR: Tauri build failed
    pause & exit /b 1
)

echo.
echo  ====================================
echo   BUILD COMPLETE
echo  ====================================
echo.

:: Find and show the built installer
for /f "delims=" %%f in ('dir /b /s "src-tauri\target\release\bundle\*.exe" 2^>nul') do (
    echo   Installer: %%f
)
for /f "delims=" %%f in ('dir /b /s "src-tauri\target\release\bundle\*.msi" 2^>nul') do (
    echo   MSI: %%f
)
for /f "delims=" %%f in ('dir /b /s "src-tauri\target\release\*.exe" 2^>nul ^| findstr /v bundle') do (
    echo   Portable: %%f
)

echo.
echo  Done! Distribute the installer or portable .exe
pause
