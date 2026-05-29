"""
BrowserResearcher
Autonomous internet research using Playwright.
Used during Phase 3 (Research) to gather real market data,
competitor info, and technical landscape.
"""

import asyncio
import hashlib
import json
import logging
import os
import re
import time
from pathlib import Path
from typing import AsyncGenerator

log = logging.getLogger(__name__)

# Research cache directory
CACHE_DIR = Path(os.getenv("RESEARCH_CACHE", Path.home() / "ai-architect-research-cache"))
CACHE_DIR.mkdir(parents=True, exist_ok=True)
CACHE_TTL_HOURS = 24


class ResearchResult:
    def __init__(self, query: str, url: str, title: str, content: str,
                 source: str = "web", cached: bool = False):
        self.query   = query
        self.url     = url
        self.title   = title
        self.content = content[:8000]   # cap per page
        self.source  = source
        self.cached  = cached

    def to_dict(self):
        return {
            "url":     self.url,
            "title":   self.title,
            "content": self.content,
            "source":  self.source,
            "cached":  self.cached,
        }


class BrowserResearcher:

    def __init__(self):
        self._browser = None
        self._playwright = None

    # ── Lifecycle ──────────────────────────────────────────────

    async def start(self):
        """Initialize Playwright browser."""
        try:
            from playwright.async_api import async_playwright
            self._playwright = await async_playwright().start()
            self._browser = await self._playwright.chromium.launch(
                headless=True,
                args=[
                    "--no-sandbox",
                    "--disable-dev-shm-usage",
                    "--disable-gpu",
                    "--disable-extensions",
                ],
            )
            log.info("Playwright browser started")
        except ImportError:
            log.warning("Playwright not installed — browser research disabled")
        except Exception as e:
            log.warning("Failed to start Playwright: %s", e)

    async def stop(self):
        """Close browser."""
        try:
            if self._browser:
                await self._browser.close()
            if self._playwright:
                await self._playwright.stop()
        except Exception:
            pass

    # ── Research ───────────────────────────────────────────────

    async def research(
        self,
        query: str,
        max_pages: int = 5,
        visible: bool = False,
    ) -> list[ResearchResult]:
        """
        Research a query autonomously.
        Returns list of ResearchResult from multiple sources.
        """
        # Check cache first
        cached = self._load_cache(query)
        if cached:
            log.info("Research cache hit for: %s", query)
            return cached

        results = []

        if not self._browser:
            log.warning("Browser not available — returning empty research")
            return results

        try:
            # Search using DuckDuckGo (no API key required)
            search_results = await self._search_duckduckgo(query, max_pages)
            results.extend(search_results)

            # Also try fetching top results content
            for result in search_results[:3]:
                try:
                    content = await self._fetch_page_content(result.url)
                    if content:
                        result.content = content
                except Exception as e:
                    log.debug("Failed to fetch %s: %s", result.url, e)

        except Exception as e:
            log.error("Research failed for '%s': %s", query, e)

        # Cache results
        if results:
            self._save_cache(query, results)

        return results

    async def research_streaming(
        self,
        query: str,
        max_pages: int = 5,
    ) -> AsyncGenerator[dict, None]:
        """
        Stream research results as they come in.
        Yields status events for frontend display.
        """
        yield {"type": "status", "message": f"Starting research: {query}"}

        cached = self._load_cache(query)
        if cached:
            yield {"type": "status", "message": "Loading from research cache..."}
            for r in cached:
                yield {"type": "result", "data": r.to_dict()}
            yield {"type": "done", "count": len(cached), "cached": True}
            return

        if not self._browser:
            yield {"type": "error", "message": "Browser not available"}
            return

        yield {"type": "status", "message": "Searching the web..."}

        try:
            search_results = await self._search_duckduckgo(query, max_pages)
            yield {"type": "status", "message": f"Found {len(search_results)} results, reading content..."}

            for i, result in enumerate(search_results[:5]):
                yield {"type": "status", "message": f"Reading: {result.title[:60]}..."}
                try:
                    content = await self._fetch_page_content(result.url)
                    if content:
                        result.content = content
                except Exception:
                    pass
                yield {"type": "result", "data": result.to_dict()}

        except Exception as e:
            yield {"type": "error", "message": str(e)}
            return

        yield {"type": "done", "count": len(search_results), "cached": False}

    # ── Internal ───────────────────────────────────────────────

    async def _search_duckduckgo(self, query: str, max_results: int) -> list[ResearchResult]:
        """Search DuckDuckGo and return results."""
        results = []
        page = await self._browser.new_page()

        try:
            await page.set_extra_http_headers({
                "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
            })

            search_url = f"https://duckduckgo.com/?q={query.replace(' ', '+')}&ia=web"
            await page.goto(search_url, wait_until="domcontentloaded", timeout=15000)
            await asyncio.sleep(1.5)

            # Extract search result links and titles
            items = await page.evaluate("""
                () => {
                    const results = [];
                    const links = document.querySelectorAll('[data-testid="result"]');
                    links.forEach(link => {
                        const titleEl = link.querySelector('h2 a');
                        const snippetEl = link.querySelector('[data-result="snippet"]');
                        if (titleEl) {
                            results.push({
                                title:   titleEl.textContent.trim(),
                                url:     titleEl.href,
                                snippet: snippetEl ? snippetEl.textContent.trim() : "",
                            });
                        }
                    });
                    return results.slice(0, 8);
                }
            """)

            for item in items[:max_results]:
                results.append(ResearchResult(
                    query   = query,
                    url     = item.get("url", ""),
                    title   = item.get("title", ""),
                    content = item.get("snippet", ""),
                    source  = "duckduckgo",
                ))

        except Exception as e:
            log.warning("DuckDuckGo search failed: %s", e)
        finally:
            await page.close()

        return results

    async def _fetch_page_content(self, url: str, timeout: int = 10000) -> str:
        """Fetch and extract readable text from a URL."""
        if not url or not url.startswith("http"):
            return ""

        page = await self._browser.new_page()
        try:
            await page.goto(url, wait_until="domcontentloaded", timeout=timeout)

            # Extract main content text
            content = await page.evaluate("""
                () => {
                    // Remove noise elements
                    ['script','style','nav','footer','header','aside',
                     '.ads','#ads','.cookie','.popup'].forEach(sel => {
                        document.querySelectorAll(sel).forEach(el => el.remove());
                    });

                    // Get main content
                    const main = document.querySelector(
                        'main, article, .content, .post, #content, #main'
                    );
                    const target = main || document.body;
                    return target.innerText
                        .replace(/\\n{3,}/g, '\\n\\n')
                        .trim()
                        .slice(0, 6000);
                }
            """)
            return content or ""
        except Exception as e:
            log.debug("Failed to fetch %s: %s", url, e)
            return ""
        finally:
            await page.close()

    # ── Cache ──────────────────────────────────────────────────

    def _cache_key(self, query: str) -> Path:
        h = hashlib.md5(query.encode()).hexdigest()
        return CACHE_DIR / f"{h}.json"

    def _load_cache(self, query: str) -> list[ResearchResult] | None:
        cache_file = self._cache_key(query)
        if not cache_file.exists():
            return None

        # Check TTL
        age_hours = (time.time() - cache_file.stat().st_mtime) / 3600
        if age_hours > CACHE_TTL_HOURS:
            cache_file.unlink()
            return None

        try:
            data = json.loads(cache_file.read_text())
            return [
                ResearchResult(
                    query   = query,
                    url     = r["url"],
                    title   = r["title"],
                    content = r["content"],
                    source  = r.get("source", "cache"),
                    cached  = True,
                )
                for r in data
            ]
        except Exception:
            return None

    def _save_cache(self, query: str, results: list[ResearchResult]):
        try:
            cache_file = self._cache_key(query)
            cache_file.write_text(json.dumps([r.to_dict() for r in results], indent=2))
        except Exception as e:
            log.warning("Cache save failed: %s", e)
