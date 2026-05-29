// ============================================================
// TOPBAR COMPONENT
// Phase badge, name, streaming status, stats toggle
// ============================================================

function Blink({ color, size = 6 }) {
  return (
    <span style={{
      display: "inline-block", width: size, height: size, borderRadius: "50%",
      background: color, flexShrink: 0, animation: "bl 1s ease-in-out infinite",
    }} />
  );
}

export default function TopBar({
  activePh,
  activeSt,
  running,
  launched,
  completedCount,
  total,
  showStats,
  onToggleStats,
  onToggleHistory,
  onOpenDrawer,
  isMobile,
}) {
  return (
    <div style={{
      padding: isMobile ? "9px 12px" : "9px 22px",
      borderBottom: "1px solid #131313", background: "#060606",
      flexShrink: 0, display: "flex", alignItems: "center", gap: 10,
      minHeight: 44,
    }}>

      {/* Mobile hamburger */}
      {isMobile && (
        <button onClick={onOpenDrawer} style={{
          background: "none", border: "1px solid #1e1e1e", borderRadius: 6,
          color: "#555", fontSize: 14, padding: "4px 9px", cursor: "pointer",
          flexShrink: 0,
        }}>☰</button>
      )}

      {/* Phase badge + name */}
      {activePh && (
        <>
          <div style={{
            padding: "3px 9px", borderRadius: 4, flexShrink: 0,
            background: activePh.color + "18", border: `1px solid ${activePh.color}30`,
            fontSize: 9.5, fontWeight: 700, letterSpacing: "0.1em", color: activePh.color,
          }}>P{activePh.id}</div>

          <span style={{
            fontSize: isMobile ? 12 : 13.5, color: "#d4c4a0", fontWeight: 600,
            whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis",
          }}>{activePh.name}</span>
        </>
      )}

      {/* Right side: status + toggles */}
      <div style={{ marginLeft: "auto", display: "flex", alignItems: "center", gap: 10, flexShrink: 0 }}>

        {/* Streaming indicator */}
        {activeSt?.status === "running" && (
          <>
            <Blink color={activePh?.color || "#f59e0b"} size={6} />
            <span style={{ fontSize: 9.5, color: activePh?.color, letterSpacing: "0.07em" }}>
              STREAMING
            </span>
          </>
        )}

        {activeSt?.status === "complete" && (
          <span style={{ fontSize: 9.5, color: "#4ade80" }}>✓ DONE</span>
        )}

        {activeSt?.status === "error" && (
          <span style={{ fontSize: 9.5, color: "#f87171" }}>✗ ERROR</span>
        )}

        {/* Phase counter */}
        {launched && !isMobile && (
          <span style={{ fontSize: 9.5, color: "#252525" }}>{completedCount}/{total}</span>
        )}

        {/* History */}
        <button
          onClick={onToggleHistory}
          style={{
            background:"none", border:"1px solid #1e1e1e",
            borderRadius:5, color:"#333", fontSize:10,
            padding:"3px 8px", cursor:"pointer", fontFamily:"inherit",
          }}
        >☰ HIST</button>

        {/* Stats toggle */}
        <button
          onClick={onToggleStats}
          title="Stats for Nerds"
          style={{
            background: showStats ? "#f59e0b22" : "none",
            border: `1px solid ${showStats ? "#f59e0b44" : "#1e1e1e"}`,
            borderRadius: 5, color: showStats ? "#f59e0b" : "#333",
            fontSize: 10, padding: "3px 8px", cursor: "pointer",
            fontFamily: "inherit", letterSpacing: "0.05em",
            transition: "all 0.15s",
          }}
        >
          ◉ STATS
        </button>
      </div>
    </div>
  );
}
