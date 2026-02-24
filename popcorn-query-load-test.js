import http from "k6/http";
import { check, sleep, group } from "k6";
import { Trend, Rate } from "k6/metrics";

/**
 * =========================================
 * Popcorn Order Query 부하 테스트 스크립트 (실제 API 버전)
 * =========================================
 *
 * ENV 변수:
 * BASE_URL               : OrderQuery Service URL (default: http://localhost:8087)
 * AUTH_TOKEN            : JWT Bearer token
 * POPUP_HOT_ID          : 핫팝업 UUID
 * USER_ID               : 테스트용 사용자 ID
 * STORE_ID              : 테스트용 스토어 ID
 * ORDER_ID_SAMPLE       : 샘플 주문 ID (실제 존재하는 ID)
 *
 * MODE                  : storm | mix | consistency | all
 * STORM_RPS_TOTAL       : 새로고침 폭탄 총 RPS (default: 1200)
 * STORM_DURATION        : 새로고침 폭탄 지속 시간 (default: 8m)
 * STORM_SPLIT_MY_ORDER_PCT : 내 주문 조회 비율 % (default: 35)
 *
 * MIX_RPS               : 정상 운영 RPS (default: 500)
 * MIX_DURATION          : 정상 운영 지속 시간 (default: 10m)
 *
 * CONSISTENCY_RPS       : 최신성 테스트 RPS (default: 80)
 * CONSISTENCY_DURATION  : 최신성 테스트 지속 시간 (default: 10m)
 */

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080"; // Gateway 주소로 변경
const AUTH_TOKEN = __ENV.AUTH_TOKEN || "";
const POPUP_HOT_ID = __ENV.POPUP_HOT_ID || "ff31f6d6-1234-5678-9abc-123456789abc";
const USER_ID = __ENV.USER_ID || "1";
const STORE_ID = __ENV.STORE_ID || "1";
const ORDER_ID_SAMPLE = __ENV.ORDER_ID_SAMPLE || "sample-order-id";

// 자동 로그인 설정
const AUTO_LOGIN = __ENV.AUTO_LOGIN === "true" || !AUTH_TOKEN;
const LOGIN_EMAIL = __ENV.LOGIN_EMAIL || "popcorn5@popcorn.com"; // Query는 admin 계정 사용
const LOGIN_PASSWORD = __ENV.LOGIN_PASSWORD || "testPassword123";
const LOGIN_ROLE = __ENV.LOGIN_ROLE || "admin"; // admin | customer

// 로그인 계정 설정
const LOGIN_ACCOUNTS = {
  customer: { email: "popcorn1@popcorn.com", password: "test123" },
  admin: { email: "popcorn5@popcorn.com", password: "testPassword123" }
};

const MODE = (__ENV.MODE || "all").toLowerCase();

const STORM_RPS_TOTAL = parseInt(__ENV.STORM_RPS_TOTAL || "1200", 10);
const STORM_DURATION = __ENV.STORM_DURATION || "8m";
const STORM_SPLIT_MY_ORDER_PCT = parseInt(__ENV.STORM_SPLIT_MY_ORDER_PCT || "35", 10);

const MIX_RPS = parseInt(__ENV.MIX_RPS || "500", 10);
const MIX_DURATION = __ENV.MIX_DURATION || "10m";

const CONSISTENCY_RPS = parseInt(__ENV.CONSISTENCY_RPS || "80", 10);
const CONSISTENCY_DURATION = __ENV.CONSISTENCY_DURATION || "10m";

const QUERY_P95_MS = parseInt(__ENV.QUERY_P95_MS || "400", 10); // 목표 400ms
const CONSISTENCY_P95_MS = parseInt(__ENV.CONSISTENCY_P95_MS || "5000", 10);

