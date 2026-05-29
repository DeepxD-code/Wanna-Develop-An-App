// ============================================================
// MARKDOWN RENDERER
// GitHub-style markdown rendering for phase outputs.
// No external libraries — pure React + regex parsing.
// Supports: headers, bold, italic, code blocks (with copy),
//           inline code, tables, lists, blockquotes, mermaid,
//           horizontal rules, links.
// ============================================================

import { useState, useEffect, useRef } from "react";

// ── Copy Button ──────────────────────────────────────────────
function CopyBtn({ text }) {
  const [copied, setCopied] = useState(false);
  const timerRef = useRef(null);

  const copy = () => {
    navigator.clipboard.writeText(text).catch(() => {
      const ta = document.createElement("textarea");
      ta.value = text;
      document.body.appendChild(ta);
      ta.select();
      document.execCommand("copy");
      document.body.removeChild(ta);
    });
    setCopied(true);
    clearTimeout(timerRef.current);
    timerRef.current = setTimeout(() => setCopied(false), 2000);
  };

  useEffect(() => () => clearTimeout(timerRef.current), []);

  return (
    <button
      onClick={copy}
      style={{
        position: "absolute", top: 8, right: 8,
        background: copied ? "#4ade8022" : "#ffffff11",
        border: `1px solid ${copied ? "#4ade8055" : "#ffffff22"}`,
        borderRadius: 4, color: copied ? "#4ade80" : "#666",
        fontSize: 9, padding: "3px 8px", cursor: "pointer",
        fontFamily: "inherit", letterSpacing: "0.05em",
        transition: "all 0.15s",
      }}
    >
      {copied ? "✓ COPIED" : "COPY"}
    </button>
  );
}

// ── Mermaid Diagram ──────────────────────────────────────────
function MermaidBlock({ code }) {
  const ref = useRef(null);
  const [error, setError] = useState(null);
  const [rendered, setRendered] = useState(false);

  useEffect(() => {
    if (!ref.current) return;

    // Try to load mermaid from CDN if not already loaded
    const renderMermaid = async () => {
      try {
        if (!window.mermaid) {
          await new Promise((resolve, reject) => {
            const script = document.createElement("script");
            script.src = "https://cdnjs.cloudflare.com/ajax/libs/mermaid/10.6.1/mermaid.min.js";
            script.onload = resolve;
            script.onerror = reject;
            document.head.appendChild(script);
          });
          window.mermaid.initialize({
            startOnLoad: false,
            theme: "dark",
            themeVariables: {
              background: "#0a0a0a",
              primaryColor: "#f59e0b",
              primaryTextColor: "#c8c0b0",
              primaryBorderColor: "#333",
              lineColor: "#555",
              secondaryColor: "#1a1a1a",
              tertiaryColor: "#111",
            },
          });
        }

        const id = "mermaid-" + Math.random().toString(36).slice(2);
        const { svg } = await window.mermaid.render(id, code);
        if (ref.current) {
          ref.current.innerHTML = svg;
          setRendered(true);
        }
      } catch (e) {
        setError(e.message);
      }
    };

    renderMermaid();
  }, [code]);

  if (error) {
    return (
      <div style={{
        background: "#0a0a0a", border: "1px solid #7f1d1d",
        borderRadius: 6, padding: 12, marginBottom: 12,
      }}>
        <div style={{ fontSize: 9, color: "#f87171", marginBottom: 6 }}>MERMAID ERROR</div>
        <pre style={{ fontSize: 11, color: "#b0a898", margin: 0 }}>{code}</pre>
      </div>
    );
  }

  return (
    <div style={{
      background: "#0a0a0a", border: "1px solid #f59e0b22",
      borderRadius: 6, padding: 12, marginBottom: 12,
      overflowX: "auto",
    }}>
      {!rendered && (
        <div style={{ fontSize: 9, color: "#f59e0b55", marginBottom: 6 }}>
          ◌ RENDERING DIAGRAM…
        </div>
      )}
      <div ref={ref} />
    </div>
  );
}

