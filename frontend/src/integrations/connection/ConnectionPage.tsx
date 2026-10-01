import { type FormEvent, type ReactNode, useEffect, useRef, useState } from "react";
import { ArrowRight, Check, CheckCircle, Link, ShieldCheck, WarningCircle, XCircle } from "@phosphor-icons/react";
import { Button } from "@/components/ui/button";
import no8doIcon from "@/assets/logo/no8do-icone.png";
import { ConnectionPath, type PathPhase } from "./ConnectionPath";
import { approveConnection, connectionError, denyConnection, displayUserCode, inspectConnection, listConnectionAgents,
  normalizeUserCode, validUserCode, effectiveConnectionState, type ConnectionAgent, type ConnectionState, type Inspection } from "./connectionApi";
import "./connection.css";

const states: Record<ConnectionState, string> = {
  PENDING: "Aguardando aprovação", APPROVED: "Conexão aprovada", DENIED: "Conexão recusada",
  EXPIRED: "Solicitação expirada", CONSUMED: "Integração conectada",
};
const hosts = { CODEX: "Codex", CLAUDE: "Claude", VSCODE: "VS Code", IDE: "IDE" };
const lifecycle = { ACTIVE: "Ativo", DISABLED: "Desativado", ARCHIVED: "Arquivado" };
type SelectionStep = "WORKSPACE" | "AGENT" | "REVIEW";
const stepCopy = {
  CODE: ["Conectar integração", "Use o código exibido pela integração para iniciar a conexão com o No8do."],
  WORKSPACE: ["Escolha o Workspace", "Selecione em qual Workspace essa integração poderá atuar."],
  AGENT: ["Escolha o Agent", "Selecione um Agent existente ou crie um novo para representar essa integração."],
  REVIEW: ["Revisar conexão", "Confira os dados antes de autorizar esta conexão."],
  RECOVERY: ["Conferir conexão", "Confira o estado da solicitação antes de continuar."],
};

export function ConnectionStep({ step, children }: { step: string; children: ReactNode }) {
  const [shownStep, setShownStep] = useState(step);
  const lastContent = useRef(children);
  const panel = useRef<HTMLFieldSetElement>(null);
  const previousStep = useRef(step);
  const leaving = shownStep !== step;
  useEffect(() => { if (!leaving) lastContent.current = children; }, [children, leaving]);
  useEffect(() => {
    if (!leaving) return;
    if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) { setShownStep(step); return; }
    const timer = window.setTimeout(() => setShownStep(step), 120);
    return () => window.clearTimeout(timer);
  }, [leaving, step]);
  useEffect(() => {
    if (previousStep.current === shownStep) return;
    previousStep.current = shownStep;
    panel.current?.querySelector<HTMLHeadingElement>("h1")?.focus();
  }, [shownStep]);
  return <fieldset key={shownStep} ref={panel} className="connection-step" disabled={leaving} data-leaving={leaving}
    aria-busy={leaving}>{leaving ? lastContent.current : children}</fieldset>;
}

export function ConnectionShell({ children, path }: { children: ReactNode; path: ReactNode }) {
  return <main className="auth-canvas connection-canvas">
    <section className="connection-shell" aria-labelledby="connection-title">
      <div className="connection-content">
        <div className="connection-content-inner">
          <header className="connection-header">
          <a href="/" className="connection-brand" aria-label="No8do — início"><img src={no8doIcon} alt="" />No8do</a>
          <p className="connection-eyebrow">Integrações / conexão</p>
          </header>
          {children}
        </div>
      </div>
      <aside className="connection-visual" aria-label="Caminho da conexão">{path}</aside>
    </section>
  </main>;
}

