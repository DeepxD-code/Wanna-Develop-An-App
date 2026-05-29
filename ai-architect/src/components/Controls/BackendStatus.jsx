// ============================================================
// BackendStatus
// Shows connection status for backend services.
// Polls /health endpoint every 10 seconds.
// ============================================================

import { useState, useEffect } from "react";

const BACKEND = import.meta.env.VITE_BACKEND_URL || "http://localhost:8080/api";

export default function BackendStatus() {
  const [status, setStatus] = useState("checking"); // checking | online | offline
  const [providerCount, setProviderCount] = useState(0);

  useEffect(() => {
    const check = async () => {
      try {
        const [healthRes, providerRes] = await Promise.all([
          fetch(`${BACKEND}/actuator/health`, { signal: AbortSignal.timeout(3000) }),
          fetch(`${BACKEND}/providers/health`, { signal: AbortSignal.timeout(3000) }),
        ]);

        if (healthRes.ok) {
          setStatus("online");
          if (providerRes.ok) {
            const providers = await providerRes.json();
            const active = Object.values(providers).filter(p => p.hasKeys).length;
            setProviderCount(active);
          }
        } else {
          setStatus("offline");
        }
      } catch {
        setStatus("offline");
      }
    };

    check();
    const interval = setInterval(check, 10000);
    return () => clearInterval(interval);
  }, []);

  const color = status === "online"   ? "#4ade80" :
                status === "offline"  ? "#f87171" : "#f59e0b";

  const label = status === "online"   ? `ONLINE · ${providerCount} provider${providerCount !== 1 ? "s" : ""}` :
                status === "offline"  ? "OFFLINE" : "CONNECTING...";

  return (
    <div style={{
      display: "flex", alignItems: "center", gap: 5,
      fontSize: 9, color, letterSpacing: "0.06em",
    }}>
      <span style={{
        display: "inline-block", width: 5, height: 5,
        borderRadius: "50%", background: color, flexShrink: 0,
        animation: status === "checking" ? "bl 1s ease-in-out infinite" : "none",
      }} />
      {label}
    </div>
  );
}
