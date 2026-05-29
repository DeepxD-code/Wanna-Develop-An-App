package com.aiarchitect.model;

import java.time.Instant;
import java.util.Map;

// ─────────────────────────────────────────────────────────────
// SESSION (in-memory model, not JPA entity)
// ─────────────────────────────────────────────────────────────
public class Session {
    private String        sessionId;
    private String        userInput;
    private String        mode;
    private SessionStatus status;
    private int           currentPhase;
    private Map<Integer, PhaseResult> phaseResults;
    private String        contextBuffer;
    private Instant       createdAt;
    private Instant       updatedAt;

    public enum SessionStatus { PENDING, RUNNING, COMPLETE, STOPPED, ERROR }

    public String        getSessionId()    { return sessionId; }
    public String        getUserInput()    { return userInput; }
    public String        getMode()         { return mode; }
    public SessionStatus getStatus()       { return status; }
    public int           getCurrentPhase() { return currentPhase; }
    public String        getContextBuffer(){ return contextBuffer; }
    public Instant       getCreatedAt()    { return createdAt; }
    public Instant       getUpdatedAt()    { return updatedAt; }

    public void setSessionId(String v)        { sessionId = v; }
    public void setUserInput(String v)        { userInput = v; }
    public void setMode(String v)             { mode = v; }
    public void setStatus(SessionStatus v)    { status = v; }
    public void setCurrentPhase(int v)        { currentPhase = v; }
    public void setContextBuffer(String v)    { contextBuffer = v; }
    public void setCreatedAt(Instant v)       { createdAt = v; }
    public void setUpdatedAt(Instant v)       { updatedAt = v; }
}

// ─────────────────────────────────────────────────────────────
// PHASE RESULT
// ─────────────────────────────────────────────────────────────
class PhaseResult {
    private int    phaseId;
    private String status;
    private String output;
    private String provider;
    private String model;
    private int    retries;

    public enum PhaseStatus { PENDING, RUNNING, COMPLETE, ERROR }

    public int    getPhaseId()  { return phaseId; }
    public String getStatus()   { return status; }
    public String getOutput()   { return output; }
    public String getProvider() { return provider; }
    public String getModel()    { return model; }
    public int    getRetries()  { return retries; }

    public void setPhaseId(int v)    { phaseId = v; }
    public void setStatus(String v)  { status = v; }
    public void setOutput(String v)  { output = v; }
    public void setProvider(String v){ provider = v; }
    public void setModel(String v)   { model = v; }
    public void setRetries(int v)    { retries = v; }
}
