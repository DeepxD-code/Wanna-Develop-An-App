// ============================================================
// Auth token management
// Token is set by useAuth hook after login/auto-login
// ============================================================
let _authToken = null;

export function setAuthToken(token) { _authToken = token; }

function authHeaders(extra = {}) {
  const h = { "Content-Type": "application/json", ...extra };
  if (_authToken) h["Authorization"] = `Bearer ${_authToken}`;
  return h;
}

// ============================================================
// BACKEND API CLIENT
// All AI calls go through Spring Boot backend — never direct.
// Frontend only connects to: http://localhost:8080/api
// ============================================================

const BACKEND_URL = import.meta.env.VITE_BACKEND_URL || "http://localhost:8080/api";

// ── Metrics tracking (populated from backend SSE events) ──
export const metrics = {
  calls: [],
  addCall: (provider, model, tokens, latency, success) => {
    metrics.calls.push({ provider, model, tokens, latency, success, timestamp: Date.now() });
    if (metrics.calls.length > 100) metrics.calls.shift();
  },
  getStats: () => {
    const byProvider = {};
    metrics.calls.forEach(c => {
      if (!byProvider[c.provider]) {
        byProvider[c.provider] = { success: 0, fail: 0, totalTokens: 0, totalLatency: 0 };
      }
      if (c.success) byProvider[c.provider].success++;
      else byProvider[c.provider].fail++;
      byProvider[c.provider].totalTokens  += c.tokens  || 0;
      byProvider[c.provider].totalLatency += c.latency || 0;
    });
    return byProvider;
  },
};

// ============================================================
// START SESSION — POST /sessions
// Returns sessionId used for all subsequent calls
// ============================================================
export async function startSession(userInput, mode) {
  const res = await fetch(`${BACKEND_URL}/sessions`, {
    method: "POST",
    headers: authHeaders(),
    body: JSON.stringify({ userInput, mode }),
  });
  if (!res.ok) throw new Error(`Failed to start session: ${res.status}`);
  return res.json(); // { sessionId, status, ... }
}

// ============================================================
// STREAM PHASE — GET /sessions/:id/phases/:phaseId/stream
// Backend streams SSE events back as phase executes.
//
// SSE event types:
//   data: { type: "delta",    text: "..." }
//   data: { type: "provider", provider: "gemini", model: "gemini-2.0-flash" }
//   data: { type: "retry",    attempt: 2 }
//   data: { type: "done",     tokens: 1234, latency: 4200 }
//   data: { type: "error",    message: "..." }
// ============================================================
export function streamPhaseFromBackend(sessionId, phaseId, callbacks = {}) {
  const { onDelta, onProviderSwitch, onRetry, onDone, onError, signal } = callbacks;
  return new Promise((resolve, reject) => {
    const url = callbacks._urlOverride || `${BACKEND_URL}/sessions/${sessionId}/phases/${phaseId}/stream`;

    // Use fetch + ReadableStream for SSE (works with AbortSignal)
    fetch(url, { signal })
      .then(res => {
        if (!res.ok) throw new Error(`Backend ${res.status}: phase ${phaseId} failed`);

        const reader = res.body.getReader();
        const dec    = new TextDecoder();
        let buffer   = "";
        let fullText = "";

        const pump = () => reader.read().then(({ done, value }) => {
          if (done || signal?.aborted) {
            resolve(fullText);
            return;
          }

          buffer += dec.decode(value, { stream: true });
          const lines = buffer.split("\n");
          buffer = lines.pop(); // keep incomplete last line

          for (const line of lines) {
            if (!line.startsWith("data:")) continue;
            const raw = line.slice(5).trim();
            if (!raw) continue;
            if (!raw || raw === "[DONE]") continue;

            try {
              const event = JSON.parse(raw);

              switch (event.type) {
                case "delta":
                  fullText += event.text;
                  onDelta?.(event.text, fullText);
                  break;

                case "provider":
                  onProviderSwitch?.(event.provider, event.model);
                  metrics.addCall(event.provider, event.model, 0, 0, true);
                  break;

                case "retry":
                  onRetry?.(event.attempt);
                  break;

                case "done":
                  metrics.addCall(event.provider, event.model, event.tokens, event.latency, true);
                  onDone?.(event);
                  resolve(fullText);
                  return;

                case "error":
                  metrics.addCall(event.provider || "unknown", "", 0, 0, false);
                  onError?.(event.message);
                  reject(new Error(event.message));
                  return;
              }
            } catch (_) {}
          }

          pump();
        }).catch(err => {
          if (signal?.aborted) resolve(fullText);
          else reject(err);
        });

        pump();
      })
      .catch(reject);
  });
}


// ============================================================
// REPLAY PHASE — GET /sessions/:id/phases/:phaseId/replay
// Same as streamPhaseFromBackend but uses /replay endpoint.
// Rebuilds context from all previous phases on backend.
// ============================================================
export function replayPhaseFromBackend(sessionId, phaseId, callbacks) {
  return streamPhaseFromBackend(sessionId, phaseId, {
    ...callbacks,
    _urlOverride: `${BACKEND_URL}/sessions/${sessionId}/phases/${phaseId}/replay`,
  });
}

// ============================================================
// STOP SESSION — DELETE /sessions/:id
// ============================================================
export async function stopSession(sessionId) {
  if (!sessionId) return;
  try {
    await fetch(`${BACKEND_URL}/sessions/${sessionId}`, { method: "DELETE", headers: _authToken ? { "Authorization": `Bearer ${_authToken}` } : {} });
  } catch (_) {}
}

// ============================================================
// GET SESSION — GET /sessions/:id
// Returns full session state (for recovery after crash)
// ============================================================
export async function getSession(sessionId) {
  const res = await fetch(`${BACKEND_URL}/sessions/${sessionId}`, { headers: _authToken ? { "Authorization": `Bearer ${_authToken}` } : {} });
  if (!res.ok) return null;
  return res.json();
}

// ============================================================
// GET PROVIDER HEALTH — GET /providers/health
// ============================================================
export async function getProviderHealth() {
  try {
    const res = await fetch(`${BACKEND_URL}/providers/health`, { headers: _authToken ? { "Authorization": `Bearer ${_authToken}` } : {} });
    if (!res.ok) return {};
    return res.json();
  } catch (_) {
    return {};
  }
}
