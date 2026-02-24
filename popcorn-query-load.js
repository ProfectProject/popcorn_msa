import http from "k6/http";
import { check, sleep, group } from "k6";
import { Trend, Rate } from "k6/metrics";
import encoding from "k6/encoding";

const BASE_URL = __ENV.BASE_URL || "https://api.goormpopcorn.shop";
const MODE = (__ENV.MODE || "mix").toLowerCase();

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
const SETUP_TIMEOUT = __ENV.SETUP_TIMEOUT || "120s";

const CUSTOMER_EMAIL = __ENV.CUSTOMER_EMAIL || "popcorn1@popcorn.com";
const CUSTOMER_PASSWORD = __ENV.CUSTOMER_PASSWORD || "test123";
const ADMIN_EMAIL = __ENV.ADMIN_EMAIL || "popcorn5@popcorn.com";
const ADMIN_PASSWORD = __ENV.ADMIN_PASSWORD || "testPassword123";
const LOGIN_EMAIL = __ENV.LOGIN_EMAIL || ADMIN_EMAIL;
const LOGIN_PASSWORD = __ENV.LOGIN_PASSWORD || ADMIN_PASSWORD;
const QUERY_AUTH_ROLE = (__ENV.QUERY_AUTH_ROLE || "admin").toLowerCase();

const POPUP_HOT_ID = __ENV.POPUP_HOT_ID || "07c79042-f179-452e-9318-0d3abb403c44";
const USER_IDS = (__ENV.USER_IDS || "1,2,3,4,5").split(",").map((v) => v.trim()).filter(Boolean);
const ORDER_ID_FEED_URL = __ENV.ORDER_ID_FEED_URL || "";

const MY_ORDER_STATUS_API_TEMPLATE = __ENV.MY_ORDER_STATUS_API_TEMPLATE || "/api/orderquery/v1/dashboard/orders?page=0&size=1";
const POPUP_STOCK_API_TEMPLATE = __ENV.POPUP_STOCK_API_TEMPLATE || "/api/orderquery/v1/dashboard/orders?popupId={popupId}&page=0&size=20";
const ORDER_LIST_API_TEMPLATE = __ENV.ORDER_LIST_API_TEMPLATE || "/api/orderquery/v1/dashboard/orders?userId={userId}&page={page}&size={size}";

const HTTP_TIMEOUT = __ENV.HTTP_TIMEOUT || "20s";
const MAX_RETRIES = parseInt(__ENV.MAX_RETRIES || "2", 10);
const RETRY_SLEEP_SEC = parseFloat(__ENV.RETRY_SLEEP_SEC || "0.2");
const ENDPOINT_TIMEOUT_BURST_THRESHOLD = parseInt(__ENV.ENDPOINT_TIMEOUT_BURST_THRESHOLD || "8", 10);
const ENDPOINT_COOLDOWN_MS = parseInt(__ENV.ENDPOINT_COOLDOWN_MS || "15000", 10);

const STORM_RPS_TOTAL = parseInt(__ENV.STORM_RPS_TOTAL || "1200", 10);
const STORM_DURATION = __ENV.STORM_DURATION || "8m";
const STORM_SPLIT_MY_ORDER_PCT = parseInt(__ENV.STORM_SPLIT_MY_ORDER_PCT || "35", 10);

const MIX_RPS = parseInt(__ENV.MIX_RPS || "500", 10);
const MIX_DURATION = __ENV.MIX_DURATION || "10m";

const CONSISTENCY_RPS = parseInt(__ENV.CONSISTENCY_RPS || "80", 10);
const CONSISTENCY_DURATION = __ENV.CONSISTENCY_DURATION || "10m";

const QUERY_P95_MS = parseInt(__ENV.QUERY_P95_MS || "400", 10);
const MY_ORDER_STATUS_P95_MS = parseInt(__ENV.MY_ORDER_STATUS_P95_MS || "1200", 10);
const CONSISTENCY_P95_MS = parseInt(__ENV.CONSISTENCY_P95_MS || "5000", 10);

const t_my_order_status = new Trend("t_my_order_status");
const t_popup_stock = new Trend("t_popup_stock");
const t_order_list = new Trend("t_order_list");
const t_consistency_lag_ms = new Trend("t_consistency_lag_ms");
const r_query_fail = new Rate("r_query_fail");
let lastLoginFailure = "";

