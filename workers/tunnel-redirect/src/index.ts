/**
 * Stable reverse proxy for Stage Manager Cloudflare quick tunnels.
 *
 * GET  /url        → text/plain tunnel base (empty if none registered)
 * POST /validate   → check Bearer WRITE_SECRET (no KV access)
 * POST /register   → store tunnel URL in KV (Bearer WRITE_SECRET)
 * POST /unregister → delete stored tunnel URL from KV (Bearer WRITE_SECRET)
 */

export interface Env {
  TUNNEL: KVNamespace;
  WRITE_SECRET: string;
}

const KV_KEY = "current";

/** Align with CloudflareTunnel.URL_PATTERN in CloudflareTunnel.kt */
const TUNNEL_URL_RE = /^https:\/\/[a-z0-9-]+\.trycloudflare\.com$/;

const QUICK_TUNNEL_API_HOST = "api.trycloudflare.com";

const HOP_BY_HOP = new Set([
  "connection",
  "keep-alive",
  "proxy-authenticate",
  "proxy-authorization",
  "te",
  "trailers",
  "transfer-encoding",
  "upgrade",
]);

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

function authorizeWrite(request: Request, env: Env): boolean {
  const auth = request.headers.get("Authorization");
  return auth === `Bearer ${env.WRITE_SECRET}`;
}

function filteredRequestHeaders(request: Request): Headers {
  const out = new Headers();
  request.headers.forEach((value, key) => {
    const lower = key.toLowerCase();
    if (lower === "host" || lower.startsWith("cf-") || HOP_BY_HOP.has(lower)) {
      return;
    }
    out.set(key, value);
  });
  return out;
}

function rewriteLocation(
  location: string,
  tunnelOrigin: string,
  workerOrigin: string,
): string {
  try {
    const loc = new URL(location, `${tunnelOrigin}/`);
    if (loc.origin === tunnelOrigin) {
      return `${workerOrigin}${loc.pathname}${loc.search}${loc.hash}`;
    }
  } catch {
    // Keep original on parse failure.
  }
  return location;
}

function filteredResponseHeaders(
  headers: Headers,
  tunnelOrigin: string,
  workerOrigin: string,
): Headers {
  const out = new Headers();
  headers.forEach((value, key) => {
    const lower = key.toLowerCase();
    if (HOP_BY_HOP.has(lower)) return;
    if (lower === "location") {
      out.set(key, rewriteLocation(value, tunnelOrigin, workerOrigin));
      return;
    }
    out.set(key, value);
  });
  return out;
}

async function proxyToTunnel(tunnelBase: string, request: Request): Promise<Response> {
  const incoming = new URL(request.url);
  const tunnelOrigin = tunnelBase.replace(/\/+$/, "");
  const upstream = new URL(`${incoming.pathname}${incoming.search}`, `${tunnelOrigin}/`);

  const init: RequestInit = {
    method: request.method,
    headers: filteredRequestHeaders(request),
    redirect: "manual",
  };
  if (request.method !== "GET" && request.method !== "HEAD") {
    init.body = request.body;
  }

  const upstreamResponse = await fetch(upstream.toString(), init);
  return new Response(upstreamResponse.body, {
    status: upstreamResponse.status,
    statusText: upstreamResponse.statusText,
    headers: filteredResponseHeaders(
      upstreamResponse.headers,
      tunnelOrigin,
      incoming.origin,
    ),
  });
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

    if (request.method === "POST" && path === "/validate") {
      if (!authorizeWrite(request, env)) {
        return unauthorized();
      }
      return Response.json({ ok: true });
    }

    if (request.method === "POST" && path === "/register") {
      if (!authorizeWrite(request, env)) {
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

    if (request.method === "POST" && path === "/unregister") {
      if (!authorizeWrite(request, env)) {
        return unauthorized();
      }

      await env.TUNNEL.delete(KV_KEY);
      return Response.json({ ok: true });
    }

    const current = await env.TUNNEL.get(KV_KEY);
    if (!current) {
      return new Response("No tunnel active", { status: 503 });
    }

    return proxyToTunnel(current, request);
  },
};
