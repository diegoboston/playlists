/**
 * Stable reverse proxy for Stage Manager Cloudflare quick tunnels.
 *
 * GET  /url         → text/plain tunnel base (empty if none registered)
 * POST /validate    → check Bearer WRITE_SECRET (no KV access)
 * POST /register    → store tunnel URL in KV (Bearer WRITE_SECRET)
 * POST /unregister  → delete stored tunnel URL from KV (Bearer WRITE_SECRET)
 * POST /push-pdf    → store playlist PDF in R2 + PIN metadata in KV (Bearer WRITE_SECRET)
 *
 * When the phone tunnel is offline, GET / serves a PIN gate and GET /pdf serves the
 * last pushed playlist PDF (same 5-digit PIN as remote play on the phone).
 */

export interface Env {
  TUNNEL: KVNamespace;
  PLAYLIST_PDF: R2Bucket;
  WRITE_SECRET: string;
}

const KV_TUNNEL = "current";
const KV_PDF_PIN = "pdf_pin";
const KV_PDF_AUTH = "pdf_auth";
const KV_PDF_META = "pdf_meta";
const PDF_OBJECT_KEY = "last.pdf";
const PDF_COOKIE = "pdf_auth";
const TUNNEL_PROBE_MS = 4_000;

/** Align with CloudflareTunnel.URL_PATTERN in CloudflareTunnel.kt */
const TUNNEL_URL_RE = /^https:\/\/[a-z0-9-]+\.trycloudflare\.com$/;
const PIN_RE = /^\d{5}$/;

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

const OFFLINE_PIN_HTML = `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>Stage Manager — offline set list</title>
  <style>
    * { box-sizing: border-box; margin: 0; padding: 0; }
    html, body { height: 100%; background: #000; color: #fff; font-family: sans-serif; }
    main { min-height: 100%; display: flex; align-items: center; justify-content: center; padding: 24px; }
    form { width: 100%; max-width: 360px; text-align: center; }
    h1 { font-size: 22px; margin-bottom: 8px; }
    p { opacity: .75; font-size: 14px; margin-bottom: 20px; line-height: 1.4; }
    input { width: 100%; padding: 14px; font-size: 28px; letter-spacing: .35em; text-align: center; border-radius: 10px; border: 1px solid #444; background: #111; color: #fff; }
    button { margin-top: 16px; width: 100%; padding: 12px; border: none; border-radius: 10px; background: #7ecbff; color: #000; font-size: 16px; font-weight: 600; cursor: pointer; }
    button:disabled { opacity: .5; cursor: default; }
    #error { color: #ff8a80; font-size: 14px; min-height: 1.4em; margin-top: 12px; }
  </style>
</head>
<body>
  <main>
    <form id="pinForm">
      <h1>Offline set list</h1>
      <p>The phone is not connected. Enter the 5-digit PIN from Stage Manager Settings to open or download the last uploaded playlist PDF.</p>
      <input id="pin" type="text" inputmode="numeric" pattern="[0-9]*" maxlength="5" autocomplete="off" autofocus />
      <button type="submit">Open PDF</button>
      <div id="error"></div>
    </form>
  </main>
  <script>
    var form = document.getElementById('pinForm');
    var pinInput = document.getElementById('pin');
    var errorEl = document.getElementById('error');
    var submitBtn = form.querySelector('button');
    pinInput.addEventListener('input', function() {
      pinInput.value = pinInput.value.replace(/\\D/g, '').slice(0, 5);
      errorEl.textContent = '';
    });
    form.addEventListener('submit', function(e) {
      e.preventDefault();
      var pin = pinInput.value;
      if (pin.length !== 5) {
        errorEl.textContent = 'Enter 5 digits.';
        return;
      }
      submitBtn.disabled = true;
      fetch('/api/auth', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ pin: pin })
      }).then(function(resp) {
        if (resp.ok) {
          window.location.href = '/pdf';
          return;
        }
        errorEl.textContent = 'Wrong PIN. Try again.';
        pinInput.select();
        submitBtn.disabled = false;
      }).catch(function() {
        errorEl.textContent = 'Could not connect. Try again.';
        submitBtn.disabled = false;
      });
    });
  </script>
</body>
</html>`;

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

