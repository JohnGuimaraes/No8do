import { useEffect, useState } from "react";
import { CaretDown, Circle, GearSix, Question, SignOut, SlidersHorizontal, UserCircle } from "@phosphor-icons/react";
import no8doIcon from "@/assets/logo/no8do-icone.png";
import { PreferencesPage, ProfilePage } from "@/account/AccountPages";
import { LoginPage } from "@/auth/LoginPage";
import { PasswordForgotPage, PasswordResetPage } from "@/auth/PasswordResetPages";
import { RegisterPage } from "@/auth/RegisterPage";
import { useAuth } from "@/auth/AuthContext";
import { Button } from "@/components/ui/button";
import { HelpPage } from "@/help/HelpPage";
import { getApiUrl } from "@/lib/api";
import { WorkspacePanel } from "@/workspaces/WorkspacePanel";
import { WorkspaceSettingsPage } from "@/workspaces/WorkspaceSettingsPage";
import { WorkspaceInvitePage } from "@/workspaces/WorkspaceInvitePage";
import { WorkspaceSwitcher } from "@/workspaces/WorkspaceSwitcher";
import { createWorkspace, listWorkspaces, type Workspace } from "@/workspaces/workspaceApi";

type WorkspaceSection = "overview" | "development" | "work-items" | "projects" | "library" | "ideas";

type HealthStatus = "checking" | "online" | "offline";
type AppRoute =
  | { view: "workspace"; workspaceId?: string; section: WorkspaceSection }
  | { view: "project-details"; workspaceId: string; projectId: string; section: WorkspaceSection }
  | { view: "login" }
  | { view: "register" }
  | { view: "forgot-password" }
  | { view: "profile" }
  | { view: "preferences" }
  | { view: "help" }
  | { view: "password-reset" }
  | { view: "invite"; token: string }
  | { view: "workspace-settings"; workspaceId: string };
function readRoute(): AppRoute {
  const pathname = window.location.pathname.replace(/\/+$/, "") || "/";
  if (pathname === "/login") return { view: "login" };
  if (pathname === "/register") return { view: "register" };
  if (pathname === "/forgot-password") return { view: "forgot-password" };
  if (pathname === "/account/profile") return { view: "profile" };
  if (pathname === "/account/preferences") return { view: "preferences" };
  if (pathname === "/help") return { view: "help" };
  if (pathname === "/reset-password") return { view: "password-reset" };
  if (pathname === "/invite") return { view: "invite", token: new URLSearchParams(window.location.search).get("token") ?? "" };
  const workspaceSettings = pathname.match(/^\/w\/([^/]+)\/settings$/);
  if (workspaceSettings) return { view: "workspace-settings", workspaceId: decodeURIComponent(workspaceSettings[1]) };
  const projectDetails = pathname.match(/^\/w\/([^/]+)\/projects\/([^/]+)$/);
  if (projectDetails) {
    return {
      view: "project-details",
      workspaceId: decodeURIComponent(projectDetails[1]),
      projectId: decodeURIComponent(projectDetails[2]),
      section: readWorkspaceSection(new URLSearchParams(window.location.search).get("from")),
    };
  }
  const workspaceRoute = pathname.match(/^\/w\/([^/]+)(?:\/(acervo|ideias|desenvolvimento|pendencias|finalizados))?$/);
  if (workspaceRoute) {
    return {
      view: "workspace",
      workspaceId: decodeURIComponent(workspaceRoute[1]),
      section: readWorkspaceSection(workspaceRoute[2]),
    };
  }
  return { view: "workspace", section: "overview" };
}

function readWorkspaceSection(value: string | null | undefined): WorkspaceSection {
  switch (value) {
    case "acervo": return "library";
    case "ideias": return "ideas";
    case "desenvolvimento": return "development";
    case "pendencias": return "work-items";
    case "finalizados": return "projects";
    default: return "overview";
  }
}

function workspacePath(workspaceId: string, section: WorkspaceSection) {
  const segment = {
    overview: "",
    library: "/acervo",
    ideas: "/ideias",
    development: "/desenvolvimento",
    "work-items": "/pendencias",
    projects: "/finalizados",
  }[section];
  return `/w/${encodeURIComponent(workspaceId)}${segment}`;
}

function isAuthenticationRoute(route: AppRoute) {
  return route.view === "login"
    || route.view === "register"
    || route.view === "forgot-password"
    || route.view === "password-reset";
}

