import type { HttpTransport, HttpRequest, HttpResponse } from "../ports.js";
export class FetchTransport implements HttpTransport {
  async send(request: HttpRequest): Promise<HttpResponse> {
    const response = await fetch(request.url, { method: request.method, body: request.body,
      signal: request.signal, redirect: "error", credentials: "omit", cache: "no-store",
      headers: { "Content-Type": "application/json", Accept: "application/json" } });
    return { status: response.status, redirected: response.redirected,
      retryAfter: response.headers.get("Retry-After") ?? undefined,
      body: response.ok ? await response.json() : undefined };
  }
}