async function readAuthBody(request: Request): Promise<{ pin?: string } | null> {
  const contentType = request.headers.get("Content-Type") ?? "";
  if (!contentType.toLowerCase().includes("application/json")) {
    return null;
  }
  try {
    return (await request.json()) as { pin?: string };
  } catch {
    return null;
  }
}

function authorizeWrite(request: Request, env: Env): boolean {
  const auth = request.headers.get("Authorization");
  return auth === `Bearer ${env.WRITE_SECRET}`;
}

async function pdfAuthToken(env: Env, pin: string): Promise<string> {
  const data = new TextEncoder().encode(`${pin}:${env.WRITE_SECRET}`);
  const hash = await crypto.subtle.digest("SHA-256", data);
  return [...new Uint8Array(hash)]
    .map((byte) => byte.toString(16).padStart(2, "0"))
    .join("");
}

function readPdfAuthCookie(request: Request): string | null {
  const cookieHeader = request.headers.get("Cookie") ?? "";
  for (const part of cookieHeader.split(";")) {
    const trimmed = part.trim();
    if (trimmed.startsWith(`${PDF_COOKIE}=`)) {
      return trimmed.slice(PDF_COOKIE.length + 1).trim();
    }
  }
  return null;
}

async function isPdfAuthed(request: Request, env: Env): Promise<boolean> {
  const expected = await env.TUNNEL.get(KV_PDF_AUTH);
  if (!expected) return false;
  const actual = readPdfAuthCookie(request);
  return actual != null && actual === expected;
}

async function hasCachedPdf(env: Env): Promise<boolean> {
  const head = await env.PLAYLIST_PDF.head(PDF_OBJECT_KEY);
  return head != null;
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

async function isTunnelAlive(tunnelBase: string): Promise<boolean> {
  const tunnelOrigin = tunnelBase.replace(/\/+$/, "");
  try {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), TUNNEL_PROBE_MS);
    const response = await fetch(`${tunnelOrigin}/compat.js`, {
      method: "GET",
      signal: controller.signal,
    });
    clearTimeout(timeout);
    return response.ok;
  } catch {
    return false;
  }
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

async function handlePushPdf(request: Request, env: Env): Promise<Response> {
  if (!authorizeWrite(request, env)) {
    return unauthorized();
  }

  const pin = request.headers.get("X-Pin")?.trim() ?? "";
  if (!PIN_RE.test(pin)) {
    return badRequest("X-Pin header must be a 5-digit PIN");
  }

  const playlistName = request.headers.get("X-Playlist-Name")?.trim() || "Playlist";
  const playlistId = request.headers.get("X-Playlist-Id")?.trim() || "";
  const pdfBytes = await request.arrayBuffer();
  if (pdfBytes.byteLength === 0) {
    return badRequest("Empty PDF body");
  }

  await env.PLAYLIST_PDF.put(PDF_OBJECT_KEY, pdfBytes, {
    httpMetadata: { contentType: "application/pdf" },
  });

  const authToken = await pdfAuthToken(env, pin);
  await env.TUNNEL.put(KV_PDF_PIN, pin);
  await env.TUNNEL.put(KV_PDF_AUTH, authToken);
  await env.TUNNEL.put(
    KV_PDF_META,
    JSON.stringify({
      playlistName,
      playlistId,
      pushedAt: new Date().toISOString(),
      bytes: pdfBytes.byteLength,
    }),
  );

  return Response.json({ ok: true, bytes: pdfBytes.byteLength });
}

