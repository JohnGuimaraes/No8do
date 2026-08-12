import { type FormEvent, type ReactNode, useState } from "react";
import { ArrowRight, Sparkle } from "@phosphor-icons/react";
import { Button } from "@/components/ui/button";
import { useAuth } from "@/auth/AuthContext";

type LoginPageProps = {
  onShowRegister: () => void;
};

export function LoginPage({ onShowRegister }: LoginPageProps) {
  const { login } = useAuth();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

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

        <Button type="submit" className="w-full" disabled={submitting}>
          {submitting ? "Entrando..." : "Entrar"}
          <ArrowRight className="h-4 w-4" />
        </Button>
      </form>

      <button
        type="button"
        className="text-sm font-medium text-foreground underline-offset-4 hover:underline"
        onClick={onShowRegister}
      >
        Criar uma conta
      </button>
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
};

export function Field({ label, name, type, value, autoComplete, onChange }: FieldProps) {
  return (
    <label className="flex flex-col gap-2 text-left text-sm font-medium text-foreground">
      {label}
      <input
        className="h-10 rounded-md border border-input bg-background px-3 text-sm outline-none ring-offset-background transition-shadow placeholder:text-muted-foreground focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
        name={name}
        type={type}
        value={value}
        autoComplete={autoComplete}
        onChange={(event) => onChange(event.target.value)}
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
    <main className="flex min-h-screen items-center justify-center bg-background px-6 py-10">
      <section className="flex w-full max-w-sm flex-col items-center gap-6 text-center">
        <div className="flex items-center gap-2 rounded-full border border-border bg-card px-4 py-1.5 shadow-sm">
          <Sparkle weight="fill" className="h-4 w-4 text-primary" />
          <span className="text-sm font-medium text-card-foreground">{eyebrow}</span>
        </div>
        <div className="space-y-2">
          <h1 className="text-3xl font-semibold tracking-tight text-foreground">{title}</h1>
        </div>
        {children}
      </section>
    </main>
  );
}
