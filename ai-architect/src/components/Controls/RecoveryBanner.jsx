// ============================================================
// RecoveryBanner
// Shown on startup when an interrupted session is detected.
// Offers: Resume from last phase | Start fresh
// ============================================================

export default function RecoveryBanner({ recoverable, onResume, onDismiss }) {
  if (!recoverable) return null;

  const { phase, userInput, mode, savedAt } = recoverable;
  const ago = savedAt ? Math.round((Date.now() - savedAt) / 60000) : "?";

  return (
    <div style={{
      position: "fixed", top: 0, left: 0, right: 0, zIndex: 100,
      background: "#060606", borderBottom: "1px solid #f59e0b33",
      padding: "10px 20px", display: "flex",
      alignItems: "center", justifyContent: "space-between",
      gap: 12, flexWrap: "wrap",
      fontFamily: "'JetBrains Mono','Fira Code',monospace",
      animation: "fadeUp 0.3s ease",
    }}>
      <div>
        <div style={{ fontSize: 10, color: "#f59e0b", fontWeight: 700, marginBottom: 3 }}>
          ↺ INTERRUPTED SESSION DETECTED
        </div>
        <div style={{ fontSize: 9, color: "#555" }}>
          {ago}min ago · Phase {phase}/11 · {mode?.toUpperCase()} mode
          {userInput ? ` · "${userInput.slice(0, 40)}${userInput.length > 40 ? "…" : ""}"` : ""}
        </div>
      </div>

      <div style={{ display: "flex", gap: 8 }}>
        <button
          onClick={onResume}
          style={{
            padding: "6px 14px", background: "#f59e0b", color: "#000",
            border: "none", borderRadius: 5, cursor: "pointer",
            fontSize: 9, fontWeight: 800, letterSpacing: "0.08em",
            fontFamily: "inherit",
          }}
        >
          ↺ RESUME
        </button>
        <button
          onClick={onDismiss}
          style={{
            padding: "6px 12px", background: "transparent", color: "#444",
            border: "1px solid #1e1e1e", borderRadius: 5, cursor: "pointer",
            fontSize: 9, fontWeight: 600, fontFamily: "inherit",
          }}
        >
          DISMISS
        </button>
      </div>
    </div>
  );
}
