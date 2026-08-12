const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";

type ApiRequestOptions = Omit<RequestInit, "body" | "credentials"> & {
  body?: unknown;
};

type CsrfResponse = {
  headerName: string;
  token: string;
};

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
    const message = data?.error ?? "Nao foi possivel concluir a requisicao.";
    throw new Error(message);
  }

  return data as T;
}

export async function apiRequest<T>(
  path: string,
  options: ApiRequestOptions & { skipCsrf?: boolean } = {},
) {
  const method = options.method?.toUpperCase() ?? "GET";
  const headers = new Headers(options.headers);

  if (options.body !== undefined) {
    headers.set("Content-Type", "application/json");
  }

  if (!options.skipCsrf && ["POST", "PUT", "PATCH", "DELETE"].includes(method)) {
    const token = await getCsrfToken();
    headers.set(token.headerName, token.token);
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    method,
    credentials: "include",
    headers,
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
  });

  return parseResponse<T>(response);
}
