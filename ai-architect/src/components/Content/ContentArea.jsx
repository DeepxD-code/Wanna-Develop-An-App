import MarkdownRenderer from "./MarkdownRenderer.jsx";
// ============================================================
// CONTENT AREA
// Input phase (before launch) + streaming output (during run)
// Exact visual clone of original + download banner on completion
// ============================================================

function Blink({ color, size = 7 }) {
  return (
    <span style={{
      display: "inline-block", width: size, height: size, borderRadius: "50%",
      background: color, flexShrink: 0, animation: "bl 1s ease-in-out infinite",
    }} />
  );
}

function InputStage({ userInput, setUserInput, mode, setMode, onLaunch, isMobile }) {
  return (
    <div style={{
      display: "flex", flexDirection: "column", alignItems: "center",
      justifyContent: "center", minHeight: "80%", gap: 20,
      textAlign: "center", padding: "20px 0",
    }}>
      <div style={{
        width: 52, height: 52, borderRadius: 13, background: "#0e0e0e",
        border: "1px solid #1e1e1e", display: "flex", alignItems: "center",
        justifyContent: "center", fontSize: 24, color: "#f59e0b",
      }}>◈</div>

      <div>
        <div style={{ fontSize: isMobile ? 16 : 19, color: "#d4c4a0", fontWeight: 700, marginBottom: 6 }}>
          AI Systems Architect
        </div>
        <div style={{ fontSize: isMobile ? 10 : 11, color: "#2e2e2e", lineHeight: 2, maxWidth: 420 }}>
          11 phases · streaming · context-chained · multi-provider failover<br />
          Auto-Ideation → Ranking → Research → Arch → Plan → API → Code → Error → Test → Roadmap → Report
        </div>
      </div>

      <div style={{ width: "100%", maxWidth: 520, textAlign: "left" }}>
        <label style={{ display: "block", fontSize: 10, color: "#444", marginBottom: 6, fontWeight: 600, letterSpacing: "0.05em" }}>
          PROJECT DESCRIPTION (optional)
        </label>
        <textarea
          value={userInput}
          onChange={e => setUserInput(e.target.value)}
          placeholder="Describe what you want to build... or leave blank for auto-generated ideas"
          style={{
            width: "100%", minHeight: 80, padding: 10, borderRadius: 6,
            background: "#0a0a0a", border: "1px solid #1e1e1e", color: "#b0a898",
            fontFamily: "inherit", fontSize: 11, resize: "vertical",
            outline: "none", lineHeight: 1.6,
          }}
        />

        <div style={{ display: "flex", gap: 8, marginTop: 12 }}>
          {["quick", "deep"].map(m => (
            <button
              key={m}
              onClick={() => setMode(m)}
              style={{
                flex: 1, padding: "7px 0",
                background: mode === m ? "#f59e0b18" : "transparent",
                border: `1px solid ${mode === m ? "#f59e0b66" : "#1e1e1e"}`,
                borderRadius: 6, cursor: "pointer", fontFamily: "inherit",
                fontSize: 10, fontWeight: 700, letterSpacing: "0.08em",
                color: mode === m ? "#f59e0b" : "#333",
                transition: "all 0.15s",
              }}
            >
              {m === "quick" ? "⚡ QUICK" : "🔬 DEEP"}
              <span style={{ display: "block", fontSize: 8.5, fontWeight: 400, marginTop: 2, color: mode === m ? "#f59e0b88" : "#222" }}>
                {m === "quick" ? "~10-15 min · MVP" : "~30-40 min · Production"}
              </span>
            </button>
          ))}
        </div>
      </div>

      <button
        onClick={onLaunch}
        style={{
          padding: "12px 44px", background: "#f59e0b", color: "#000",
          border: "none", borderRadius: 8, fontSize: 12, fontWeight: 800,
          letterSpacing: "0.12em", cursor: "pointer", fontFamily: "inherit",
        }}
      >
        ▶ LAUNCH
      </button>
    </div>
  );
}

