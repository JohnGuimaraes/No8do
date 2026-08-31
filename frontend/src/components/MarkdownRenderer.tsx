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
      code: ({ className: codeClassName, children }) => codeClassName ? <code className={codeClassName}>{children}</code> : <code className="rounded bg-muted px-1.5 py-0.5 font-mono text-[0.9em]">{children}</code>,
    }}>{content}</ReactMarkdown>
  </div>;
}
