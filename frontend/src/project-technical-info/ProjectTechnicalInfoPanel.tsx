import { FormEvent, useEffect, useState } from "react";
import { PencilSimple, FloppyDisk } from "@phosphor-icons/react";
import { Button } from "@/components/ui/button";
import {
  getProjectTechnicalInfo,
  saveProjectTechnicalInfo,
  type ProjectTechnicalInfo,
} from "@/project-technical-info/technicalInfoApi";
import { ProjectGithubRepositoryPanel } from "@/project-technical-info/ProjectGithubRepositoryPanel";

type ProjectTechnicalInfoPanelProps = {
  workspaceId: string;
  projectId: string;
  canWrite: boolean;
};

const MAX_URL_LENGTH = 1000;
const MAX_STACK_LENGTH = 3000;
const MAX_LOCAL_PATH_LENGTH = 2000;
const MAX_RUN_COMMAND_LENGTH = 1000;

export function ProjectTechnicalInfoPanel({ workspaceId, projectId, canWrite }: ProjectTechnicalInfoPanelProps) {
  const [technicalInfo, setTechnicalInfo] = useState<ProjectTechnicalInfo | null>(null);
  const [loading, setLoading] = useState(true);
  const [editing, setEditing] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [repositoryUrl, setRepositoryUrl] = useState("");
  const [stack, setStack] = useState("");
  const [productionUrl, setProductionUrl] = useState("");
  const [developmentUrl, setDevelopmentUrl] = useState("");
  const [localPath, setLocalPath] = useState("");
  const [runCommand, setRunCommand] = useState("");

  useEffect(() => {
    let active = true;

    async function loadTechnicalInfo() {
      setLoading(true);
      setError(null);
      setEditing(false);

      try {
        const response = await getProjectTechnicalInfo(workspaceId, projectId);
        if (active) {
          setTechnicalInfo(response);
          fillForm(response);
        }
      } catch (err) {
        if (active) {
          setError(err instanceof Error ? err.message : "Nao foi possivel carregar as informacoes tecnicas.");
        }
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    }

    void loadTechnicalInfo();

    return () => {
      active = false;
    };
  }, [workspaceId, projectId]);

  function fillForm(info: ProjectTechnicalInfo) {
    setRepositoryUrl(info.repositoryUrl ?? "");
    setStack(info.stack ?? "");
    setProductionUrl(info.productionUrl ?? "");
    setDevelopmentUrl(info.developmentUrl ?? "");
    setLocalPath(info.localPath ?? "");
    setRunCommand(info.runCommand ?? "");
    setFormError(null);
  }

  function cancelEditing() {
    if (technicalInfo) {
      fillForm(technicalInfo);
    }
    setEditing(false);
    setFormError(null);
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    const validationError = validateForm();
    if (validationError) {
      setFormError(validationError);
      return;
    }

    setSaving(true);
    setFormError(null);

    try {
      const response = await saveProjectTechnicalInfo(workspaceId, projectId, {
        repositoryUrl: repositoryUrl.trim(),
        stack: stack.trim(),
        productionUrl: productionUrl.trim(),
        developmentUrl: developmentUrl.trim(),
        localPath: localPath.trim(),
        runCommand: runCommand.trim(),
      });
      setTechnicalInfo(response);
      fillForm(response);
      setEditing(false);
    } catch (err) {
      setFormError(err instanceof Error ? err.message : "Nao foi possivel salvar as informacoes tecnicas.");
    } finally {
      setSaving(false);
    }
  }

  function validateForm() {
    if (repositoryUrl.trim().length > MAX_URL_LENGTH) {
      return "Repositorio deve ter no maximo 1.000 caracteres.";
    }
    if (stack.trim().length > MAX_STACK_LENGTH) {
      return "Stack deve ter no maximo 3.000 caracteres.";
    }
    if (productionUrl.trim().length > MAX_URL_LENGTH) {
      return "URL de producao deve ter no maximo 1.000 caracteres.";
    }
    if (developmentUrl.trim().length > MAX_URL_LENGTH) {
      return "URL de desenvolvimento deve ter no maximo 1.000 caracteres.";
    }
    if (localPath.trim().length > MAX_LOCAL_PATH_LENGTH) {
      return "Diretorio local deve ter no maximo 2.000 caracteres.";
    }
    if (runCommand.trim().length > MAX_RUN_COMMAND_LENGTH) {
      return "Comando deve ter no maximo 1.000 caracteres.";
    }
    return null;
  }

  return (
    <section className="min-w-0 rounded-md border border-border bg-background p-4">
      <div className="mb-3 flex min-w-0 flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <h3 className="text-sm font-semibold text-foreground">Informacoes tecnicas</h3>
          <p className="mt-1 text-xs text-muted-foreground">
            Credenciais e segredos devem ser armazenados no Cofre.
          </p>
        </div>
        {canWrite && !editing ? (
          <Button
            type="button"
            size="sm"
            variant="outline"
            className="w-full sm:w-auto"
            onClick={() => setEditing(true)}
          >
            <PencilSimple className="h-4 w-4" />
            Editar
          </Button>
        ) : null}
      </div>

      {loading ? <p className="text-sm text-muted-foreground">Carregando informacoes tecnicas...</p> : null}
      {error ? <p className="text-sm text-destructive">{error}</p> : null}

      {!loading && !error && technicalInfo && !editing ? (
        <TechnicalInfoView technicalInfo={technicalInfo} />
      ) : null}

      {!loading && !error && technicalInfo && editing ? (
        <form className="grid min-w-0 gap-3" onSubmit={handleSubmit}>
          <Field
            label="Repositorio"
            value={repositoryUrl}
            maxLength={MAX_URL_LENGTH}
            placeholder="https://github.com/organizacao/projeto"
            onChange={setRepositoryUrl}
          />
          <TextAreaField
            label="Stack"
            value={stack}
            maxLength={MAX_STACK_LENGTH}
            placeholder="React, TypeScript, Spring Boot, PostgreSQL, Docker"
            onChange={setStack}
          />
          <Field
            label="Producao"
            value={productionUrl}
            maxLength={MAX_URL_LENGTH}
            placeholder="https://app.exemplo.com"
            onChange={setProductionUrl}
          />
          <Field
            label="Desenvolvimento"
            value={developmentUrl}
            maxLength={MAX_URL_LENGTH}
            placeholder="http://localhost:5173"
            onChange={setDevelopmentUrl}
          />
          <Field
            label="Diretorio local"
            value={localPath}
            maxLength={MAX_LOCAL_PATH_LENGTH}
            placeholder="D:\\dev\\repositorios\\meu-projeto"
            onChange={setLocalPath}
          />
          <Field
            label="Comando para executar"
            value={runCommand}
            maxLength={MAX_RUN_COMMAND_LENGTH}
            placeholder="npm run dev"
            onChange={setRunCommand}
          />

          {formError ? <p className="text-sm text-destructive">{formError}</p> : null}

          <div className="grid gap-2 sm:flex sm:justify-end">
            <Button type="button" variant="outline" size="sm" onClick={cancelEditing} disabled={saving}>
              Cancelar
            </Button>
            <Button type="submit" size="sm" disabled={saving}>
              <FloppyDisk className="h-4 w-4" />
              {saving ? "Salvando..." : "Salvar informacoes"}
            </Button>
          </div>
        </form>
      ) : null}

      {!loading && !error && technicalInfo ? (
        <ProjectGithubRepositoryPanel workspaceId={workspaceId} projectId={projectId} canWrite={canWrite} />
      ) : null}
    </section>
  );
}

function TechnicalInfoView({ technicalInfo }: { technicalInfo: ProjectTechnicalInfo }) {
  const hasAnyValue = [
    technicalInfo.repositoryUrl,
    technicalInfo.stack,
    technicalInfo.productionUrl,
    technicalInfo.developmentUrl,
    technicalInfo.localPath,
    technicalInfo.runCommand,
  ].some(Boolean);

  if (!hasAnyValue) {
    return (
      <div className="rounded-md border border-dashed border-border px-3 py-6 text-center">
        <p className="text-sm font-medium text-muted-foreground">Sem informacoes tecnicas cadastradas</p>
      </div>
    );
  }

  return (
    <dl className="grid min-w-0 gap-3">
      <InfoItem label="Repositorio" value={technicalInfo.repositoryUrl} link />
      <InfoItem label="Stack" value={technicalInfo.stack} multiline />
      <InfoItem label="Producao" value={technicalInfo.productionUrl} link />
      <InfoItem label="Desenvolvimento" value={technicalInfo.developmentUrl} link />
      <InfoItem label="Diretorio local" value={technicalInfo.localPath} mono />
      <InfoItem label="Comando para executar" value={technicalInfo.runCommand} mono />
    </dl>
  );
}

function InfoItem({
  label,
  value,
  link = false,
  mono = false,
  multiline = false,
}: {
  label: string;
  value: string | null;
  link?: boolean;
  mono?: boolean;
  multiline?: boolean;
}) {
  if (!value) {
    return null;
  }

  return (
    <div className="min-w-0 rounded-md border border-border bg-card px-3 py-2">
      <dt className="text-[11px] font-medium uppercase text-muted-foreground">{label}</dt>
      <dd
        className={`mt-1 break-words text-sm text-card-foreground ${
          mono ? "font-mono" : ""
        } ${multiline ? "whitespace-pre-wrap leading-6" : ""}`}
      >
        {link && isHttpUrl(value) ? (
          <a
            href={value}
            target="_blank"
            rel="noreferrer"
            className="text-primary underline-offset-4 hover:underline"
          >
            {value}
          </a>
        ) : (
          value
        )}
      </dd>
    </div>
  );
}

function Field({
  label,
  value,
  maxLength,
  placeholder,
  onChange,
}: {
  label: string;
  value: string;
  maxLength: number;
  placeholder: string;
  onChange: (value: string) => void;
}) {
  return (
    <label className="grid min-w-0 gap-1 text-sm font-medium text-foreground">
      {label}
      <input
        className="h-10 min-w-0 rounded-md border border-input bg-card px-3 text-sm font-normal outline-none ring-offset-background transition-shadow placeholder:text-muted-foreground focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
        value={value}
        onChange={(event) => onChange(event.target.value)}
        maxLength={maxLength}
        placeholder={placeholder}
      />
    </label>
  );
}

function TextAreaField({
  label,
  value,
  maxLength,
  placeholder,
  onChange,
}: {
  label: string;
  value: string;
  maxLength: number;
  placeholder: string;
  onChange: (value: string) => void;
}) {
  return (
    <label className="grid min-w-0 gap-1 text-sm font-medium text-foreground">
      {label}
      <textarea
        className="min-h-24 min-w-0 resize-y rounded-md border border-input bg-card px-3 py-2 text-sm font-normal outline-none ring-offset-background transition-shadow placeholder:text-muted-foreground focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
        value={value}
        onChange={(event) => onChange(event.target.value)}
        maxLength={maxLength}
        placeholder={placeholder}
      />
    </label>
  );
}

function isHttpUrl(value: string) {
  return value.startsWith("http://") || value.startsWith("https://");
}