// ---------- metrics ----------
const t_dashboard_main = new Trend("t_dashboard_main");
const t_dashboard_orders = new Trend("t_dashboard_orders");
const t_dashboard_statistics = new Trend("t_dashboard_statistics");
const t_dashboard_status_summary = new Trend("t_dashboard_status_summary");
const t_order_summary = new Trend("t_order_summary");
const t_order_items = new Trend("t_order_items");
const t_consistency_lag_ms = new Trend("t_consistency_lag_ms");

const r_query_fail = new Rate("r_query_fail");

// ===== 글로벌 토큰 관리 =====
let globalAccessToken = AUTH_TOKEN;
let globalRefreshToken = "";
let tokenExpiresAt = 0; // timestamp

// ---------- helpers ----------
function headers() {
  const h = { "Content-Type": "application/json" };

  // 토큰 만료 확인 및 갱신
  if (AUTO_LOGIN && needsTokenRefresh()) {
    refreshAccessToken();
  }

  const token = globalAccessToken || AUTH_TOKEN;
  if (token) h["Authorization"] = `Bearer ${token}`;
  return h;
}

function needsTokenRefresh() {
  // 토큰이 없거나 만료 5분 전이면 갱신 필요
  const fiveMinutesFromNow = Date.now() + (5 * 60 * 1000);
  return !globalAccessToken || tokenExpiresAt <= fiveMinutesFromNow;
}

function extractDataFromResponse(resBody) {
  try {
    const json = JSON.parse(resBody);
    return json?.data || null;
  } catch (_) {
    return null;
  }
}

function ok2xx(res) {
  const ok = res.status >= 200 && res.status < 300;
  r_query_fail.add(!ok);
  return ok;
}

/**
 * =========================================
 * 인증 관련 API 호출 함수들
 * =========================================
 */

// 로그인 (실제: POST /api/users/v1/auth/login)
function performLogin() {
  const account = LOGIN_ACCOUNTS[LOGIN_ROLE] || LOGIN_ACCOUNTS.admin;

  const body = JSON.stringify({
    email: account.email,
    password: account.password
  });

  const res = http.post(
    `${BASE_URL}/api/users/v1/auth/login`,
    body,
    { headers: { "Content-Type": "application/json" }, tags: { name: "login" } }
  );

  const ok = ok2xx(res);

  if (ok) {
    try {
      const data = JSON.parse(res.body);
      globalAccessToken = data.token;
      globalRefreshToken = data.refreshToken;
      tokenExpiresAt = Date.now() + (50 * 60 * 1000);

      console.log(`✅ Login successful for ${account.email}`);
    } catch (e) {
      console.error(`❌ Login response parsing failed: ${e.message}`);
    }
  } else {
    console.error(`❌ Login failed: ${res.status} - ${res.body}`);
  }

  return res;
}

// 토큰 갱신 (실제: POST /api/users/v1/auth/refresh)
function refreshAccessToken() {
  if (!globalRefreshToken) {
    console.log("🔄 No refresh token, performing fresh login...");
    return performLogin();
  }

  const body = JSON.stringify({
    refreshToken: globalRefreshToken
  });

  const res = http.post(
    `${BASE_URL}/api/users/v1/auth/refresh`,
    body,
    { headers: { "Content-Type": "application/json" }, tags: { name: "refresh" } }
  );

  const ok = ok2xx(res);

  if (ok) {
    try {
      const data = JSON.parse(res.body);
      globalAccessToken = data.accessToken;
      tokenExpiresAt = Date.now() + (50 * 60 * 1000);

      console.log("✅ Token refresh successful");
    } catch (e) {
      console.error(`❌ Refresh response parsing failed: ${e.message}`);
      return performLogin();
    }
  } else {
    console.error(`❌ Token refresh failed: ${res.status}, performing fresh login...`);
    return performLogin();
  }

  return res;
}

/**
 * =========================================
 * 실제 OrderQuery API 호출 함수들
 * =========================================
 */

