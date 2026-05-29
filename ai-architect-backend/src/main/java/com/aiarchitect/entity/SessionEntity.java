package com.aiarchitect.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name = "sessions")
public class SessionEntity {
    @Id @Column(name = "session_id", length = 36) private String sessionId;
    @Column(name = "user_input", columnDefinition = "TEXT") private String userInput;
    @Column(name = "mode", length = 10) private String mode;
    @Column(name = "status", length = 20) private String status;
    @Column(name = "current_phase") private int currentPhase;
    @Column(name = "context_buffer", columnDefinition = "TEXT") private String contextBuffer;
    @Column(name = "created_at") private Instant createdAt;
    @Column(name = "updated_at") private Instant updatedAt;

    @PrePersist void prePersist() { if (createdAt == null) createdAt = Instant.now(); updatedAt = Instant.now(); }
    @PreUpdate  void preUpdate()  { updatedAt = Instant.now(); }

    public String  getSessionId()     { return sessionId; }
    public String  getUserInput()     { return userInput; }
    public String  getMode()          { return mode; }
    public String  getStatus()        { return status; }
    public int     getCurrentPhase()  { return currentPhase; }
    public String  getContextBuffer() { return contextBuffer; }
    public Instant getCreatedAt()     { return createdAt; }
    public Instant getUpdatedAt()     { return updatedAt; }

    public void setSessionId(String v)     { sessionId = v; }
    public void setUserInput(String v)     { userInput = v; }
    public void setMode(String v)          { mode = v; }
    public void setStatus(String v)        { status = v; }
    public void setCurrentPhase(int v)     { currentPhase = v; }
    public void setContextBuffer(String v) { contextBuffer = v; }
    public void setCreatedAt(Instant v)    { createdAt = v; }
    public void setUpdatedAt(Instant v)    { updatedAt = v; }

    public static Builder builder() { return new Builder(); }
    public static class Builder {
        private final SessionEntity e = new SessionEntity();
        public Builder sessionId(String v)     { e.sessionId = v;     return this; }
        public Builder userInput(String v)     { e.userInput = v;     return this; }
        public Builder mode(String v)          { e.mode = v;          return this; }
        public Builder status(String v)        { e.status = v;        return this; }
        public Builder currentPhase(int v)     { e.currentPhase = v;  return this; }
        public Builder contextBuffer(String v) { e.contextBuffer = v; return this; }
        public SessionEntity build()           { return e; }
    }
}