const endpointGuard = {
  my_order_status: { timeoutStreak: 0, disabledUntil: 0 },
  popup_stock: { timeoutStreak: 0, disabledUntil: 0 },
  order_list: { timeoutStreak: 0, disabledUntil: 0 },
  order_id_feed: { timeoutStreak: 0, disabledUntil: 0 },
};

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

function extractJwtExpMs(token) {
  try {
    const parts = String(token || "").split(".");
    if (parts.length < 2) return null;
    const payload = JSON.parse(encoding.b64decode(parts[1], "rawurl", "s"));
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

function extractTokenFromAuthResponse(body) {
  return (
    body?.token ||
    body?.accessToken ||
    body?.jwt ||
    body?.data?.token ||
    body?.data?.accessToken ||
    body?.result?.token ||
    body?.result?.accessToken ||
    ""
  );
}

function extractRefreshFromAuthResponse(body) {
  return body?.refreshToken || body?.data?.refreshToken || body?.result?.refreshToken || "";
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
        console.log(`[login:${role}] status=${res.status} body=${(res.body || "").slice(0, 200)}`);
      }
      if (res.status >= 500) {
        lastLoginFailure = `login ${role} failed with ${res.status}`;
      } else if (res.status >= 400) {
        lastLoginFailure = `login ${role} failed with ${res.status} (check credentials)`;
      }

      if (res.status >= 200 && res.status < 300) {
        try {
          const parsed = JSON.parse(res.body || "{}");
          const accessToken = extractTokenFromAuthResponse(parsed);
          const refreshToken = extractRefreshFromAuthResponse(parsed);
          if (accessToken) return buildTokenBundle(accessToken, refreshToken);
        } catch (_) {
          // ignore parse error
        }
      }
    }

    if (attempt < LOGIN_MAX_RETRIES) {
      sleep(LOGIN_RETRY_SLEEP_SEC * (attempt + 1));
    }
  }

  return buildTokenBundle("", "");
}

function refreshAccessToken(tokenBundle, role = "admin") {
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
    const accessToken = extractTokenFromAuthResponse(parsed);
    if (!accessToken) return null;
    return buildTokenBundle(accessToken, refreshToken);
  } catch (_) {
    return null;
  }
}

