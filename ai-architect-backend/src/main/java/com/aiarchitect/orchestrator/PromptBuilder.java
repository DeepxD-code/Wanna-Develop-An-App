package com.aiarchitect.orchestrator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Component;

/**
 * PromptBuilder
 * Builds the prompt for each of the 11 phases.
 * Injects rolling context from previous phases.
 */
@Component
public class PromptBuilder {
    private static final Logger log = LoggerFactory.getLogger(PromptBuilder.class);


    public String build(int phaseId, String context, String userInput, String mode) {
        String ctx = (context != null && !context.isBlank())
            ? "\n[CONTEXT FROM PREVIOUS PHASES]\n" + context + "\n[END CONTEXT]\n\n"
            : "";

        String modeNote = "deep".equals(mode)
            ? "This is DEEP MODE — be thorough, comprehensive, and production-grade."
            : "This is QUICK MODE — be concise, focus on essentials, MVP quality.";

        return switch (phaseId) {
            case 1  -> phase1(userInput, modeNote);
            case 2  -> phase2(ctx, modeNote);
            case 3  -> phase3(ctx, modeNote);
            case 4  -> phase4(ctx, modeNote);
            case 5  -> phase5(ctx, modeNote);
            case 6  -> phase6(ctx, modeNote);
            case 7  -> phase7(ctx, modeNote);
            case 8  -> phase8(ctx, modeNote);
            case 9  -> phase9(ctx, modeNote);
            case 10 -> phase10(ctx, modeNote);
            case 11 -> phase11(ctx, modeNote);
            default -> throw new IllegalArgumentException("Invalid phase: " + phaseId);
        };
    }

    // ── Phase 1: Auto Ideation ─────────────────────────────────────────────────
    private String phase1(String userInput, String mode) {
        return """
            You are an expert AI systems architect. %s
            Generate 5 innovative project ideas (NOT generic chatbots). Be specific, creative, technically grounded.
            %s

            Output exactly in this format — no deviation:

            # 5 Innovative AI API Projects

            ---

            ## 1. **[Project Name]** — [One-line tagline]
            **Problem:** [what problem it solves]
            **Why current solutions fail:** [gap in existing tools]
            **Key components:** [4-5 comma-separated technical components]
            **Difficulty:** [X/10]
            **Impact:** [X/10]

            (repeat for ideas 2-5, each separated by ---)

            ---

            ## Rankings

            | Project | Innovation | Feasibility | Market Size | Build Complexity |
            |---|---|---|---|---|
            | [Name] | ★★★★☆ | ★★★★☆ | ★★★★☆ | ★★★★☆ |
            | ... | ... | ... | ... | ... |

            ---

            **★ SELECTED: [Idea Name]** — [2-3 sentence justification]
            """.formatted(
                userInput != null && !userInput.isBlank()
                    ? "User focus: \"" + userInput + "\""
                    : "Generate across varied domains — productivity, health, finance, dev tools, automation.",
                mode
            );
    }

    // ── Phase 2: Auto Ranking ──────────────────────────────────────────────────
    private String phase2(String ctx, String mode) {
        return ctx + """
            # [Project Name] — Phase 2 Analysis

            ## 1. Selection Scoring Breakdown

            | Dimension | Score | Weight | Weighted |
            |---|---|---|---|
            | Innovation | [X/10] | [XX]% | [X.XX] |
            | Feasibility | [X/10] | [XX]% | [X.XX] |
            | Market Potential | [X/10] | [XX]% | [X.XX] |
            | Build Complexity | [X/10] | [XX]% | [X.XX] |
            | **Overall** | — | — | **[X.XX / 10]** |

            [2-3 sentence explanation of weighting rationale]

            ## 2. Why [Project] Beat the Runners-Up

            **Runner-up 1: [Name] ([score])**
            [Detailed comparison — what made it weaker]

            **Runner-up 2: [Name] ([score])**
            [Detailed comparison — what made it weaker]

            **What specifically separates [Project]:**
            [Key differentiating factor]

            ## 3. Key Success Factors for Execution

            **[Factor 1]:** [Detailed explanation]
            **[Factor 2]:** [Detailed explanation]
            **[Factor 3]:** [Detailed explanation]
            **[Factor 4]:** [Detailed explanation]

            ## 4. Preliminary Feasibility Check

            **Technical feasibility: [High/Med/Low]**
            [Detailed assessment]

            **Market feasibility: [High/Med/Low]**
            [Detailed assessment]

            **Timeline feasibility: [High/Med/Low]**
            [Detailed assessment]

            **Primary risk:** [Biggest risk and mitigation]
            """.formatted(mode);
    }

