import http from "k6/http";
import { check, sleep, group } from "k6";
import { Trend, Rate } from "k6/metrics";
import encoding from "k6/encoding";

const BASE_URL = __ENV.BASE_URL || "https://api.goormpopcorn.shop";
const SCENARIO = (__ENV.SCENARIO || "dist").toLowerCase();

const AUTH_TOKEN = __ENV.AUTH_TOKEN || "";
const AUTO_LOGIN = (__ENV.AUTO_LOGIN || "true").toLowerCase() === "true";
const LOGIN_URL = __ENV.LOGIN_URL || `${BASE_URL}/api/users/v1/auth/login`;
const REFRESH_URL = __ENV.REFRESH_URL || `${BASE_URL}/api/users/v1/auth/refresh`;
const LOGIN_REQUIRED = (__ENV.LOGIN_REQUIRED || "true").toLowerCase() === "true";
const LOGIN_DEBUG = (__ENV.LOGIN_DEBUG || "false").toLowerCase() === "true";
const LOGIN_MAX_RETRIES = parseInt(__ENV.LOGIN_MAX_RETRIES || "2", 10);
const LOGIN_RETRY_SLEEP_SEC = parseFloat(__ENV.LOGIN_RETRY_SLEEP_SEC || "1");
const LOGIN_PAYLOAD_MODE = (__ENV.LOGIN_PAYLOAD_MODE || "simple").toLowerCase(); // simple | compat
const LOGIN_ONCE_ONLY = (__ENV.LOGIN_ONCE_ONLY || "true").toLowerCase() === "true";
const NEED_ADMIN_LOGIN = (__ENV.NEED_ADMIN_LOGIN || "false").toLowerCase() === "true";
const ACCESS_TOKEN_TTL_SEC = parseInt(__ENV.ACCESS_TOKEN_TTL_SEC || "3600", 10);
const TOKEN_REFRESH_SKEW_SEC = parseInt(__ENV.TOKEN_REFRESH_SKEW_SEC || "60", 10);
const SKIP_WRITES_WHEN_AUTH_UNAVAILABLE = (__ENV.SKIP_WRITES_WHEN_AUTH_UNAVAILABLE || "true").toLowerCase() === "true";

const CUSTOMER_EMAIL = __ENV.CUSTOMER_EMAIL || "popcorn1@popcorn.com";
const CUSTOMER_PASSWORD = __ENV.CUSTOMER_PASSWORD || "test123";
const CUSTOMER_EMAILS = (__ENV.CUSTOMER_EMAILS || "popcorn1@popcorn.com,popcorn2@popcorn.com,popcorn3@popcorn.com,popcorn4@popcorn.com,popcorn6@popcorn.com")
  .split(",")
  .map((v) => v.trim())
  .filter(Boolean);
const ADMIN_EMAIL = __ENV.ADMIN_EMAIL || "popcorn5@popcorn.com";
const ADMIN_PASSWORD = __ENV.ADMIN_PASSWORD || "testPassword123";

const POPUP_HOT_ID = __ENV.POPUP_HOT_ID || "07c79042-f179-452e-9318-0d3abb403c44";
const POPUP_IDS = (__ENV.POPUP_IDS || "07c79042-f179-452e-9318-0d3abb403c44,7e413857-3363-4bfc-b153-a2da54b7a94c,e534dfc8-23e7-4a7c-93c6-ce4ac6ec934d")
  .split(",")
  .map((v) => v.trim())
  .filter(Boolean);

const HTTP_TIMEOUT = __ENV.HTTP_TIMEOUT || "30s";
const MAX_RETRIES = parseInt(__ENV.MAX_RETRIES || "2", 10);
const RETRY_SLEEP_SEC = parseFloat(__ENV.RETRY_SLEEP_SEC || "0.2");
const NO_CONNECTION_REUSE = (__ENV.NO_CONNECTION_REUSE || "false").toLowerCase() === "true";