export function ConnectionPage({ onSessionExpired }: { onSessionExpired: () => Promise<void> }) {
  const [code, setCode] = useState("");
  const [inspection, setInspection] = useState<Inspection | null>(null);
  const [workspaceId, setWorkspaceId] = useState("");
  const [agents, setAgents] = useState<ConnectionAgent[]>([]);
  const [agentId, setAgentId] = useState("");
  const [agentMode, setAgentMode] = useState<"existing" | "new">("existing");
  const [newName, setNewName] = useState("");
  const [busy, setBusy] = useState<"inspect" | "approve" | "deny" | null>(null);
  const [loadingAgents, setLoadingAgents] = useState(false);
  const [agentError, setAgentError] = useState<string | null>(null);
  const [retryAgents, setRetryAgents] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [fieldError, setFieldError] = useState(false);
  const [needsInspection, setNeedsInspection] = useState(false);
  const [now, setNow] = useState(Date.now());
  const [selectionStep, setSelectionStep] = useState<SelectionStep>("WORKSPACE");
  const lock = useRef(false);
  const mounted = useRef(true);
  const request = useRef<AbortController | null>(null);
  const input = useRef<HTMLInputElement>(null);
  const heading = useRef<HTMLHeadingElement>(null);
  const agentLoadVersion = useRef(0);
  const workspace = inspection?.eligibleWorkspaces.find(item => item.id === workspaceId);
  const agent = agents.find(item => item.id === agentId && item.workspaceId === workspaceId && item.lifecycleStatus === "ACTIVE");
  const name = newName.trim();
  const ready = Boolean(workspace && (agentMode === "new" ? name.length > 0 && name.length <= 160 : agent));
  const state = inspection ? effectiveConnectionState(inspection, now) : undefined;
  const pending = state === "PENDING";
  const integration = inspection ? inspection.displayLabel || hosts[inspection.hostType] : "Integração";
  const phase: PathPhase = error ? "ERROR" : busy === "approve" ? "APPROVING" : state && state !== "PENDING" ? state
    : ready ? "READY" : workspace ? "WORKSPACE_SELECTED" : inspection ? "VALIDATED" : "INITIAL";

  useEffect(() => {
    mounted.current = true;
    const title = document.title;
    document.title = "Conectar integração — No8do";
    return () => { mounted.current = false; request.current?.abort(); document.title = title; };
  }, []);
  useEffect(() => {
    if (!inspection || inspection.state !== "PENDING") return;
    const tick = () => setNow(Date.now());
    const timer = window.setInterval(tick, 1000);
    window.addEventListener("focus", tick);
    return () => { window.clearInterval(timer); window.removeEventListener("focus", tick); };
  }, [inspection]);
  useEffect(() => {
    const version = ++agentLoadVersion.current;
    setAgents([]); setAgentId(""); setAgentError(null); setLoadingAgents(false);
    if (!workspaceId || !pending || needsInspection) return;
    const controller = new AbortController();
    const timeout = window.setTimeout(() => controller.abort(), 20000);
    setLoadingAgents(true);
    listConnectionAgents(workspaceId, controller.signal).then(items => {
      if (version === agentLoadVersion.current && !controller.signal.aborted) setAgents(items.filter(item => item.workspaceId === workspaceId));
    }).catch(() => {
      if (version === agentLoadVersion.current && mounted.current) setAgentError("Não foi possível carregar os Agents. Tente novamente.");
    }).finally(() => {
      window.clearTimeout(timeout);
      if (version === agentLoadVersion.current && mounted.current) setLoadingAgents(false);
    });
    return () => { ++agentLoadVersion.current; controller.abort(); window.clearTimeout(timeout); };
  }, [workspaceId, pending, needsInspection, retryAgents]);

  async function perform(action: "inspect" | "approve" | "deny") {
    if (lock.current) return;
    const normalized = normalizeUserCode(code);
    if (!validUserCode(normalized)) {
      setFieldError(true); setError("Use os 8 caracteres do código exibido pela integração."); input.current?.focus(); return;
    }
    if (action !== "inspect" && (!pending || needsInspection || Date.now() >= Date.parse(inspection!.expiresAt))) return;
    if (action === "approve" && !ready) return;
    lock.current = true; setBusy(action); setError(null); setFieldError(false);
    const controller = new AbortController(); request.current = controller;
    const timeout = window.setTimeout(() => controller.abort(), 20000);
    try {
      if (action === "inspect") {
        const result = await inspectConnection(normalized, controller.signal);
        if (!mounted.current) return;
        if (!states[result.state] || !Number.isFinite(Date.parse(result.expiresAt))) throw new Error("Invalid public response");
        setInspection(result); setWorkspaceId(""); setAgents([]); setAgentId(""); setNewName("");
        setAgentMode("existing"); setNeedsInspection(false); setNow(Date.now());
        setSelectionStep("WORKSPACE");
      } else {
        const result = action === "deny" ? await denyConnection(normalized, controller.signal)
          : await approveConnection(normalized, workspaceId,
            agentMode === "new" ? { newAgent: { name } } : { existingAgentId: agent!.id }, controller.signal);
        if (!mounted.current) return;
        if (result.requestId !== inspection!.requestId || result.state !== (action === "deny" ? "DENIED" : "APPROVED")) {
          throw new Error("Unexpected decision response");
        }
        setInspection(current => current ? { ...current, state: result.state } : current);
      }
      window.requestAnimationFrame(() => { if (mounted.current) heading.current?.focus(); });
    } catch (cause) {
      if (mounted.current) { setError(connectionError(cause)); setNeedsInspection(true); }
    } finally {
      window.clearTimeout(timeout); lock.current = false;
      if (mounted.current) setBusy(null);
    }
  }
  function submit(event: FormEvent) { event.preventDefault(); void perform("inspect"); }
  const seconds = inspection ? Math.max(0, Math.ceil((Date.parse(inspection.expiresAt) - now) / 1000)) : 0;
  const step = !inspection ? "CODE" : !pending ? "COMPLETE" : needsInspection ? "RECOVERY" : selectionStep;
  const copy = step === "COMPLETE" ? [state ? states[state] : "Conexão", state === "APPROVED"
    ? "Conexão aprovada. Volte à integração para concluir a conexão."
    : state === "CONSUMED" ? "A integração foi conectada. Volte à integração para continuar."
    : state === "EXPIRED" ? "Solicite um novo código na integração." : "Esta solicitação não pode mais ser aprovada."] : stepCopy[step];
  return <ConnectionShell path={<ConnectionPath phase={phase} integration={integration} workspace={workspace?.name}
    agent={agentMode === "new" ? name || undefined : agent?.name} />}>
    <div className="connection-flow" aria-busy={busy !== null}>
      <ConnectionStep step={step}>
      <div className="connection-step-heading"><h1 ref={heading} tabIndex={-1} id="connection-title">{copy[0]}</h1><p>{copy[1]}</p></div>
      {step === "CODE" ? <form noValidate onSubmit={submit} className="connection-code-form">
        <label htmlFor="connection-code">Código de conexão</label>
        <input ref={input} id="connection-code" className="connection-code" value={displayUserCode(code)}
          onChange={event => { setCode(normalizeUserCode(event.target.value)); setFieldError(false); setError(null); }}
          autoComplete="off" autoCapitalize="characters" spellCheck={false} maxLength={24} disabled={busy !== null}
          aria-invalid={fieldError} aria-describedby={fieldError ? "connection-error" : "connection-code-help"} placeholder="XXXX-XXXX" />
        <p id="connection-code-help" className="connection-help">Cole o código exibido pela integração.</p>
        <Button type="submit" disabled={busy !== null} className="connection-primary connection-initial-cta">Validar código<ArrowRight size={18} /></Button>
      </form> : <>
        <section className="connection-identity" aria-labelledby="connection-identity-title">
          <Link size={23} aria-hidden="true" /><div><h2 id="connection-identity-title">{integration}</h2>
            <p>{inspection ? hosts[inspection.hostType] : ""}{inspection?.integrationVersion ? ` · ${inspection.integrationVersion}` : ""}</p></div>
        </section>
        <div className="connection-status" data-state={state} role="status">
          {state === "APPROVED" || state === "CONSUMED" ? <CheckCircle size={20} /> : state === "DENIED" ? <XCircle size={20} /> : <WarningCircle size={20} />}
          <span>{state ? states[state] : ""}</span>
          {pending ? <span className="connection-expiry" aria-live="off">{Math.floor(seconds / 60)}:{String(seconds % 60).padStart(2, "0")}</span> : null}
        </div>
        {pending && !needsInspection ? <>
          {step === "WORKSPACE" ? <fieldset disabled={busy !== null} className="connection-options"><legend className="sr-only">Escolha o Workspace</legend>
            {inspection!.eligibleWorkspaces.length === 0 ? <p className="connection-help">Você não possui permissão para conectar integrações em nenhum workspace.</p> : null}
            {inspection!.eligibleWorkspaces.map(item => <label className="connection-option" key={item.id} data-selected={workspaceId === item.id}>
              <input type="radio" name="connection-workspace" value={item.id} checked={workspaceId === item.id}
                onChange={() => { setWorkspaceId(item.id); setAgentId(""); setNewName(""); }} />
              <span><strong>{item.name}</strong><small>{item.role === "OWNER" ? "Proprietário" : "Administrador"}</small></span>
            </label>)}
          </fieldset> : null}
          {step === "AGENT" && workspace ? <section className="connection-agent-selection" aria-labelledby="connection-title">
            <fieldset disabled={busy !== null} className="connection-mode"><legend className="sr-only">Modo de seleção do Agent</legend>
              <label><input type="radio" name="connection-agent-mode" checked={agentMode === "existing"} onChange={() => setAgentMode("existing")} />Usar existente</label>
              <label><input type="radio" name="connection-agent-mode" checked={agentMode === "new"} onChange={() => setAgentMode("new")} />Criar novo</label>
            </fieldset>
            {agentMode === "new" ? <div className="connection-new-agent"><label htmlFor="connection-agent-name">Nome do Agent</label>
              <input id="connection-agent-name" className="auth-field__input" value={newName} maxLength={160} disabled={busy !== null}
                onChange={event => setNewName(event.target.value)} aria-describedby="connection-new-agent-help" />
              <p id="connection-new-agent-help" className="connection-help">O Agent será criado somente ao conectar.</p></div> :
              <fieldset disabled={busy !== null} className="connection-options" aria-busy={loadingAgents}><legend className="sr-only">Agents existentes</legend>
                {loadingAgents ? <p role="status" className="connection-help">Carregando Agents…</p> : agentError ? <div role="alert"><p>{agentError}</p>
                  <Button variant="outline" onClick={() => setRetryAgents(value => value + 1)}>Tentar novamente</Button></div> :
                  agents.length === 0 ? <p className="connection-help">Nenhum Agent neste Workspace. Você pode criar um novo ao conectar.</p> : agents.map(item =>
                    <label className="connection-option" key={item.id} data-selected={agentId === item.id} data-unavailable={item.lifecycleStatus !== "ACTIVE"}>
                      <input type="radio" name="connection-agent" checked={agentId === item.id} disabled={item.lifecycleStatus !== "ACTIVE"}
                        onChange={() => setAgentId(item.id)} />
                      <span><strong>{item.name}</strong><small>{lifecycle[item.lifecycleStatus]}{item.providerDescriptor ? ` · ${item.providerDescriptor}` : ""}</small></span>
                    </label>)}
              </fieldset>}
          </section> : null}
          {step === "REVIEW" && ready ? <section className="connection-review" aria-labelledby="connection-review-title"><h2 id="connection-review-title" className="sr-only">Dados da conexão</h2>
            <p>{integration} → {workspace!.name} → {agentMode === "new" ? name : agent!.name}</p>
            <h3>Recursos autorizados</h3><ul>{["Vincular este Agent ao Workspace", "Criar e manter sessões da integração", "Registrar presença e atividade operacional",
              "Usar somente capabilities liberadas para este Agent", "Consultar Replays quando permitido"].map(text => <li key={text}><Check size={16} aria-hidden="true" />{text}</li>)}</ul>
            <p className="connection-help">O Agent continua limitado pelas permissões, capabilities e políticas configuradas no No8do.</p>
          </section> : null}
          {step === "REVIEW" ? <p className="connection-help">Recusar encerra esta solicitação.</p> : null}
          <div className="connection-actions">
            {step !== "REVIEW" ? <Button variant="ghost" className="connection-deny" disabled={busy !== null} onClick={() => void perform("deny")}>Recusar</Button> : null}
            {step !== "WORKSPACE" ? <Button variant="ghost" disabled={busy !== null} onClick={() => setSelectionStep(step === "REVIEW" ? "AGENT" : "WORKSPACE")}>Voltar</Button> : null}
            {step === "WORKSPACE" ? <Button className="connection-primary" disabled={!workspace || busy !== null} onClick={() => setSelectionStep("AGENT")}>Continuar<ArrowRight size={18} /></Button> : null}
            {step === "AGENT" ? <Button className="connection-primary" disabled={!ready || busy !== null} onClick={() => setSelectionStep("REVIEW")}>Revisar conexão<ArrowRight size={18} /></Button> : null}
          </div>
          {step === "REVIEW" ? <>
          <div className="connection-actions"><Button variant="outline" className="connection-deny" disabled={busy !== null} onClick={() => void perform("deny")}>Recusar</Button>
            <Button className="connection-primary" disabled={!ready || busy !== null} onClick={() => void perform("approve")}>Conectar<ArrowRight size={18} /></Button></div>
          </> : null}
        </> : null}
        {(needsInspection && pending) || state === "APPROVED" ? <Button variant="outline" disabled={busy !== null} onClick={() => void perform("inspect")}>Conferir estado novamente</Button> : null}
        <Button variant="ghost" disabled={busy !== null} onClick={() => { setInspection(null); setWorkspaceId(""); setAgents([]); setAgentId(""); setCode(""); setError(null); setNeedsInspection(false); }}>Usar outro código</Button>
      </>}
      </ConnectionStep>
      <div className="connection-feedback" aria-live="polite">{busy ? <p role="status">{busy === "inspect" ? "Conferindo solicitação…" : busy === "approve" ? "Aprovando conexão…" : "Recusando solicitação…"}</p> : null}
        {error ? <p id="connection-error" role="alert">{error}</p> : null}</div>
      {error === "Sua sessão terminou. Entre novamente para continuar." ? <Button variant="outline" onClick={() => void onSessionExpired()}>Entrar novamente</Button> : null}
      <p className="connection-trust"><ShieldCheck size={20} aria-hidden="true" /><span>A integração recebe uma identidade própria no No8do. Suas credenciais pessoais não são compartilhadas.</span></p>
    </div>
  </ConnectionShell>;
}
