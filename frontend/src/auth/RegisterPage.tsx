import { FormEvent, useState } from "react";
import { ArrowRight } from "@phosphor-icons/react";
import { Button } from "@/components/ui/button";
import { AuthShell, Field } from "@/auth/LoginPage";
import { useAuth } from "@/auth/AuthContext";

type RegisterPageProps = {
  onShowLogin: () => void;
};

export function RegisterPage({ onShowLogin }: RegisterPageProps) {
  const { register } = useAuth();
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);

    try {
      await register({ name, email, password });
    } catch (err) {
      setError(err instanceof Error ? err.message : "Nao foi possivel criar a conta.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <AuthShell eyebrow="No8do" title="Crie seu acesso">
      <form className="flex w-full flex-col gap-4" onSubmit={handleSubmit}>
        <Field
          label="Nome"
          name="name"
          type="text"
          value={name}
          onChange={setName}
          autoComplete="name"
        />
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
          autoComplete="new-password"
        />

        {error ? <p className="text-sm text-destructive">{error}</p> : null}

        <Button type="submit" className="w-full" disabled={submitting}>
          {submitting ? "Criando..." : "Criar conta"}
          <ArrowRight className="h-4 w-4" />
        </Button>
      </form>

      <button
        type="button"
        className="text-sm font-medium text-foreground underline-offset-4 hover:underline"
        onClick={onShowLogin}
      >
        Ja tenho uma conta
      </button>
    </AuthShell>
  );
}
