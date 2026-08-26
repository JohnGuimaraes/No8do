import { useEffect, useState } from "react";
import { CaretDown, Circle, GearSix, Question, SignOut, SlidersHorizontal, UserCircle } from "@phosphor-icons/react";
import no8doIcon from "@/assets/logo/no8do-icone.png";
import { PreferencesPage, ProfilePage } from "@/account/AccountPages";
import { LoginPage } from "@/auth/LoginPage";
import { RegisterPage } from "@/auth/RegisterPage";
import { useAuth } from "@/auth/AuthContext";
import { Button } from "@/components/ui/button";
import { HelpPage } from "@/help/HelpPage";
import { getApiUrl } from "@/lib/api";
import { WorkspacePanel } from "@/workspaces/WorkspacePanel";
import { WorkspaceSettingsPage } from "@/workspaces/WorkspaceSettingsPage";
import { WorkspaceSwitcher } from "@/workspaces/WorkspaceSwitcher";
import { createWorkspace, listWorkspaces, type Workspace } from "@/workspaces/workspaceApi";

type AuthView = "login" | "register";
type HealthStatus = "checking" | "online" | "offline";
type AppRoute =
  | { view: "workspace" }
  | { view: "profile" }
  | { view: "preferences" }
  | { view: "help" }
  | { view: "workspace-settings"; workspaceId: string };
function readRoute(): AppRoute {
  const pathname = window.location.pathname.replace(/\/+$/, "") || "/";
  if (pathname === "/account/profile") return { view: "profile" };
  if (pathname === "/account/preferences") return { view: "preferences" };
  if (pathname === "/help") return { view: "help" };
  const workspaceSettings = pathname.match(/^\/w\/([^/]+)\/settings$/);
  if (workspaceSettings) return { view: "workspace-settings", workspaceId: decodeURIComponent(workspaceSettings[1]) };
  return { view: "workspace" };
}

function isWorkspaceManager(workspace: Workspace | null) {
  return workspace?.role === "OWNER" || workspace?.role === "ADMIN";
}

function syncStoredTheme() {
  const preference = localStorage.getItem("no8do-theme") || "system";
  const dark = preference === "dark" || (preference === "system" && window.matchMedia("(prefers-color-scheme: dark)").matches);
  document.documentElement.classList.toggle("dark", dark);
  document.documentElement.style.colorScheme = dark ? "dark" : "light";
}

function useBackendHealth() {
  const [backendStatus, setBackendStatus] = useState<HealthStatus>("checking");
  useEffect(() => {
    let cancelled = false;
    fetch(getApiUrl("/api/health"), { credentials: "include" })
      .then((response) => { if (!response.ok) throw new Error("Backend offline"); if (!cancelled) setBackendStatus("online"); })
      .catch(() => { if (!cancelled) setBackendStatus("offline"); });
    return () => { cancelled = true; };
  }, []);
  return backendStatus;
}

