import { apiRequest } from "@/lib/api";

export type GithubAppRepository = {
  repositoryId: number;
  name: string;
  fullName: string;
  ownerLogin: string;
  private: boolean;
  fork: boolean;
  archived: boolean;
  htmlUrl: string;
  description: string | null;
  defaultBranch: string | null;
  language: string | null;
  updatedAt: string;
  topics: string[];
};

export type GithubAppRepositoryPage = {
  items: GithubAppRepository[];
  page: number;
  perPage: number;
  totalCount: number;
};

export type GithubAppRepositoryPreview = {
  repository: GithubAppRepository;
  readme: string | null;
  rootFiles: string[];
  detectedStacks: string[];
};

export type ProjectGithubRepositoryState = "NOT_ASSOCIATED" | "ASSOCIATED" | "INACCESSIBLE";

export type ProjectGithubRepository = {
  state: ProjectGithubRepositoryState;
  repositoryId: number | null;
  repository: GithubAppRepository | null;
};

export function listGithubAppRepositories(workspaceId: string, page: number, perPage: number) {
  return apiRequest<GithubAppRepositoryPage>(
    `/api/workspaces/${workspaceId}/integrations/github/app/repositories?page=${page}&perPage=${perPage}`,
  );
}

export function previewGithubAppRepository(workspaceId: string, repositoryId: number) {
  return apiRequest<GithubAppRepositoryPreview>(
    `/api/workspaces/${workspaceId}/integrations/github/app/repositories/${repositoryId}/preview`,
  );
}

export function getProjectGithubRepository(workspaceId: string, projectId: string) {
  return apiRequest<ProjectGithubRepository>(
    `/api/workspaces/${workspaceId}/projects/${projectId}/github-repository`,
  );
}

export function associateProjectGithubRepository(workspaceId: string, projectId: string, repositoryId: number) {
  return apiRequest<ProjectGithubRepository>(
    `/api/workspaces/${workspaceId}/projects/${projectId}/github-repository`,
    { method: "PUT", body: { repositoryId } },
  );
}

export function dissociateProjectGithubRepository(workspaceId: string, projectId: string) {
  return apiRequest<void>(
    `/api/workspaces/${workspaceId}/projects/${projectId}/github-repository`,
    { method: "DELETE" },
  );
}
