import http from "k6/http";
import { check, sleep, group } from "k6";
import { Trend, Rate } from "k6/metrics";

/**
 * =========================================
 * Order Query 부하 시나리오 3종 (완전판)
 * =========================================
 * 1) 새로고침 폭탄: 초당 800~1500건
 * 2) 정상 운영 mix: Read-heavy 80~95%
 * 3) CQRS 최신성: 이벤트 반영 지연 측정
 */

const BASE_URL = __ENV.BASE_URL || "https://api.goormpopcorn.shop";
const AUTH_TOKEN = __ENV.AUTH_TOKEN || "";
const POPUP_HOT_ID = __ENV.POPUP_HOT_ID || "07c79042-f179-452e-9318-0d3abb403c44";
const USER_IDS = (__ENV.USER_IDS || "U1,U2,U3,U4,U5").split(",");
const ORDER_ID_FEED_URL = __ENV.ORDER_ID_FEED_URL || "";

const MODE = (__ENV.MODE || "all").toLowerCase();

const STORM_RPS_TOTAL = parseInt(__ENV.STORM_RPS_TOTAL || "1200", 10);
const STORM_DURATION = __ENV.STORM_DURATION || "8m";
const STORM_SPLIT_MY_ORDER_PCT = parseInt(__ENV.STORM_SPLIT_MY_ORDER_PCT || "35", 10);

const MIX_RPS = parseInt(__ENV.MIX_RPS || "500", 10);
const MIX_DURATION = __ENV.MIX_DURATION || "10m";

const CONSISTENCY_RPS = parseInt(__ENV.CONSISTENCY_RPS || "80", 10);
const CONSISTENCY_DURATION = __ENV.CONSISTENCY_DURATION || "10m";

const QUERY_P95_MS = parseInt(__ENV.QUERY_P95_MS || "400", 10); // 목표 300~400ms
const CONSISTENCY_P95_MS = parseInt(__ENV.CONSISTENCY_P95_MS || "5000", 10);

// 🔐 로그인 설정
const AUTO_LOGIN = __ENV.AUTO_LOGIN !== "false";
const LOGIN_ROLE = __ENV.LOGIN_ROLE || "admin"; // OrderQuery는 admin 권한 필요
const LOGIN_ACCOUNTS = {
  customer: { email: "popcorn1@popcorn.com", password: "test123" },
  admin: { email: "popcorn5@popcorn.com", password: "testPassword123" }
};

let globalAccessToken = "";

// ---------- metrics ----------
const t_login = new Trend("t_login");
const t_my_order_status = new Trend("t_my_order_status");
const t_popup_stock = new Trend("t_popup_stock");
const t_order_list = new Trend("t_order_list");
const t_consistency_lag_ms = new Trend("t_consistency_lag_ms");

const r_query_fail = new Rate("r_query_fail");
const r_login_success = new Rate("r_login_success");

// ---------- helpers ----------
function headers() {
  const h = { "Content-Type": "application/json" };
  if (globalAccessToken) h["Authorization"] = `Bearer ${globalAccessToken}`;
  return h;
}

function pickUserId() {
  return USER_IDS[Math.floor(Math.random() * USER_IDS.length)];
}

// 🔐 로그인 함수 (OrderQuery용)
function performLogin() {
  const account = LOGIN_ACCOUNTS[LOGIN_ROLE];
  const body = JSON.stringify(account);

  const res = http.post(`${BASE_URL}/api/users/v1/auth/login`, body, {
    headers: { "Content-Type": "application/json" },
    timeout: "60s",
    tags: { name: "login" }
  });

  t_login.add(res.timings.duration);
  const success = res.status >= 200 && res.status < 300;
  r_login_success.add(success);

  if (success) {
    try {
      const data = JSON.parse(res.body);
      globalAccessToken = data.token;
      console.log(`✅ OrderQuery login success: ${account.email}`);
      return true;
    } catch (e) {
      console.error(`❌ Login parse failed: ${e}`);
      return false;
    }
  }

  console.error(`❌ OrderQuery login failed: ${res.status}`);
  return false;
}

function safeLogin(maxRetries = 5) {
  for (let i = 0; i < maxRetries; i++) {
    if (performLogin()) return true;
    const backoff = Math.min(Math.pow(2, i + 1), 32);
    console.log(`🔄 OrderQuery retry ${i + 1}/${maxRetries} in ${backoff}s...`);
    sleep(backoff);
  }
  return false;
}

/**
 * ---- OrderQuery API (실제 경로로 교체 필요) ----
 */

