package com.aiarchitect.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name = "projects")
public class ProjectEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "session_id", length = 36) private String sessionId;
    @Column(name = "name", length = 200)      private String name;
    @Column(name = "project_dir", columnDefinition = "TEXT") private String projectDir;
    @Column(name = "zip_path",    columnDefinition = "TEXT") private String zipPath;
    @Column(name = "confidence_avg") private Double confidenceAvg;
    @Column(name = "outcome", length = 20) private String outcome;
    @Column(name = "created_at") private Instant createdAt;

    @PrePersist void prePersist() { if (createdAt == null) createdAt = Instant.now(); }

    public Long    getId()            { return id; }
    public String  getSessionId()     { return sessionId; }
    public String  getName()          { return name; }
    public String  getProjectDir()    { return projectDir; }
    public String  getZipPath()       { return zipPath; }
    public Double  getConfidenceAvg() { return confidenceAvg; }
    public String  getOutcome()       { return outcome; }
    public Instant getCreatedAt()     { return createdAt; }

    public void setSessionId(String v)     { sessionId = v; }
    public void setName(String v)          { name = v; }
    public void setProjectDir(String v)    { projectDir = v; }
    public void setZipPath(String v)       { zipPath = v; }
    public void setConfidenceAvg(Double v) { confidenceAvg = v; }
    public void setOutcome(String v)       { outcome = v; }

    public static Builder builder() { return new Builder(); }
    public static class Builder {
        private final ProjectEntity e = new ProjectEntity();
        public Builder sessionId(String v)     { e.sessionId = v;     return this; }
        public Builder name(String v)          { e.name = v;          return this; }
        public Builder projectDir(String v)    { e.projectDir = v;    return this; }
        public Builder confidenceAvg(Double v) { e.confidenceAvg = v; return this; }
        public Builder outcome(String v)       { e.outcome = v;       return this; }
        public ProjectEntity build()           { return e; }
    }
}