const ENABLE_POPUP_LIST = (__ENV.ENABLE_POPUP_LIST || "true").toLowerCase() === "true";
const POPUP_PUBLIC = (__ENV.POPUP_PUBLIC || "true").toLowerCase() === "true";
const POPUP_LIST_RATIO = Math.max(0, Math.min(1, parseFloat(__ENV.POPUP_LIST_RATIO || "0.10")));
const POPUP_LIST_TIMEOUT = __ENV.POPUP_LIST_TIMEOUT || "5s";
const POPUP_LIST_MAX_RETRIES = parseInt(__ENV.POPUP_LIST_MAX_RETRIES || "1", 10);
const POPUP_LIST_BACKOFF_MS = parseInt(__ENV.POPUP_LIST_BACKOFF_MS || "30000", 10);

const POPUP_LIST_API = __ENV.POPUP_LIST_API || "/api/stores/v1/popups";
const POPUP_DETAIL_API_TEMPLATE = __ENV.POPUP_DETAIL_API_TEMPLATE || "/api/stores/v1/popups/{popupId}";
const ORDER_CREATE_API = __ENV.ORDER_CREATE_API || "/api/orders/v1";
const STOCK_RESERVE_API = __ENV.STOCK_RESERVE_API || "/api/stores/v1/stocks/reserve";
const PAYMENT_REQUEST_API = __ENV.PAYMENT_REQUEST_API || "/api/pay/v1/payments";

const ENABLE_STOCK_RESERVE = (__ENV.ENABLE_STOCK_RESERVE || "false").toLowerCase() === "true";
const ENABLE_PAYMENT_REQUEST = (__ENV.ENABLE_PAYMENT_REQUEST || "false").toLowerCase() === "true";
const WRITE_PROB_MULTIPLIER = parseFloat(__ENV.WRITE_PROB_MULTIPLIER || "1");

const STEADY_RPS = parseInt(__ENV.STEADY_RPS || "1", 10);
const RUSH_RPS = parseInt(__ENV.RUSH_RPS || "1", 10);
const SPIKE_RPS = parseInt(__ENV.SPIKE_RPS || "1", 10);

const STEADY_DURATION = __ENV.STEADY_DURATION || "5s";
const RUSH_DURATION = __ENV.RUSH_DURATION || "5s";
const SPIKE_DURATION = __ENV.SPIKE_DURATION || "5s";
const ENABLE_STAGE1 = (__ENV.ENABLE_STAGE1 || "true").toLowerCase() === "true";
const ENABLE_STAGE2 = (__ENV.ENABLE_STAGE2 || "true").toLowerCase() === "true";
const ENABLE_STAGE3 = (__ENV.ENABLE_STAGE3 || "true").toLowerCase() === "true";
const GRACEFUL_STOP = __ENV.GRACEFUL_STOP || "10s";
const SETUP_TIMEOUT = __ENV.SETUP_TIMEOUT || "120s";

const THRESHOLD_FAIL_RATE = parseFloat(__ENV.THRESHOLD_FAIL_RATE || "0.01");
const THRESHOLD_STEADY_P95 = parseInt(__ENV.THRESHOLD_STEADY_P95 || "500", 10);
const THRESHOLD_RUSH_P95 = parseInt(__ENV.THRESHOLD_RUSH_P95 || "1500", 10);
const THRESHOLD_SPIKE_P95 = parseInt(__ENV.THRESHOLD_SPIKE_P95 || "4000", 10);
const THRESHOLD_ORDER_CREATE_P95 = parseInt(__ENV.THRESHOLD_ORDER_CREATE_P95 || "2000", 10);
const VU_PREALLOC_MULTIPLIER = parseFloat(__ENV.VU_PREALLOC_MULTIPLIER || "2");
const VU_MAX_MULTIPLIER = parseFloat(__ENV.VU_MAX_MULTIPLIER || "10");
const VU_PREALLOC_MIN = parseInt(__ENV.VU_PREALLOC_MIN || "10", 10);

const t_popup_list = new Trend("t_popup_list");
const t_popup_detail = new Trend("t_popup_detail");
const t_order_create = new Trend("t_order_create");
const t_stock_reserve = new Trend("t_stock_reserve");
const t_payment_req = new Trend("t_payment_req");
const r_fail = new Rate("r_fail");

let popupListDisabledUntil = 0;

function parseDurationSeconds(input) {
  const m = String(input || "").trim().match(/^(\d+)\s*([smh])$/i);
  if (!m) return null;
  const n = parseInt(m[1], 10);
  const u = m[2].toLowerCase();
  if (u === "s") return n;
  if (u === "m") return n * 60;
  return n * 3600;
}

