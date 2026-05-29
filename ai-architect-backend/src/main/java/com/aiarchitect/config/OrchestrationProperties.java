package com.aiarchitect.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "orchestration")
public class OrchestrationProperties {

    private int  maxRetries        = 3;
    private long phaseDelayMs      = 2000;
    private long requestDelayMs    = 1500;
    private long phaseTimeoutMs    = 300_000;
    private int  sessionTtlMinutes = 120;
    private int  maxContextPhases  = 2;

    public int  getMaxRetries()        { return maxRetries; }
    public long getPhaseDelayMs()      { return phaseDelayMs; }
    public long getRequestDelayMs()    { return requestDelayMs; }
    public long getPhaseTimeoutMs()    { return phaseTimeoutMs; }
    public int  getSessionTtlMinutes() { return sessionTtlMinutes; }
    public int  getMaxContextPhases()  { return maxContextPhases; }

    public void setMaxRetries(int v)        { maxRetries = v; }
    public void setPhaseDelayMs(long v)     { phaseDelayMs = v; }
    public void setRequestDelayMs(long v)   { requestDelayMs = v; }
    public void setPhaseTimeoutMs(long v)   { phaseTimeoutMs = v; }
    public void setSessionTtlMinutes(int v) { sessionTtlMinutes = v; }
    public void setMaxContextPhases(int v)  { maxContextPhases = v; }
}
