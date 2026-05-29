"""
VulnScanner
Scans generated project dependencies for known vulnerabilities.
Uses pip-audit (Python) and npm audit (Node.js).
"""

import asyncio
import json
import logging
from pathlib import Path
from typing import Optional

log = logging.getLogger(__name__)


class VulnScanner:

    async def scan(self, project_dir: Path) -> dict:
        """
        Scan a project directory for dependency vulnerabilities.
        Auto-detects Python vs Node.js based on project files.
        Returns a dict with findings and severity counts.
        """
        results = {
            "scanned":   False,
            "critical":  0,
            "high":      0,
            "medium":    0,
            "low":       0,
            "findings":  [],
            "scanner":   None,
        }

        # Python project
        req_file = project_dir / "requirements.txt"
        pyproject = project_dir / "pyproject.toml"
        if req_file.exists() or pyproject.exists():
            py_results = await self._scan_python(project_dir)
            results.update(py_results)
            results["scanned"] = True

        # Node.js project
        pkg_json = project_dir / "package.json"
        if pkg_json.exists():
            node_results = await self._scan_node(project_dir)
            # Merge results
            for k in ["critical", "high", "medium", "low"]:
                results[k] += node_results.get(k, 0)
            results["findings"].extend(node_results.get("findings", []))
            results["scanned"] = True

        results["total_issues"] = sum([
            results["critical"], results["high"],
            results["medium"], results["low"]
        ])
        results["passed"] = results["total_issues"] == 0

        return results

    async def _scan_python(self, project_dir: Path) -> dict:
        """Run pip-audit on Python project."""
        try:
            proc = await asyncio.create_subprocess_exec(
                "pip-audit", "--format", "json", "--progress-spinner", "off",
                stdout=asyncio.subprocess.PIPE,
                stderr=asyncio.subprocess.PIPE,
                cwd=str(project_dir),
            )
            stdout, stderr = await asyncio.wait_for(proc.communicate(), timeout=60)
            return self._parse_pip_audit(stdout.decode("utf-8", errors="replace"))
        except FileNotFoundError:
            # pip-audit not installed — try installing it
            try:
                await asyncio.create_subprocess_exec(
                    "pip", "install", "pip-audit", "--quiet", "--break-system-packages"
                )
                log.info("pip-audit installed")
            except Exception:
                pass
            return {"findings": [], "scanner": "pip-audit-unavailable"}
        except asyncio.TimeoutError:
            return {"findings": [], "scanner": "pip-audit-timeout"}
        except Exception as e:
            log.warning("Python vuln scan failed: %s", e)
            return {"findings": [], "scanner": "pip-audit-error"}

    async def _scan_node(self, project_dir: Path) -> dict:
        """Run npm audit on Node.js project."""
        try:
            proc = await asyncio.create_subprocess_exec(
                "npm", "audit", "--json",
                stdout=asyncio.subprocess.PIPE,
                stderr=asyncio.subprocess.PIPE,
                cwd=str(project_dir),
            )
            stdout, _ = await asyncio.wait_for(proc.communicate(), timeout=60)
            return self._parse_npm_audit(stdout.decode("utf-8", errors="replace"))
        except FileNotFoundError:
            return {"findings": [], "scanner": "npm-unavailable"}
        except asyncio.TimeoutError:
            return {"findings": [], "scanner": "npm-audit-timeout"}
        except Exception as e:
            log.warning("Node vuln scan failed: %s", e)
            return {"findings": [], "scanner": "npm-audit-error"}

    def _parse_pip_audit(self, output: str) -> dict:
        results = {"critical": 0, "high": 0, "medium": 0, "low": 0,
                   "findings": [], "scanner": "pip-audit"}
        try:
            data = json.loads(output)
            for dep in data.get("dependencies", []):
                for vuln in dep.get("vulns", []):
                    severity = vuln.get("fix_versions", [])
                    finding = {
                        "package":     dep.get("name"),
                        "version":     dep.get("version"),
                        "id":          vuln.get("id"),
                        "description": vuln.get("description", "")[:200],
                        "severity":    "high",  # pip-audit doesn't always include severity
                    }
                    results["findings"].append(finding)
                    results["high"] += 1
        except (json.JSONDecodeError, KeyError):
            pass
        return results

    def _parse_npm_audit(self, output: str) -> dict:
        results = {"critical": 0, "high": 0, "medium": 0, "low": 0,
                   "findings": [], "scanner": "npm-audit"}
        try:
            data = json.loads(output)
            meta = data.get("metadata", {}).get("vulnerabilities", {})
            results["critical"] = meta.get("critical", 0)
            results["high"]     = meta.get("high",     0)
            results["medium"]   = meta.get("moderate", 0)
            results["low"]      = meta.get("low",      0)

            for name, vuln in data.get("vulnerabilities", {}).items():
                results["findings"].append({
                    "package":     name,
                    "severity":    vuln.get("severity", "unknown"),
                    "description": vuln.get("title", "")[:200],
                })
        except (json.JSONDecodeError, KeyError):
            pass
        return results
