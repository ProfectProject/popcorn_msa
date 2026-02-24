import http from "k6/http";
import { check, sleep, group } from "k6";
import { Trend, Rate } from "k6/metrics";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
const AUTH_TOKEN = __ENV.AUTH_TOKEN || "";
const SCENARIO = (__ENV.SCENARIO || "hot").toLowerCase();
const ENABLE_POPUP_LIST = (__ENV.ENABLE_POPUP_LIST || "false").toLowerCase() === "true";
const ENABLE_WRITE = (__ENV.ENABLE_WRITE || "false").toLowerCase() === "true";
const REQUEST_TIMEOUT = __ENV.REQUEST_TIMEOUT || "30s";
const MAX_RETRIES = parseInt(__ENV.MAX_RETRIES || "3", 10);
const POPUP_DETAIL_RETRIES = parseInt(__ENV.POPUP_DETAIL_RETRIES || "3", 10);
const RETRY_BACKOFF_MS = parseInt(__ENV.RETRY_BACKOFF_MS || "400", 10);
const POPUP_DETAIL_COOLDOWN_SEC = parseInt(__ENV.POPUP_DETAIL_COOLDOWN_SEC || "20", 10);
const POPUP_DETAIL_SAMPLE_RATE = parseFloat(__ENV.POPUP_DETAIL_SAMPLE_RATE || "0.25");
const POPUP_DETAIL_TIMEOUT_COOLDOWN_SEC = parseInt(__ENV.POPUP_DETAIL_TIMEOUT_COOLDOWN_SEC || "120", 10);
const NO_CONNECTION_REUSE = (__ENV.NO_CONNECTION_REUSE || "false").toLowerCase() === "true";
const NO_VU_CONNECTION_REUSE = (__ENV.NO_VU_CONNECTION_REUSE || "false").toLowerCase() === "true";
const BATCH = parseInt(__ENV.BATCH || "20", 10);
const BATCH_PER_HOST = parseInt(__ENV.BATCH_PER_HOST || "8", 10);

const POPUP_HOT_ID = __ENV.POPUP_HOT_ID || "";
const POPUP_IDS = (__ENV.POPUP_IDS || "")
  .split(",")
  .map((v) => v.trim())
  .filter(Boolean);
const EFFECTIVE_POPUP_IDS = [...new Set([POPUP_HOT_ID, ...POPUP_IDS].filter(Boolean))]
  .filter((v) => v !== "HOT_POPUP_ID" && !/^P\\d+$/i.test(v));
if (EFFECTIVE_POPUP_IDS.length === 0) {
  throw new Error("Set valid POPUP_HOT_ID or POPUP_IDS (UUID list).");
}

const STEADY_RPS = parseInt(__ENV.STEADY_RPS || "250", 10);
const RUSH_RPS = parseInt(__ENV.RUSH_RPS || "800", 10);
const SPIKE_RPS = parseInt(__ENV.SPIKE_RPS || "1800", 10);

const STEADY_DURATION = __ENV.STEADY_DURATION || "10m";
const RUSH_DURATION = __ENV.RUSH_DURATION || "10m";
const SPIKE_DURATION = __ENV.SPIKE_DURATION || "3m";

const t_popup_list = new Trend("t_popup_list");
const t_popup_detail = new Trend("t_popup_detail");
const t_order_create = new Trend("t_order_create");
const t_stock_reserve = new Trend("t_stock_reserve");
const t_payment_req = new Trend("t_payment_req");

const r_fail = new Rate("r_fail");
const popupDetailCooldown = new Map();

function headers() {
  const h = { "Content-Type": "application/json" };
  if (AUTH_TOKEN) h["Authorization"] = `Bearer ${AUTH_TOKEN}`;
  return h;
}
function ok2xx(res) {
  const ok = res.status >= 200 && res.status < 300;
  r_fail.add(!ok);
  return ok;
}
function pickDistributedPopup() {
  if (EFFECTIVE_POPUP_IDS.length === 0) return POPUP_HOT_ID;
  return EFFECTIVE_POPUP_IDS[Math.floor(Math.random() * EFFECTIVE_POPUP_IDS.length)];
}

function shouldRetry(res) {
  if (!res) return true;
  if (res.status === 0) return true;
  return res.status === 408 || res.status === 429 || res.status >= 500;
}