// ── Code Block ───────────────────────────────────────────────
function CodeBlock({ code, lang }) {
  // Color map for syntax keywords
  const LANG_COLORS = {
    keyword:  "#c084fc",
    string:   "#4ade80",
    comment:  "#374151",
    number:   "#38bdf8",
    function: "#f59e0b",
    operator: "#fb7185",
    type:     "#34d399",
  };

  const highlight = (text) => {
    if (!lang || lang === "text" || lang === "plain") return text;

    const patterns = [
      { re: /(\/\/.*$|#.*$|\/\*[\s\S]*?\*\/)/gm,                  cls: "comment"  },
      { re: /("(?:[^"\\]|\\.)*"|'(?:[^'\\]|\\.)*'|`(?:[^`\\]|\\.)*`)/g, cls: "string"  },
      { re: /\b(def|class|import|from|return|if|else|elif|for|while|try|except|with|as|in|not|and|or|is|None|True|False|async|await|yield|pass|break|continue|raise|lambda)\b/g, cls: "keyword" },
      { re: /\b(function|const|let|var|return|if|else|for|while|do|switch|case|break|continue|new|this|typeof|instanceof|import|export|default|class|extends|async|await|=>|null|undefined|true|false)\b/g, cls: "keyword" },
      { re: /\b(public|private|protected|static|void|int|string|boolean|double|float|long|char|byte|class|interface|extends|implements|new|return|if|else|for|while|try|catch|finally|throw|throws|import|package|this|super|null|true|false|final|abstract|enum)\b/g, cls: "keyword" },
      { re: /\b(\d+\.?\d*)\b/g,                                     cls: "number"   },
      { re: /\b([A-Z][a-zA-Z0-9_]*)\b/g,                           cls: "type"     },
      { re: /\b([a-z_][a-z0-9_]*)\s*(?=\()/g,                      cls: "function" },
    ];

    // Simple token-based highlighting (no parse tree, just coloring)
    return text;
  };

  return (
    <div style={{ position: "relative", marginBottom: 14 }}>
      {lang && (
        <div style={{
          background: "#0d0d0d", borderBottom: "1px solid #1e1e1e",
          padding: "4px 10px", borderRadius: "6px 6px 0 0",
          fontSize: 9, color: "#444", letterSpacing: "0.08em",
          display: "flex", justifyContent: "space-between",
        }}>
          <span>{lang.toUpperCase()}</span>
        </div>
      )}
      <div style={{
        position: "relative",
        background: "#0a0a0a",
        border: "1px solid #1e1e1e",
        borderTop: lang ? "none" : "1px solid #1e1e1e",
        borderRadius: lang ? "0 0 6px 6px" : 6,
        overflow: "hidden",
      }}>
        <CopyBtn text={code} />
        <pre style={{
          margin: 0, padding: "12px 40px 12px 14px",
          fontSize: 11.5, lineHeight: 1.7, color: "#b0a898",
          overflowX: "auto", fontFamily: "'JetBrains Mono','Fira Code',monospace",
        }}>
          {code}
        </pre>
      </div>
    </div>
  );
}

// ── Table ─────────────────────────────────────────────────────
function Table({ rows }) {
  if (!rows || rows.length === 0) return null;
  const headers = rows[0];
  const body    = rows.slice(2); // skip separator row

  return (
    <div style={{ overflowX: "auto", marginBottom: 14 }}>
      <table style={{
        borderCollapse: "collapse", width: "100%",
        fontSize: 11.5, color: "#b0a898",
      }}>
        <thead>
          <tr>
            {headers.map((h, i) => (
              <th key={i} style={{
                padding: "6px 12px", textAlign: "left",
                borderBottom: "1px solid #2a2a2a",
                color: "#d4c4a0", fontWeight: 600, fontSize: 10,
                letterSpacing: "0.05em", whiteSpace: "nowrap",
                background: "#0a0a0a",
              }}>{h.trim()}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {body.map((row, i) => (
            <tr key={i} style={{ borderBottom: "1px solid #141414" }}>
              {row.map((cell, j) => (
                <td key={j} style={{
                  padding: "5px 12px", verticalAlign: "top",
                  color: j === 0 ? "#d4c4a0" : "#b0a898",
                }}>{cell.trim()}</td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

// ── Inline Markdown ───────────────────────────────────────────
function InlineMarkdown({ text }) {
  if (!text) return null;

  const parts = [];
  let remaining = text;
  let key = 0;

  const patterns = [
    { re: /\*\*(.+?)\*\*/,  render: (m) => <strong key={key++} style={{ color: "#e0d4b8", fontWeight: 700 }}>{m[1]}</strong> },
    { re: /\*(.+?)\*/,      render: (m) => <em key={key++} style={{ color: "#c8bfa8", fontStyle: "italic" }}>{m[1]}</em> },
    { re: /`([^`]+)`/,      render: (m) => <code key={key++} style={{ background: "#0d0d0d", border: "1px solid #222", borderRadius: 3, padding: "1px 5px", fontSize: "0.9em", color: "#4ade80" }}>{m[1]}</code> },
    { re: /\[([^\]]+)\]\(([^)]+)\)/, render: (m) => <a key={key++} href={m[2]} target="_blank" rel="noreferrer" style={{ color: "#38bdf8", textDecoration: "none" }}>{m[1]}</a> },
  ];

  while (remaining.length > 0) {
    let earliest = null;
    let match = null;

    for (const p of patterns) {
      const m = remaining.match(p.re);
      if (m && (earliest === null || m.index < earliest.index)) {
        earliest = m;
        match = p;
      }
    }

    if (!earliest) {
      parts.push(<span key={key++}>{remaining}</span>);
      break;
    }

    if (earliest.index > 0) {
      parts.push(<span key={key++}>{remaining.slice(0, earliest.index)}</span>);
    }
    parts.push(match.render(earliest));
    remaining = remaining.slice(earliest.index + earliest[0].length);
  }

  return <>{parts}</>;
}

// ── Main Parser ───────────────────────────────────────────────
function parseMarkdown(text) {
  const lines    = text.split("\n");
  const elements = [];
  let i          = 0;
  let key        = 0;

  while (i < lines.length) {
    const line = lines[i];

    // ── Code fence (``` or mermaid) ──
    if (line.startsWith("```")) {
      const lang  = line.slice(3).trim().toLowerCase();
      const code  = [];
      i++;
      while (i < lines.length && !lines[i].startsWith("```")) {
        code.push(lines[i]);
        i++;
      }
      i++; // skip closing ```
      const codeStr = code.join("\n");
      if (lang === "mermaid") {
        elements.push(<MermaidBlock key={key++} code={codeStr} />);
      } else {
        elements.push(<CodeBlock key={key++} code={codeStr} lang={lang} />);
      }
      continue;
    }

    // ── Horizontal rule ──
    if (/^[-*_]{3,}$/.test(line.trim())) {
      elements.push(<hr key={key++} style={{ border: "none", borderTop: "1px solid #1e1e1e", margin: "16px 0" }} />);
      i++;
      continue;
    }

    // ── Headers ──
    const headerMatch = line.match(/^(#{1,6})\s+(.+)/);
    if (headerMatch) {
      const level = headerMatch[1].length;
      const sizes  = { 1: 18, 2: 16, 3: 14, 4: 13, 5: 12, 6: 11 };
      const colors = { 1: "#e0d4b8", 2: "#d4c4a0", 3: "#c8bfa8", 4: "#b0a898", 5: "#9a9080", 6: "#7a7060" };
      elements.push(
        <div key={key++} style={{
          fontSize: sizes[level], fontWeight: level <= 2 ? 700 : 600,
          color: colors[level], marginTop: level === 1 ? 20 : 14, marginBottom: 6,
          borderBottom: level <= 2 ? "1px solid #1a1a1a" : "none",
          paddingBottom: level <= 2 ? 6 : 0,
          letterSpacing: level === 1 ? "0.02em" : 0,
        }}>
          <InlineMarkdown text={headerMatch[2]} />
        </div>
      );
      i++;
      continue;
    }

    // ── Blockquote ──
    if (line.startsWith("> ")) {
      const quoteLines = [];
      while (i < lines.length && lines[i].startsWith("> ")) {
        quoteLines.push(lines[i].slice(2));
        i++;
      }
      elements.push(
        <div key={key++} style={{
          borderLeft: "3px solid #f59e0b44", paddingLeft: 12,
          margin: "8px 0", color: "#7a7060", fontStyle: "italic",
        }}>
          {quoteLines.map((l, li) => <div key={li}><InlineMarkdown text={l} /></div>)}
        </div>
      );
      continue;
    }

    // ── Table ──
    if (line.includes("|") && lines[i + 1]?.includes("|") && lines[i + 1]?.includes("-")) {
      const tableRows = [];
      while (i < lines.length && lines[i].includes("|")) {
        const cells = lines[i].split("|").filter((_, ci) => ci > 0 && ci < lines[i].split("|").length - 1);
        tableRows.push(cells);
        i++;
      }
      elements.push(<Table key={key++} rows={tableRows} />);
      continue;
    }

    // ── Unordered list ──
    if (/^[\s]*[-*•]\s/.test(line)) {
      const items = [];
      while (i < lines.length && /^[\s]*[-*•]\s/.test(lines[i])) {
        const indent = lines[i].match(/^(\s*)/)[1].length;
        items.push({ text: lines[i].replace(/^[\s]*[-*•]\s/, ""), indent });
        i++;
      }
      elements.push(
        <ul key={key++} style={{ margin: "6px 0 10px 0", paddingLeft: 0, listStyle: "none" }}>
          {items.map((item, ii) => (
            <li key={ii} style={{
              display: "flex", gap: 8, alignItems: "flex-start",
              marginBottom: 3, paddingLeft: item.indent * 8,
              color: "#b0a898", fontSize: 12.5, lineHeight: 1.7,
            }}>
              <span style={{ color: "#f59e0b", flexShrink: 0, marginTop: 2 }}>•</span>
              <span><InlineMarkdown text={item.text} /></span>
            </li>
          ))}
        </ul>
      );
      continue;
    }

    // ── Ordered list ──
    if (/^\d+\.\s/.test(line)) {
      const items = [];
      let num = 1;
      while (i < lines.length && /^\d+\.\s/.test(lines[i])) {
        items.push({ text: lines[i].replace(/^\d+\.\s/, ""), num: num++ });
        i++;
      }
      elements.push(
        <ol key={key++} style={{ margin: "6px 0 10px 0", paddingLeft: 0, listStyle: "none" }}>
          {items.map((item, ii) => (
            <li key={ii} style={{
              display: "flex", gap: 10, alignItems: "flex-start",
              marginBottom: 4, color: "#b0a898", fontSize: 12.5, lineHeight: 1.7,
            }}>
              <span style={{
                color: "#f59e0b", flexShrink: 0, minWidth: 18,
                fontSize: 10, marginTop: 3, fontWeight: 700,
              }}>{item.num}.</span>
              <span><InlineMarkdown text={item.text} /></span>
            </li>
          ))}
        </ol>
      );
      continue;
    }

    // ── Empty line ──
    if (!line.trim()) {
      elements.push(<div key={key++} style={{ height: 6 }} />);
      i++;
      continue;
    }

    // ── Emoji/status lines (🔴 🟡 ✅ etc) ──
    if (/^[🔴🟡✅⚠️📊🎯💪🚀📅🔧•★►✓✗]/u.test(line.trim())) {
      elements.push(
        <div key={key++} style={{
          fontSize: 12.5, lineHeight: 1.7, color: "#b0a898",
          marginBottom: 2, paddingLeft: 2,
        }}>
          <InlineMarkdown text={line} />
        </div>
      );
      i++;
      continue;
    }

    // ── Regular paragraph ──
    elements.push(
      <div key={key++} style={{
        fontSize: 12.5, lineHeight: 1.9, color: "#b0a898",
        marginBottom: 2,
      }}>
        <InlineMarkdown text={line} />
      </div>
    );
    i++;
  }

  return elements;
}

// ── Main Component ────────────────────────────────────────────
export default function MarkdownRenderer({ text, streaming }) {
  if (!text) return null;

  return (
    <div style={{ fontFamily: "'JetBrains Mono','Fira Code',monospace" }}>
      {parseMarkdown(text)}
      {streaming && (
        <span style={{
          display: "inline-block", width: 2, height: "1em",
          background: "#f59e0b", verticalAlign: "text-bottom",
          marginLeft: 2, animation: "bl 0.7s step-end infinite",
        }} />
      )}
    </div>
  );
}