function secondsToDuration(totalSeconds) {
  if (totalSeconds % 3600 === 0) return `${totalSeconds / 3600}h`;
  if (totalSeconds % 60 === 0) return `${totalSeconds / 60}m`;
  return `${totalSeconds}s`;
}

function addDurations(a, b) {
  const sa = parseDurationSeconds(a);
  const sb = parseDurationSeconds(b);
  if (sa === null || sb === null) {
    throw new Error(`Unsupported duration format: ${a}, ${b}. Use s/m/h like 30s, 10m, 1h`);
  }
  return secondsToDuration(sa + sb);
}

function pickFirstHeaderValue(headerValue) {
  if (!headerValue) return null;
  return Array.isArray(headerValue) ? headerValue[0] || null : headerValue;
}

function extractTokenFromLoginResponse(res) {
  try {
    const parsed = JSON.parse(res.body || "{}");
    const tokenFromBody =
      parsed?.token ||
      parsed?.accessToken ||
      parsed?.jwt ||
      parsed?.data?.token ||
      parsed?.data?.accessToken ||
      parsed?.data?.jwt ||
      parsed?.result?.token ||
      parsed?.result?.accessToken ||
      null;
    if (tokenFromBody) return String(tokenFromBody);
  } catch (_) {
    // ignore
  }

  const authHeader = pickFirstHeaderValue(res.headers?.Authorization || res.headers?.authorization);
  if (authHeader) {
    const bearer = String(authHeader).match(/Bearer\s+(.+)/i);
    if (bearer?.[1]) return bearer[1].trim();
  }

  const setCookie = pickFirstHeaderValue(res.headers?.["Set-Cookie"] || res.headers?.["set-cookie"]);
  if (setCookie) {
    const m = String(setCookie).match(/(?:access_token|accessToken|token)=([^;]+)/i);
    if (m?.[1]) return m[1];
  }

  return null;
}

function extractJwtExpMs(token) {
  try {
    const parts = String(token || "").split(".");
    if (parts.length < 2) return null;
    const payload = JSON.parse(
      encoding.b64decode(parts[1], "rawurl", "s")
    );
    if (!payload?.exp) return null;
    return Number(payload.exp) * 1000;
  } catch (_) {
    return null;
  }
}

function buildTokenBundle(accessToken, refreshToken) {
  const expMs = extractJwtExpMs(accessToken);
  const fallbackMs = Date.now() + ACCESS_TOKEN_TTL_SEC * 1000;
  return {
    accessToken: accessToken || "",
    refreshToken: refreshToken || "",
    accessTokenExpiresAtMs: expMs || fallbackMs,
  };
}

function extractRefreshTokenFromLoginResponse(res) {
  try {
    const parsed = JSON.parse(res.body || "{}");
    return (
      parsed?.refreshToken ||
      parsed?.data?.refreshToken ||
      parsed?.result?.refreshToken ||
      ""
    );
  } catch (_) {
    return "";
  }
}

function login(email, password, role) {
  const payloads = LOGIN_PAYLOAD_MODE === "compat"
    ? [
        { email, password },
        { username: email, password },
        { loginId: email, password },
        { userEmail: email, userPassword: password },
        { email, passwd: password },
      ]
    : [{ email, password }];

  for (let attempt = 0; attempt <= LOGIN_MAX_RETRIES; attempt++) {
    for (const payload of payloads) {
      const res = http.post(
        LOGIN_URL,
        JSON.stringify(payload),
        {
          headers: { "Content-Type": "application/json" },
          timeout: HTTP_TIMEOUT,
          responseType: "text",
          tags: { name: `login_${role}` },
        }
      );

      if (LOGIN_DEBUG) {
        console.log(`[login:${role}] status=${res.status} keys=${Object.keys(payload).join(",")} body=${(res.body || "").slice(0, 200)}`);
      }

      if (res.status >= 200 && res.status < 300) {
        const token = extractTokenFromLoginResponse(res);
        const refreshToken = extractRefreshTokenFromLoginResponse(res);
        if (token) return buildTokenBundle(token, refreshToken);
      }
    }

    if (attempt < LOGIN_MAX_RETRIES) {
      sleep(LOGIN_RETRY_SLEEP_SEC * (attempt + 1));
    }
  }

  return buildTokenBundle("", "");
}