async function handleOfflineAuth(request: Request, env: Env): Promise<Response> {
  const body = await readAuthBody(request);
  const submittedPin = body?.pin?.trim() ?? "";
  if (!PIN_RE.test(submittedPin)) {
    return badRequest("Expected JSON body: {\"pin\":\"12345\"}");
  }

  const storedPin = await env.TUNNEL.get(KV_PDF_PIN);
  if (!storedPin || submittedPin !== storedPin) {
    return new Response("Wrong PIN", { status: 401 });
  }

  const authToken = await env.TUNNEL.get(KV_PDF_AUTH);
  if (!authToken) {
    return new Response("No cached PDF", { status: 503 });
  }

  return new Response(JSON.stringify({ ok: true }), {
    status: 200,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      "Set-Cookie": `${PDF_COOKIE}=${authToken}; Path=/; HttpOnly; Secure; SameSite=Lax`,
    },
  });
}

async function serveCachedPdf(request: Request, env: Env): Promise<Response> {
  if (!(await isPdfAuthed(request, env))) {
    return new Response("Unauthorized", { status: 401 });
  }

  const object = await env.PLAYLIST_PDF.get(PDF_OBJECT_KEY);
  if (!object) {
    return new Response("No cached PDF", { status: 404 });
  }

  const metaRaw = await env.TUNNEL.get(KV_PDF_META);
  let filename = "playlist.pdf";
  if (metaRaw) {
    try {
      const meta = JSON.parse(metaRaw) as { playlistName?: string };
      if (meta.playlistName) {
        filename = `${meta.playlistName.replace(/[^\w.-]+/g, "_")}.pdf`;
      }
    } catch {
      // Keep default filename.
    }
  }

  const download = new URL(request.url).searchParams.has("download");
  const disposition = download ? "attachment" : "inline";
  const headers = new Headers();
  object.writeHttpMetadata(headers);
  headers.set("Content-Type", "application/pdf");
  headers.set("Content-Disposition", `${disposition}; filename="${filename}"`);
  headers.set("Cache-Control", "private, no-store");

  return new Response(object.body, { status: 200, headers });
}

async function handleOfflineRequest(request: Request, env: Env, path: string): Promise<Response> {
  if (!(await hasCachedPdf(env))) {
    return new Response("No tunnel active and no cached playlist PDF", { status: 503 });
  }

  if (request.method === "POST" && path === "/api/auth") {
    return handleOfflineAuth(request, env);
  }

  if (request.method === "GET" && path === "/pdf") {
    if (await isPdfAuthed(request, env)) {
      return serveCachedPdf(request, env);
    }
    return Response.redirect(new URL("/", request.url).toString(), 302);
  }

  if (request.method === "GET" && (path === "/" || path === "/index.html")) {
    if (await isPdfAuthed(request, env)) {
      return Response.redirect(new URL("/pdf", request.url).toString(), 302);
    }
    return new Response(OFFLINE_PIN_HTML, {
      status: 200,
      headers: { "Content-Type": "text/html; charset=utf-8", "Cache-Control": "no-store" },
    });
  }

  return new Response("Phone offline — open / for cached playlist PDF", { status: 503 });
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);
    const path = normalizePath(url.pathname);

    if (request.method === "GET" && path === "/url") {
      const current = await env.TUNNEL.get(KV_TUNNEL);
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

      await env.TUNNEL.put(KV_TUNNEL, normalized);
      return Response.json({ ok: true, url: normalized });
    }

    if (request.method === "POST" && path === "/unregister") {
      if (!authorizeWrite(request, env)) {
        return unauthorized();
      }

      await env.TUNNEL.delete(KV_TUNNEL);
      return Response.json({ ok: true });
    }

    if (request.method === "POST" && path === "/push-pdf") {
      return handlePushPdf(request, env);
    }

    const current = await env.TUNNEL.get(KV_TUNNEL);
    if (current && (await isTunnelAlive(current))) {
      return proxyToTunnel(current, request);
    }

    return handleOfflineRequest(request, env, path);
  },
};
