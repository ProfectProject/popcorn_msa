import http from "k6/http";
import { check, sleep, group } from "k6";
import { Trend, Rate } from "k6/metrics";

const BASE_URL = __ENV.BASE_URL || "https://api.goormpopcorn.shop";
const AUTH_TOKEN = __ENV.AUTH_TOKEN || "";
const POPUP_HOT_ID = __ENV.POPUP_HOT_ID || "e534dfc8-23e7-4a7c-93c6-ce4ac6ec934d";
const USER_IDS = (__ENV.USER_IDS || "1,2,3,4,5").split(",");
const ORDER_ID_FEED_URL = __ENV.ORDER_ID_FEED_URL || "";

const MODE = (__ENV.MODE || "all").toLowerCase();

const STORM_RPS_TOTAL = parseInt(__ENV.STORM_RPS_TOTAL || "1200", 10);
const STORM_DURATION = __ENV.STORM_DURATION || "8m";
const STORM_SPLIT_MY_ORDER_PCT = parseInt(__ENV.STORM_SPLIT_MY_ORDER_PCT || "35", 10);

const MIX_RPS = parseInt(__ENV.MIX_RPS || "500", 10);
const MIX_DURATION = __ENV.MIX_DURATION || "10m";

const CONSISTENCY_RPS = parseInt(__ENV.CONSISTENCY_RPS || "80", 10);
const CONSISTENCY_DURATION = __ENV.CONSISTENCY_DURATION || "10m";

const QUERY_P95_MS = parseInt(__ENV.QUERY_P95_MS || "400", 10);
const CONSISTENCY_P95_MS = parseInt(__ENV.CONSISTENCY_P95_MS || "5000", 10);

const t_my_order_status = new Trend("t_my_order_status");
const t_popup_stock = new Trend("t_popup_stock");
const t_order_list = new Trend("t_order_list");
const t_consistency_lag_ms = new Trend("t_consistency_lag_ms");
const r_query_fail = new Rate("r_query_fail");

function headers() {
  const h = { "Content-Type": "application/json" };
  if (AUTH_TOKEN) h["Authorization"] = `Bearer ${AUTH_TOKEN}`;
  return h;
}

function pickUserId() {
  return USER_IDS[Math.floor(Math.random() * USER_IDS.length)];
}

function api_my_order_status(orderId) {
  const res = http.get(`${BASE_URL}/api/order-query/v1/orders/${orderId}`, {
    headers: headers(),
    tags: { name: "query_my_order_status" },
  });
  t_my_order_status.add(res.timings.duration);
  const ok = res.status >= 200 && res.status < 300;
  r_query_fail.add(!ok);
  check(res, { "my_order_status 2xx": () => ok });
  return res;
}

function api_popup_stock(popupId) {
  const res = http.get(`${BASE_URL}/api/order-query/v1/popups/${popupId}/stock`, {
    headers: headers(),
    tags: { name: "query_popup_stock" },
  });
  t_popup_stock.add(res.timings.duration);
  const ok = res.status >= 200 && res.status < 300;
  r_query_fail.add(!ok);
  check(res, { "popup_stock 2xx": () => ok });
  return res;
}

function api_order_list(userId, page = 0, size = 20) {
  const res = http.get(`${BASE_URL}/api/order-query/v1/users/${userId}/orders?page=${page}&size=${size}`, {
    headers: headers(),
    tags: { name: "query_order_list" },
  });
  t_order_list.add(res.timings.duration);
  const ok = res.status >= 200 && res.status < 300;
  r_query_fail.add(!ok);
  check(res, { "order_list 2xx": () => ok });
  return res;
}