// Dashboard 메인 (실제: GET /api/order-query/v1/dashboard/main) - Gateway를 통해 접근
function api_dashboard_main() {
  const res = http.get(
    `${BASE_URL}/api/order-query/v1/dashboard/main`,
    { headers: headers(), tags: { name: "dashboard_main" } }
  );

  t_dashboard_main.add(res.timings.duration);
  const ok = ok2xx(res);
  check(res, {
    "dashboard_main 2xx": () => ok,
    "dashboard_main has data": () => {
      if (!ok) return false;
      const data = extractDataFromResponse(res.body);
      return data != null;
    }
  });

  return res;
}

// Dashboard 주문 목록 (Gateway를 통해 접근)
function api_dashboard_orders(page = 0, size = 20, status = null) {
  let url = `${BASE_URL}/api/order-query/v1/dashboard/orders?page=${page}&size=${size}`;
  if (status) url += `&status=${status}`;

  const res = http.get(url, {
    headers: headers(),
    tags: { name: "dashboard_orders" }
  });

  t_dashboard_orders.add(res.timings.duration);
  const ok = ok2xx(res);
  check(res, {
    "dashboard_orders 2xx": () => ok,
    "dashboard_orders has content": () => {
      if (!ok) return false;
      const data = extractDataFromResponse(res.body);
      return data && data.content && Array.isArray(data.content);
    }
  });

  return res;
}

// Dashboard 통계 (Gateway를 통해 접근)
function api_dashboard_statistics() {
  const res = http.get(
    `${BASE_URL}/api/order-query/v1/dashboard/statistics`,
    { headers: headers(), tags: { name: "dashboard_statistics" } }
  );

  t_dashboard_statistics.add(res.timings.duration);
  const ok = ok2xx(res);
  check(res, {
    "dashboard_statistics 2xx": () => ok,
    "dashboard_statistics has data": () => {
      if (!ok) return false;
      const data = extractDataFromResponse(res.body);
      return data != null;
    }
  });

  return res;
}

// Dashboard 상태 요약 (Gateway를 통해 접근)
function api_dashboard_status_summary() {
  const res = http.get(
    `${BASE_URL}/api/order-query/v1/dashboard/status-summary`,
    { headers: headers(), tags: { name: "dashboard_status_summary" } }
  );

  t_dashboard_status_summary.add(res.timings.duration);
  const ok = ok2xx(res);
  check(res, {
    "dashboard_status_summary 2xx": () => ok,
    "dashboard_status_summary has data": () => {
      if (!ok) return false;
      const data = extractDataFromResponse(res.body);
      return data != null;
    }
  });

  return res;
}

// 팝업 주문 요약 (Gateway를 통해 접근)
function api_order_summary(storeId, popupId) {
  const res = http.get(
    `${BASE_URL}/api/order-query/v1/owner/stores/${storeId}/popups/${popupId}/orders/summary`,
    { headers: headers(), tags: { name: "order_summary" } }
  );

  t_order_summary.add(res.timings.duration);
  const ok = ok2xx(res);
  check(res, {
    "order_summary 2xx": () => ok,
    "order_summary has data": () => {
      if (!ok) return false;
      const data = extractDataFromResponse(res.body);
      return data != null;
    }
  });

  return res;
}

// 팝업 주문 아이템 목록 (Gateway를 통해 접근)
function api_order_items(storeId, popupId, page = 0, size = 20, status = null) {
  let url = `${BASE_URL}/api/order-query/v1/owner/stores/${storeId}/popups/${popupId}/orders/items?page=${page}&size=${size}`;
  if (status) url += `&status=${status}`;

  const res = http.get(url, {
    headers: headers(),
    tags: { name: "order_items" }
  });

  t_order_items.add(res.timings.duration);
  const ok = ok2xx(res);
  check(res, {
    "order_items 2xx": () => ok,
    "order_items has content": () => {
      if (!ok) return false;
      const data = extractDataFromResponse(res.body);
      return data && data.content && Array.isArray(data.content);
    }
  });

  return res;
}

