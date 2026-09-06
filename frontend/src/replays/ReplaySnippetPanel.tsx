import { useLayoutEffect, useRef, useState } from "react";
import { gsap } from "gsap";

export type ReplayPreviewSnippet = {
  id: string;
  title: string;
  language: string;
  content: string;
  provenance: string;
  stack: string;
};

export function ReplaySnippetPanel({ snippets, paused }: { snippets: ReplayPreviewSnippet[]; paused: boolean }) {
  const [position, setPosition] = useState(0);
  const root = useRef<HTMLElement>(null);
  const index = position % Math.max(snippets.length, 1);
  const current = snippets[index];
  const next = snippets[(index + 1) % snippets.length];

  useLayoutEffect(() => {
    if (paused || snippets.length < 2 || !root.current) return;
    const media = gsap.matchMedia();
    media.add("(prefers-reduced-motion: no-preference)", () => {
      gsap.timeline({ delay: 5.5, onComplete: () => setPosition((value) => (value + 1) % snippets.length) })
        .to("[data-snippet-current]", { xPercent: -105, y: -8, scale: .97, opacity: .3, duration: .44, ease: "power2.inOut" }, 0)
        .fromTo("[data-snippet-next]", { xPercent: 105, y: 8, scale: .97, visibility: "visible" }, { xPercent: 0, y: 0, scale: 1, duration: .44, ease: "power2.inOut" }, 0);
    }, root);
    return () => media.revert();
  }, [index, paused, snippets]);

  if (!current) return null;
  return <aside ref={root} className="replay-snippet-panel" aria-label="Prévia de código dos Replays">
    <header><span>recent code</span><span>{index + 1} / {snippets.length}</span></header>
    <div className="replay-snippet-panel__viewport">
      <div data-snippet-current><SnippetCard snippet={current} /></div>
      {snippets.length > 1 ? <div data-snippet-next aria-hidden="true"><SnippetCard snippet={next} /></div> : null}
    </div>
  </aside>;
}

function SnippetCard({ snippet }: { snippet: ReplayPreviewSnippet }) {
  const lines = snippet.content.replace(/\r\n/g, "\n").split("\n");
  return <div className="replay-snippet-panel__card">
    <div className="replay-snippet-panel__source"><span>{snippet.language}</span><p>{snippet.title}</p></div>
    <pre><code>{lines.slice(0, 9).map((line, index) => <span className="replay-snippet-panel__line" key={index}><span aria-hidden="true">{String(index + 1).padStart(2, "0")}</span><span>{highlight(line)}</span>{"\n"}</span>)}</code></pre>
    <footer><span>{snippet.provenance}{lines.length > 9 ? " · trecho" : ""}</span><span>{snippet.stack || "reaproveitável"}</span></footer>
  </div>;
}

function highlight(line: string) {
  // React escapes every token; no HTML injection or language runtime is involved.
  return line.split(/("(?:[^"\\]|\\.)*"|'(?:[^'\\]|\\.)*'|\/\/.*$|\b(?:const|let|return|async|await|function|export|import|from|public|private|class|final|void|new|if|throw|SELECT|FROM|WHERE|AND|true|false|null)\b)/g)
    .map((token, index) => <span key={index} className={token.startsWith("//") ? "replay-snippet-panel__comment" : /^["']/.test(token) ? "replay-snippet-panel__string" : /^(const|let|return|async|await|function|export|import|from|public|private|class|final|void|new|if|throw|SELECT|FROM|WHERE|AND|true|false|null)$/.test(token) ? "replay-snippet-panel__keyword" : undefined}>{token}</span>);
}
