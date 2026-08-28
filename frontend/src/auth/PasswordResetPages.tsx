import { type FormEvent, useState } from "react";
import { ArrowRight } from "@phosphor-icons/react";
import { AuthShell, Field } from "@/auth/LoginPage";
import { Button } from "@/components/ui/button";
import { apiRequest } from "@/lib/api";

type PasswordForgotPageProps = {
  onShowLogin: () => void;
};

export function PasswordForgotPage({ onShowLogin }: PasswordForgotPageProps) {
  const [email, setEmail] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitted, setSubmitted] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);

    try {
      await apiRequest<{ status: string }>("/api/auth/password/forgot", {
        method: "POST",
        body: { email },
      });
      setSubmitted(true);
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "Nao foi possivel enviar a solicitacao.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <AuthShell eyebrow="No8do" title="Redefina sua senha">
      {submitted ? (
        <div className="flex flex-col gap-5">
          <p className="text-sm leading-6 text-muted-foreground">Se existir uma conta com esse e-mail, enviaremos as instrucoes para redefinir sua senha.</p>
          <Button type="button" className="auth-submit-button group w-full" onClick={onShowLogin}>
            Voltar para entrar
            <ArrowRight className="auth-submit-button__arrow h-4 w-4" />
          </Button>
        </div>
      ) : (
        <>
          <form className="flex w-full flex-col gap-4" onSubmit={handleSubmit}>
            <Field label="E-mail" name="email" type="email" value={email} onChange={setEmail} autoComplete="email" />
            {error ? <p className="text-sm text-destructive">{error}</p> : null}
            <Button type="submit" className="auth-submit-button group w-full" disabled={submitting}>
              {submitting ? "Enviando..." : "Enviar instrucoes"}
              <ArrowRight className="auth-submit-button__arrow h-4 w-4" />
            </Button>
          </form>
          <button type="button" className="auth-secondary-action text-sm font-medium underline-offset-4" onClick={onShowLogin}>
            Voltar para entrar
          </button>
        </>
      )}
    </AuthShell>
  );
}

type PasswordResetPageProps = {
  onShowLogin: () => void;
  onResetComplete: () => void;
};

export function PasswordResetPage({ onShowLogin, onResetComplete }: PasswordResetPageProps) {
  const [token] = useState(() => new URLSearchParams(window.location.search).get("token") ?? "");
  const [password, setPassword] = useState("");
  const [confirmation, setConfirmation] = useState("");
  const [error, setError] = useState<string | null>(token ? null : "O link de redefinicao e invalido ou incompleto.");
  const [completed, setCompleted] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!token) return;
    if (password !== confirmation) {
      setError("As senhas precisam ser iguais.");
      return;
    }

    setError(null);
    setSubmitting(true);
    try {
      await apiRequest<{ status: string }>("/api/auth/password/reset", {
        method: "POST",
        body: { token, password },
      });
      setCompleted(true);
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "Nao foi possivel redefinir a senha.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <AuthShell eyebrow="No8do" title="Escolha uma nova senha">
      {completed ? (
        <div className="flex flex-col gap-5">
          <p className="text-sm leading-6 text-muted-foreground">Senha redefinida com sucesso. Entre com sua nova senha para continuar.</p>
          <Button type="button" className="auth-submit-button group w-full" onClick={onResetComplete}>
            Ir para entrar
            <ArrowRight className="auth-submit-button__arrow h-4 w-4" />
          </Button>
        </div>
      ) : (
        <>
          <form className="flex w-full flex-col gap-4" onSubmit={handleSubmit}>
            <Field label="Nova senha" name="password" type="password" value={password} onChange={setPassword} autoComplete="new-password" />
            <Field label="Confirmar nova senha" name="confirmation" type="password" value={confirmation} onChange={setConfirmation} autoComplete="new-password" />
            {error ? <p className="text-sm text-destructive">{error}</p> : null}
            <Button type="submit" className="auth-submit-button group w-full" disabled={submitting || !token}>
              {submitting ? "Redefinindo..." : "Redefinir senha"}
              <ArrowRight className="auth-submit-button__arrow h-4 w-4" />
            </Button>
          </form>
          <button type="button" className="auth-secondary-action text-sm font-medium underline-offset-4" onClick={onShowLogin}>
            Voltar para entrar
          </button>
        </>
      )}
    </AuthShell>
  );
}