// 내 주문 상태 조회 (orderId 기반)
function api_my_order_status(orderId) {
  const res = http.get(`${BASE_URL}/api/order-query/v1/orders/${orderId}`, {
    headers: headers(),
    tags: { name: "query_my_order_status" },
    timeout: "30s"
  });

  t_my_order_status.add(res.timings.duration);

  const ok = res.status >= 200 && res.status < 300;
  r_query_fail.add(!ok);

  check(res, { "my_order_status 2xx": () => ok });
  return res;
}

// 팝업 상세/재고 조회 (popupId 기반)
function api_popup_stock(popupId) {
  const res = http.get(`${BASE_URL}/api/order-query/v1/popups/${popupId}/stock`, {
    headers: headers(),
    tags: { name: "query_popup_stock" },
    timeout: "30s"
  });

  t_popup_stock.add(res.timings.duration);

  const ok = res.status >= 200 && res.status < 300;
  r_query_fail.add(!ok);

  check(res, { "popup_stock 2xx": () => ok });
  return res;
}

// 주문 목록(마이페이지)
function api_order_list(userId, page = 0, size = 20) {
  const res = http.get(`${BASE_URL}/api/order-query/v1/users/${userId}/orders?page=${page}&size=${size}`, {
    headers: headers(),
    tags: { name: "query_order_list" },
    timeout: "30s"
  });

  t_order_list.add(res.timings.duration);

  const ok = res.status >= 200 && res.status < 300;
  r_query_fail.add(!ok);

  check(res, { "order_list 2xx": () => ok });
  return res;
}

// 최신성(반영지연) 계산
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

