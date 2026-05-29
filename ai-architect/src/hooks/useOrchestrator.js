import { useSessionRecovery } from "./useSessionRecovery.js";
// ============================================================
// useOrchestrator — Core frontend engine
// All AI work delegated to Spring Boot backend via SSE.
// Frontend only manages: UI state, streaming display, metrics.
// ============================================================

import { useState, useRef, useEffect, useCallback } from "react";
import { PHASES } from "../config/phases.js";
import {
  startSession,
  streamPhaseFromBackend,
  replayPhaseFromBackend,
  stopSession,
  metrics,
} from "../utils/api.js";

// ── useIsMobile ──────────────────────────────────────────────
export function useIsMobile() {
  const [mob, setMob] = useState(window.innerWidth < 768);
  useEffect(() => {
    const fn = () => setMob(window.innerWidth < 768);
    window.addEventListener("resize", fn);
    return () => window.removeEventListener("resize", fn);
  }, []);
  return mob;
}

// ── useOrchestrator ──────────────────────────────────────────
export function useOrchestrator() {
  const initStates = () =>
    Object.fromEntries(PHASES.map(p => [p.id, { status: "pending", output: "" }]));

  const [phaseStates, setPhaseStates]         = useState(initStates);
  const [activePhase, setActivePhase]         = useState(1);
  const [running, setRunning]                 = useState(false);
  const [launched, setLaunched]               = useState(false);
  const [inputPhase, setInputPhase]           = useState(true);
  const [userInput, setUserInput]             = useState("");
  const [mode, setMode]                       = useState("deep");
  const [currentProvider, setCurrentProvider] = useState(null);
  const [currentModel, setCurrentModel]       = useState(null);
  const [liveStats, setLiveStats]             = useState(null);
  const [projectReady, setProjectReady]       = useState(false);
  const [confidence, setConfidence]           = useState({});   // phaseId → score
  const [supervisorMsg, setSupervisorMsg]     = useState(null);

  const sessionIdRef = useRef(null);
  const { saveState, clearState } = useSessionRecovery();
  const abortRef     = useRef(false);
  const abortCtrlRef = useRef(null);

  const PHASE_DELAY = parseInt(import.meta.env.VITE_PHASE_DELAY_MS || "2000");

  // ── run one phase via backend SSE ───────────────────────────
  const runPhase = useCallback(async (phaseId) => {
    if (abortRef.current) return;

    setPhaseStates(prev => ({
      ...prev,
      [phaseId]: { status: "running", output: "", retries: 0 },
    }));

    abortCtrlRef.current = new AbortController();

    await streamPhaseFromBackend(sessionIdRef.current, phaseId, {
      signal: abortCtrlRef.current.signal,

      onDelta: (_, full) => {
        if (abortRef.current) return;
        setPhaseStates(prev => ({
          ...prev,
          [phaseId]: { ...prev[phaseId], status: "running", output: full },
        }));
      },

      onProviderSwitch: (provider, model) => {
        setCurrentProvider(provider);
        setCurrentModel(model);
        setLiveStats(metrics.getStats());
      },

      onRetry: (attempt) => {
        setPhaseStates(prev => ({
          ...prev,
          [phaseId]: { ...prev[phaseId], retries: attempt },
        }));
      },

      onDone: (event) => {
        if (event.type === "project_ready") {
          setProjectReady(true);
          return;
        }
        if (event.type === "confidence") {
          setConfidence(prev => ({ ...prev, [event.phaseId]: event.score }));
          return;
        }
        if (event.type === "supervisor") {
          setSupervisorMsg(event.message);
          return;
        }
        if (event.type === "clear") {
          setPhaseStates(prev => ({ ...prev, [event.phaseId]: { ...prev[event.phaseId], output: "" } }));
          return;
        }
        setPhaseStates(prev => ({
          ...prev,
          [phaseId]: {
            ...prev[phaseId],
            status:   "complete",
            provider: event.provider,
            model:    event.model,
            tokens:   event.tokens,
            latency:  event.latency,
          },
        }));
        setLiveStats(metrics.getStats());
      },

      onError: (message) => {
        setPhaseStates(prev => ({
          ...prev,
          [phaseId]: {
            ...prev[phaseId],
            status: "error",
            output: `❌ Phase ${phaseId} failed\n\n${message}\n\nCheck backend logs for details.`,
          },
        }));
      },
    });
  }, []);

  // ── launch full pipeline ─────────────────────────────────────
  const launch = useCallback(async () => {
    if (running) return;

    setRunning(true);
    setLaunched(true);
    setInputPhase(false);
    abortRef.current = false;
    setPhaseStates(initStates());
    setActivePhase(1);
    setCurrentProvider(null);
    setCurrentModel(null);

    // Create session on backend first
    try {
      const session = await startSession(userInput, mode);
      sessionIdRef.current = session.sessionId;
      saveState(session.sessionId, 1, userInput, mode);
    } catch (err) {
      setPhaseStates(prev => ({
        ...prev,
        1: {
          status: "error",
          output: `❌ Cannot connect to backend.\n\n${err.message}\n\nMake sure Spring Boot backend is running:\n  cd ai-architect-backend\n  ./mvnw spring-boot:run`,
        },
      }));
      setRunning(false);
      return;
    }

    // Run all phases sequentially
    for (const ph of PHASES) {
      saveState(sessionIdRef.current, ph.id, userInput, mode);
      if (abortRef.current) break;
      setActivePhase(ph.id);

      try {
        await runPhase(ph.id);
      } catch (err) {
        if (!abortRef.current) {
          console.error(`Pipeline stopped at phase ${ph.id}:`, err.message);
          setPhaseStates(prev => ({
            ...prev,
            [ph.id]: {
              ...prev[ph.id],
              status: "error",
              output: `❌ Phase ${ph.id} failed\n\n${err.message}\n\nCheck backend logs for details.`,
            },
          }));
        }
        break;
      }

      // Delay between phases to avoid rate limits
      if (!abortRef.current) {
        await new Promise(r => setTimeout(r, PHASE_DELAY));
      }
    }

    setRunning(false);
    setCurrentProvider(null);
    setCurrentModel(null);
    clearState();
  }, [running, userInput, mode, runPhase, PHASE_DELAY]);



  // ── resume crashed session ────────────────────────────────────
  const resumeSession = useCallback(async (sessionId, fromPhase, savedUserInput, savedMode) => {
    if (running) return;

    setRunning(true);
    setLaunched(true);
    setInputPhase(false);
    abortRef.current = false;
    sessionIdRef.current = sessionId;

    if (savedUserInput) setUserInput(savedUserInput);
    if (savedMode)      setMode(savedMode);

    // Mark all phases before fromPhase as complete (they already ran)
    setPhaseStates(prev => {
      const next = { ...prev };
      for (let i = 1; i < fromPhase; i++) {
        if (next[i].status === "pending") {
          next[i] = { status: "complete", output: "[Recovered — output stored in session]" };
        }
      }
      // Reset fromPhase onwards
      for (let i = fromPhase; i <= 11; i++) {
        next[i] = { status: "pending", output: "" };
      }
      return next;
    });

    setActivePhase(fromPhase);

    // Run remaining phases
    for (let phaseId = fromPhase; phaseId <= 11; phaseId++) {
      if (abortRef.current) break;
      setActivePhase(phaseId);
      try {
        await runPhase(phaseId);
      } catch (err) {
        console.error(`Resume: phase ${phaseId} failed:`, err.message);
        break;
      }
      if (!abortRef.current) {
        await new Promise(r => setTimeout(r, parseInt(import.meta.env.VITE_PHASE_DELAY_MS || "2000")));
      }
    }

    setRunning(false);
    setCurrentProvider(null);
    setCurrentModel(null);
  }, [running, runPhase]);

  // ── replay single phase via /replay endpoint ─────────────────
  const replayPhase = useCallback(async (phaseId) => {
    if (running || !sessionIdRef.current) return;

    setRunning(true);
    setActivePhase(phaseId);
    abortRef.current = false;
    abortCtrlRef.current = new AbortController();

    // Reset only this phase, preserve all others
    setPhaseStates(prev => ({
      ...prev,
      [phaseId]: { status: "running", output: "", retries: 0 },
    }));

    try {
      await replayPhaseFromBackend(sessionIdRef.current, phaseId, {
        signal: abortCtrlRef.current.signal,
        onDelta: (_, full) => {
          if (abortRef.current) return;
          setPhaseStates(prev => ({ ...prev, [phaseId]: { ...prev[phaseId], output: full } }));
        },
        onProviderSwitch: (prov, model) => { setCurrentProvider(prov); setCurrentModel(model); },
        onRetry:  (a) => setPhaseStates(prev => ({ ...prev, [phaseId]: { ...prev[phaseId], retries: a } })),
        onDone:   (e) => setPhaseStates(prev => ({ ...prev, [phaseId]: { ...prev[phaseId], status: "complete", provider: e.provider, model: e.model } })),
        onError:  (msg) => setPhaseStates(prev => ({ ...prev, [phaseId]: { ...prev[phaseId], status: "error", output: `Replay failed: ${msg}` } })),
      });
    } catch (err) {
      console.error(`Phase ${phaseId} replay failed:`, err.message);
    }

    setRunning(false);
    setCurrentProvider(null);
    setCurrentModel(null);
  }, [running]);

  // ── stop ─────────────────────────────────────────────────────
  const stop = useCallback(() => {
    abortRef.current = true;
    abortCtrlRef.current?.abort();
    stopSession(sessionIdRef.current);
    setRunning(false);
    setCurrentProvider(null);
    setCurrentModel(null);
  }, []);

  // ── restart ──────────────────────────────────────────────────
  const restart = useCallback(() => {
    stop();
    setTimeout(() => {
      sessionIdRef.current = null;
      abortRef.current     = false;
      setLaunched(false);
      setInputPhase(true);
      setActivePhase(1);
      setPhaseStates(initStates());
      setLiveStats(null);
      setProjectReady(false);
      clearState();
    }, 300);
  }, [stop]);

  const completedCount = Object.values(phaseStates)
    .filter(s => s.status === "complete").length;

  return {
    phaseStates,
    activePhase, setActivePhase,
    running, launched, inputPhase,
    userInput, setUserInput,
    mode, setMode,
    currentProvider, currentModel,
    liveStats,
    completedCount,
    projectReady,
    confidence,
    supervisorMsg,
    sessionId: sessionIdRef.current,
    launch, stop, restart, replayPhase, resumeSession,
  };
}
