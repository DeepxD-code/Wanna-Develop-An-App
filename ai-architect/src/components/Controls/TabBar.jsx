// ============================================================
// MOBILE TAB BAR
// Phase tabs for mobile navigation
// ============================================================

import { PHASES } from "../../config/phases.js";

export default function TabBar({ phaseStates, activePhase, setActivePhase }) {
  return (
    <div style={{
      display: "flex", overflowX: "auto", background: "#080808",
      borderTop: "1px solid #171717", padding: "6px 4px", gap: 2,
      flexShrink: 0, scrollbarWidth: "none",
    }}>
      {PHASES.map(ph => {
        const st = phaseStates[ph.id];
        const accessible = st?.status !== "pending";
        return (
          <button
            key={ph.id}
            onClick={() => accessible && setActivePhase(ph.id)}
            style={{
              flexShrink: 0, padding: "5px 8px",
              background: activePhase === ph.id ? "#111" : "transparent",
              border: `1px solid ${activePhase === ph.id ? ph.color + "40" : "transparent"}`,
              borderRadius: 6, cursor: accessible ? "pointer" : "default",
              display: "flex", flexDirection: "column", alignItems: "center", gap: 2,
              minWidth: 46,
            }}
          >
            <span style={{
              fontSize: 12,
              color: st?.status === "complete" ? "#4ade80" :
                     st?.status === "running"  ? ph.color :
                     st?.status === "error"    ? "#f87171" : "#222",
            }}>
              {st?.status === "complete" ? "✓" :
               st?.status === "running"  ? "●" :
               st?.status === "error"    ? "✗" : ph.id}
            </span>
            <span style={{
              fontSize: 8, letterSpacing: "0.04em",
              color: activePhase === ph.id ? ph.color : "#2a2a2a",
            }}>
              {ph.tag.slice(0, 4)}
            </span>
          </button>
        );
      })}
    </div>
  );
}