function loginAnyCustomer() {
  const candidates = [CUSTOMER_EMAIL, ...CUSTOMER_EMAILS].filter(Boolean);
  const dedup = [...new Set(candidates)];
  for (const email of dedup) {
    const token = login(email, CUSTOMER_PASSWORD, "customer");
    if (token?.accessToken) return token;
  }
  return buildTokenBundle("", "");
}

function refreshAccessToken(tokenBundle, role = "customer") {
  const refreshToken = tokenBundle?.refreshToken || "";
  if (!refreshToken) return null;

  const res = http.post(
    REFRESH_URL,
    JSON.stringify({ refreshToken }),
    {
      headers: { "Content-Type": "application/json" },
      timeout: HTTP_TIMEOUT,
      responseType: "text",
      tags: { name: `refresh_${role}` },
    }
  );

  if (LOGIN_DEBUG) {
    console.log(`[refresh:${role}] status=${res.status} body=${(res.body || "").slice(0, 200)}`);
  }

  if (res.status < 200 || res.status >= 300) return null;

  try {
    const parsed = JSON.parse(res.body || "{}");
    const accessToken =
      parsed?.accessToken ||
      parsed?.token ||
      parsed?.data?.accessToken ||
      parsed?.result?.accessToken ||
      "";
    if (!accessToken) return null;
    return buildTokenBundle(accessToken, refreshToken);
  } catch (_) {
    return null;
  }
}

function ensureRoleToken(setupData, role = "customer") {
  if (AUTH_TOKEN) return AUTH_TOKEN;
  const roleTokens = setupData?.tokens?.[role];
  if (!roleTokens) return "";

  const now = Date.now();
  const shouldRefresh = now >= (Number(roleTokens.accessTokenExpiresAtMs || 0) - TOKEN_REFRESH_SKEW_SEC * 1000);
  if (!shouldRefresh && roleTokens.accessToken) return roleTokens.accessToken;

  const refreshed = refreshAccessToken(roleTokens, role);
  if (refreshed?.accessToken) {
    setupData.tokens[role] = refreshed;
    return refreshed.accessToken;
  }

  const reloginCred =
    role === "admin"
      ? { email: ADMIN_EMAIL, password: ADMIN_PASSWORD }
      : { email: CUSTOMER_EMAIL, password: CUSTOMER_PASSWORD };
  const reloginBundle = login(reloginCred.email, reloginCred.password, role);
  setupData.tokens[role] = reloginBundle;
  return reloginBundle?.accessToken || "";
}

export function setup() {
  if (AUTH_TOKEN) {
    const staticBundle = buildTokenBundle(AUTH_TOKEN, "");
    return { tokens: { customer: staticBundle, admin: staticBundle } };
  }

  if (!AUTO_LOGIN) {
    if (LOGIN_REQUIRED) {
      throw new Error("No AUTH_TOKEN and AUTO_LOGIN=false. Enable AUTO_LOGIN or set AUTH_TOKEN.");
    }
    return {
      tokens: {
        customer: buildTokenBundle("", ""),
        admin: buildTokenBundle("", ""),
      },
    };
  }

  const customer = loginAnyCustomer();
  let admin = customer;
  if (!LOGIN_ONCE_ONLY && NEED_ADMIN_LOGIN) {
    admin = login(ADMIN_EMAIL, ADMIN_PASSWORD, "admin");
  }

  if (LOGIN_REQUIRED && !customer?.accessToken) {
    if (POPUP_PUBLIC && SKIP_WRITES_WHEN_AUTH_UNAVAILABLE) {
      console.warn("Customer auto login failed, but continuing with public popup endpoints and write skip mode.");
    } else {
      throw new Error("Customer auto login failed. Check CUSTOMER_EMAIL/CUSTOMER_PASSWORD/LOGIN_URL");
    }
  }

  if (!customer?.accessToken) {
    console.warn("Customer auto login failed; requests may be unauthorized.");
  }
  if (!admin?.accessToken) {
    console.warn("Admin auto login failed; admin token will fallback to customer token.");
  }

  return {
    tokens: {
      customer,
      admin: admin?.accessToken ? admin : customer,
    },
  };
}

