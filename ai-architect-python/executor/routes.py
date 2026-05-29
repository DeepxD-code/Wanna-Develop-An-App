"""
Executor Routes
FastAPI endpoints for running generated code in sandboxed venvs.
Called by Spring Boot backend after Phase 7 completes.
"""

import logging
from pathlib import Path

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel

from executor.sandbox import SandboxExecutor
from executor.vuln_scanner import VulnScanner

log    = logging.getLogger(__name__)
router = APIRouter()
sandbox  = SandboxExecutor()
scanner  = VulnScanner()


# ── Request / Response Models ──────────────────────────────────

class RunRequest(BaseModel):
    project_dir:  str
    entry_file:   str | None = None   # relative to project_dir, e.g. "src/main.py"
    requirements: list[str] | None = None
    env_vars:     dict[str, str] | None = None
    timeout:      int = 60


class TestRequest(BaseModel):
    project_dir:  str
    requirements: list[str] | None = None


class LintRequest(BaseModel):
    project_dir:  str
    requirements: list[str] | None = None


# ── Endpoints ──────────────────────────────────────────────────

@router.post("/run")
async def run_code(req: RunRequest):
    """
    Execute generated project code in isolated venv.
    Auto-detects entry point if not specified.
    """
    project_dir = Path(req.project_dir)
    if not project_dir.exists():
        raise HTTPException(status_code=404, detail=f"Project dir not found: {project_dir}")

    # Auto-detect entry point
    entry = _find_entry_point(project_dir, req.entry_file)
    if not entry:
        raise HTTPException(status_code=422, detail="No runnable entry point found in project")

    log.info("Running %s in %s", entry, project_dir)
    result = await sandbox.run_file(
        file_path   = entry,
        project_dir = project_dir,
        requirements = req.requirements,
        timeout      = req.timeout,
        env_vars     = req.env_vars,
    )
    return result.to_dict()


@router.post("/test")
async def run_tests(req: TestRequest):
    """Run pytest on the generated project."""
    project_dir = Path(req.project_dir)
    if not project_dir.exists():
        raise HTTPException(status_code=404, detail=f"Project dir not found: {project_dir}")

    tests_dir = project_dir / "tests"
    if not tests_dir.exists():
        return {
            "success":   False,
            "stdout":    "",
            "stderr":    "No tests/ directory found",
            "exit_code": -1,
            "duration":  0,
        }

    log.info("Running tests in %s", project_dir)
    result = await sandbox.run_tests(project_dir, req.requirements)
    return result.to_dict()


@router.post("/lint")
async def run_lint(req: LintRequest):
    """Run flake8 linter on generated code."""
    project_dir = Path(req.project_dir)
    if not project_dir.exists():
        raise HTTPException(status_code=404, detail=f"Project dir not found: {project_dir}")

    log.info("Linting %s", project_dir)
    result = await sandbox.run_linter(project_dir, req.requirements)
    return result.to_dict()


@router.post("/full-pipeline")
async def run_full_pipeline(req: RunRequest):
    """
    Run full execution pipeline:
    1. Execute main code
    2. Run tests
    3. Run linter
    Returns combined results.
    """
    project_dir = Path(req.project_dir)
    if not project_dir.exists():
        raise HTTPException(status_code=404, detail=f"Project dir not found: {project_dir}")

    results = {}

    # 1. Run code
    entry = _find_entry_point(project_dir, req.entry_file)
    if entry:
        log.info("Pipeline: running %s", entry)
        run_result = await sandbox.run_file(
            file_path    = entry,
            project_dir  = project_dir,
            requirements = req.requirements,
            timeout      = req.timeout,
            env_vars     = req.env_vars,
        )
        results["execution"] = run_result.to_dict()
    else:
        results["execution"] = {"success": False, "stderr": "No entry point found"}

    # 2. Run tests
    tests_dir = project_dir / "tests"
    if tests_dir.exists():
        log.info("Pipeline: running tests")
        test_result = await sandbox.run_tests(project_dir, req.requirements)
        results["tests"] = test_result.to_dict()
    else:
        results["tests"] = {"success": False, "stderr": "No tests directory"}

    # 3. Lint
    log.info("Pipeline: linting")
    lint_result = await sandbox.run_linter(project_dir, req.requirements)
    results["lint"] = lint_result.to_dict()

    # Overall success
    results["overall_success"] = (
        results["execution"].get("success", False) and
        results["tests"].get("success", True) and      # tests optional
        results["lint"].get("exit_code", 1) == 0
    )

    return results


# ── Helpers ────────────────────────────────────────────────────

def _find_entry_point(project_dir: Path, hint: str | None) -> Path | None:
    """Find the best entry point file."""
    if hint:
        candidate = project_dir / hint
        if candidate.exists():
            return candidate

    # Common entry point names in priority order
    candidates = [
        "src/main.py", "src/app.py", "src/index.py",
        "main.py", "app.py", "index.py", "run.py", "server.py",
    ]
    for c in candidates:
        p = project_dir / c
        if p.exists():
            return p

    # Find any .py file in src/
    src = project_dir / "src"
    if src.exists():
        py_files = list(src.glob("*.py"))
        if py_files:
            return py_files[0]

    # Any .py file at root
    py_files = list(project_dir.glob("*.py"))
    return py_files[0] if py_files else None


@router.post("/scan")
async def scan_vulnerabilities(req: TestRequest):
    """Scan generated project for dependency vulnerabilities."""
    project_dir = Path(req.project_dir)
    if not project_dir.exists():
        raise HTTPException(status_code=404, detail=f"Project dir not found: {project_dir}")

    log.info("Scanning vulnerabilities in %s", project_dir)
    results = await scanner.scan(project_dir)
    return results
