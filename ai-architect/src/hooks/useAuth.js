// ============================================================
// useAuth
// Manages JWT token for backend communication.
// Desktop mode: auto-fetches token on startup (no login needed).
// Auth mode: shows login prompt.
// ============================================================

import { useState, useEffect } from "react";

const BACKEND   = import.meta.env.VITE_BACKEND_URL || "http://localhost:8080/api";
const TOKEN_KEY = "ai-architect:token";

export function useAuth() {
  const [token,     setToken]     = useState(null);
  const [authReady, setAuthReady] = useState(false);
  const [authMode,  setAuthMode]  = useState("desktop"); // desktop | local-auth

  useEffect(() => {
    const init = async () => {
      try {
        // Check auth mode
        const statusRes = await fetch(`${BACKEND}/auth/status`);
        if (statusRes.ok) {
          const status = await statusRes.json();
          setAuthMode(status.mode);

          if (!status.authEnabled) {
            // Desktop mode — auto-fetch token, no credentials needed
            await fetchToken();
          } else {
            // Auth mode — try stored token first
            const stored = getStoredToken();
            if (stored && await validateStored(stored)) {
              setToken(stored);
            }
            // Otherwise wait for user to log in
          }
        }
      } catch {
        // Backend not available yet — try again shortly
        setTimeout(init, 3000);
        return;
      }
      setAuthReady(true);
    };
    init();
  }, []);

  const fetchToken = async (password = "") => {
    try {
      const res = await fetch(`${BACKEND}/auth/token`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ password }),
      });
      if (res.ok) {
        const data = await res.json();
        setToken(data.token);
        storeToken(data.token);
        return true;
      }
    } catch {}
    return false;
  };

  const logout = () => {
    setToken(null);
    clearToken();
  };

  // Attach token to all fetch calls
  const authFetch = (url, options = {}) => {
    const headers = { ...options.headers };
    if (token) headers["Authorization"] = `Bearer ${token}`;
    return fetch(url, { ...options, headers });
  };

  return { token, authReady, authMode, fetchToken, logout, authFetch };
}

// Storage helpers
function storeToken(t)  { try { localStorage.setItem(TOKEN_KEY, t); } catch {} }
function clearToken()   { try { localStorage.removeItem(TOKEN_KEY); } catch {} }
function getStoredToken() {
  try { return localStorage.getItem(TOKEN_KEY); } catch { return null; }
}
async function validateStored(token) {
  try {
    const res = await fetch(`${BACKEND}/auth/validate`, {
      headers: { "Authorization": `Bearer ${token}` }
    });
    if (res.ok) {
      const data = await res.json();
      return data.valid;
    }
  } catch {}
  return false;
}