function ensureRoleToken(setupData, role = "admin") {
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

  const reloginCred = role === "customer"
    ? { email: CUSTOMER_EMAIL, password: CUSTOMER_PASSWORD }
    : { email: ADMIN_EMAIL, password: ADMIN_PASSWORD };

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

  const primaryRole = QUERY_AUTH_ROLE === "customer" ? "customer" : "admin";
  const primaryCred = LOGIN_ONCE_ONLY
    ? { email: LOGIN_EMAIL, password: LOGIN_PASSWORD }
    : (primaryRole === "customer"
      ? { email: CUSTOMER_EMAIL, password: CUSTOMER_PASSWORD }
      : { email: ADMIN_EMAIL, password: ADMIN_PASSWORD });
  const primaryToken = login(primaryCred.email, primaryCred.password, primaryRole);
  const fallbackToken = primaryRole === "admin"
    ? login(CUSTOMER_EMAIL, CUSTOMER_PASSWORD, "customer")
    : login(ADMIN_EMAIL, ADMIN_PASSWORD, "admin");

  let customer = primaryRole === "customer" ? primaryToken : fallbackToken;
  let admin = primaryRole === "admin" ? primaryToken : fallbackToken;

  if (!LOGIN_ONCE_ONLY && NEED_ADMIN_LOGIN && primaryRole !== "admin") {
    admin = login(ADMIN_EMAIL, ADMIN_PASSWORD, "admin");
  }
  if (!LOGIN_ONCE_ONLY && primaryRole !== "customer") {
    customer = login(CUSTOMER_EMAIL, CUSTOMER_PASSWORD, "customer");
  }

  if (LOGIN_REQUIRED && !primaryToken?.accessToken && !fallbackToken?.accessToken) {
    const detail = lastLoginFailure ? ` (${lastLoginFailure})` : "";
    throw new Error(`Auto login failed for both admin/customer${detail}. Check LOGIN_URL/service health.`);
  }

  if (!customer?.accessToken) {
    console.warn("Customer auto login failed; customer-role requests may be unauthorized.");
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

function headers(setupData, role = QUERY_AUTH_ROLE) {
  const h = { "Content-Type": "application/json" };
  const token = ensureRoleToken(setupData, role) || ensureRoleToken(setupData, "admin") || ensureRoleToken(setupData, "customer");
  if (token) h["Authorization"] = `Bearer ${token}`;
  return h;
}

function reqParams(name, setupData, role = QUERY_AUTH_ROLE) {
  return { headers: headers(setupData, role), tags: { name }, timeout: HTTP_TIMEOUT };
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

function makeSkippedResponse() {
  return {
    status: 598,
    timings: { duration: 0 },
    body: "",
    error: "endpoint cooldown active",
  };
}

function guardedRequest(endpointKey, method, url, body, params, maxRetries = MAX_RETRIES) {
  const guard = endpointGuard[endpointKey];
  const now = Date.now();
  if (guard && now < guard.disabledUntil) {
    r_query_fail.add(true);
    return makeSkippedResponse();
  }

  const res = requestWithRetry(method, url, body, params, maxRetries);
  const timedOut = !res || !!res.error;

  if (guard) {
    if (timedOut) {
      guard.timeoutStreak += 1;
      if (guard.timeoutStreak >= ENDPOINT_TIMEOUT_BURST_THRESHOLD) {
        guard.disabledUntil = Date.now() + ENDPOINT_COOLDOWN_MS;
        guard.timeoutStreak = 0;
      }
    } else {
      guard.timeoutStreak = 0;
      guard.disabledUntil = 0;
    }
  }

  return res;
}

function applyTemplate(path, vars = {}) {
  let out = path;
  Object.keys(vars).forEach((k) => {
    out = out.replaceAll(`{${k}}`, encodeURIComponent(String(vars[k])));
  });
  return out;
}

function pickUserId() {
  return USER_IDS[Math.floor(Math.random() * USER_IDS.length)];
}

function api_my_order_status(orderId, setupData) {
  const path = applyTemplate(MY_ORDER_STATUS_API_TEMPLATE, { orderId });
  const res = guardedRequest("my_order_status", "GET", `${BASE_URL}${path}`, null, reqParams("query_my_order_status", setupData));

  t_my_order_status.add(res?.timings?.duration || 0);
  const ok = res.status >= 200 && res.status < 300;
  r_query_fail.add(!ok);
  check(res, { "my_order_status 2xx": () => ok });
  return res;
}

function api_popup_stock(popupId, setupData) {
  const path = applyTemplate(POPUP_STOCK_API_TEMPLATE, { popupId });
  const res = guardedRequest("popup_stock", "GET", `${BASE_URL}${path}`, null, reqParams("query_popup_stock", setupData));

  t_popup_stock.add(res?.timings?.duration || 0);
  const ok = res.status >= 200 && res.status < 300;
  r_query_fail.add(!ok);
  check(res, { "popup_stock 2xx": () => ok });
  return res;
}

function api_order_list(userId, page, size, setupData) {
  const path = applyTemplate(ORDER_LIST_API_TEMPLATE, { userId, page, size });
  const res = guardedRequest("order_list", "GET", `${BASE_URL}${path}`, null, reqParams("query_order_list", setupData));

  t_order_list.add(res?.timings?.duration || 0);
  const ok = res.status >= 200 && res.status < 300;
  r_query_fail.add(!ok);
  check(res, { "order_list 2xx": () => ok });
  return res;
}

function calcConsistencyLagMs(resBody) {
  try {
    const json = JSON.parse(resBody || "{}");
    const occurredAt = json?.data?.eventOccurredAt || json?.data?.occurredAt;
    const updatedAt = json?.data?.queryUpdatedAt || json?.data?.updatedAt;
    if (!occurredAt || !updatedAt) return null;

    const t1 = Date.parse(occurredAt);
    const t2 = Date.parse(updatedAt);
    if (Number.isNaN(t1) || Number.isNaN(t2)) return null;
    return t2 - t1;
  } catch (_) {
    return null;
  }
}

function fetchRecentOrderId(setupData) {
  if (!ORDER_ID_FEED_URL) return null;
  const res = guardedRequest("order_id_feed", "GET", ORDER_ID_FEED_URL, null, reqParams("order_id_feed", setupData));
  if (!(res.status >= 200 && res.status < 300)) return null;

  try {
    const json = JSON.parse(res.body || "{}");
    const ids = json?.data?.orderIds || json?.orderIds || [];
    if (!ids.length) return null;
    return ids[Math.floor(Math.random() * ids.length)];
  } catch (_) {
    return null;
  }
}

function modeEnabled(x) {
  return MODE === x;
}

const STORM_MY_ORDER_RPS = Math.floor((STORM_RPS_TOTAL * STORM_SPLIT_MY_ORDER_PCT) / 100);
const STORM_POPUP_STOCK_RPS = Math.max(0, STORM_RPS_TOTAL - STORM_MY_ORDER_RPS);
const mixStart = STORM_DURATION;
const consistencyStart = addDurations(STORM_DURATION, MIX_DURATION);

const scenarios = {};

if (modeEnabled("storm") || modeEnabled("all")) {
  scenarios.storm_my_order = {
    executor: "constant-arrival-rate",
    rate: STORM_MY_ORDER_RPS,
    timeUnit: "1s",
    duration: STORM_DURATION,
    preAllocatedVUs: 800,
    maxVUs: 12000,
    exec: "scenarioStormMyOrder",
    tags: { scenario: "storm_my_order" },
  };

  scenarios.storm_popup_stock = {
    executor: "constant-arrival-rate",
    rate: STORM_POPUP_STOCK_RPS,
    timeUnit: "1s",
    duration: STORM_DURATION,
    preAllocatedVUs: 1200,
    maxVUs: 20000,
    exec: "scenarioStormPopupStock",
    tags: { scenario: "storm_popup_stock" },
  };
}

if (modeEnabled("mix") || modeEnabled("all")) {
  scenarios.steady_read_mix = {
    executor: "constant-arrival-rate",
    rate: MIX_RPS,
    timeUnit: "1s",
    duration: MIX_DURATION,
    preAllocatedVUs: 800,
    maxVUs: 16000,
    exec: "scenarioSteadyReadMix",
    tags: { scenario: "steady_read_mix" },
    ...(modeEnabled("all") ? { startTime: mixStart } : {}),
  };
}

if (modeEnabled("consistency") || modeEnabled("all")) {
  scenarios.consistency_after_event_burst = {
    executor: "constant-arrival-rate",
    rate: CONSISTENCY_RPS,
    timeUnit: "1s",
    duration: CONSISTENCY_DURATION,
    preAllocatedVUs: 200,
    maxVUs: 5000,
    exec: "scenarioConsistency",
    tags: { scenario: "consistency_after_event_burst" },
    ...(modeEnabled("all") ? { startTime: consistencyStart } : {}),
  };
}

export const options = {
  scenarios,
  thresholds: {
    r_query_fail: ["rate<0.01"],
    http_req_duration: [`p(95)<${QUERY_P95_MS}`],
    t_my_order_status: [`p(95)<${MY_ORDER_STATUS_P95_MS}`],
    t_popup_stock: [`p(95)<${QUERY_P95_MS}`],
    t_order_list: ["p(95)<700"],
    t_consistency_lag_ms: [`p(95)<${CONSISTENCY_P95_MS}`],
  },
  setupTimeout: SETUP_TIMEOUT,
};

export function scenarioStormMyOrder(setupData) {
  group("storm_my_order", () => {
    const orderId = fetchRecentOrderId(setupData);
    api_my_order_status(orderId || "latest", setupData);
    sleep(0.05);
  });
}

export function scenarioStormPopupStock(setupData) {
  group("storm_popup_stock", () => {
    api_popup_stock(POPUP_HOT_ID, setupData);
    sleep(0.02);
  });
}

export function scenarioSteadyReadMix(setupData) {
  group("steady_read_mix", () => {
    const x = Math.random();
    if (x < 0.55) {
      api_popup_stock(POPUP_HOT_ID, setupData);
    } else if (x < 0.90) {
      api_order_list(pickUserId(), Math.floor(Math.random() * 10), 20, setupData);
    } else {
      const orderId = fetchRecentOrderId(setupData);
      api_my_order_status(orderId || "latest", setupData);
    }
    sleep(0.1 + Math.random() * 0.4);
  });
}

export function scenarioConsistency(setupData) {
  group("consistency_after_event_burst", () => {
    const orderId = fetchRecentOrderId(setupData);
    const res = api_my_order_status(orderId || "latest", setupData);
    const lag = calcConsistencyLagMs(res.body);
    if (lag !== null) t_consistency_lag_ms.add(lag);
    sleep(0.2);
  });
}

export default function () {}
