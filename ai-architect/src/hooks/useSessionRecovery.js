// ============================================================
// useSessionRecovery
// Detects interrupted sessions on app startup and offers resume.
// Stores session state in localStorage for crash recovery.
// ============================================================

import { useState, useEffect } from "react";

const STORAGE_KEY = "ai-architect:last-session";
const BACKEND     = import.meta.env.VITE_BACKEND_URL || "http://localhost:8080/api";

export function useSessionRecovery() {
  const [recoverable, setRecoverable] = useState(null); // { sessionId, phase, userInput, mode }
  const [checking, setChecking]       = useState(true);

  // Check on mount if there's a recoverable session
  useEffect(() => {
    const check = async () => {
      const stored = loadStored();
      if (!stored) { setChecking(false); return; }

      // Verify session still exists on backend
      try {
        const res = await fetch(`${BACKEND}/sessions/${stored.sessionId}`);
        if (res.ok) {
          const session = await res.json();
          // Only offer recovery if session was mid-run (not complete/stopped)
          if (session.status === "RUNNING" || session.status === "PENDING") {
            setRecoverable({ ...stored, backendSession: session });
          } else {
            clearStored();
          }
        } else {
          clearStored();
        }
      } catch {
        // Backend not available — still show recovery option with local info
        if (stored.phase > 1) setRecoverable(stored);
      }

      setChecking(false);
    };

    check();
  }, []);

  // Save current session state (call this during launch and phase updates)
  const saveState = (sessionId, phase, userInput, mode) => {
    const state = { sessionId, phase, userInput, mode, savedAt: Date.now() };
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
    } catch {}
  };

  // Clear saved state (call on successful completion or restart)
  const clearState = () => clearStored();

  // Dismiss recovery prompt without resuming
  const dismissRecovery = () => {
    setRecoverable(null);
    clearStored();
  };

  return { recoverable, checking, saveState, clearState, dismissRecovery };
}

function loadStored() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    const data = JSON.parse(raw);
    // Ignore sessions older than 2 hours
    if (Date.now() - data.savedAt > 2 * 60 * 60 * 1000) {
      clearStored();
      return null;
    }
    return data;
  } catch {
    return null;
  }
}

function clearStored() {
  try { localStorage.removeItem(STORAGE_KEY); } catch {}
}