function App() {
  const { status, user, logout } = useAuth();
  const [authView, setAuthView] = useState<AuthView>("login");
  const [loggingOut, setLoggingOut] = useState(false);
  const [workspaces, setWorkspaces] = useState<Workspace[]>([]);
  const [activeWorkspaceId, setActiveWorkspaceId] = useState<string | null>(null);
  const [loadingWorkspaces, setLoadingWorkspaces] = useState(true);
  const [creatingWorkspace, setCreatingWorkspace] = useState(false);
  const [workspaceError, setWorkspaceError] = useState<string | null>(null);
  const [route, setRoute] = useState<AppRoute>(() => readRoute());
  const [accountMenuOpen, setAccountMenuOpen] = useState(false);
  const backendStatus = useBackendHealth();
  const activeWorkspace = workspaces.find((workspace) => workspace.id === activeWorkspaceId) ?? null;
  const routedWorkspace = route.view === "workspace-settings" ? workspaces.find((workspace) => workspace.id === route.workspaceId) ?? null : activeWorkspace;

  function navigate(path: string) {
    window.history.pushState({}, "", path);
    setRoute(readRoute());
  }

  useEffect(() => {
    if (status !== "authenticated") return;
    let active = true;
    async function loadWorkspaces() {
      setLoadingWorkspaces(true);
      setWorkspaceError(null);
      try {
        const items = await listWorkspaces();
        if (active) { setWorkspaces(items); setActiveWorkspaceId(items[0]?.id ?? null); }
      } catch (error) {
        if (active) setWorkspaceError(error instanceof Error ? error.message : "Nao foi possivel carregar workspaces.");
      } finally { if (active) setLoadingWorkspaces(false); }
    }
    void loadWorkspaces();
    return () => { active = false; };
  }, [status]);

  useEffect(() => {
    function handlePopState() {
      setRoute(readRoute());
    }
    window.addEventListener("popstate", handlePopState);
    return () => window.removeEventListener("popstate", handlePopState);
  }, []);

  useEffect(() => {
    const mediaQuery = window.matchMedia("(prefers-color-scheme: dark)");
    syncStoredTheme();
    mediaQuery.addEventListener("change", syncStoredTheme);
    return () => mediaQuery.removeEventListener("change", syncStoredTheme);
  }, []);

  useEffect(() => {
    if (route.view !== "workspace-settings" || loadingWorkspaces) return;
    const workspace = workspaces.find((item) => item.id === route.workspaceId) ?? null;
    if (!workspace || !isWorkspaceManager(workspace)) {
      window.history.replaceState({}, "", "/");
      setRoute({ view: "workspace" });
      return;
    }
    setActiveWorkspaceId(workspace.id);
  }, [loadingWorkspaces, route, workspaces]);

  async function handleLogout() {
    setAccountMenuOpen(false);
    setLoggingOut(true);
    try { await logout(); } finally { setLoggingOut(false); }
  }

  function handleWorkspaceSelect(workspaceId: string) {
    setActiveWorkspaceId(workspaceId);
    if (route.view === "workspace-settings") navigate(`/w/${workspaceId}/settings`);
  }

  function returnToWorkspace() {
    navigate("/");
  }

  function openWorkspaceSettings() {
    if (!activeWorkspace || !isWorkspaceManager(activeWorkspace)) return;
    navigate(`/w/${activeWorkspace.id}/settings`);
  }

  async function handleCreateWorkspace(name: string) {
    setCreatingWorkspace(true);
    setWorkspaceError(null);
    try {
      const workspace = await createWorkspace(name);
      setWorkspaces((current) => [...current, workspace]);
      setActiveWorkspaceId(workspace.id);
    } catch (error) {
      setWorkspaceError(error instanceof Error ? error.message : "Nao foi possivel criar o workspace.");
      throw error;
    } finally { setCreatingWorkspace(false); }
  }

  if (status === "loading") return <main className="auth-canvas flex min-h-screen items-center justify-center px-6 text-sm text-muted-foreground"><Circle weight="fill" className="mr-3 h-2 w-2 animate-pulse" />Carregando sessao...</main>;
  if (status === "unauthenticated") return authView === "login" ? <LoginPage onShowRegister={() => setAuthView("register")} /> : <RegisterPage onShowLogin={() => setAuthView("login")} />;

  return (
    <main className="no8do-canvas min-h-screen">
      <header className="app-topbar"><div className="app-topbar__inner">
        <div className="flex min-w-0 items-center gap-2 sm:gap-3"><div className="brand-mark"><img src={no8doIcon} alt="" aria-hidden="true" /></div><span className="hidden text-base font-semibold tracking-tight text-foreground sm:block">No8do</span><span className="hidden h-6 w-px bg-border/80 sm:block" />
          <WorkspaceSwitcher workspaces={workspaces} activeWorkspaceId={activeWorkspaceId} loading={loadingWorkspaces} creating={creatingWorkspace} error={workspaceError} onSelect={handleWorkspaceSelect} onCreate={handleCreateWorkspace} />
          {isWorkspaceManager(activeWorkspace) ? <Button type="button" variant="ghost" size="icon" onClick={openWorkspaceSettings} aria-label="Configurações do workspace" title="Configurações do workspace"><GearSix className="h-4 w-4" /></Button> : null}
        </div>
        <div className="flex items-center gap-1 sm:gap-2"><span className={`hidden items-center gap-1.5 text-xs text-muted-foreground md:inline-flex ${backendStatus === "online" ? "text-emerald-700" : ""}`}><Circle weight="fill" className="h-2 w-2" />{backendStatus === "online" ? "Conectado" : backendStatus === "checking" ? "Verificando" : "Offline"}</span><Button type="button" variant="ghost" size="sm" className="hidden sm:inline-flex" onClick={() => navigate("/help")}><Question className="h-4 w-4" />Ajuda</Button><div className="relative"><Button type="button" variant="ghost" size="sm" className="gap-1.5" onClick={() => setAccountMenuOpen((open) => !open)} aria-haspopup="menu" aria-expanded={accountMenuOpen} aria-label="Menu da conta"><UserCircle className="h-5 w-5" /><span className="hidden max-w-32 truncate sm:inline">{user?.name ?? "Conta"}</span><CaretDown className="h-3.5 w-3.5" /></Button>{accountMenuOpen ? <div className="account-menu" role="menu" aria-label="Menu da conta"><button type="button" role="menuitem" className="account-menu__item" onClick={() => { navigate("/account/profile"); setAccountMenuOpen(false); }}><UserCircle className="h-4 w-4" />Perfil</button><button type="button" role="menuitem" className="account-menu__item" onClick={() => { navigate("/account/preferences"); setAccountMenuOpen(false); }}><SlidersHorizontal className="h-4 w-4" />Preferências</button><button type="button" role="menuitem" className="account-menu__item sm:hidden" onClick={() => { navigate("/help"); setAccountMenuOpen(false); }}><Question className="h-4 w-4" />Ajuda</button><div className="my-1 border-t border-border/70" /><button type="button" role="menuitem" className="account-menu__item" onClick={() => void handleLogout()} disabled={loggingOut}><SignOut className="h-4 w-4" />{loggingOut ? "Saindo..." : "Sair"}</button></div> : null}</div></div>
      </div></header>
      <section className="app-frame">
        {route.view === "profile" ? <ProfilePage user={user} onReturnToWorkspace={returnToWorkspace} onLogout={() => void handleLogout()} loggingOut={loggingOut} /> : null}
        {route.view === "preferences" ? <PreferencesPage user={user} onReturnToWorkspace={returnToWorkspace} /> : null}
        {route.view === "help" ? <HelpPage onReturnToWorkspace={returnToWorkspace} /> : null}
        {route.view === "workspace-settings" && routedWorkspace ? <WorkspaceSettingsPage workspace={routedWorkspace} onReturnToWorkspace={returnToWorkspace} /> : null}
        {route.view === "workspace-settings" && !routedWorkspace ? <div className="editorial-empty-state"><p className="text-sm text-muted-foreground">Carregando workspace...</p></div> : null}
        {route.view === "workspace" ? <WorkspacePanel workspace={activeWorkspace} /> : null}
      </section>
    </main>
  );
}

export default App;
