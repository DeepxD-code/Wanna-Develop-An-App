package com.aiarchitect.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name = "phase_results")
public class PhaseResultEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "session_id", length = 36) private String sessionId;
    @Column(name = "phase_id")  private int phaseId;
    @Column(name = "status", length = 20) private String status;
    @Column(name = "output", columnDefinition = "TEXT") private String output;
    @Column(name = "provider", length = 50) private String provider;
    @Column(name = "model", length = 100) private String model;
    @Column(name = "confidence") private Double confidence;
    @Column(name = "latency_ms") private Long latencyMs;
    @Column(name = "token_count") private Integer tokenCount;
    @Column(name = "retries") private int retries;
    @Column(name = "error_message", columnDefinition = "TEXT") private String errorMessage;
    @Column(name = "started_at")   private Instant startedAt;
    @Column(name = "completed_at") private Instant completedAt;

    public Long    getId()           { return id; }
    public String  getSessionId()    { return sessionId; }
    public int     getPhaseId()      { return phaseId; }
    public String  getStatus()       { return status; }
    public String  getOutput()       { return output; }
    public String  getProvider()     { return provider; }
    public String  getModel()        { return model; }
    public Double  getConfidence()   { return confidence; }
    public Long    getLatencyMs()    { return latencyMs; }
    public Integer getTokenCount()   { return tokenCount; }
    public int     getRetries()      { return retries; }
    public String  getErrorMessage() { return errorMessage; }
    public Instant getStartedAt()    { return startedAt; }
    public Instant getCompletedAt()  { return completedAt; }

    public void setSessionId(String v)    { sessionId = v; }
    public void setPhaseId(int v)         { phaseId = v; }
    public void setStatus(String v)       { status = v; }
    public void setOutput(String v)       { output = v; }
    public void setProvider(String v)     { provider = v; }
    public void setModel(String v)        { model = v; }
    public void setConfidence(Double v)   { confidence = v; }
    public void setLatencyMs(Long v)      { latencyMs = v; }
    public void setTokenCount(Integer v)  { tokenCount = v; }
    public void setRetries(int v)         { retries = v; }
    public void setErrorMessage(String v) { errorMessage = v; }
    public void setStartedAt(Instant v)   { startedAt = v; }
    public void setCompletedAt(Instant v) { completedAt = v; }

    public static Builder builder() { return new Builder(); }
    public static class Builder {
        private final PhaseResultEntity e = new PhaseResultEntity();
        public Builder sessionId(String v) { e.sessionId = v; return this; }
        public Builder phaseId(int v)      { e.phaseId = v;   return this; }
        public Builder status(String v)    { e.status = v;    return this; }
        public PhaseResultEntity build()   { return e; }
    }
}