// 최신성 확인을 위한 최적화된 Dashboard 호출 (Gateway를 통해 접근)
function api_dashboard_optimized() {
  const res = http.get(
    `${BASE_URL}/api/order-query/v1/dashboard/main/optimized`,
    { headers: headers(), tags: { name: "dashboard_optimized" } }
  );

  const ok = ok2xx(res);
  check(res, {
    "dashboard_optimized 2xx": () => ok
  });

  return res;
}

// 최신성(반영지연) 계산
function calcConsistencyLagMs(resBody) {
  try {
    const json = JSON.parse(resBody);
    const data = json?.data;
    if (!data) return null;

    // 대시보드의 경우 lastUpdatedAt 또는 generatedAt 필드를 확인
    const lastUpdatedAt = data.lastUpdatedAt || data.generatedAt;
    if (!lastUpdatedAt) return null;

    const currentTime = Date.now();
    const updateTime = Date.parse(lastUpdatedAt);
    if (Number.isNaN(updateTime)) return null;

    return currentTime - updateTime;
  } catch (_) {
    return null;
  }
}

/**
 * =========================================
 * 시나리오 실행 구성
 * =========================================
 */

// STORM: 총 RPS를 나누기
const STORM_DASHBOARD_RPS = Math.floor((STORM_RPS_TOTAL * STORM_SPLIT_MY_ORDER_PCT) / 100);
const STORM_ORDER_QUERY_RPS = Math.max(0, STORM_RPS_TOTAL - STORM_DASHBOARD_RPS);

// startTime 계산
function addDurations(a, b) {
  const ma = parseInt(String(a).replace("m", ""), 10);
  const mb = parseInt(String(b).replace("m", ""), 10);
  return `${ma + mb}m`;
}

const mixStart = STORM_DURATION;
const consistencyStart = addDurations(STORM_DURATION, MIX_DURATION);

