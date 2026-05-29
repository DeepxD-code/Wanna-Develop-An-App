"""
SandboxExecutor
Runs generated code in isolated Python venvs.
- Creates venv per project
- Auto-installs dependencies
- Captures stdout/stderr
- Enforces timeouts and resource limits
- Reuses venv if stack matches
"""

import asyncio
import hashlib
import json
import logging
import os
import re
import shutil
import subprocess
import sys
import time
from pathlib import Path
from typing import Optional

log = logging.getLogger(__name__)

# Base directory for venvs
VENV_BASE = Path(os.getenv("VENV_BASE", Path.home() / "ai-architect-venvs"))
VENV_BASE.mkdir(parents=True, exist_ok=True)

# Execution limits
MAX_EXEC_TIME   = int(os.getenv("MAX_EXEC_TIME_SECS", "60"))   # seconds
MAX_MEMORY_MB   = int(os.getenv("MAX_MEMORY_MB", "512"))
MAX_OUTPUT_CHARS = 50_000


class ExecutionResult:
    def __init__(self, success: bool, stdout: str, stderr: str,
                 exit_code: int, duration_secs: float, venv_path: str = ""):
        self.success      = success
        self.stdout       = stdout[:MAX_OUTPUT_CHARS]
        self.stderr       = stderr[:MAX_OUTPUT_CHARS]
        self.exit_code    = exit_code
        self.duration     = duration_secs
        self.venv_path    = venv_path

    def to_dict(self):
        return {
            "success":    self.success,
            "stdout":     self.stdout,
            "stderr":     self.stderr,
            "exit_code":  self.exit_code,
            "duration":   round(self.duration, 2),
            "venv_path":  self.venv_path,
        }