function OutputStage({ activePh, activeSt, isMobile, projectReady, sessionId }) {
  const BACKEND = import.meta.env.VITE_BACKEND_URL || "http://localhost:8080/api";

  return (
    <div style={{ maxWidth: 860, animation: "fadeUp 0.25s ease" }}>

      {/* ── Project Ready Banner ── */}
      {projectReady && (
        <div style={{
          marginBottom: 20, padding: "14px 16px",
          background: "#05532222", border: "1px solid #4ade8055",
          borderRadius: 8, display: "flex", alignItems: "center",
          justifyContent: "space-between", gap: 12, flexWrap: "wrap",
        }}>
          <div>
            <div style={{ fontSize: 11, fontWeight: 700, color: "#4ade80", marginBottom: 3 }}>
              ✓ PROJECT COMPLETE — READY TO DOWNLOAD
            </div>
            <div style={{ fontSize: 10, color: "#4ade8077" }}>
              Full source · CI/CD · Docs · Git history · Makefile · .env.example
            </div>
          </div>
          <div style={{ display: "flex", gap: 8 }}>
            <a
              href={`${BACKEND}/sessions/${sessionId}/download`}
              download
              style={{
                padding: "8px 18px", background: "#4ade80", color: "#000",
                borderRadius: 6, fontSize: 10, fontWeight: 800,
                letterSpacing: "0.08em", textDecoration: "none", display: "inline-block",
              }}
            >
              ↓ DOWNLOAD ZIP
            </a>
            <a
              href={`${BACKEND}/sessions/${sessionId}/git`}
              target="_blank"
              rel="noreferrer"
              style={{
                padding: "8px 14px", background: "transparent",
                color: "#4ade80", border: "1px solid #4ade8033",
                borderRadius: 6, fontSize: 10, fontWeight: 700,
                letterSpacing: "0.08em", textDecoration: "none", display: "inline-block",
              }}
            >
              ⎇ GIT LOG
            </a>
          </div>
        </div>
      )}

      {/* ── Phase Header ── */}
      {activePh && (
        <div style={{
          marginBottom: 18, padding: "11px 16px", background: "#0a0a0a",
          borderRadius: 8, borderLeft: `3px solid ${activePh.color}`,
          border: `1px solid ${activePh.color}18`, borderLeftWidth: 3,
        }}>
          <div style={{ fontSize: 9.5, color: activePh.color, letterSpacing: "0.12em", marginBottom: 3 }}>
            PHASE {activePh.id} OF 11 · {activePh.tag}
            {activeSt?.retries > 0 && (
              <span style={{ marginLeft: 10, color: "#f87171" }}>
                · RETRY {activeSt.retries}/{import.meta.env.VITE_MAX_RETRIES || 3}
              </span>
            )}
          </div>
          <div style={{ fontSize: isMobile ? 13 : 15, color: "#e0d4b8", fontWeight: 700 }}>
            {activePh.name}
          </div>
          {activeSt?.provider && activeSt?.status === "complete" && (
            <div style={{ fontSize: 9, color: "#333", marginTop: 4, letterSpacing: "0.04em" }}>
              via {activeSt.provider} · {activeSt.model}
            </div>
          )}
        </div>
      )}

      {/* ── Streaming placeholder ── */}
      {activeSt?.status === "running" && !activeSt?.output && (
        <div style={{ display: "flex", alignItems: "center", gap: 8, color: activePh?.color }}>
          <Blink color={activePh?.color || "#f59e0b"} />
          <span style={{ fontSize: 11 }}>Connecting to {activePh?.name}…</span>
        </div>
      )}

      {/* ── Output text (markdown rendered) ── */}
      {activeSt?.output ? (
        <MarkdownRenderer
          text={activeSt.output}
          streaming={activeSt.status === "running"}
        />
      ) : activeSt?.status === "pending" ? (
        <div style={{ display: "flex", alignItems: "center", gap: 8, color: "#555", fontSize: 11 }}>
          <span>⏳</span> Waiting for next phase…
        </div>
      ) : null}
    </div>
  );
}

export default function ContentArea({
  inputPhase, activePh, activeSt,
  userInput, setUserInput, mode, setMode,
  onLaunch, isMobile, outRef,
  projectReady, sessionId,
}) {
  return (
    <div
      ref={outRef}
      style={{ flex: 1, overflowY: "auto", padding: isMobile ? "14px 14px" : "22px 28px" }}
    >
      {inputPhase ? (
        <InputStage
          userInput={userInput} setUserInput={setUserInput}
          mode={mode} setMode={setMode}
          onLaunch={onLaunch} isMobile={isMobile}
        />
      ) : (
        <OutputStage
          activePh={activePh} activeSt={activeSt}
          isMobile={isMobile}
          projectReady={projectReady}
          sessionId={sessionId}
        />
      )}
    </div>
  );
}
