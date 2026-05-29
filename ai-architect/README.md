# AI ARCHITECT — AUTONOMOUS ENGINEERING PLATFORM

## SETUP IN 3 STEPS

### Step 1: Install Dependencies
```bash
cd ai-architect
npm install
```

### Step 2: Add API Keys
```bash
cp .env.example .env
```
Then open `.env` and replace the placeholder values:
```
VITE_GEMINI_KEY_1=your_actual_gemini_key_here
VITE_GEMINI_KEY_2=your_second_gemini_key_here   # optional but recommended
```

**Where to get free API keys:**
| Provider   | URL                                          | Free Tier         |
|------------|----------------------------------------------|-------------------|
| Gemini     | https://aistudio.google.com/apikey           | 15 req/min free   |
| OpenRouter | https://openrouter.ai/keys                   | Free models       |
| Groq       | https://console.groq.com/keys                | 30 req/min free   |
| TogetherAI | https://api.together.xyz/settings/api-keys   | Free credits      |
| Cerebras   | https://cloud.cerebras.ai/                   | Free tier         |

> **Tip:** Add at least Gemini + one fallback (Groq or OpenRouter) for best reliability.

### Step 3: Run
```bash
npm run dev
```
Open http://localhost:5173

---

## HOW IT WORKS

1. **Launch the app** → you see the input screen
2. **Optionally describe** your project (or leave blank for auto-generated ideas)
3. **Choose mode:**
   - ⚡ **Quick** (~10-15 min) — MVP-level output
   - 🔬 **Deep** (~30-40 min) — Production-grade output
4. **Click LAUNCH** — watch 11 phases execute autonomously
5. **Click any completed phase** in the sidebar to review its output

---

## FILE STRUCTURE

```
ai-architect/
├── src/
│   ├── App.jsx                          # Root component
│   ├── main.jsx                         # Entry point
│   ├── config/
│   │   └── phases.js                    # Phase definitions + prompts
│   ├── hooks/
│   │   └── useOrchestrator.js           # Core orchestration engine
│   ├── utils/
│   │   └── providers.js                 # Multi-provider API abstraction
│   └── components/
│       ├── Sidebar/Sidebar.jsx          # Phase nav + progress
│       ├── TopBar/TopBar.jsx            # Status bar + toggles
│       ├── Content/ContentArea.jsx      # Input + streaming output
│       ├── Metrics/StatsPanel.jsx       # Stats for Nerds panel
│       └── Controls/TabBar.jsx          # Mobile tab bar
├── .env.example                         # API key template
├── .env                                 # Your actual keys (gitignored)
├── index.html
├── package.json
└── vite.config.js
```

---

## THE 11 PHASES

| # | Name              | Tag         | What It Does                                      |
|---|-------------------|-------------|---------------------------------------------------|
| 1 | Auto Ideation     | IDEATION    | Generates 5 project ideas + selects winner        |
| 2 | Auto Ranking      | RANK        | Scores + justifies selection                      |
| 3 | Research          | RESEARCH    | Market analysis, competitors, risks               |
| 4 | Architecture      | ARCHITECT   | System design, ASCII diagrams, modules            |
| 5 | Implementation    | PLANNING    | 6-phase dev roadmap with deliverables             |
| 6 | API Integration   | INTEGRATION | Prompt templates, context management              |
| 7 | Code Generation   | CODE        | Full production-ready source code                 |
| 8 | Error Checking    | VALIDATE    | 3-pass audit: logic, runtime, security            |
| 9 | Stress Testing    | TESTING     | 8 resilience scenarios + score                    |
|10 | Extensibility     | ROADMAP     | Future improvements + quarterly roadmap           |
|11 | Final Report      | REPORT      | Executive summary + production verdict            |

---

## TOGGLEABLE FEATURES

### ◉ STATS (top-right button)
Shows real-time:
- Active provider + model
- Phase completion status
- Retry counts
- Provider health (success/fail/tokens)
- API key status (SET / NOT SET)

---

## ADDING MORE API KEYS

Edit `.env` and add a second key for any provider to enable automatic key rotation:
```
VITE_GEMINI_KEY_1=key_one
VITE_GEMINI_KEY_2=key_two   # rotates automatically on rate limit
```

---

## RATE LIMIT PROTECTION

The system automatically:
- Rotates between keys when one hits rate limits
- Adds delays between phases (configurable in `.env`)
- Falls back to next provider if all keys exhausted
- Retries failed phases (max 3 attempts, with context from previous failures)

Adjust timing in `.env`:
```
VITE_PHASE_DELAY_MS=2000      # delay between phases
VITE_REQUEST_DELAY_MS=1500    # delay between retries
VITE_MAX_RETRIES=3            # max retries per phase
```

---

## COMING NEXT

- [ ] Tauri desktop packaging (Windows exe)
- [ ] Java Spring Boot backend (orchestration engine)
- [ ] Python worker services (AI execution, browser automation)
- [ ] Redis session management
- [ ] Git integration (generated projects become repos)
- [ ] Downloadable ZIP output
- [ ] Persistent session recovery
- [ ] Knowledge graph (learns from previous projects)
