import http from "k6/http";
import { check, sleep, group } from "k6";
import { Trend, Rate } from "k6/metrics";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
const AUTH_TOKEN = __ENV.AUTH_TOKEN || "";
const SCENARIO = (__ENV.SCENARIO || "hot").toLowerCase();
const ENABLE_POPUP_LIST = (__ENV.ENABLE_POPUP_LIST || "false").toLowerCase() === "true";
const ENABLE_WRITE = (__ENV.ENABLE_WRITE || "false").toLowerCase() === "true";

const POPUP_HOT_ID = __ENV.POPUP_HOT_ID || "HOT_POPUP_ID";
const POPUP_IDS = (__ENV.POPUP_IDS || "P1,P2,P3,P4").split(",");

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
  return POPUP_IDS[Math.floor(Math.random() * POPUP_IDS.length)];
}

function api_popup_list() {
  const res = http.get(`${BASE_URL}/api/stores/v1/popups`, { headers: headers(), tags: { name: "popup_list" } });
  t_popup_list.add(res.timings.duration);
  check(res, { "popup_list 2xx": () => ok2xx(res) });
  return res;
}

function api_popup_detail(popupId) {
  const res = http.get(`${BASE_URL}/api/stores/v1/popups/${popupId}`, { headers: headers(), tags: { name: "popup_detail" } });
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

  const res = http.post(`${BASE_URL}/api/orders/v1/orders`, body, { headers: headers(), tags: { name: "order_create" } });
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

  const res = http.post(`${BASE_URL}/api/stores/v1/stocks/reserve`, body, { headers: headers(), tags: { name: "stock_reserve" } });
  t_stock_reserve.add(res.timings.duration);
  check(res, { "stock_reserve 2xx": () => ok2xx(res) });
  return res;
}

function api_payment_request({ orderId }) {
  const body = JSON.stringify({ orderId });

  const res = http.post(`${BASE_URL}/api/payments/v1/payments/request`, body, { headers: headers(), tags: { name: "payment_request" } });
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
  scenarios: {
    stage1_steady: {
      executor: "constant-arrival-rate",
      rate: STEADY_RPS,
      timeUnit: "1s",
      duration: STEADY_DURATION,
      preAllocatedVUs: 500,
      maxVUs: 15000,
      exec: "stageSteady",
      tags: { stage: "steady", scenario: SCENARIO },
    },
    stage2_rush: {
      executor: "constant-arrival-rate",
      rate: RUSH_RPS,
      timeUnit: "1s",
      duration: RUSH_DURATION,
      preAllocatedVUs: 800,
      maxVUs: 20000,
      exec: "stageRush",
      startTime: STEADY_DURATION,
      tags: { stage: "rush", scenario: SCENARIO },
    },
    stage3_spike: {
      executor: "constant-arrival-rate",
      rate: SPIKE_RPS,
      timeUnit: "1s",
      duration: SPIKE_DURATION,
      preAllocatedVUs: 1500,
      maxVUs: 25000,
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
  if (ENABLE_POPUP_LIST) api_popup_list();
  api_popup_detail(POPUP_HOT_ID);

  let writeProb = 0.15;
  if (stage === "rush") writeProb = 0.30;
  if (stage === "spike") writeProb = 0.55;

  if (ENABLE_WRITE && Math.random() < writeProb) {
    const orderRes = api_order_create({ popupId: POPUP_HOT_ID });
    const orderId = extractOrderId(orderRes);

    if (orderId) api_stock_reserve({ popupId: POPUP_HOT_ID, orderId });
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
  const popupId = stage === "rush" || stage === "spike" ? POPUP_HOT_ID : pickDistributedPopup();

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
