"""
AI Architect — Python Worker Service
Handles: code execution, browser automation, sandboxed venv runs
Runs alongside Spring Boot backend on port 8081
"""

import asyncio
import logging
import os
from contextlib import asynccontextmanager

import uvicorn
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from executor.sandbox import SandboxExecutor
from browser.researcher import BrowserResearcher
from executor.routes import router as executor_router
from browser.routes import router as browser_router, researcher

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
)
log = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    # ── Startup ──
    log.info("AI Architect Python Worker starting...")
    try:
        await researcher.start()
        log.info("Playwright browser initialized")
    except Exception as e:
        log.warning(f"Playwright init failed (browser research disabled): {e}")

    yield  # app runs here

    # ── Shutdown ──
    log.info("Shutting down...")
    try:
        await researcher.stop()
    except Exception:
        pass
    log.info("AI Architect Python Worker stopped")


app = FastAPI(
    title="AI Architect Python Worker",
    description="Code execution + browser automation worker",
    version="1.0.0",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=[
        "http://localhost:8080",
        "http://localhost:5173",
        "tauri://localhost",
    ],
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(executor_router, prefix="/execute", tags=["executor"])
app.include_router(browser_router,  prefix="/browser",  tags=["browser"])


@app.get("/health")
async def health():
    browser_ok = researcher._browser is not None
    return {
        "status":  "ok",
        "service": "ai-architect-python-worker",
        "browser": "ready" if browser_ok else "unavailable",
    }


if __name__ == "__main__":
    uvicorn.run(
        "main:app",
        host="0.0.0.0",
        port=int(os.getenv("WORKER_PORT", "8081")),
        reload=False,
        log_level="info",
    )
