// ============================================================
// MAIN APP
// Assembles all components, wires up orchestrator
// ============================================================

import { useRef, useState, useEffect } from "react";
import { PHASES } from "./config/phases.js";
import { useOrchestrator, useIsMobile } from "./hooks/useOrchestrator.js";
import Sidebar     from "./components/Sidebar/Sidebar.jsx";
import TopBar      from "./components/TopBar/TopBar.jsx";
import ContentArea from "./components/Content/ContentArea.jsx";
import StatsPanel  from "./components/Metrics/StatsPanel.jsx";
import TabBar         from "./components/Controls/TabBar.jsx";
import RecoveryBanner  from "./components/Controls/RecoveryBanner.jsx";
import BackendStatus   from "./components/Controls/BackendStatus.jsx";
import HistoryPanel    from "./components/Controls/HistoryPanel.jsx";
import { useSessionRecovery } from "./hooks/useSessionRecovery.js";
import { useAuth }            from "./hooks/useAuth.js";
import { setAuthToken }       from "./utils/api.js";

export default function App() {
  const isMobile   = useIsMobile();
  const { token, authReady } = useAuth();
  const { recoverable, dismissRecovery } = useSessionRecovery();
  const [showHistory, setShowHistory] = useState(false);

  // Keep API client in sync with auth token
  useEffect(() => { setAuthToken(token); }, [token]);
  const outRef     = useRef(null);
  const [drawer, setDrawer]       = useState(false);
  const [showStats, setShowStats] = useState(false);

  const {
    phaseStates,
    activePhase, setActivePhase,
    running, launched, inputPhase,
    userInput, setUserInput,
    mode, setMode,
    currentProvider, currentModel,
    liveStats,
    completedCount, projectReady, sessionId, confidence, supervisorMsg,
    launch, stop, restart, replayPhase, resumeSession,
  } = useOrchestrator();

  // Scroll output on update
  const activePh = PHASES.find(p => p.id === activePhase);
  const activeSt = phaseStates[activePhase];

  const handleLaunch  = () => inputPhase ? launch() : restart();
  const handleStop    = () => stop();

  return (
    <div style={{
      display: "flex",
      flexDirection: isMobile ? "column" : "row",
      height: "100vh",
      background: "#050505",
      color: "#c8c0b0",
      fontFamily: "'JetBrains Mono','Fira Code',monospace",
      overflow: "hidden",
      position: "relative",
    }}>
      <style>{`
        @keyframes bl { 0%,100%{opacity:1} 50%{opacity:0.15} }
        @keyframes fadeUp { from{opacity:0;transform:translateY(5px)} to{opacity:1;transform:translateY(0)} }
        ::-webkit-scrollbar { width:3px; height:3px }
        ::-webkit-scrollbar-track { background:#080808 }
        ::-webkit-scrollbar-thumb { background:#1e1e1e; border-radius:2px }
        textarea:focus { outline:none; border-color:#f59e0b44 !important; }
        button:active { opacity:0.75; }
      `}</style>

      <RecoveryBanner
        recoverable={recoverable}
        onResume={() => {
          if (recoverable) {
            resumeSession(
              recoverable.sessionId,
              recoverable.phase || 1,
              recoverable.userInput,
              recoverable.mode
            );
          }
          dismissRecovery();
        }}
        onDismiss={dismissRecovery}
      />

      {/* ── Desktop Sidebar ── */}
      {!isMobile && (
        <div style={{ width: 236, flexShrink: 0, borderRight: "1px solid #131313", overflow: "hidden" }}>
          <Sidebar
            phaseStates={phaseStates}
            activePhase={activePhase}
            setActivePhase={setActivePhase}
            running={running}
            launched={launched}
            completedCount={completedCount}
            currentProvider={currentProvider}
            currentModel={currentModel}
            onLaunch={handleLaunch}
            onStop={handleStop}
            onReplay={replayPhase}
            isMobile={false}
          />
        </div>
      )}

      {/* ── Mobile Drawer ── */}
      {isMobile && drawer && (
        <>
          <div
            onClick={() => setDrawer(false)}
            style={{ position: "fixed", inset: 0, background: "#000c", zIndex: 40 }}
          />
          <div style={{
            position: "fixed", top: 0, left: 0, bottom: 0, width: 256,
            zIndex: 50, boxShadow: "6px 0 30px #0008",
          }}>
            <Sidebar
              phaseStates={phaseStates}
              activePhase={activePhase}
              setActivePhase={setActivePhase}
              running={running}
              launched={launched}
              completedCount={completedCount}
              currentProvider={currentProvider}
              currentModel={currentModel}
              onLaunch={handleLaunch}
              onStop={handleStop}
              onClose={() => setDrawer(false)}
              isMobile={true}
            />
          </div>
        </>
      )}

      {/* ── Main Area ── */}
      <div style={{ flex: 1, display: "flex", flexDirection: "column", overflow: "hidden", position: "relative" }}>

        {/* Top Bar */}
        <TopBar
          activePh={activePh}
          activeSt={activeSt}
          running={running}
          launched={launched}
          completedCount={completedCount}
          total={PHASES.length}
          showStats={showStats}
          onToggleStats={() => setShowStats(p => !p)}
          onToggleHistory={() => setShowHistory(p => !p)}
          onOpenDrawer={() => setDrawer(true)}
          isMobile={isMobile}
        />

        {/* Content + Stats overlay */}
        <div style={{ flex: 1, display: "flex", overflow: "hidden", position: "relative" }}>
          <ContentArea
            inputPhase={inputPhase}
            activePh={activePh}
            activeSt={activeSt}
            userInput={userInput}
            setUserInput={setUserInput}
            mode={mode}
            setMode={setMode}
            onLaunch={handleLaunch}
            isMobile={isMobile}
            outRef={outRef}
            projectReady={projectReady}
            sessionId={sessionId}
          />

          {/* Stats for Nerds overlay */}
          {showStats && (
            <StatsPanel
              liveStats={liveStats}
              phaseStates={phaseStates}
              currentProvider={currentProvider}
              currentModel={currentModel}
              running={running}
              confidence={confidence}
              supervisorMsg={supervisorMsg}
            />
          )}
        </div>

        {/* Mobile Tab Bar */}
        {isMobile && launched && (
          <TabBar
            phaseStates={phaseStates}
            activePhase={activePhase}
            setActivePhase={setActivePhase}
          />
        )}

        {/* Footer */}
        {!isMobile && (
          <div style={{
            padding: "5px 24px", borderTop: "1px solid #0d0d0d",
            background: "#040404", display: "flex", alignItems: "center",
            gap: 14, flexShrink: 0,
          }}>
            <BackendStatus />
            <span style={{ fontSize: 9.5, color: "#1a1a1a", marginLeft: 12 }}>
              gemini · streaming · context-chained · multi-provider failover
            </span>
            {running && (
              <span style={{ marginLeft: "auto", fontSize: 9.5, color: "#f59e0b44" }}>● LIVE</span>
            )}
          </div>
        )}
      </div>
      <HistoryPanel visible={showHistory} onClose={() => setShowHistory(false)} onReopen={() => setShowHistory(false)} />
    </div>
  );
}