function requestWithRetry(method, url, body, tag, retries = MAX_RETRIES) {
  let res = null;
  for (let i = 0; i <= retries; i++) {
    if (method === "GET") {
      res = http.get(url, { headers: headers(), timeout: REQUEST_TIMEOUT, tags: { name: tag } });
    } else {
      res = http.post(url, body, { headers: headers(), timeout: REQUEST_TIMEOUT, tags: { name: tag } });
    }
    if (!shouldRetry(res)) return res;
    if (i < retries) sleep((RETRY_BACKOFF_MS * (i + 1)) / 1000);
  }
  return res;
}

function api_popup_list() {
  const res = requestWithRetry("GET", `${BASE_URL}/api/stores/v1/popups`, null, "popup_list");
  t_popup_list.add(res.timings.duration);
  check(res, { "popup_list 2xx": () => ok2xx(res) });
  return res;
}

function api_popup_detail(popupId) {
  if (Math.random() > POPUP_DETAIL_SAMPLE_RATE) {
    return { status: 204, timings: { duration: 0 } };
  }

  const now = Date.now();
  const blockedUntil = popupDetailCooldown.get(popupId) || 0;
  if (blockedUntil > now) {
    return { status: 204, timings: { duration: 0 } };
  }

  const res = requestWithRetry(
    "GET",
    `${BASE_URL}/api/stores/v1/popups/${popupId}`,
    null,
    "popup_detail",
    POPUP_DETAIL_RETRIES
  );

  if (!res || res.status === 0) {
    popupDetailCooldown.set(popupId, now + POPUP_DETAIL_TIMEOUT_COOLDOWN_SEC * 1000);
  } else if (res.status >= 500) {
    popupDetailCooldown.set(popupId, now + POPUP_DETAIL_COOLDOWN_SEC * 1000);
  }

  t_popup_detail.add(res.timings.duration);
  check(res, { "popup_detail 2xx": () => ok2xx(res) });
  return res;
}

function api_order_create({ popupId }) {
  const body = JSON.stringify({
    popupId,
    lines: [{ goodsId: "G1", quantity: 1 }],
    orderType: "RESERVATION",
  });

  const res = requestWithRetry("POST", `${BASE_URL}/api/orders/v1/orders`, body, "order_create");
  t_order_create.add(res.timings.duration);
  check(res, { "order_create 2xx": () => ok2xx(res) });
  return res;
}

function api_stock_reserve({ popupId, orderId }) {
  const body = JSON.stringify({
    popupId,
    orderId,
    items: [{ goodsId: "G1", quantity: 1 }],
  });

  const res = requestWithRetry("POST", `${BASE_URL}/api/stores/v1/stocks/reserve`, body, "stock_reserve");
  t_stock_reserve.add(res.timings.duration);
  check(res, { "stock_reserve 2xx": () => ok2xx(res) });
  return res;
}

function api_payment_request({ orderId }) {
  const body = JSON.stringify({ orderId });

  const res = requestWithRetry("POST", `${BASE_URL}/api/payments/v1/payments/request`, body, "payment_request");
  t_payment_req.add(res.timings.duration);
  check(res, { "payment_request 2xx": () => ok2xx(res) });
  return res;
}

function extractOrderId(orderRes) {
  try {
    const j = JSON.parse(orderRes.body);
    return j?.data?.id || j?.data?.orderId || j?.orderId || null;
  } catch (_) {
    return null;
  }
}

