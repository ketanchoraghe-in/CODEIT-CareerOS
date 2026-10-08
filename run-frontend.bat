@echo off
REM ============================================================
REM  CODEIT CareerOS - Frontend runner (Next.js)
REM  Usage:  run-frontend.bat
REM  Stops with Ctrl+C. Frontend: http://localhost:3000
REM  Requires the backend on http://localhost:8080 (run-backend.bat)
REM ============================================================
setlocal
cd /d "%~dp0careeros-frontend"

where node >nul 2>nul
if errorlevel 1 (
  echo [ERROR] Node.js not found on PATH. Install Node 20+ and reopen the terminal.
  exit /b 1
)
echo [INFO] Using Node:
call node --version
call npm --version

if not exist "node_modules" (
  echo [INFO] node_modules missing - installing dependencies, one time only ...
  call npm install
  if errorlevel 1 (
    echo [ERROR] npm install failed. Check your network and try again.
    exit /b 1
  )
)

echo [INFO] Starting frontend ...
echo [INFO] Open: http://localhost:3000  (Ctrl+C to stop)
call npm run dev
