/**
 * Stable redirect for Stage Manager Cloudflare quick tunnels.
 *
 * GET  /         → 302 to current *.trycloudflare.com (query string preserved)
 * GET  /url      → text/plain tunnel base (empty if none registered)
 * POST /register → store tunnel URL in KV (Bearer WRITE_SECRET)
 */

export interface Env {
  TUNNEL: KVNamespace;
  WRITE_SECRET: string;
}

const KV_KEY = "current";

/** Align with CloudflareTunnel.URL_PATTERN in CloudflareTunnel.kt */
const TUNNEL_URL_RE = /^https:\/\/[a-z0-9-]+\.trycloudflare\.com$/;

const QUICK_TUNNEL_API_HOST = "api.trycloudflare.com";

function normalizePath(pathname: string): string {
  const trimmed = pathname.replace(/\/+$/, "");
  return trimmed === "" ? "/" : trimmed;
}

function normalizeTunnelUrl(raw: string): string | null {
  const trimmed = raw.trim().replace(/\/+$/, "");
  if (!trimmed) return null;

  let parsed: URL;
  try {
    parsed = new URL(trimmed);
  } catch {
    return null;
  }

  if (parsed.protocol !== "https:") return null;
  if (parsed.hostname === QUICK_TUNNEL_API_HOST) return null;
  if (!parsed.hostname.endsWith(".trycloudflare.com")) return null;

  const base = `https://${parsed.hostname}`;
  if (!TUNNEL_URL_RE.test(base)) return null;
  return base;
}

function unauthorized(): Response {
  return new Response("Unauthorized", { status: 401 });
}

function badRequest(message: string): Response {
  return new Response(message, { status: 400 });
}

async function readRegisterBody(request: Request): Promise<{ url?: string } | null> {
  const contentType = request.headers.get("Content-Type") ?? "";
  if (!contentType.toLowerCase().includes("application/json")) {
    return null;
  }
  try {
    return (await request.json()) as { url?: string };
  } catch {
    return null;
  }
}

function authorizeRegister(request: Request, env: Env): boolean {
  const auth = request.headers.get("Authorization");
  return auth === `Bearer ${env.WRITE_SECRET}`;
}

function redirectToTunnel(tunnelBase: string, requestUrl: URL): Response {
  const destination = new URL(`${tunnelBase}/`);
  requestUrl.searchParams.forEach((value, key) => {
    destination.searchParams.set(key, value);
  });
  return Response.redirect(destination.toString(), 302);
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);
    const path = normalizePath(url.pathname);

    if (request.method === "GET" && path === "/url") {
      const current = await env.TUNNEL.get(KV_KEY);
      return new Response(current ?? "", {
        status: 200,
        headers: { "Content-Type": "text/plain; charset=utf-8" },
      });
    }

    if (request.method === "GET" && path === "/") {
      const current = await env.TUNNEL.get(KV_KEY);
      if (!current) {
        return new Response("No tunnel active", { status: 503 });
      }
      return redirectToTunnel(current, url);
    }

    if (request.method === "POST" && path === "/register") {
      if (!authorizeRegister(request, env)) {
        return unauthorized();
      }

      const body = await readRegisterBody(request);
      if (!body || typeof body.url !== "string") {
        return badRequest("Expected JSON body: {\"url\":\"https://….trycloudflare.com\"}");
      }

      const normalized = normalizeTunnelUrl(body.url);
      if (!normalized) {
        return badRequest("Invalid tunnel URL");
      }

      await env.TUNNEL.put(KV_KEY, normalized);
      return Response.json({ ok: true, url: normalized });
    }

    return new Response("Not found", { status: 404 });
  },
};
