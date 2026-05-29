# AI ARCHITECT — PYTHON WORKER

Handles: autonomous code execution, web research, sandboxed venv runs.
Runs alongside Spring Boot backend on port **8081**.

## SETUP

```bash
cd ai-architect-python

# Create virtual env
python -m venv .venv
.venv\Scripts\activate       # Windows
# source .venv/bin/activate  # Linux/Mac

# Install dependencies
pip install -r requirements.txt

# Install Playwright browsers
playwright install chromium
playwright install-deps      # Linux only
```

## RUN

```bash
python main.py
```

Worker starts at: **http://localhost:8081**

## ENDPOINTS

| Method | Endpoint                    | Description                         |
|--------|-----------------------------|-------------------------------------|
| POST   | /execute/run                | Run generated code in isolated venv |
| POST   | /execute/test               | Run pytest on generated project     |
| POST   | /execute/lint               | Run flake8 linter                   |
| POST   | /execute/full-pipeline      | Run code + tests + lint combined    |
| POST   | /browser/research           | Research a topic autonomously       |
| POST   | /browser/research/stream    | Stream research results (SSE)       |
| GET    | /browser/cache/clear        | Clear research cache                |
| GET    | /health                     | Health check                        |

## ENV VARS

```
WORKER_PORT=8081               # Port to run on
VENV_BASE=~/ai-architect-venvs # Where project venvs are stored
RESEARCH_CACHE=~/ai-architect-research-cache  # Research cache dir
MAX_EXEC_TIME_SECS=60          # Max code execution time
MAX_MEMORY_MB=512              # Max memory per execution
```

## ARCHITECTURE

```
Python Worker
├── executor/
│   ├── sandbox.py    # Isolated venv execution engine
│   └── routes.py     # FastAPI execution endpoints
├── browser/
│   ├── researcher.py # Playwright-based web research
│   └── routes.py     # FastAPI browser endpoints
└── main.py           # FastAPI app entry point
```
