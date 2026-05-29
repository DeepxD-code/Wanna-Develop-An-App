package com.aiarchitect.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name = "audit_logs")
public class AuditLogEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "session_id", length = 36) private String sessionId;
    @Column(name = "level",     length = 10)  private String level;
    @Column(name = "component", length = 100) private String component;
    @Column(name = "message",   columnDefinition = "TEXT") private String message;
    @Column(name = "metadata",  columnDefinition = "text") private String metadata;
    @Column(name = "logged_at") private Instant loggedAt;

    @PrePersist void prePersist() { if (loggedAt == null) loggedAt = Instant.now(); }

    public Long    getId()        { return id; }
    public String  getSessionId() { return sessionId; }
    public String  getLevel()     { return level; }
    public String  getComponent() { return component; }
    public String  getMessage()   { return message; }
    public String  getMetadata()  { return metadata; }
    public Instant getLoggedAt()  { return loggedAt; }

    public void setSessionId(String v) { sessionId = v; }
    public void setLevel(String v)     { level = v; }
    public void setComponent(String v) { component = v; }
    public void setMessage(String v)   { message = v; }
    public void setMetadata(String v)  { metadata = v; }
    public void setLoggedAt(Instant v) { loggedAt = v; }

    public static Builder builder() { return new Builder(); }
    public static class Builder {
        private final AuditLogEntity e = new AuditLogEntity();
        public Builder sessionId(String v) { e.sessionId = v; return this; }
        public Builder level(String v)     { e.level = v;     return this; }
        public Builder component(String v) { e.component = v; return this; }
        public Builder message(String v)   { e.message = v;   return this; }
        public AuditLogEntity build()      { return e; }
    }
}

