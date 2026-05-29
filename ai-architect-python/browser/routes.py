"""
Browser Routes
FastAPI endpoints for autonomous web research.
Called by Spring Boot backend during Phase 3 (Research).
"""

import logging
from fastapi import APIRouter
from fastapi.responses import StreamingResponse
from pydantic import BaseModel
import json

from browser.researcher import BrowserResearcher

log      = logging.getLogger(__name__)
router   = APIRouter()
researcher = BrowserResearcher()


# ── Startup / Shutdown ─────────────────────────────────────────

async def startup():
    await researcher.start()


async def shutdown():
    await researcher.stop()


# ── Models ─────────────────────────────────────────────────────

class ResearchRequest(BaseModel):
    query:     str
    max_pages: int = 5
    visible:   bool = False   # show browser window (for debugging)


# ── Endpoints ──────────────────────────────────────────────────

@router.post("/research")
async def research(req: ResearchRequest):
    """
    Research a topic autonomously.
    Returns list of results from multiple sources.
    """
    log.info("Research request: %s", req.query)
    results = await researcher.research(req.query, req.max_pages, req.visible)
    return {
        "query":   req.query,
        "results": [r.to_dict() for r in results],
        "count":   len(results),
    }


@router.post("/research/stream")
async def research_stream(req: ResearchRequest):
    """
    Stream research results as SSE events.
    Frontend can display progress in real-time.
    """
    async def generate():
        async for event in researcher.research_streaming(req.query, req.max_pages):
            yield f"data: {json.dumps(event)}\n\n"

    return StreamingResponse(
        generate(),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"},
    )


@router.get("/cache/clear")
async def clear_cache():
    """Clear research cache."""
    from browser.researcher import CACHE_DIR
    count = 0
    for f in CACHE_DIR.glob("*.json"):
        f.unlink()
        count += 1
    return {"cleared": count}
