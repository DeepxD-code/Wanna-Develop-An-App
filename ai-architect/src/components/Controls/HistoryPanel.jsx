// ============================================================
// HistoryPanel
// Shows recent sessions from PostgreSQL.
// Slide-in from the right, toggled by a History button.
// ============================================================

import { useState, useEffect } from "react";

const BACKEND = import.meta.env.VITE_BACKEND_URL || "http://localhost:8080/api";

const STATUS_COLORS = {
  COMPLETE: "#4ade80",
  RUNNING:  "#f59e0b",
  ERROR:    "#f87171",
  STOPPED:  "#6b7280",
  PENDING:  "#374151",
};

export default function HistoryPanel({ visible, onClose, onReopen }) {
  const [sessions, setSessions] = useState([]);
  const [loading, setLoading]   = useState(false);

  useEffect(() => {
    if (!visible) return;
    setLoading(true);
    fetch(`${BACKEND}/analytics/sessions?limit=20`)
      .then(r => r.ok ? r.json() : [])
      .then(data => { setSessions(Array.isArray(data) ? data : []); setLoading(false); })
      .catch(() => setLoading(false));
  }, [visible]);

  if (!visible) return null;

  return (
    <>
      {/* Backdrop */}
      <div
        onClick={onClose}
        style={{
          position: "fixed", inset: 0,
          background: "#00000066", zIndex: 30,
        }}
      />

      {/* Panel */}
      <div style={{
        position: "fixed", top: 0, right: 0, bottom: 0,
        width: 340, zIndex: 31,
        background: "#080808", borderLeft: "1px solid #171717",
        display: "flex", flexDirection: "column",
        fontFamily: "'JetBrains Mono','Fira Code',monospace",
        animation: "slideIn 0.2s ease",
      }}>
        <style>{`@keyframes slideIn { from{transform:translateX(100%)} to{transform:translateX(0)} }`}</style>

        {/* Header */}
        <div style={{
          padding: "14px 16px", borderBottom: "1px solid #131313",
          display: "flex", alignItems: "center", justifyContent: "space-between",
        }}>
          <div>
            <div style={{ fontSize: 11, fontWeight: 700, color: "#d4c4a0", letterSpacing: "0.06em" }}>
              SESSION HISTORY
            </div>
            <div style={{ fontSize: 9, color: "#333", marginTop: 2 }}>
              Recent generations from PostgreSQL
            </div>
          </div>
          <button onClick={onClose} style={{
            background: "none", border: "1px solid #1e1e1e",
            borderRadius: 5, color: "#444", padding: "4px 8px",
            cursor: "pointer", fontFamily: "inherit", fontSize: 12,
          }}>✕</button>
        </div>

        {/* Session list */}
        <div style={{ flex: 1, overflowY: "auto" }}>
          {loading ? (
            <div style={{ padding: 24, color: "#333", fontSize: 11, textAlign: "center" }}>
              Loading history...
            </div>
          ) : sessions.length === 0 ? (
            <div style={{ padding: 24, color: "#222", fontSize: 11, textAlign: "center" }}>
              No sessions yet.<br />
              <span style={{ color: "#1a1a1a" }}>Run your first project to see history.</span>
            </div>
          ) : (
            sessions.map((s, i) => (
              <div
                key={s.sessionId || i}
                style={{
                  padding: "12px 16px",
                  borderBottom: "1px solid #0d0d0d",
                  cursor: "pointer",
                  transition: "background 0.1s",
                }}
                onMouseEnter={e => e.currentTarget.style.background = "#0d0d0d"}
                onMouseLeave={e => e.currentTarget.style.background = "transparent"}
                onClick={() => onReopen?.(s.sessionId)}
              >
                {/* Status + mode */}
                <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 4 }}>
                  <span style={{
                    fontSize: 9, fontWeight: 700, letterSpacing: "0.06em",
                    color: STATUS_COLORS[s.status] || "#555",
                  }}>
                    {s.status}
                  </span>
                  <span style={{ fontSize: 9, color: "#333" }}>
                    {s.mode?.toUpperCase() || "DEEP"}
                  </span>
                </div>

                {/* User input or placeholder */}
                <div style={{
                  fontSize: 11, color: "#7a7060",
                  whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis",
                  marginBottom: 4,
                }}>
                  {s.userInput?.trim()
                    ? `"${s.userInput.slice(0, 60)}${s.userInput.length > 60 ? "…" : ""}"`
                    : "(auto-generated idea)"}
                </div>

                {/* Date */}
                <div style={{ fontSize: 9, color: "#252525" }}>
                  {s.createdAt ? new Date(s.createdAt).toLocaleString() : "—"}
                </div>
              </div>
            ))
          )}
        </div>

        {/* Footer */}
        <div style={{
          padding: "10px 16px", borderTop: "1px solid #131313",
          fontSize: 9, color: "#1a1a1a", textAlign: "center",
        }}>
          Powered by PostgreSQL · Sessions persist across restarts
        </div>
      </div>
    </>
  );
}