function headers(setupData, role = "customer", withAuth = true) {
  const h = { "Content-Type": "application/json" };
  if (withAuth) {
    const token = ensureRoleToken(setupData, role) || ensureRoleToken(setupData, "customer");
    if (token) h["Authorization"] = `Bearer ${token}`;
  }
  return h;
}

function reqParams(name, setupData, role = "customer", withAuth = true) {
  return { headers: headers(setupData, role, withAuth), tags: { name }, timeout: HTTP_TIMEOUT };
}

function hasAuthToken(setupData, role = "customer") {
  if (AUTH_TOKEN) return true;
  const token = setupData?.tokens?.[role]?.accessToken || "";
  return Boolean(token);
}

function requestWithRetry(method, url, body, params, maxRetries = MAX_RETRIES) {
  let res;
  for (let attempt = 0; attempt <= maxRetries; attempt++) {
    res = method === "GET" ? http.get(url, params) : http.post(url, body, params);
    if (res && !res.error) return res;
    if (attempt < maxRetries) sleep(RETRY_SLEEP_SEC * (attempt + 1));
  }
  return res;
}

function ok2xx(res) {
  const ok = res.status >= 200 && res.status < 300;
  r_fail.add(!ok);
  return ok;
}

function pickDistributedPopup() {
  return POPUP_IDS[Math.floor(Math.random() * POPUP_IDS.length)];
}

function api_popup_list(setupData) {
  if (Date.now() < popupListDisabledUntil) return null;

  const res = requestWithRetry(
    "GET",
    `${BASE_URL}${POPUP_LIST_API}`,
    null,
    { ...reqParams("popup_list", setupData, "customer", !POPUP_PUBLIC), timeout: POPUP_LIST_TIMEOUT },
    POPUP_LIST_MAX_RETRIES
  );

  if (res?.error || res.status >= 500) {
    popupListDisabledUntil = Date.now() + POPUP_LIST_BACKOFF_MS;
  }

  if (!res) return null;
  t_popup_list.add(res.timings.duration);
  check(res, { "popup_list 2xx": () => ok2xx(res) });
  return res;
}

function api_popup_detail(popupId, setupData) {
  const detailPath = POPUP_DETAIL_API_TEMPLATE.replace("{popupId}", encodeURIComponent(popupId));
  const res = requestWithRetry("GET", `${BASE_URL}${detailPath}`, null, reqParams("popup_detail", setupData, "customer", !POPUP_PUBLIC));
  t_popup_detail.add(res.timings.duration);
  check(res, { "popup_detail 2xx": () => ok2xx(res) });
  return res;
}

function api_order_create({ popupId }, setupData) {
  const body = JSON.stringify({
    popupId,
    orderType: "GOODS",
    paymentMethod: "CARD",
    items: [
      {
        orderItemType: "GOODS",
        qty: 1,
        unitPrice: 10000,
        goodsId: "00000000-0000-0000-0000-000000000301",
      },
    ],
  });

  const res = requestWithRetry("POST", `${BASE_URL}${ORDER_CREATE_API}`, body, {
    ...reqParams("order_create", setupData, "customer"),
    responseType: "text",
  });

  t_order_create.add(res.timings.duration);
  check(res, { "order_create 2xx": () => ok2xx(res) });
  return res;
}

function api_stock_reserve({ popupId, orderId }, setupData) {
  const body = JSON.stringify({
    popupId,
    orderId,
    items: [{ goodsId: "00000000-0000-0000-0000-000000000301", quantity: 1 }],
  });

  const res = requestWithRetry("POST", `${BASE_URL}${STOCK_RESERVE_API}`, body, reqParams("stock_reserve", setupData));
  t_stock_reserve.add(res.timings.duration);
  check(res, { "stock_reserve 2xx": () => ok2xx(res) });
  return res;
}

function api_payment_request({ orderId }, setupData) {
  const body = JSON.stringify({ orderId, paymentMethod: "CARD", amount: 10000 });

  const res = requestWithRetry("POST", `${BASE_URL}${PAYMENT_REQUEST_API}`, body, reqParams("payment_request", setupData, "customer"));
  t_payment_req.add(res.timings.duration);
  check(res, { "payment_request 2xx": () => ok2xx(res) });
  return res;
}

