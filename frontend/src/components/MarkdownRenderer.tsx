import { useState } from "react";
import { Check, Copy } from "@phosphor-icons/react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";

function safeUrl(value: string) {
  try {
    const url = new URL(value, window.location.origin);
    return ["http:", "https:", "mailto:"].includes(url.protocol) ? value : "";
  } catch {
    return "";
  }
}

export function MarkdownRenderer({ content, className = "" }: { content: string; className?: string }) {
  return <div className={`markdown-content break-words text-sm leading-6 text-foreground ${className}`}>
    <ReactMarkdown remarkPlugins={[remarkGfm]} urlTransform={safeUrl} components={{
      a: ({ href, children }) => href ? <a href={href} target="_blank" rel="noopener noreferrer" className="font-medium text-primary underline underline-offset-4">{children}</a> : <>{children}</>,
      pre: ({ children }) => <>{children}</>,
      code: ({ className: codeClassName, children }) => codeClassName ? <MarkdownCodeBlock className={codeClassName}>{String(children).replace(/\n$/, "")}</MarkdownCodeBlock> : <code className="markdown-inline-code">{children}</code>,
    }}>{content}</ReactMarkdown>
  </div>;
}

export function MarkdownCodeBlock({ className, children, language: explicitLanguage }: { className?: string; children: string; language?: string }) {
  const [copied, setCopied] = useState(false);
  const language = explicitLanguage || className?.replace("language-", "") || "texto";
  async function copy() {
    try { await navigator.clipboard.writeText(children); setCopied(true); window.setTimeout(() => setCopied(false), 1600); } catch { setCopied(false); }
  }
  return <div className="markdown-code-block"><div className="markdown-code-block__header"><span>{language}</span><button type="button" onClick={() => void copy()} aria-label={copied ? "Código copiado" : "Copiar código"}>{copied ? <Check className="h-3.5 w-3.5" /> : <Copy className="h-3.5 w-3.5" />}{copied ? "Copiado" : "Copiar"}</button></div><pre><code className={className}>{children}</code></pre></div>;
}
