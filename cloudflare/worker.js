const JSON_HEADERS = {
  "content-type": "application/json; charset=utf-8",
  "access-control-allow-origin": "*",
  "access-control-allow-methods": "GET, POST, DELETE, OPTIONS",
  "access-control-allow-headers": "authorization, content-type, x-haoleme-account",
};
const DEFAULT_MIN_ANDROID_VERSION_CODE = 22;

export default {
  async fetch(request, env) {
    if (request.method === "OPTIONS") {
      return new Response(null, { status: 204, headers: JSON_HEADERS });
    }

    const url = new URL(request.url);
    if (url.pathname === "/health") {
      return json({
        ok: true,
        service: "haoleme-cloud",
        pairing: {
          minAndroidVersionCode: minAndroidVersionCode(env),
          pairCodeDigits: 6,
          expiresIn: 600,
        },
      });
    }

    if (url.pathname === "/api/pair/start" && request.method === "POST") {
      return startPairing(env.RUNS, request);
    }

    if (url.pathname === "/api/pair/status" && request.method === "POST") {
      return pairingStatus(env.RUNS, request);
    }

    if (!url.pathname.startsWith("/api/")) {
      return json({ error: "not found" }, 404);
    }

    const accountKey = await authenticatedAccountKey(request);
    if (!accountKey) {
      return json({ error: "unauthorized" }, 401);
    }

    if (url.pathname === "/api/runs" && request.method === "GET") {
      return listRuns(env.RUNS, accountKey, url);
    }

    if (url.pathname === "/api/runs" && request.method === "POST") {
      return upsertRun(env.RUNS, accountKey, request);
    }

    if (url.pathname === "/api/pair/confirm" && request.method === "POST") {
      return confirmPairing(env.RUNS, request, env);
    }

    if (url.pathname.startsWith("/api/runs/")) {
      const runId = decodeURIComponent(url.pathname.slice("/api/runs/".length));
      if (!runId) {
        return json({ error: "missing run id" }, 400);
      }
      if (request.method === "GET") {
        return getRun(env.RUNS, accountKey, runId);
      }
      if (request.method === "DELETE") {
        return deleteRun(env.RUNS, accountKey, runId);
      }
    }

    return json({ error: "not found" }, 404);
  },
};

async function startPairing(kv, request) {
  const payload = await request.json().catch(() => ({}));
  const deviceName = String(payload.deviceName || "Haoleme CLI").slice(0, 80);
  const createdAt = new Date();
  for (let attempt = 0; attempt < 5; attempt++) {
    const code = String(crypto.getRandomValues(new Uint32Array(1))[0] % 1000000).padStart(6, "0");
    const existing = await kv.get(pairKey(code), "json");
    if (existing) {
      continue;
    }
    const pairToken = randomToken(32);
    await kv.put(
      pairKey(code),
      JSON.stringify({
        code,
        pairToken,
        deviceName,
        status: "pending",
        createdAt: createdAt.toISOString(),
        expiresAt: new Date(createdAt.getTime() + 600000).toISOString(),
      }),
      { expirationTtl: 600 },
    );
    return json({ code, pairToken, expiresIn: 600, deviceName, serverTime: new Date().toISOString() });
  }
  return json({ error: "could not allocate pair code", code: "pair_code_unavailable" }, 503);
}

async function confirmPairing(kv, request, env) {
  const payload = await request.json().catch(() => null);
  const appVersionCode = Number(payload && payload.appVersionCode);
  const requiredVersionCode = minAndroidVersionCode(env);
  if (Number.isFinite(appVersionCode) && appVersionCode > 0 && appVersionCode < requiredVersionCode) {
    return json(
      {
        error: "app version too old",
        code: "app_version_too_old",
        minAndroidVersionCode: requiredVersionCode,
      },
      426,
    );
  }

  const token = bearerToken(request);
  if (!token || token.length < 16) {
    return json({ error: "unauthorized", code: "unauthorized" }, 401);
  }
  const code = normalizePairCode(payload && payload.code);
  if (!code) {
    return json({ error: "missing code", code: "missing_pair_code" }, 400);
  }

  const pair = await kv.get(pairKey(code), "json");
  if (!pair) {
    return json({ error: "pair code expired or not found", code: "pair_code_expired" }, 404);
  }
  if (pair.status !== "pending") {
    return json({ error: "pair code already used", code: "pair_code_used" }, 409);
  }

  pair.status = "confirmed";
  pair.account = "default";
  pair.token = token;
  pair.confirmedAt = new Date().toISOString();
  pair.appVersionCode = Number.isFinite(appVersionCode) ? appVersionCode : null;
  pair.appVersionName = String((payload && payload.appVersionName) || "").slice(0, 40);
  pair.platform = String((payload && payload.platform) || "android").slice(0, 24);
  await kv.put(pairKey(code), JSON.stringify(pair), { expirationTtl: 600 });
  return json({
    ok: true,
    account: pair.account,
    deviceName: pair.deviceName || "",
    pairedAt: pair.confirmedAt,
    serverTime: new Date().toISOString(),
  });
}