function extractOrderId(orderRes) {
  try {
    const j = JSON.parse(orderRes.body || "{}");
    return j?.data?.id || j?.data?.orderId || j?.result?.orderId || j?.orderId || null;
  } catch (_) {
    return null;
  }
}

function calcPreAllocated(rate, envKey) {
  const fromEnv = parseInt(__ENV[envKey] || "", 10);
  if (Number.isFinite(fromEnv) && fromEnv > 0) return fromEnv;
  return Math.max(VU_PREALLOC_MIN, Math.ceil(rate * VU_PREALLOC_MULTIPLIER));
}

function calcMaxVUs(rate, preAllocated, envKey) {
  const fromEnv = parseInt(__ENV[envKey] || "", 10);
  if (Number.isFinite(fromEnv) && fromEnv > 0) return fromEnv;
  return Math.max(preAllocated * 2, Math.ceil(rate * VU_MAX_MULTIPLIER));
}

const steadyPreAllocated = calcPreAllocated(STEADY_RPS, "STEADY_PREALLOC_VUS");
const rushPreAllocated = calcPreAllocated(RUSH_RPS, "RUSH_PREALLOC_VUS");
const spikePreAllocated = calcPreAllocated(SPIKE_RPS, "SPIKE_PREALLOC_VUS");
const steadyMaxVUs = calcMaxVUs(STEADY_RPS, steadyPreAllocated, "STEADY_MAX_VUS");
const rushMaxVUs = calcMaxVUs(RUSH_RPS, rushPreAllocated, "RUSH_MAX_VUS");
const spikeMaxVUs = calcMaxVUs(SPIKE_RPS, spikePreAllocated, "SPIKE_MAX_VUS");

const scenarios = {};

if (ENABLE_STAGE1) {
  scenarios.stage1_steady = {
    executor: "constant-arrival-rate",
    rate: STEADY_RPS,
    timeUnit: "1s",
    duration: STEADY_DURATION,
    preAllocatedVUs: steadyPreAllocated,
    maxVUs: steadyMaxVUs,
    exec: "stageSteady",
    gracefulStop: GRACEFUL_STOP,
    tags: { stage: "steady", scenario: SCENARIO },
  };
}

if (ENABLE_STAGE2) {
  scenarios.stage2_rush = {
    executor: "constant-arrival-rate",
    rate: RUSH_RPS,
    timeUnit: "1s",
    duration: RUSH_DURATION,
    preAllocatedVUs: rushPreAllocated,
    maxVUs: rushMaxVUs,
    exec: "stageRush",
    startTime: STEADY_DURATION,
    gracefulStop: GRACEFUL_STOP,
    tags: { stage: "rush", scenario: SCENARIO },
  };
}

if (ENABLE_STAGE3) {
  scenarios.stage3_spike = {
    executor: "constant-arrival-rate",
    rate: SPIKE_RPS,
    timeUnit: "1s",
    duration: SPIKE_DURATION,
    preAllocatedVUs: spikePreAllocated,
    maxVUs: spikeMaxVUs,
    exec: "stageSpike",
    startTime: addDurations(STEADY_DURATION, RUSH_DURATION),
    gracefulStop: GRACEFUL_STOP,
    tags: { stage: "spike", scenario: SCENARIO },
  };
}

const thresholds = {
  t_order_create: [`p(95)<${THRESHOLD_ORDER_CREATE_P95}`],
};

if (ENABLE_STAGE1) {
  thresholds["r_fail{stage:steady}"] = [`rate<${THRESHOLD_FAIL_RATE}`];
  thresholds["http_req_duration{stage:steady}"] = [`p(95)<${THRESHOLD_STEADY_P95}`];
}
if (ENABLE_STAGE2) {
  thresholds["r_fail{stage:rush}"] = [`rate<${THRESHOLD_FAIL_RATE}`];
  thresholds["http_req_duration{stage:rush}"] = [`p(95)<${THRESHOLD_RUSH_P95}`];
}
if (ENABLE_STAGE3) {
  thresholds["r_fail{stage:spike}"] = [`rate<${THRESHOLD_FAIL_RATE}`];
  thresholds["http_req_duration{stage:spike}"] = [`p(95)<${THRESHOLD_SPIKE_P95}`];
}