export const options = {
  discardResponseBodies: true,
  noConnectionReuse: NO_CONNECTION_REUSE,
  noVUConnectionReuse: NO_VU_CONNECTION_REUSE,
  batch: BATCH,
  batchPerHost: BATCH_PER_HOST,
  scenarios: {
    stage1_steady: {
      executor: "constant-arrival-rate",
      rate: STEADY_RPS,
      timeUnit: "1s",
      duration: STEADY_DURATION,
      preAllocatedVUs: parseInt(__ENV.STEADY_PRE_VUS || "300", 10),
      maxVUs: parseInt(__ENV.STEADY_MAX_VUS || "3000", 10),
      exec: "stageSteady",
      tags: { stage: "steady", scenario: SCENARIO },
    },
    stage2_rush: {
      executor: "constant-arrival-rate",
      rate: RUSH_RPS,
      timeUnit: "1s",
      duration: RUSH_DURATION,
      preAllocatedVUs: parseInt(__ENV.RUSH_PRE_VUS || "600", 10),
      maxVUs: parseInt(__ENV.RUSH_MAX_VUS || "6000", 10),
      exec: "stageRush",
      startTime: STEADY_DURATION,
      tags: { stage: "rush", scenario: SCENARIO },
    },
    stage3_spike: {
      executor: "constant-arrival-rate",
      rate: SPIKE_RPS,
      timeUnit: "1s",
      duration: SPIKE_DURATION,
      preAllocatedVUs: parseInt(__ENV.SPIKE_PRE_VUS || "900", 10),
      maxVUs: parseInt(__ENV.SPIKE_MAX_VUS || "9000", 10),
      exec: "stageSpike",
      startTime: addDurations(STEADY_DURATION, RUSH_DURATION),
      tags: { stage: "spike", scenario: SCENARIO },
    },
  },

  thresholds: {
    r_fail: ["rate<0.01"],
    http_req_duration: ["p(95)<500"],
    t_order_create: ["p(95)<1200"],
    t_payment_req: ["p(95)<1500"],
  },
};

function addDurations(a, b) {
  const toSeconds = (value) => {
    const v = String(value).trim();
    const m = v.match(/^(\d+)([smh])$/i);
    if (!m) throw new Error(`Unsupported duration format: ${value}`);
    const n = parseInt(m[1], 10);
    const unit = m[2].toLowerCase();
    if (unit === "s") return n;
    if (unit === "m") return n * 60;
    if (unit === "h") return n * 3600;
    throw new Error(`Unsupported duration unit: ${unit}`);
  };

  const totalSeconds = toSeconds(a) + toSeconds(b);
  return `${totalSeconds}s`;
}

export function stageSteady() {
  group("stage1_steady", () => {
    runScenario(SCENARIO, "steady");
    sleep(0.2 + Math.random() * 0.8);
  });
}

export function stageRush() {
  group("stage2_rush", () => {
    runScenario(SCENARIO, "rush");
    sleep(Math.random() * 0.5);
  });
}

export function stageSpike() {
  group("stage3_spike", () => {
    runScenario(SCENARIO, "spike");
    sleep(Math.random() * 0.2);
  });
}

function scenarioHot(stage) {
  const hotPopupId = POPUP_HOT_ID || pickDistributedPopup();
  if (ENABLE_POPUP_LIST) api_popup_list();
  api_popup_detail(hotPopupId);

  let writeProb = 0.15;
  if (stage === "rush") writeProb = 0.30;
  if (stage === "spike") writeProb = 0.55;

  if (ENABLE_WRITE && Math.random() < writeProb) {
    const orderRes = api_order_create({ popupId: hotPopupId });
    const orderId = extractOrderId(orderRes);

    if (orderId) api_stock_reserve({ popupId: hotPopupId, orderId });
    if (orderId) api_payment_request({ orderId });
  }
}

function scenarioDist(stage) {
  const popupId = pickDistributedPopup();

  if (ENABLE_POPUP_LIST) api_popup_list();
  api_popup_detail(popupId);

  let writeProb = 0.10;
  if (stage === "rush") writeProb = 0.15;
  if (stage === "spike") writeProb = 0.25;

  if (ENABLE_WRITE && Math.random() < writeProb) {
    const orderRes = api_order_create({ popupId });
    const orderId = extractOrderId(orderRes);
    if (orderId) api_payment_request({ orderId });
  }
}

function scenarioFault(stage) {
  const hotPopupId = POPUP_HOT_ID || pickDistributedPopup();
  const popupId = stage === "rush" || stage === "spike" ? hotPopupId : pickDistributedPopup();

  if (ENABLE_POPUP_LIST) api_popup_list();
  api_popup_detail(popupId);

  let writeProb = 0.20;
  if (stage === "rush") writeProb = 0.35;
  if (stage === "spike") writeProb = 0.60;

  if (ENABLE_WRITE && Math.random() < writeProb) {
    const orderRes = api_order_create({ popupId });
    const orderId = extractOrderId(orderRes);
    if (orderId) api_payment_request({ orderId });
  }
}

function runScenario(name, stage) {
  if (name === "hot") return scenarioHot(stage);
  if (name === "dist") return scenarioDist(stage);
  if (name === "fault") return scenarioFault(stage);
  return scenarioHot(stage);
}

export default function () {}