    // ── Phase 3: Research ──────────────────────────────────────────────────────
    private String phase3(String ctx, String mode) {
        return ctx + """
            # [Project Name] — Phase 3 Deep Research

            ## 1. MARKET ANALYSIS

            ### Total Addressable Market
            [2-3 paragraphs with market sizing, CAGR data, and current enterprise adoption context]

            ### Competitive Landscape
            **[Competitor 1]:** [Detailed analysis: what they do, core limitation relative to this project, integration dependency]
            **[Competitor 2]:** [Detailed analysis: maturity level, weakness this project exploits, target customer mismatch]
            **[Competitor 3]:** [Detailed analysis: what they do well, why they're a partner not competitor, gap they don't fill]
            **[Competitor 4]:** [Detailed analysis: strategic threat scenario, how to compete]
            **[Competitor 5]:** [Detailed analysis: risk of "good enough" displacement, differentiation strategy]

            ### Market Gaps and Opportunities
            [Primary gap and how project fills it]
            [Secondary gap and standardization opportunity]

            ### Growth Trajectory
            [Market growth rate, timing advantage, why this is the right entry point]

            ## 2. TECHNICAL LANDSCAPE

            ### Current Solutions and Their Limitations
            [Pattern 1: description and limitations]
            [Pattern 2: description and limitations]
            [Pattern 3: description and limitations]

            ### Emerging Technologies Relevant to This Project
            [Tech 1: how it applies and why it matters]
            [Tech 2: how it applies and why it matters]
            [Tech 3: how it applies and why it matters]

            ### Feasibility Assessment: [X/10]
            [Explanation of score and key challenges]

            ### Tech Maturity Level
            [Component assessment — what's mature, what's novel]

            ## 3. USER RESEARCH

            ### Persona 1: [Title] (Primary Buyer)
            [Name, role, company context, current workflow, specific pain, budget authority]
            **[What they need from this project:]**

            ### Persona 2: [Title] (Economic Buyer)
            [Name, role, accountability, budget ownership, regulatory concerns]
            **[What they need from this project:]**

            ### Persona 3: [Title] (Volume Play)
            [Name, role, scale, budget constraints, required simplicity]
            **[What they need from this project:]**

            ### User Pain Points Being Solved
            [Core structural pain and how the project addresses it]

            ### User Adoption Barriers
            [Primary integration barrier and solution]
            [Secondary trust/benchmark barrier and solution]

            ### Success Metrics for Users
            [For persona 1: specific measurable outcome]
            [For persona 2: specific measurable outcome]
            [For persona 3: specific measurable outcome]

            ## 4. RISK ANALYSIS

            ### Technical Risks
            **[Risk 1]:** [Description and mitigation strategy]
            **[Risk 2]:** [Description and mitigation strategy]
            **[Risk 3]:** [Description and mitigation strategy]

            ### Market Risks
            **[Risk 1]:** [Description and mitigation strategy]
            **[Risk 2]:** [Description and mitigation strategy]

            ### Execution Risks
            **[Risk 1]:** [Description and mitigation strategy]
            **[Risk 2]:** [Description and mitigation strategy]
            """.formatted(mode);
    }

    // ── Phase 4: Architecture ──────────────────────────────────────────────────
    private String phase4(String ctx, String mode) {
        return ctx + """
            # [Project Name] — Phase 4 System Architecture

            ## 1. System Overview
            [3 sentences: what it does, how it works, key architectural principle]

            ## 2. ASCII Architecture Diagram
            ```
            [Full component diagram showing all services, databases, connections]
            ```

            ## 3. Data Flow
            Numbered steps for the 3 most critical user journeys.

            ## 4. Module Breakdown

            | Module | Purpose | Tech Stack | Key Responsibilities | Failure Points |
            |---|---|---|---|---|
            | Input Processing | ... | ... | ... | ... |
            | AI Layer | ... | ... | ... | ... |
            | Business Logic | ... | ... | ... | ... |
            | Data Layer | ... | ... | ... | ... |
            | Auth | ... | ... | ... | ... |
            | Cache | ... | ... | ... | ... |

            ## 5. Security Model
            Auth approach, data encryption, input validation, rate limiting.

            ## 6. Scalability Strategy
            - 100 users: [architecture]
            - 10k users: [what changes]
            - 100k users: [what changes]
            - Bottlenecks to watch

            Be precise with technology choices. No vague recommendations.
            """.formatted(mode);
    }

