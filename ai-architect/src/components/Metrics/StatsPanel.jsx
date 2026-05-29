// ============================================================
// STATS FOR NERDS PANEL
// Toggleable overlay — token usage, latency, provider health
// ============================================================

export default function StatsPanel({ liveStats, phaseStates, currentProvider, currentModel, running, confidence, supervisorMsg }) {
  const phases = Object.entries(phaseStates);
  const completed = phases.filter(([, s]) => s.status === "complete");
  const errors = phases.filter(([, s]) => s.status === "error");
  const totalRetries = phases.reduce((sum, [, s]) => sum + (s.retries || 0), 0);

  return (
    <div style={{
      position: "absolute", top: 44, right: 0, bottom: 0,
      width: 280, background: "#060606",
      borderLeft: "1px solid #131313",
      overflowY: "auto", zIndex: 10,
      fontFamily: "'JetBrains Mono','Fira Code',monospace",
      fontSize: 10,
    }}>
      <div style={{ padding: "12px 14px" }}>

        {/* Header */}
        <div style={{
          fontSize: 9, fontWeight: 700, letterSpacing: "0.12em",
          color: "#f59e0b", marginBottom: 14,
        }}>◉ STATS FOR NERDS</div>

        {/* Live Status */}
        <Section title="LIVE STATUS">
          <Row label="Running" value={running ? "YES" : "NO"} color={running ? "#4ade80" : "#555"} />
          <Row label="Provider" value={currentProvider || "—"} color="#f59e0b" />
          <Row label="Model" value={currentModel ? currentModel.split("/").pop() : "—"} color="#f59e0b88" />
        </Section>

        {/* Pipeline Stats */}
        <Section title="PIPELINE">
          <Row label="Phases done" value={`${completed.length} / ${phases.length}`} />
          <Row label="Errors" value={errors.length} color={errors.length > 0 ? "#f87171" : "#555"} />
          <Row label="Total retries" value={totalRetries} color={totalRetries > 0 ? "#fbbf24" : "#555"} />
        </Section>

        {/* Phase Breakdown */}
        <Section title="PHASE BREAKDOWN">
          {phases.map(([id, st]) => (
            <div key={id} style={{
              display: "flex", justifyContent: "space-between",
              padding: "3px 0", borderBottom: "1px solid #111",
            }}>
              <span style={{ color: "#444" }}>P{id}</span>
              <span style={{
                color: st.status === "complete" ? "#4ade80" :
                       st.status === "running"  ? "#f59e0b" :
                       st.status === "error"    ? "#f87171" : "#252525",
              }}>
                {st.status === "complete" ? "✓ done" :
                 st.status === "running"  ? "● live" :
                 st.status === "error"    ? "✗ error" : "· wait"}
                {st.retries > 0 ? ` (${st.retries}x)` : ""}
              </span>
            </div>
          ))}
        </Section>

        {/* Provider Health */}
        {liveStats && Object.keys(liveStats).length > 0 && (
          <Section title="PROVIDER HEALTH">
            {Object.entries(liveStats).map(([prov, stats]) => (
              <div key={prov} style={{ marginBottom: 8 }}>
                <div style={{ color: "#f59e0b88", marginBottom: 3, textTransform: "uppercase", fontSize: 9 }}>
                  {prov}
                </div>
                <Row label="Success" value={stats.success} color="#4ade80" />
                <Row label="Fail" value={stats.fail} color={stats.fail > 0 ? "#f87171" : "#555"} />
                <Row label="Tokens" value={stats.totalTokens.toLocaleString()} />
                {stats.totalLatency > 0 && (
                  <Row label="Avg latency" value={`${Math.round(stats.totalLatency / (stats.success || 1))}ms`} />
                )}
              </div>
            ))}
          </Section>
        )}


        {/* Cost Tracking */}
        <Section title="COST TRACKER">
          {(() => {
            const stats = liveStats || {};
            let totalTokens = 0;
            Object.values(stats).forEach(s => { totalTokens += (s.totalTokens || 0); });
            // Free tier models = $0.00, but show token usage
            const estimatedCost = 0.00; // all free tier
            return (
              <>
                <Row label="Total tokens" value={totalTokens.toLocaleString()} />
                <Row label="Est. cost" value="$0.00" color="#4ade80" />
                <Row label="Tier" value="FREE" color="#4ade80" />
              </>
            );
          })()}
        </Section>

        {/* Supervisor & Confidence */}
        {Object.keys(confidence || {}).length > 0 && (
          <Section title="CONFIDENCE SCORES">
            {Object.entries(confidence).map(([phaseId, score]) => {
              const color = score >= 8 ? "#4ade80" : score >= 6.5 ? "#f59e0b" : "#f87171";
              return (
                <div key={phaseId} style={{ display:"flex", justifyContent:"space-between", padding:"2px 0" }}>
                  <span style={{ color:"#333" }}>P{phaseId}</span>
                  <span style={{ color }}>
                    {score.toFixed(1)}/10 {score >= 8 ? "✓" : score >= 6.5 ? "~" : "✗"}
                  </span>
                </div>
              );
            })}
          </Section>
        )}

        {supervisorMsg && (
          <Section title="SUPERVISOR">
            <div style={{ fontSize: 9, color: "#f59e0b88", lineHeight: 1.5 }}>{supervisorMsg}</div>
          </Section>
        )}

        {/* Config */}
        <Section title="CONFIG">
          <Row label="Max retries" value={import.meta.env.VITE_MAX_RETRIES || "3"} />
          <Row label="Phase delay" value={`${import.meta.env.VITE_PHASE_DELAY_MS || "2000"}ms`} />
          <Row label="Request delay" value={`${import.meta.env.VITE_REQUEST_DELAY_MS || "1500"}ms`} />
        </Section>

        {/* API Key Status */}
        <Section title="API KEYS">
          {[
            ["GEMINI",     import.meta.env.VITE_GEMINI_KEY_1],
            ["OPENROUTER", import.meta.env.VITE_OPENROUTER_KEY_1],
            ["GROQ",       import.meta.env.VITE_GROQ_KEY_1],
            ["TOGETHER",   import.meta.env.VITE_TOGETHER_KEY_1],
            ["CEREBRAS",   import.meta.env.VITE_CEREBRAS_KEY_1],
          ].map(([name, key]) => (
            <Row
              key={name}
              label={name}
              value={!key || key.startsWith("YOUR_") ? "NOT SET" : "✓ SET"}
              color={!key || key.startsWith("YOUR_") ? "#555" : "#4ade80"}
            />
          ))}
        </Section>

      </div>
    </div>
  );
}

function Section({ title, children }) {
  return (
    <div style={{ marginBottom: 16 }}>
      <div style={{
        fontSize: 8.5, letterSpacing: "0.1em", color: "#252525",
        fontWeight: 700, marginBottom: 6, paddingBottom: 4,
        borderBottom: "1px solid #111",
      }}>{title}</div>
      {children}
    </div>
  );
}

function Row({ label, value, color }) {
  return (
    <div style={{ display: "flex", justifyContent: "space-between", padding: "2px 0" }}>
      <span style={{ color: "#333" }}>{label}</span>
      <span style={{ color: color || "#666" }}>{value}</span>
    </div>
  );
}
