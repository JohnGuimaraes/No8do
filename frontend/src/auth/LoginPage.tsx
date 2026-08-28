import { type FormEvent, type ReactNode, useState } from "react";
import { ArrowRight, GoogleLogo } from "@phosphor-icons/react";
import no8doIcon from "@/assets/logo/no8do-icone.png";
import { Button } from "@/components/ui/button";
import { useAuth } from "@/auth/AuthContext";
import { WorkspaceNodeGraphic } from "@/components/visual/WorkspaceNodeGraphic";
import { getApiUrl } from "@/lib/api";

type LoginPageProps = {
  onShowRegister: () => void;
  onShowForgotPassword: () => void;
  googleError?: string | null;
};

export function LoginPage({ onShowRegister, onShowForgotPassword, googleError = null }: LoginPageProps) {
  const { login } = useAuth();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(googleError);
  const [submitting, setSubmitting] = useState(false);
  const [connectingGoogle, setConnectingGoogle] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);

    try {
      await login({ email, password });
    } catch (err) {
      setError(err instanceof Error ? err.message : "Nao foi possivel entrar.");
    } finally {
      setSubmitting(false);
    }
  }

  function handleGoogleLogin() {
    setConnectingGoogle(true);
    window.location.assign(getApiUrl("/api/auth/google"));
  }

  return (
    <AuthShell eyebrow="No8do" title="Entre no seu workspace">
      <form className="flex w-full flex-col gap-4" onSubmit={handleSubmit}>
        <Field
          label="E-mail"
          name="email"
          type="email"
          value={email}
          onChange={setEmail}
          autoComplete="email"
        />
        <Field
          label="Senha"
          name="password"
          type="password"
          value={password}
          onChange={setPassword}
          autoComplete="current-password"
        />

        {error ? <p className="text-sm text-destructive">{error}</p> : null}

        <Button type="submit" className="auth-submit-button group w-full" disabled={submitting}>
          {submitting ? "Entrando..." : "Entrar"}
          <ArrowRight className="auth-submit-button__arrow h-4 w-4" />
        </Button>
      </form>

      <div className="flex items-center gap-3 text-xs text-muted-foreground" aria-hidden="true">
        <span className="h-px flex-1 bg-border" />
        <span>ou</span>
        <span className="h-px flex-1 bg-border" />
      </div>

      <Button type="button" variant="outline" className="w-full" onClick={handleGoogleLogin} disabled={submitting || connectingGoogle}>
        <GoogleLogo className="h-4 w-4" />
        {connectingGoogle ? "Conectando com Google..." : "Continuar com Google"}
      </Button>

      <div className="flex flex-col items-start gap-3">
        <button
          type="button"
          className="auth-secondary-action text-sm font-medium underline-offset-4"
          onClick={onShowForgotPassword}
        >
          Esqueci minha senha
        </button>
        <button
          type="button"
          className="auth-secondary-action text-sm font-medium underline-offset-4"
          onClick={onShowRegister}
        >
          Criar uma conta
        </button>
      </div>
    </AuthShell>
  );
}

type FieldProps = {
  label: string;
  name: string;
  type: string;
  value: string;
  autoComplete: string;
  onChange: (value: string) => void;
  readOnly?: boolean;
};

export function Field({ label, name, type, value, autoComplete, onChange, readOnly = false }: FieldProps) {
  return (
    <label className="auth-field flex flex-col gap-2 text-left text-sm font-medium text-foreground">
      {label}
      <input
        className="auth-field__input h-10 rounded-md border border-input px-3 text-sm outline-none transition-[border-color,box-shadow,background-color] placeholder:text-muted-foreground focus-visible:outline-none"
        name={name}
        type={type}
        value={value}
        autoComplete={autoComplete}
        onChange={(event) => onChange(event.target.value)}
        readOnly={readOnly}
      />
    </label>
  );
}

export function AuthShell({
  eyebrow,
  title,
  children,
}: {
  eyebrow: string;
  title: string;
  children: ReactNode;
}) {
  return (
    <main className="auth-canvas flex min-h-screen items-center px-4 py-6 sm:px-6 lg:p-10">
      <section className="auth-shell mx-auto grid w-full max-w-6xl overflow-hidden lg:grid-cols-[minmax(0,1fr)_minmax(360px,.9fr)]">
        <div className="auth-shell__content flex min-w-0 flex-col justify-center px-6 py-10 sm:px-10 lg:px-14 lg:py-16">
          <div className="auth-brand mb-10 flex items-center gap-3 text-sm font-semibold text-foreground"><span className="auth-brand__mark"><img src={no8doIcon} alt="" aria-hidden="true" /></span><span>{eyebrow}</span></div>
          <div className="max-w-md space-y-3"><p className="auth-eyebrow text-xs font-medium uppercase tracking-[.16em] text-primary"><span aria-hidden="true" />Memória operacional</p><h1 className="text-4xl font-medium tracking-tight text-foreground sm:text-5xl">{title}</h1><p className="auth-intro text-sm leading-6 text-muted-foreground">Projetos, decisões e próximos passos em um espaço que acompanha o seu ritmo.</p></div>
          <div className="mt-10 flex w-full max-w-md flex-col gap-5">{children}</div>
        </div>
        <div className="auth-node-stage hidden min-h-full lg:flex"><WorkspaceNodeGraphic className="auth-node" /><p className="auth-node-caption"><span>ideias</span><i aria-hidden="true">•</i><span>projetos</span><i aria-hidden="true">•</i><span>memória</span></p></div>
      </section>
    </main>
  );
}