    // ── Phase 5: Implementation Plan ──────────────────────────────────────────
    private String phase5(String ctx, String mode) {
        return ctx + """
            # [Project Name] — Phase 5 Implementation Plan

            6-phase development roadmap. %s

            | Phase | Focus | Tasks | Deliverables | Risks |
            |---|---|---|---|---|
            | A | Foundation | [numbered, actionable tasks] | [what exists] | [risks & mitigation] |
            | B | Core API | [numbered, actionable tasks] | [what exists] | [risks & mitigation] |
            | C | Core AI | [numbered, actionable tasks] | [what exists] | [risks & mitigation] |
            | D | Features | [numbered, actionable tasks] | [what exists] | [risks & mitigation] |
            | E | Testing | [numbered, actionable tasks] | [what exists] | [risks & mitigation] |
            | F | Deploy | [numbered, actionable tasks] | [what exists] | [risks & mitigation] |

            **MVP Definition:** [exactly what MVP includes]
            **Critical Path:** [sequence of must-complete tasks]
            **Time Estimate:** [per phase and total]
            **Tech Debt to watch:** [items to refactor later]
            """.formatted(mode);
    }

    // ── Phase 6: API Integration ───────────────────────────────────────────────
    private String phase6(String ctx, String mode) {
        return ctx + """
            # [Project Name] — Phase 6 API & AI Integration Design

            ## 1. Prompt Templates
            Show actual prompt template strings (real templates, not descriptions).

            ## 2. Context Management
            How context flows between requests, token budget, summarization strategy.

            ## 3. Token Optimization
            Cost formula, optimization techniques, expected cost per operation.

            ## 4. Error & Retry Strategy
            Retry logic with exponential backoff (pseudocode), fallback behavior, graceful degradation.

            ## 5. Core Integration Functions

            | Function | Input | API Payload | Output Format | Error Handling |
            |---|---|---|---|---|
            | [Function 1] | ... | ... | ... | ... |
            | [Function 2] | ... | ... | ... | ... |
            | [Function 3] | ... | ... | ... | ... |

            Keep it technical and concrete.
            """.formatted(mode);
    }

    // ── Phase 7: Code Generation ───────────────────────────────────────────────
    private String phase7(String ctx, String mode) {
        return ctx + """
            # [Project Name] — Phase 7 Code Generation

            Write COMPLETE, RUNNABLE production code. %s

            ## File Structure
            ```
            /project/api/client.py
            /project/core/main.py
            /project/utils/helpers.py
            /project/main.py
            ```

            ## Requirements for Every File
            ✓ Complete runnable code (no placeholders, no TODOs)
            ✓ Env vars for all secrets (never hardcoded)
            ✓ Logging at key decision points
            ✓ Retry logic with exponential backoff
            ✓ Input validation on all public interfaces
            ✓ Type hints / JSDoc on all functions
            ✓ Docstrings: purpose, args, returns, raises
            ✓ Meaningful error messages

            Choose the best language (Python or Node.js) for this project.
            """.formatted(mode);
    }

    // ── Phase 8: Error Checking ────────────────────────────────────────────────
    private String phase8(String ctx, String mode) {
        return ctx + """
            # [Project Name] — Phase 8 Quality & Security Audit

            3-PASS AUDIT. %s

            ## PASS 1 — Logic & Correctness
            • Algorithm bugs, edge cases, off-by-one errors
            • State management issues, null handling
            • Business logic correctness

            ## PASS 2 — Runtime & Resilience
            • API failure handling, timeout/retry coverage
            • Race conditions in async code
            • Memory leaks, resource cleanup
            • Input validation gaps

            ## PASS 3 — Security & Performance
            • Injection vulnerabilities (SQL, command, prompt)
            • Auth and authorization flaws
            • Sensitive data exposure
            • Performance bottlenecks, dependency vulnerabilities

            ## Format for Each Issue
            🔴 **ISSUE:** [description]
               **WHY:** [impact if not fixed]
               **FIX:** [show corrected code snippet]

            ## Summary
            📊 **Code Health Score:** [X/100]
            🎯 **Top 3 Critical Fixes:** [ordered by priority]
            ✅ **Passed checks:** [what's good]
            """.formatted(mode);
    }

