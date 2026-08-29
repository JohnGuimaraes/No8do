const configuredApiBaseUrl = import.meta.env.VITE_API_BASE_URL?.replace(/\/+$/, "");
const API_BASE_URL = configuredApiBaseUrl ?? (import.meta.env.DEV ? "http://localhost:8080" : "");

export function getApiUrl(path: string) {
  return `${API_BASE_URL}${path.startsWith("/") ? path : `/${path}`}`;
}

type ApiRequestOptions = Omit<RequestInit, "body" | "credentials"> & {
  body?: unknown;
};

type CsrfResponse = {
  headerName: string;
  token: string;
};

export class ApiRequestError extends Error {
  constructor(public readonly status: number, message: string) {
    super(message);
    this.name = "ApiRequestError";
  }
}

let csrfToken: CsrfResponse | null = null;

async function getCsrfToken() {
  if (csrfToken) {
    return csrfToken;
  }

  csrfToken = await apiRequest<CsrfResponse>("/api/csrf", {
    skipCsrf: true,
  });
  return csrfToken;
}

async function parseResponse<T>(response: Response): Promise<T> {
  if (response.status === 204) {
    return undefined as T;
  }

  const text = await response.text();
  const data = text ? JSON.parse(text) : undefined;

  if (!response.ok) {
    const message = data?.error ?? (response.status === 403 ? "Você não tem permissão para gerenciar membros deste workspace." : "Nao foi possivel concluir a requisicao.");
    throw new ApiRequestError(response.status, message);
  }

  return data as T;
}

export async function apiRequest<T>(
  path: string,
  options: ApiRequestOptions & { skipCsrf?: boolean } = {},
) {
  const method = options.method?.toUpperCase() ?? "GET";
  const headers = new Headers(options.headers);

  const isFormData = options.body instanceof FormData;

  if (options.body !== undefined && !isFormData) {
    headers.set("Content-Type", "application/json");
  }

  if (!options.skipCsrf && ["POST", "PUT", "PATCH", "DELETE"].includes(method)) {
    const token = await getCsrfToken();
    headers.set(token.headerName, token.token);
  }

  const response = await fetch(getApiUrl(path), {
    ...options,
    method,
    credentials: "include",
    headers,
    body: options.body === undefined ? undefined : isFormData ? options.body as BodyInit : JSON.stringify(options.body),
  });

  return parseResponse<T>(response);
}