// 최근 orderId 가져오기 (옵션)
function fetchRecentOrderId() {
  if (!ORDER_ID_FEED_URL) return null;
  const res = http.get(ORDER_ID_FEED_URL, {
    headers: headers(),
    tags: { name: "order_id_feed" },
    timeout: "15s"
  });
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

/**
 * =========================================
 * 시나리오 실행 구성
 * =========================================
 */

// STORM: 총 RPS를 my_order/popup_stock 비율로 나눔
const STORM_MY_ORDER_RPS = Math.floor((STORM_RPS_TOTAL * STORM_SPLIT_MY_ORDER_PCT) / 100);
const STORM_POPUP_STOCK_RPS = Math.max(0, STORM_RPS_TOTAL - STORM_MY_ORDER_RPS);

// startTime 계산(분 단위 문자열만 지원: "8m", "10m" 등)
function addDurations(a, b) {
  const ma = parseInt(String(a).replace("m", ""), 10);
  const mb = parseInt(String(b).replace("m", ""), 10);
  return `${ma + mb}m`;
}

// all 모드의 타임라인
const mixStart = STORM_DURATION;
const consistencyStart = addDurations(STORM_DURATION, MIX_DURATION);

export const options = {
  scenarios: {
    // ========== 1) 새로고침 폭탄 (동시) ==========
    storm_my_order: modeEnabled("storm") || modeEnabled("all") ? {
      executor: "constant-arrival-rate",
      rate: STORM_MY_ORDER_RPS,
      timeUnit: "1s",
      duration: STORM_DURATION,
      preAllocatedVUs: 800,
      maxVUs: 12000,
      exec: "scenarioStormMyOrder",
      tags: { scenario: "storm_my_order" },
    } : undefined,

    storm_popup_stock: modeEnabled("storm") || modeEnabled("all") ? {
      executor: "constant-arrival-rate",
      rate: STORM_POPUP_STOCK_RPS,
      timeUnit: "1s",
      duration: STORM_DURATION,
      preAllocatedVUs: 1200,
      maxVUs: 20000,
      exec: "scenarioStormPopupStock",
      tags: { scenario: "storm_popup_stock" },
    } : undefined,

    // ========== 2) 정상 운영 mix ==========
    steady_read_mix: modeEnabled("mix") ? {
      executor: "constant-arrival-rate",
      rate: MIX_RPS,
      timeUnit: "1s",
      duration: MIX_DURATION,
      preAllocatedVUs: 800,
      maxVUs: 16000,
      exec: "scenarioSteadyReadMix",
      tags: { scenario: "steady_read_mix" },
    } : (modeEnabled("all") ? {
      executor: "constant-arrival-rate",
      rate: MIX_RPS,
      timeUnit: "1s",
      duration: MIX_DURATION,
      preAllocatedVUs: 800,
      maxVUs: 16000,
      exec: "scenarioSteadyReadMix",
      startTime: mixStart,
      tags: { scenario: "steady_read_mix" },
    } : undefined),

    // ========== 3) 최신성 테스트 ==========
    consistency_after_event_burst: modeEnabled("consistency") ? {
      executor: "constant-arrival-rate",
      rate: CONSISTENCY_RPS,
      timeUnit: "1s",
      duration: CONSISTENCY_DURATION,
      preAllocatedVUs: 200,
      maxVUs: 5000,
      exec: "scenarioConsistency",
      tags: { scenario: "consistency_after_event_burst" },
    } : (modeEnabled("all") ? {
      executor: "constant-arrival-rate",
      rate: CONSISTENCY_RPS,
      timeUnit: "1s",
      duration: CONSISTENCY_DURATION,
      preAllocatedVUs: 200,
      maxVUs: 5000,
      exec: "scenarioConsistency",
      startTime: consistencyStart,
      tags: { scenario: "consistency_after_event_burst" },
    } : undefined),
  },

  thresholds: {
    // 목표 반영: p95 300~400ms
    r_query_fail: ["rate<0.01"], // 에러율 < 1%
    r_login_success: ["rate>0.95"], // 로그인 성공률 > 95%
    http_req_duration: [`p(95)<${QUERY_P95_MS}`],

    // 엔드포인트별
    t_login: ["p(95)<60000"], // 로그인 p95 < 60초
    t_my_order_status: [`p(95)<${QUERY_P95_MS}`],
    t_popup_stock: [`p(95)<${QUERY_P95_MS}`],
    t_order_list: ["p(95)<700"], // 목록은 조금 더 느려도 OK

    // 최신성 목표
    t_consistency_lag_ms: [`p(95)<${CONSISTENCY_P95_MS}`],
  },
};

function modeEnabled(x) {
  return MODE === x || MODE === "all";
}

/**
 * =========================================
 * Setup: OrderQuery용 로그인
 * =========================================
 */
export function setup() {
  if (AUTO_LOGIN) {
    console.log(`🔐 OrderQuery login with ${LOGIN_ROLE} account...`);

    if (safeLogin()) {
      console.log("✅ OrderQuery setup successful - token ready");
      return { accessToken: globalAccessToken };
    }

    console.error("❌ OrderQuery setup failed");
  }
  return null;
}

/**
 * =========================================
 * 구현: 시나리오 1) 오픈 직후 새로고침 폭탄
 * =========================================
 */
export function scenarioStormMyOrder(data) {
  if (data?.accessToken) globalAccessToken = data.accessToken;

  group("storm_my_order", () => {
    // 토큰 재검증
    if (!globalAccessToken && Math.random() < 0.1) {
      safeLogin(2);
    }

    const orderId = fetchRecentOrderId();
    if (orderId) {
      api_my_order_status(orderId);
    } else {
      // orderId feed가 없다면 최소 동작: 목록으로 대체
      api_order_list(pickUserId(), 0, 20);
    }

    sleep(0.05);
  });
}

export function scenarioStormPopupStock(data) {
  if (data?.accessToken) globalAccessToken = data.accessToken;

  group("storm_popup_stock", () => {
    // 토큰 재검증
    if (!globalAccessToken && Math.random() < 0.1) {
      safeLogin(2);
    }

    api_popup_stock(POPUP_HOT_ID);
    sleep(0.02);
  });
}

/**
 * =========================================
 * 구현: 시나리오 2) 정상 운영 mix (80~95% 조회)
 * =========================================
 */
export function scenarioSteadyReadMix(data) {
  if (data?.accessToken) globalAccessToken = data.accessToken;

  group("steady_read_mix", () => {
    // 토큰 재검증
    if (!globalAccessToken && Math.random() < 0.05) {
      safeLogin(2);
    }

    const x = Math.random();

    // read-heavy: 90% 조회(재고/목록/상태)
    if (x < 0.55) {
      api_popup_stock(POPUP_HOT_ID); // 상세/재고 쿼리
    } else if (x < 0.90) {
      api_order_list(pickUserId(), Math.floor(Math.random() * 10), 20); // 페이지네이션
    } else {
      const orderId = fetchRecentOrderId();
      if (orderId) api_my_order_status(orderId);
      else api_order_list(pickUserId(), 0, 20);
    }

    sleep(0.1 + Math.random() * 0.4);
  });
}

/**
 * =========================================
 * 구현: 시나리오 3) 최신성(Consistency) 테스트
 * =========================================
 */
export function scenarioConsistency(data) {
  if (data?.accessToken) globalAccessToken = data.accessToken;

  group("consistency_after_event_burst", () => {
    // 토큰 재검증
    if (!globalAccessToken && Math.random() < 0.05) {
      safeLogin(2);
    }

    const orderId = fetchRecentOrderId();
    if (!orderId) {
      // orderId가 없으면 대체로 재고/목록 조회
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