    // ── Phase 9: Stress Testing ────────────────────────────────────────────────
    private String phase9(String ctx, String mode) {
        return ctx + """
            # [Project Name] — Phase 9 Stress & Resilience Testing

            8 test scenarios. %s

            | # | Scenario | Setup | Input | Expected | Simulated Result | Code Snippet | Risk |
            |---|---|---|---|---|---|---|---|
            | 1 | 10x/100x oversized inputs | ... | ... | ... | ... | ... | ... |
            | 2 | 100 requests/second burst | ... | ... | ... | ... | ... | ... |
            | 3 | Prompt injection / adversarial | ... | ... | ... | ... | ... | ... |
            | 4 | API timeout (30s delay) | ... | ... | ... | ... | ... | ... |
            | 5 | API outage (60s) | ... | ... | ... | ... | ... | ... |
            | 6 | 1000 concurrent users | ... | ... | ... | ... | ... | ... |
            | 7 | Memory pressure (OOM) | ... | ... | ... | ... | ... | ... |
            | 8 | Network partition / packet loss | ... | ... | ... | ... | ... | ... |

            ## Results
            📊 **Resilience Score:** [X/100]
            🔴 **Critical failure modes:** [list]
            🟡 **Performance bottlenecks:** [list]
            ✅ **System strengths:** [list]
            🔧 **Top 3 hardening recommendations**
            """.formatted(mode);
    }

    // ── Phase 10: Extensibility ────────────────────────────────────────────────
    private String phase10(String ctx, String mode) {
        return ctx + """
            # [Project Name] — Phase 10 Extensibility & Future Roadmap

            8 high-impact future improvements. %s

            | # | Name | Description | Tech Approach | Difficulty | Impact | Effort (wks) | Priority |
            |---|---|---|---|---|---|---|---|
            | 1 | ... | ... | ... | X/10 | X/10 | X | X.X |
            | 2 | ... | ... | ... | X/10 | X/10 | X | X.X |
            | 3 | ... | ... | ... | X/10 | X/10 | X | X.X |
            | 4 | ... | ... | ... | X/10 | X/10 | X | X.X |
            | 5 | ... | ... | ... | X/10 | X/10 | X | X.X |
            | 6 | ... | ... | ... | X/10 | X/10 | X | X.X |
            | 7 | ... | ... | ... | X/10 | X/10 | X | X.X |
            | 8 | ... | ... | ... | X/10 | X/10 | X | X.X |

            Priority = Impact² / Difficulty. Categories: multi-agent, fine-tuning, vector memory, tool-use, automation, multimodal, analytics, enterprise, security.

            ## Quarterly Roadmap
            ```
            Q1 → [top 2 improvements]
            Q2 → [next 2]
            Q3 → [next 2]
            Q4 → [final 2]
            ```

            **Vision Statement:** [one powerful paragraph on 3-year direction]
            """.formatted(mode);
    }

    // ── Phase 11: Final Report ─────────────────────────────────────────────────
    private String phase11(String ctx, String mode) {
        return ctx + """
            # [Project Name] — Phase 11 Final Report & Production Assessment

            ## 1. Executive Summary

            | Field | Value |
            |---|---|
            | Project Name | [name] |
            | Tagline | [one-liner] |
            | Problem | [what it solves] |
            | Solution | [how it solves it] |
            | Key Differentiator | [unique advantage] |
            | Target Users | [who uses it] |
            | Success Metrics | [how to measure] |

            ## 2. Complete Architecture Diagram
            ```
            [Full ASCII diagram — all components, services, databases]
            ```

            ## 3. Key Algorithms
            [3 critical algorithms with pseudocode + Big O complexity]

            ## 4. Module Summary Table

            | Module | Purpose | Est. LOC | Test Coverage | Status |
            |---|---|---|---|---|
            | ... | ... | ... | ... | ... |

            ## 5. Quality Report
            **Code Health Score:** [X/100]
            **Issues Found/Fixed:** [X/Y]
            **Stress Test Resilience:** [X/100]
            **Security Vulnerabilities:** [found/fixed]

            ## 6. Setup & Deployment Guide
            Prerequisites, step-by-step setup (numbered runnable commands), environment variables needed, common issues and solutions.

            ## 7. Production Readiness Checklist
            - [ ] Tests passing
            - [ ] Security scanned
            - [ ] Docs complete
            - [ ] CI/CD configured
            - [ ] Monitoring ready
            - [ ] Secrets managed
            - [ ] Backups configured

            ## 8. Final Verdict
            🎯 **Production Readiness:** [X/10]
            💪 **Strengths:** [3 specific]
            ⚠️ **Limitations:** [3 honest]
            🚀 **Verdict:** SHIP IT / NEEDS WORK / PROOF OF CONCEPT
            """.formatted(mode);
    }
}
