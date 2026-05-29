# AI ARCHITECT — BACKEND (Spring Boot)

## SETUP IN 4 STEPS

### Step 1: Prerequisites
- Java 21+ (https://adoptium.net)
- Maven 3.8+ (https://maven.apache.org)
- Redis (https://redis.io/download OR `winget install Redis.Redis` on Windows)

### Step 2: Add API Keys
```bash
cp .env.example .env
```
Edit `.env` and fill in your API keys.
**Minimum required:** at least one of GEMINI_KEY_1 or GROQ_KEY_1.

### Step 3: Start Redis
```bash
# Windows (after install)
redis-server

# Or with Docker
docker run -d -p 6379:6379 redis:alpine
```

### Step 4: Run Backend
```bash
# Load env vars and run
set -a && source .env && set +a   # Linux/Mac
# OR on Windows PowerShell:
# Get-Content .env | ForEach-Object { [System.Environment]::SetEnvironmentVariable($_.Split('=')[0], $_.Split('=')[1]) }

./mvnw spring-boot:run
```

Backend starts at: **http://localhost:8080/api**

---

## API ENDPOINTS

| Method | Endpoint                                    | Description            |
|--------|---------------------------------------------|------------------------|
| POST   | /api/sessions                               | Create new session     |
| GET    | /api/sessions/:id                           | Get session state      |
| DELETE | /api/sessions/:id                           | Stop + delete session  |
| GET    | /api/sessions/:id/phases/:phaseId/stream    | Stream phase via SSE   |
| DELETE | /api/sessions/:id/phases/stop               | Stop current phase     |
| GET    | /api/providers/health                       | Check provider status  |

---

## SSE EVENT FORMAT

Each phase streams events in this format:
```json
data: {"type":"delta","text":"...token..."}
data: {"type":"provider","provider":"gemini","model":"gemini-2.0-flash"}
data: {"type":"retry","attempt":2}
data: {"type":"done","provider":"gemini","model":"...","tokens":1234,"latency":4200}
data: {"type":"error","message":"..."}
```

---

## PROVIDER PRIORITY

1. **Gemini** (Google AI Studio — free tier, 15 req/min)
2. **OpenRouter** (free models available)
3. **Groq** (fast, generous free tier)
4. **Together AI** (free credits on signup)
5. **Cerebras** (free tier)

The system automatically falls through this list on rate limits or failures.

---

## FILE STRUCTURE

```
src/main/java/com/aiarchitect/
├── AiArchitectApplication.java       # Entry point
├── config/
│   ├── AppConfig.java                # OkHttp, Redis, CORS beans
│   ├── ProviderProperties.java       # Provider config binding
│   └── OrchestrationProperties.java  # Timing + retry config
├── controller/
│   └── ApiController.java            # REST + SSE endpoints
├── orchestrator/
│   ├── PhaseOrchestrator.java        # Phase execution engine
│   └── PromptBuilder.java            # All 11 phase prompts
├── provider/
│   └── ProviderService.java          # Multi-provider AI client
└── service/
    └── SessionStore.java             # Redis session persistence
```