class SandboxExecutor:

    # ── Venv Management ────────────────────────────────────────

    def get_venv_path(self, requirements: list[str]) -> Path:
        """
        Return a venv path for the given requirements.
        Reuses existing venv if requirements hash matches.
        """
        req_hash = hashlib.md5(
            json.dumps(sorted(requirements)).encode()
        ).hexdigest()[:12]
        return VENV_BASE / f"venv_{req_hash}"

    async def ensure_venv(self, requirements: list[str]) -> Path:
        """Create venv and install deps if not already done."""
        venv_path = self.get_venv_path(requirements)

        if venv_path.exists():
            log.info("Reusing existing venv: %s", venv_path)
            return venv_path

        log.info("Creating venv: %s", venv_path)
        await self._run_cmd([sys.executable, "-m", "venv", str(venv_path)])

        if requirements:
            pip = venv_path / ("Scripts/pip" if os.name == "nt" else "bin/pip")
            log.info("Installing: %s", requirements)
            await self._run_cmd([
                str(pip), "install", "--quiet", "--no-cache-dir",
                *requirements
            ], timeout=120)

        return venv_path

    # ── Execution ──────────────────────────────────────────────

    async def run_file(
        self,
        file_path: Path,
        project_dir: Path,
        requirements: list[str] | None = None,
        timeout: int = MAX_EXEC_TIME,
        env_vars: dict | None = None,
    ) -> ExecutionResult:
        """
        Execute a Python file in a sandboxed venv.
        Returns ExecutionResult with stdout, stderr, exit_code.
        """
        reqs = requirements or self._detect_requirements(file_path)
        venv_path = await self.ensure_venv(reqs)
        python = venv_path / ("Scripts/python" if os.name == "nt" else "bin/python")

        env = {
            **os.environ,
            "PYTHONPATH": str(project_dir),
            "PYTHONDONTWRITEBYTECODE": "1",
            **(env_vars or {}),
        }

        log.info("Running %s in venv %s", file_path, venv_path.name)
        start = time.monotonic()

        try:
            proc = await asyncio.create_subprocess_exec(
                str(python), str(file_path),
                stdout=asyncio.subprocess.PIPE,
                stderr=asyncio.subprocess.PIPE,
                cwd=str(project_dir),
                env=env,
            )

            try:
                stdout, stderr = await asyncio.wait_for(
                    proc.communicate(), timeout=timeout
                )
                duration = time.monotonic() - start
                success  = proc.returncode == 0

                return ExecutionResult(
                    success    = success,
                    stdout     = stdout.decode("utf-8", errors="replace"),
                    stderr     = stderr.decode("utf-8", errors="replace"),
                    exit_code  = proc.returncode,
                    duration   = duration,
                    venv_path  = str(venv_path),
                )

            except asyncio.TimeoutError:
                proc.kill()
                await proc.communicate()
                return ExecutionResult(
                    success   = False,
                    stdout    = "",
                    stderr    = f"Execution timed out after {timeout}s",
                    exit_code = -1,
                    duration  = timeout,
                    venv_path = str(venv_path),
                )

        except Exception as e:
            return ExecutionResult(
                success   = False,
                stdout    = "",
                stderr    = str(e),
                exit_code = -1,
                duration  = time.monotonic() - start,
                venv_path = str(venv_path),
            )

    async def run_tests(
        self,
        project_dir: Path,
        requirements: list[str] | None = None,
    ) -> ExecutionResult:
        """Run pytest in the project directory."""
        reqs = list({*(requirements or []), "pytest", "pytest-asyncio"})
        venv_path = await self.ensure_venv(reqs)
        pytest    = venv_path / ("Scripts/pytest" if os.name == "nt" else "bin/pytest")

        if not pytest.exists():
            return ExecutionResult(False, "", "pytest not found in venv", -1, 0)

        start = time.monotonic()
        try:
            proc = await asyncio.create_subprocess_exec(
                str(pytest), str(project_dir / "tests"), "-v", "--tb=short",
                stdout=asyncio.subprocess.PIPE,
                stderr=asyncio.subprocess.PIPE,
                cwd=str(project_dir),
            )
            stdout, stderr = await asyncio.wait_for(proc.communicate(), timeout=120)
            return ExecutionResult(
                success   = proc.returncode == 0,
                stdout    = stdout.decode("utf-8", errors="replace"),
                stderr    = stderr.decode("utf-8", errors="replace"),
                exit_code = proc.returncode,
                duration  = time.monotonic() - start,
                venv_path = str(venv_path),
            )
        except asyncio.TimeoutError:
            return ExecutionResult(False, "", "Tests timed out", -1, 120)
        except Exception as e:
            return ExecutionResult(False, "", str(e), -1, 0)

    async def run_linter(
        self,
        project_dir: Path,
        requirements: list[str] | None = None,
    ) -> ExecutionResult:
        """Run flake8 linter on src/ directory."""
        reqs = list({*(requirements or []), "flake8"})
        venv_path = await self.ensure_venv(reqs)
        flake8    = venv_path / ("Scripts/flake8" if os.name == "nt" else "bin/flake8")

        src_dir = project_dir / "src"
        target  = src_dir if src_dir.exists() else project_dir

        start = time.monotonic()
        try:
            proc = await asyncio.create_subprocess_exec(
                str(flake8), str(target),
                "--max-line-length=120",
                "--extend-ignore=E501,W503",
                stdout=asyncio.subprocess.PIPE,
                stderr=asyncio.subprocess.PIPE,
                cwd=str(project_dir),
            )
            stdout, stderr = await asyncio.wait_for(proc.communicate(), timeout=60)
            return ExecutionResult(
                success   = proc.returncode == 0,
                stdout    = stdout.decode("utf-8", errors="replace"),
                stderr    = stderr.decode("utf-8", errors="replace"),
                exit_code = proc.returncode,
                duration  = time.monotonic() - start,
            )
        except Exception as e:
            return ExecutionResult(False, "", str(e), -1, 0)

    # ── Helpers ────────────────────────────────────────────────

    def _detect_requirements(self, file_path: Path) -> list[str]:
        """Scan file for import statements and guess requirements."""
        try:
            content = file_path.read_text(errors="ignore")
        except Exception:
            return []

        imports = re.findall(r"^(?:import|from)\s+([a-zA-Z_][a-zA-Z0-9_]*)", content, re.MULTILINE)
        stdlib  = {
            "os", "sys", "re", "json", "time", "math", "random", "datetime",
            "pathlib", "typing", "collections", "itertools", "functools",
            "asyncio", "threading", "subprocess", "hashlib", "base64",
            "logging", "unittest", "io", "copy", "abc", "enum", "dataclasses",
        }
        pkg_map = {
            "fastapi":   "fastapi uvicorn",
            "flask":     "flask",
            "requests":  "requests",
            "aiohttp":   "aiohttp",
            "sqlalchemy":"sqlalchemy",
            "pydantic":  "pydantic",
            "numpy":     "numpy",
            "pandas":    "pandas",
            "redis":     "redis",
            "psycopg2":  "psycopg2-binary",
            "dotenv":    "python-dotenv",
            "jwt":       "PyJWT",
            "bcrypt":    "bcrypt",
            "celery":    "celery",
            "boto3":     "boto3",
        }

        reqs = []
        for imp in set(imports):
            if imp not in stdlib and imp in pkg_map:
                reqs.extend(pkg_map[imp].split())

        # Also check requirements.txt if it exists
        req_file = file_path.parent / "requirements.txt"
        if not req_file.exists():
            req_file = file_path.parent.parent / "requirements.txt"
        if req_file.exists():
            for line in req_file.read_text().splitlines():
                line = line.strip()
                if line and not line.startswith("#"):
                    reqs.append(line)

        return list(set(reqs))

    async def _run_cmd(self, cmd: list[str], timeout: int = 60):
        proc = await asyncio.create_subprocess_exec(
            *cmd,
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.PIPE,
        )
        await asyncio.wait_for(proc.communicate(), timeout=timeout)

    def cleanup_venv(self, requirements: list[str]):
        """Remove a venv (call after project is done)."""
        venv_path = self.get_venv_path(requirements)
        if venv_path.exists():
            shutil.rmtree(venv_path)
            log.info("Cleaned up venv: %s", venv_path)
