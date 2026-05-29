package com.aiarchitect.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name = "provider_metrics")
public class ProviderMetricEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "provider", length = 50) private String  provider;
    @Column(name = "model",    length = 100) private String model;
    @Column(name = "success")  private boolean success;
    @Column(name = "tokens")   private Integer tokens;
    @Column(name = "latency_ms") private Long latencyMs;
    @Column(name = "phase_id")   private Integer phaseId;
    @Column(name = "session_id", length = 36) private String sessionId;
    @Column(name = "recorded_at") private Instant recordedAt;

    @PrePersist void prePersist() { if (recordedAt == null) recordedAt = Instant.now(); }

    public Long    getId()         { return id; }
    public String  getProvider()   { return provider; }
    public String  getModel()      { return model; }
    public boolean isSuccess()     { return success; }
    public Integer getTokens()     { return tokens; }
    public Long    getLatencyMs()  { return latencyMs; }
    public Integer getPhaseId()    { return phaseId; }
    public String  getSessionId()  { return sessionId; }
    public Instant getRecordedAt() { return recordedAt; }

    public void setProvider(String v)   { provider = v; }
    public void setModel(String v)      { model = v; }
    public void setSuccess(boolean v)   { success = v; }
    public void setTokens(Integer v)    { tokens = v; }
    public void setLatencyMs(Long v)    { latencyMs = v; }
    public void setPhaseId(Integer v)   { phaseId = v; }
    public void setSessionId(String v)  { sessionId = v; }
    public void setRecordedAt(Instant v){ recordedAt = v; }

    public static Builder builder() { return new Builder(); }
    public static class Builder {
        private final ProviderMetricEntity e = new ProviderMetricEntity();
        public Builder provider(String v)  { e.provider = v;  return this; }
        public Builder model(String v)     { e.model = v;     return this; }
        public Builder success(boolean v)  { e.success = v;   return this; }
        public Builder tokens(Integer v)   { e.tokens = v;    return this; }
        public Builder latencyMs(Long v)   { e.latencyMs = v; return this; }
        public Builder phaseId(Integer v)  { e.phaseId = v;   return this; }
        public Builder sessionId(String v) { e.sessionId = v; return this; }
        public ProviderMetricEntity build(){ return e; }
    }
}