function getGoogleAuthenticationError() {
  switch (new URLSearchParams(window.location.search).get("authError")) {
    case "google-account-exists":
      return "Ja existe uma conta com este e-mail. Entre com e-mail e senha.";
    case "google-registration-disabled":
      return "O cadastro com Google esta desativado no momento.";
    case "google-unavailable":
      return "A entrada com Google esta indisponivel no momento.";
    case "google":
      return "Nao foi possivel entrar com Google. Tente novamente.";
    default:
      return null;
  }
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
  const routedWorkspaceId = route.view === "workspace" || route.view === "project-details" || route.view === "workspace-settings"
    ? route.workspaceId
    : activeWorkspaceId;
  const routedWorkspace = route.view === "workspace" || route.view === "project-details" || route.view === "workspace-settings"
    ? workspaces.find((workspace) => workspace.id === routedWorkspaceId) ?? null
    : activeWorkspace;

  function navigate(path: string) {
    window.history.pushState({}, "", path);
    setRoute(readRoute());
  }

  function replaceNavigation(path: string) {
    window.history.replaceState({}, "", path);
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
    if (status === "unauthenticated" && !isAuthenticationRoute(route) && route.view !== "invite") {
      const search = new URLSearchParams(window.location.search);
      const googleError = search.get("authError");
      replaceNavigation(googleError ? `/login?authError=${encodeURIComponent(googleError)}` : "/login");
    }
    const inviteToken = new URLSearchParams(window.location.search).get("invite");
    if (status === "authenticated" && route.view === "login" && inviteToken) {
      replaceNavigation(`/invite?token=${encodeURIComponent(inviteToken)}`);
      return;
    }
    if (status === "authenticated" && isAuthenticationRoute(route)) {
      replaceNavigation("/");
    }
  }, [route, status]);

  useEffect(() => {
    const mediaQuery = window.matchMedia("(prefers-color-scheme: dark)");
    syncStoredTheme();
    mediaQuery.addEventListener("change", syncStoredTheme);
    return () => mediaQuery.removeEventListener("change", syncStoredTheme);
  }, []);

  useEffect(() => {
    if (loadingWorkspaces || status !== "authenticated") return;
    if (route.view !== "workspace" && route.view !== "project-details" && route.view !== "workspace-settings") return;
    const workspace = workspaces.find((item) => item.id === route.workspaceId) ?? null;
    if (!workspace) {
      const fallback = workspaces[0];
      if (fallback) replaceNavigation(workspacePath(fallback.id, "overview"));
      return;
    }
    if (route.view === "workspace-settings" && !isWorkspaceManager(workspace)) {
      replaceNavigation(workspacePath(workspace.id, "overview"));
      return;
    }
    setActiveWorkspaceId(workspace.id);
  }, [loadingWorkspaces, route, status, workspaces]);

  async function handleLogout() {
    setAccountMenuOpen(false);
    setLoggingOut(true);
    try { await logout(); } finally { setLoggingOut(false); }
  }

  function handleWorkspaceSelect(workspaceId: string) {
    setActiveWorkspaceId(workspaceId);
    if (route.view === "workspace-settings") {
      navigate(`/w/${workspaceId}/settings`);
      return;
    }
    if (route.view === "workspace" || route.view === "project-details") {
      navigate(workspacePath(workspaceId, route.section));
    }
  }

  function returnToWorkspace() {
    if (activeWorkspaceId) navigate(workspacePath(activeWorkspaceId, "overview"));
    else navigate("/");
  }

  function openWorkspaceSettings() {
    if (!activeWorkspace || !isWorkspaceManager(activeWorkspace)) return;
    navigate(`/w/${activeWorkspace.id}/settings`);
  }

  function navigateWorkspaceSection(section: WorkspaceSection) {
    if (activeWorkspaceId) navigate(workspacePath(activeWorkspaceId, section));
  }

  function openWorkspaceProject(projectId: string, section: WorkspaceSection) {
    if (!activeWorkspaceId) return;
    navigate(`/w/${encodeURIComponent(activeWorkspaceId)}/projects/${encodeURIComponent(projectId)}?from=${encodeURIComponent(section)}`);
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
  if (status === "unauthenticated") {
    if (route.view === "invite") {
      return <WorkspaceInvitePage token={route.token} onAccepted={(workspaceId) => replaceNavigation(workspacePath(workspaceId, "overview"))} onShowLogin={() => navigate(`/login?invite=${encodeURIComponent(route.token)}`)} />;
    }
    if (route.view === "password-reset") {
      return <PasswordResetPage onShowLogin={() => navigate("/login")} onResetComplete={() => replaceNavigation("/login")} />;
    }
    if (route.view === "register") {
      return <RegisterPage onShowLogin={() => navigate("/login")} />;
    }
    if (route.view === "forgot-password") {
      return <PasswordForgotPage onShowLogin={() => navigate("/login")} />;
    }
    return <LoginPage
      onShowRegister={() => navigate("/register")}
      onShowForgotPassword={() => navigate("/forgot-password")}
      googleError={getGoogleAuthenticationError()}
    />;
  }

  if (route.view === "invite") {
    return <WorkspaceInvitePage token={route.token} onAccepted={(workspaceId) => replaceNavigation(workspacePath(workspaceId, "overview"))} onShowLogin={() => navigate(`/login?invite=${encodeURIComponent(route.token)}`)} />;
  }

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
        {route.view === "workspace" || route.view === "project-details" ? <WorkspacePanel workspace={routedWorkspace} activeSection={route.section} selectedProjectId={route.view === "project-details" ? route.projectId : null} onNavigateSection={navigateWorkspaceSection} onOpenProject={openWorkspaceProject} /> : null}
      </section>
    </main>
  );
}

export default App;