export const options = {
  discardResponseBodies: true,
  noConnectionReuse: NO_CONNECTION_REUSE,
  scenarios,
  thresholds,
  setupTimeout: SETUP_TIMEOUT,
};

export function stageSteady(setupData) {
  group("stage1_steady", () => {
    runScenario(SCENARIO, "steady", setupData);
    sleep(0.2 + Math.random() * 0.8);
  });
}

export function stageRush(setupData) {
  group("stage2_rush", () => {
    runScenario(SCENARIO, "rush", setupData);
    sleep(Math.random() * 0.5);
  });
}

export function stageSpike(setupData) {
  group("stage3_spike", () => {
    runScenario(SCENARIO, "spike", setupData);
    sleep(Math.random() * 0.2);
  });
}

function scenarioHot(stage, setupData) {
  if (ENABLE_POPUP_LIST && Math.random() < POPUP_LIST_RATIO) {
    api_popup_list(setupData);
  }
  api_popup_detail(POPUP_HOT_ID, setupData);

  let writeProb = 0.15;
  if (stage === "rush") writeProb = 0.3;
  if (stage === "spike") writeProb = 0.55;
  writeProb = Math.max(0, Math.min(1, writeProb * WRITE_PROB_MULTIPLIER));

  if (Math.random() < writeProb) {
    if (SKIP_WRITES_WHEN_AUTH_UNAVAILABLE && !hasAuthToken(setupData, "customer")) return;
    const orderRes = api_order_create({ popupId: POPUP_HOT_ID }, setupData);
    const orderId = extractOrderId(orderRes);

    if (ENABLE_STOCK_RESERVE && orderId) {
      api_stock_reserve({ popupId: POPUP_HOT_ID, orderId }, setupData);
    }

    if (ENABLE_PAYMENT_REQUEST && orderId) {
      api_payment_request({ orderId }, setupData);
    }
  }
}

function scenarioDist(stage, setupData) {
  const popupId = pickDistributedPopup();

  if (ENABLE_POPUP_LIST && Math.random() < POPUP_LIST_RATIO) {
    api_popup_list(setupData);
  }
  api_popup_detail(popupId, setupData);

  let writeProb = 0.1;
  if (stage === "rush") writeProb = 0.15;
  if (stage === "spike") writeProb = 0.25;
  writeProb = Math.max(0, Math.min(1, writeProb * WRITE_PROB_MULTIPLIER));

  if (Math.random() < writeProb) {
    if (SKIP_WRITES_WHEN_AUTH_UNAVAILABLE && !hasAuthToken(setupData, "customer")) return;
    const orderRes = api_order_create({ popupId }, setupData);
    const orderId = extractOrderId(orderRes);

    if (ENABLE_PAYMENT_REQUEST && orderId) {
      api_payment_request({ orderId }, setupData);
    }
  }
}

function scenarioFault(stage, setupData) {
  const popupId = stage === "rush" || stage === "spike" ? POPUP_HOT_ID : pickDistributedPopup();
  api_popup_detail(popupId, setupData);

  let writeProb = 0.2;
  if (stage === "rush") writeProb = 0.35;
  if (stage === "spike") writeProb = 0.6;
  writeProb = Math.max(0, Math.min(1, writeProb * WRITE_PROB_MULTIPLIER));

  if (Math.random() < writeProb) {
    if (SKIP_WRITES_WHEN_AUTH_UNAVAILABLE && !hasAuthToken(setupData, "customer")) return;
    const orderRes = api_order_create({ popupId }, setupData);
    const orderId = extractOrderId(orderRes);

    if (ENABLE_PAYMENT_REQUEST && orderId) {
      api_payment_request({ orderId }, setupData);
    }
  }
}

function runScenario(name, stage, setupData) {
  if (name === "hot") return scenarioHot(stage, setupData);
  if (name === "dist") return scenarioDist(stage, setupData);
  if (name === "fault") return scenarioFault(stage, setupData);
  return scenarioHot(stage, setupData);
}

export default function () {}