function calcConsistencyLagMs(resBody) {
  try {
    const json = JSON.parse(resBody);
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

function fetchRecentOrderId() {
  if (!ORDER_ID_FEED_URL) return null;
  const res = http.get(ORDER_ID_FEED_URL, { headers: headers(), tags: { name: "order_id_feed" } });
  if (!(res.status >= 200 && res.status < 300)) return null;
  try {
    const json = JSON.parse(res.body);
    const ids = json?.data?.orderIds || json?.orderIds || [];
    if (!ids.length) return null;
    return ids[Math.floor(Math.random() * ids.length)];
  } catch (_) {
    return null;
  }
}

const STORM_MY_ORDER_RPS = Math.floor((STORM_RPS_TOTAL * STORM_SPLIT_MY_ORDER_PCT) / 100);
const STORM_POPUP_STOCK_RPS = Math.max(0, STORM_RPS_TOTAL - STORM_MY_ORDER_RPS);

function addDurations(a, b) {
  const ma = parseInt(String(a).replace("m", ""), 10);
  const mb = parseInt(String(b).replace("m", ""), 10);
  return `${ma + mb}m`;
}

const mixStart = STORM_DURATION;
const consistencyStart = addDurations(STORM_DURATION, MIX_DURATION);

function modeEnabled(x) {
  return MODE === x;
}

export const options = {
  scenarios: createScenarios(),

  thresholds: {
    r_query_fail: ["rate<0.01"],
    http_req_duration: [`p(95)<${QUERY_P95_MS}`],
    t_my_order_status: [`p(95)<${QUERY_P95_MS}`],
    t_popup_stock: [`p(95)<${QUERY_P95_MS}`],
    t_order_list: ["p(95)<700"],
    t_consistency_lag_ms: [`p(95)<${CONSISTENCY_P95_MS}`],
  },
};

function createScenarios() {
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

  if (modeEnabled("mix")) {
    scenarios.steady_read_mix = {
      executor: "constant-arrival-rate",
      rate: MIX_RPS,
      timeUnit: "1s",
      duration: MIX_DURATION,
      preAllocatedVUs: 800,
      maxVUs: 16000,
      exec: "scenarioSteadyReadMix",
      tags: { scenario: "steady_read_mix" },
    };
  } else if (modeEnabled("all")) {
    scenarios.steady_read_mix = {
      executor: "constant-arrival-rate",
      rate: MIX_RPS,
      timeUnit: "1s",
      duration: MIX_DURATION,
      preAllocatedVUs: 800,
      maxVUs: 16000,
      exec: "scenarioSteadyReadMix",
      startTime: mixStart,
      tags: { scenario: "steady_read_mix" },
    };
  }

  if (modeEnabled("consistency")) {
    scenarios.consistency_after_event_burst = {
      executor: "constant-arrival-rate",
      rate: CONSISTENCY_RPS,
      timeUnit: "1s",
      duration: CONSISTENCY_DURATION,
      preAllocatedVUs: 200,
      maxVUs: 5000,
      exec: "scenarioConsistency",
      tags: { scenario: "consistency_after_event_burst" },
    };
  } else if (modeEnabled("all")) {
    scenarios.consistency_after_event_burst = {
      executor: "constant-arrival-rate",
      rate: CONSISTENCY_RPS,
      timeUnit: "1s",
      duration: CONSISTENCY_DURATION,
      preAllocatedVUs: 200,
      maxVUs: 5000,
      exec: "scenarioConsistency",
      startTime: consistencyStart,
      tags: { scenario: "consistency_after_event_burst" },
    };
  }

  return scenarios;
}

export function scenarioStormMyOrder() {
  group("storm_my_order", () => {
    const orderId = fetchRecentOrderId();
    if (orderId) {
      api_my_order_status(orderId);
    } else {
      api_order_list(pickUserId(), 0, 20);
    }
    sleep(0.05);
  });
}

export function scenarioStormPopupStock() {
  group("storm_popup_stock", () => {
    api_popup_stock(POPUP_HOT_ID);
    sleep(0.02);
  });
}

export function scenarioSteadyReadMix() {
  group("steady_read_mix", () => {
    const x = Math.random();
    if (x < 0.55) {
      api_popup_stock(POPUP_HOT_ID);
    } else if (x < 0.90) {
      api_order_list(pickUserId(), Math.floor(Math.random() * 10), 20);
    } else {
      const orderId = fetchRecentOrderId();
      if (orderId) api_my_order_status(orderId);
      else api_order_list(pickUserId(), 0, 20);
    }
    sleep(0.1 + Math.random() * 0.4);
  });
}

export function scenarioConsistency() {
  group("consistency_after_event_burst", () => {
    const orderId = fetchRecentOrderId();
    if (!orderId) {
      api_popup_stock(POPUP_HOT_ID);
      sleep(0.2);
      return;
    }
    const res = api_my_order_status(orderId);
    const lag = calcConsistencyLagMs(res.body);
    if (lag !== null) t_consistency_lag_ms.add(lag);
    sleep(0.2);
  });
}

export default function () {}