export const options = {
  scenarios: {
    // ========== 1) 새로고침 폭탄 - Dashboard 조회 집중 ==========
    storm_dashboard: modeEnabled("storm") || modeEnabled("all") ? {
      executor: "constant-arrival-rate",
      rate: STORM_DASHBOARD_RPS,
      timeUnit: "1s",
      duration: STORM_DURATION,
      preAllocatedVUs: 800,
      maxVUs: 12000,
      exec: "scenarioStormDashboard",
      tags: { scenario: "storm_dashboard" },
    } : undefined,

    // ========== 새로고침 폭탄 - 상세 주문 쿼리 집중 ==========
    storm_order_query: modeEnabled("storm") || modeEnabled("all") ? {
      executor: "constant-arrival-rate",
      rate: STORM_ORDER_QUERY_RPS,
      timeUnit: "1s",
      duration: STORM_DURATION,
      preAllocatedVUs: 1200,
      maxVUs: 20000,
      exec: "scenarioStormOrderQuery",
      tags: { scenario: "storm_order_query" },
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
    consistency_check: modeEnabled("consistency") ? {
      executor: "constant-arrival-rate",
      rate: CONSISTENCY_RPS,
      timeUnit: "1s",
      duration: CONSISTENCY_DURATION,
      preAllocatedVUs: 200,
      maxVUs: 5000,
      exec: "scenarioConsistency",
      tags: { scenario: "consistency_check" },
    } : (modeEnabled("all") ? {
      executor: "constant-arrival-rate",
      rate: CONSISTENCY_RPS,
      timeUnit: "1s",
      duration: CONSISTENCY_DURATION,
      preAllocatedVUs: 200,
      maxVUs: 5000,
      exec: "scenarioConsistency",
      startTime: consistencyStart,
      tags: { scenario: "consistency_check" },
    } : undefined),
  },

  thresholds: {
    r_query_fail: ["rate<0.01"], // 에러율 < 1%
    http_req_duration: [`p(95)<${QUERY_P95_MS}`],

    // 엔드포인트별 목표
    t_dashboard_main: [`p(95)<${QUERY_P95_MS}`],
    t_dashboard_orders: [`p(95)<${QUERY_P95_MS}`],
    t_dashboard_statistics: [`p(95)<700`], // 통계는 좀 더 여유
    t_order_summary: [`p(95)<${QUERY_P95_MS}`],
    t_order_items: [`p(95)<${QUERY_P95_MS}`],

    // 최신성 목표
    t_consistency_lag_ms: [`p(95)<${CONSISTENCY_P95_MS}`],
  },
};

function modeEnabled(x) {
  return MODE === x;
}

/**
 * =========================================
 * 시나리오 1) 새로고침 폭탄
 * =========================================
 */

export function scenarioStormDashboard() {
  group("storm_dashboard", () => {
    // Dashboard 메인화면을 반복 조회 (관리자들이 F5 연타)
    api_dashboard_main();

    // 가끔 상태 요약도 확인
    if (Math.random() < 0.3) {
      api_dashboard_status_summary();
    }

    sleep(0.05);
  });
}

export function scenarioStormOrderQuery() {
  group("storm_order_query", () => {
    // 주문 목록을 다양한 조건으로 조회
    const statuses = ["REQUESTED", "PAID", "COMPLETED", "CANCELLED"];
    const randomStatus = Math.random() < 0.7 ? statuses[Math.floor(Math.random() * statuses.length)] : null;

    api_dashboard_orders(
      Math.floor(Math.random() * 10),  // 0-9 페이지
      20,
      randomStatus
    );

    // 가끔 특정 팝업 상세 조회
    if (Math.random() < 0.2) {
      api_order_summary(STORE_ID, POPUP_HOT_ID);
    }

    sleep(0.02);
  });
}

/**
 * =========================================
 * 시나리오 2) 정상 운영 mix
 * =========================================
 */
export function scenarioSteadyReadMix() {
  group("steady_read_mix", () => {
    const x = Math.random();

    // read-heavy: 다양한 조회 패턴
    if (x < 0.40) {
      // Dashboard 메인 (40%)
      api_dashboard_main();
    } else if (x < 0.70) {
      // 주문 목록 조회 (30%)
      api_dashboard_orders(
        Math.floor(Math.random() * 20), // 0-19 페이지
        Math.floor(Math.random() * 20) + 10 // 10-29 사이즈
      );
    } else if (x < 0.85) {
      // 통계 조회 (15%)
      api_dashboard_statistics();
    } else if (x < 0.95) {
      // 상태 요약 (10%)
      api_dashboard_status_summary();
    } else {
      // 팝업별 상세 조회 (5%)
      api_order_summary(STORE_ID, POPUP_HOT_ID);
    }

    sleep(0.1 + Math.random() * 0.4);
  });
}

/**
 * =========================================
 * 시나리오 3) 최신성 테스트
 * =========================================
 */
export function scenarioConsistency() {
  group("consistency_check", () => {
    // 최신성 측정을 위한 최적화된 엔드포인트 호출
    const res = api_dashboard_optimized();
    const lag = calcConsistencyLagMs(res.body);
    if (lag !== null) {
      t_consistency_lag_ms.add(lag);
    }

    // 일반 Dashboard도 함께 측정
    if (Math.random() < 0.5) {
      const mainRes = api_dashboard_main();
      const mainLag = calcConsistencyLagMs(mainRes.body);
      if (mainLag !== null) {
        t_consistency_lag_ms.add(mainLag);
      }
    }

    sleep(0.2);
  });
}

// 테스트 시작 전 1회 실행 - 로그인
export function setup() {
  if (AUTO_LOGIN) {
    console.log(`🔐 Auto login enabled with ${LOGIN_ROLE} account...`);
    const loginRes = performLogin();
    if (globalAccessToken) {
      console.log("✅ Initial login successful");
      return { accessToken: globalAccessToken, refreshToken: globalRefreshToken };
    } else {
      console.error("❌ Initial login failed");
      return null;
    }
  }
  return null;
}

export default function () {}