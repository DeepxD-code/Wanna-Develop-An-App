// ============================================================
// SIDEBAR COMPONENT
// Exact visual clone of original — phase nav + progress + launch
// ============================================================

import { PHASES } from "../../config/phases.js";

function Blink({ color, size = 6 }) {
  return (
    <span style={{
      display: "inline-block", width: size, height: size, borderRadius: "50%",
      background: color, flexShrink: 0, animation: "bl 1s ease-in-out infinite",
    }} />
  );
}

export default function Sidebar({
  phaseStates,
  activePhase,
  setActivePhase,
  running,
  launched,
  completedCount,
  currentProvider,
  currentModel,
  onLaunch,
  onStop,
  onReplay,
  onClose,        // mobile only
  isMobile,
}) {
  const total = PHASES.length;

  return (
    <div style={{ display: "flex", flexDirection: "column", height: "100%", background: "#080808" }}>

      {/* ── Header ── */}
      <div style={{ padding: "16px 14px 12px", borderBottom: "1px solid #171717" }}>
        <div style={{ display: "flex", alignItems: "center", gap: 9, marginBottom: launched ? 10 : 0 }}>
          <div style={{
            width: 30, height: 30, borderRadius: 7, background: "#f59e0b",
            display: "flex", alignItems: "center", justifyContent: "center",
            fontWeight: 900, color: "#000", fontSize: 15, flexShrink: 0,
          }}>◈</div>
          <div style={{ flex: 1, minWidth: 0 }}>
            <div style={{ fontSize: 12.5, fontWeight: 700, color: "#f0e6cc" }}>AI Architect</div>
            <div style={{ fontSize: 9.5, color: "#2a2a2a", letterSpacing: "0.06em" }}>
              {total}-PHASE BUILDER
            </div>
          </div>
          {isMobile && (
            <button onClick={onClose} style={{
              background: "none", border: "none", color: "#444", fontSize: 18, cursor: "pointer",
            }}>✕</button>
          )}
        </div>

        {/* Progress bar */}
        {launched && (
          <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
            <div style={{ flex: 1, height: 3, background: "#151515", borderRadius: 2, overflow: "hidden" }}>
              <div style={{
                width: `${(completedCount / total) * 100}%`, height: "100%",
                background: "#f59e0b", borderRadius: 2, transition: "width 0.4s",
              }} />
            </div>
            <span style={{ fontSize: 10, color: "#7a6a40" }}>{completedCount}/{total}</span>
          </div>
        )}

        {/* Active provider pill */}
        {running && currentProvider && (
          <div style={{
            marginTop: 8, padding: "3px 8px", borderRadius: 4,
            background: "#f59e0b11", border: "1px solid #f59e0b22",
            fontSize: 9, color: "#f59e0b88", letterSpacing: "0.05em",
            whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis",
          }}>
            ⚡ {currentProvider} · {currentModel}
          </div>
        )}
      </div>

      {/* ── Phase List ── */}
      <div style={{ flex: 1, overflowY: "auto", padding: 6 }}>
        {PHASES.map(ph => {
          const st = phaseStates[ph.id];
          const isAct = activePhase === ph.id;
          const accessible = st?.status !== "pending";

          return (
            <div
              key={ph.id}
              onClick={() => { if (accessible) { setActivePhase(ph.id); if (isMobile) onClose?.(); } }}
              style={{
                padding: "8px 10px", marginBottom: 2, borderRadius: 7,
                cursor: accessible ? "pointer" : "default",
                background: isAct ? "#111" : "transparent",
                border: `1px solid ${isAct ? ph.color + "30" : "transparent"}`,
                transition: "all 0.12s",
              }}
            >
              <div style={{ display: "flex", alignItems: "center", gap: 8 }}>

                {/* Status circle */}
                <div style={{
                  width: 24, height: 24, borderRadius: "50%", flexShrink: 0,
                  display: "flex", alignItems: "center", justifyContent: "center",
                  fontSize: st?.status === "complete" ? 11 : 10, fontWeight: 700,
                  background:
                    st?.status === "complete" ? "#05532244" :
                    st?.status === "running"  ? ph.color + "22" :
                    st?.status === "error"    ? "#7f1d1d33" : "#111",
                  color:
                    st?.status === "complete" ? "#4ade80" :
                    st?.status === "running"  ? ph.color :
                    st?.status === "error"    ? "#f87171" : "#2a2a2a",
                  border: `1px solid ${
                    st?.status === "complete" ? "#065f46" :
                    st?.status === "running"  ? ph.color + "66" :
                    st?.status === "error"    ? "#7f1d1d" : "#1c1c1c"
                  }`,
                }}>
                  {st?.status === "complete" ? "✓" :
                   st?.status === "running"  ? <Blink color={ph.color} size={6} /> :
                   st?.status === "error"    ? "✗" : ph.id}
                </div>

                {/* Phase name */}
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{
                    fontSize: 9.5, fontWeight: 700, letterSpacing: "0.09em",
                    color: accessible ? ph.color : "#252525",
                  }}>{ph.tag}</div>
                  <div style={{
                    fontSize: 11.5, color: accessible ? "#7a7060" : "#222",
                    whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis",
                  }}>{ph.name}</div>
                </div>

                {/* Retry badge */}
                {st?.retries > 0 && (
                  <div style={{
                    fontSize: 8, padding: "1px 5px", borderRadius: 3,
                    background: "#7f1d1d33", color: "#f87171", flexShrink: 0,
                  }}>{st.retries}×</div>
                )}

                {/* Replay button — shown on hover for completed phases */}
                {st?.status === "complete" && onReplay && !running && (
                  <button
                    onClick={(e) => { e.stopPropagation(); onReplay(ph.id); }}
                    title="Replay this phase"
                    style={{
                      background: "none", border: "1px solid #f59e0b33",
                      borderRadius: 3, color: "#f59e0b66", fontSize: 9,
                      padding: "1px 5px", cursor: "pointer", flexShrink: 0,
                      fontFamily: "inherit", lineHeight: 1,
                    }}
                    onMouseEnter={e => e.currentTarget.style.color = "#f59e0b"}
                    onMouseLeave={e => e.currentTarget.style.color = "#f59e0b66"}
                  >↺</button>
                )}
              </div>
            </div>
          );
        })}
      </div>

      {/* ── Launch Button ── */}
      <div style={{ padding: "10px 10px 14px", borderTop: "1px solid #141414" }}>
        <button
          onClick={running ? onStop : onLaunch}
          style={{
            width: "100%", padding: "11px 0",
            background: running ? "#0a0a0a" : "#f59e0b",
            color: running ? "#f59e0b" : "#000",
            border: running ? "1px solid #f59e0b33" : "none",
            borderRadius: 8, fontSize: 11, fontWeight: 800, letterSpacing: "0.1em",
            cursor: "pointer", fontFamily: "inherit", transition: "all 0.2s",
          }}
        >
          {running
            ? `■ STOP (P${activePhase}/${total})`
            : launched ? "↺ RESTART" : "▶ LAUNCH"}
        </button>
      </div>
    </div>
  );
}