async function pairingStatus(kv, request) {
  const payload = await request.json().catch(() => null);
  const code = normalizePairCode(payload && payload.code);
  const pairToken = String((payload && payload.pairToken) || "");
  if (!code || !pairToken) {
    return json({ error: "missing pair status credentials", code: "missing_pair_status_credentials" }, 400);
  }

  const pair = await kv.get(pairKey(code), "json");
  if (!pair || pair.pairToken !== pairToken) {
    return json({ error: "pair code expired or not found", code: "pair_code_expired" }, 404);
  }
  if (pair.status !== "confirmed") {
    return json({ status: "pending", deviceName: pair.deviceName || "", expiresAt: pair.expiresAt || "" });
  }
  return json({
    status: "confirmed",
    account: pair.account || "default",
    token: pair.token,
    deviceName: pair.deviceName || "",
    pairedAt: pair.confirmedAt || "",
  });
}

async function listRuns(kv, accountKey, url) {
  const limit = clampInt(url.searchParams.get("limit") || "100", 1, 500);
  const index = await readIndex(kv, accountKey);
  const runs = [];
  for (const item of index.slice(0, limit)) {
    const run = await kv.get(runKey(accountKey, item.id), "json");
    if (run) {
      runs.push(run);
    }
  }
  return json({ runs });
}

async function upsertRun(kv, accountKey, request) {
  const payload = await request.json().catch(() => null);
  const run = payload && (payload.run || payload);
  if (!run || typeof run !== "object" || !run.id) {
    return json({ error: "missing run" }, 400);
  }

  const stored = normalizeRun(run);
  await kv.put(runKey(accountKey, stored.id), JSON.stringify(stored));

  const index = await readIndex(kv, accountKey);
  const updated = [
    { id: stored.id, updatedAt: stored.updatedAt || "" },
    ...index.filter((item) => item.id !== stored.id),
  ]
    .sort((a, b) => String(b.updatedAt).localeCompare(String(a.updatedAt)))
    .slice(0, 200);
  await kv.put(indexKey(accountKey), JSON.stringify(updated));
  return json({ ok: true });
}

async function getRun(kv, accountKey, runId) {
  const run = await kv.get(runKey(accountKey, runId), "json");
  if (!run) {
    return json({ error: "run not found" }, 404);
  }
  return json({ run });
}

async function deleteRun(kv, accountKey, runId) {
  await kv.delete(runKey(accountKey, runId));
  const index = await readIndex(kv, accountKey);
  await kv.put(indexKey(accountKey), JSON.stringify(index.filter((item) => item.id !== runId)));
  return json({ deleted: true });
}

async function readIndex(kv, accountKey) {
  const index = await kv.get(indexKey(accountKey), "json");
  return Array.isArray(index) ? index : [];
}

function normalizeRun(run) {
  return {
    id: String(run.id || ""),
    command: Array.isArray(run.command) ? run.command.map(String) : [],
    commandText: String(run.commandText || ""),
    cwd: String(run.cwd || ""),
    status: String(run.status || "unknown"),
    pid: run.pid ?? null,
    exitCode: run.exitCode ?? null,
    startedAt: String(run.startedAt || ""),
    endedAt: run.endedAt ?? null,
    updatedAt: String(run.updatedAt || new Date().toISOString()),
    stdoutTail: String(run.stdoutTail || "").slice(-12000),
    stderrTail: String(run.stderrTail || "").slice(-12000),
    outputTail: String(run.outputTail || "").slice(-12000),
  };
}

async function authenticatedAccountKey(request) {
  const token = bearerToken(request);
  if (!token || token.length < 16) {
    return "";
  }
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(token));
  return [...new Uint8Array(digest)].map((byte) => byte.toString(16).padStart(2, "0")).join("");
}

function bearerToken(request) {
  const auth = request.headers.get("authorization") || "";
  return auth.toLowerCase().startsWith("bearer ") ? auth.slice(7).trim() : "";
}

function pairKey(code) {
  return `pair:${code}`;
}

function normalizePairCode(value) {
  const code = String(value || "").replace(/\D/g, "");
  return code.length === 6 ? code : "";
}

function randomToken(bytes) {
  const values = new Uint8Array(bytes);
  crypto.getRandomValues(values);
  let binary = "";
  for (const value of values) {
    binary += String.fromCharCode(value);
  }
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}

function minAndroidVersionCode(env) {
  const configured = Number(env && env.MIN_ANDROID_VERSION_CODE);
  if (Number.isFinite(configured) && configured > 0) {
    return configured;
  }
  return DEFAULT_MIN_ANDROID_VERSION_CODE;
}

function indexKey(accountKey) {
  return `acct:${accountKey}:index`;
}

function runKey(accountKey, runId) {
  return `acct:${accountKey}:run:${runId}`;
}

function clampInt(raw, min, max) {
  const parsed = Number.parseInt(raw, 10);
  if (!Number.isFinite(parsed)) {
    return max;
  }
  return Math.max(min, Math.min(max, parsed));
}

function json(payload, status = 200) {
  return new Response(JSON.stringify(payload), { status, headers: JSON_HEADERS });
}
