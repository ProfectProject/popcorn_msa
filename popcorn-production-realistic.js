import http from "k6/http";
import { check, sleep, group } from "k6";
import { Trend, Rate } from "k6/metrics";

/**
 * =========================================
 * Popcorn 실전 부하 시나리오 (문서 기반)
 * =========================================
 * ENV 변수:
 * BASE_URL        : Gateway URL (default: https://api.goormpopcorn.shop)
 * AUTH_TOKEN      : JWT Bearer token
 * POPUP_HOT_ID    : 핫팝업 UUID
 * POPUP_IDS       : 분산 팝업 UUIDs (콤마 구분)
 * USER_ID         : 테스트용 사용자 ID
 *
 * SCENARIO        : hot | dist | fault
 * STEADY_RPS      : 200~300 (default 250)
 * RUSH_RPS        : 500~1000 (default 800)
 * SPIKE_RPS       : 1500~2000 (default 1800)
 *
 * STEADY_DURATION : 10m (default 10m)
 * RUSH_DURATION   : 5m~10m (default 10m)
 * SPIKE_DURATION  : 2m~3m (default 3m)
 */

const BASE_URL = __ENV.BASE_URL || "https://api.goormpopcorn.shop";
const AUTH_TOKEN = __ENV.AUTH_TOKEN || "";
const SCENARIO = (__ENV.SCENARIO || "hot").toLowerCase();

const POPUP_HOT_ID = __ENV.POPUP_HOT_ID || "07c79042-f179-452e-9318-0d3abb403c44"; // 야식 배달 기술 전시 (핫팝업)
const POPUP_IDS = (__ENV.POPUP_IDS || "07c79042-f179-452e-9318-0d3abb403c44,7e413857-3363-4bfc-b153-a2da54b7a94c,e534dfc8-23e7-4a7c-93c6-ce4ac6ec934d,1bb5eef2-13ac-4f12-8003-35d0eecf9b36,6b455543-d7dd-481e-9cea-f91e20bed808,7ac19fc7-36aa-47b7-a283-f42d21e5a47d").split(",");

const STEADY_RPS = parseInt(__ENV.STEADY_RPS || "250", 10);
const RUSH_RPS = parseInt(__ENV.RUSH_RPS || "800", 10);
const SPIKE_RPS = parseInt(__ENV.SPIKE_RPS || "1800", 10);

const STEADY_DURATION = __ENV.STEADY_DURATION || "10m";
const RUSH_DURATION = __ENV.RUSH_DURATION || "10m";
const SPIKE_DURATION = __ENV.SPIKE_DURATION || "3m";

// Auto login 설정
const AUTO_LOGIN = __ENV.AUTO_LOGIN === "true" || !AUTH_TOKEN;
const LOGIN_ROLE = __ENV.LOGIN_ROLE || "admin";

const LOGIN_ACCOUNTS = {
  customer: { email: "popcorn1@popcorn.com", password: "test123" },
  admin: { email: "popcorn5@popcorn.com", password: "testPassword123" }
};

// 토큰 관리
let globalAccessToken = AUTH_TOKEN;
let globalRefreshToken = "";

// ===== metrics =====
const t_popup_list = new Trend("t_popup_list");
const t_popup_detail = new Trend("t_popup_detail");
const t_order_create = new Trend("t_order_create");
const t_stock_reserve = new Trend("t_stock_reserve");
const t_payment_req = new Trend("t_payment_req");

const r_fail = new Rate("r_fail");

// ===== helpers =====
function headers() {
  const h = { "Content-Type": "application/json" };
  const token = globalAccessToken || AUTH_TOKEN;
  if (token) h["Authorization"] = `Bearer ${token}`;
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

// 로그인 함수
function performLogin() {
  const account = LOGIN_ACCOUNTS[LOGIN_ROLE] || LOGIN_ACCOUNTS.admin;
  const body = JSON.stringify({
    email: account.email,
    password: account.password
  });

  const res = http.post(`${BASE_URL}/api/users/v1/auth/login`, body, {
    headers: { "Content-Type": "application/json" },
    tags: { name: "login" }
  });

  const ok = ok2xx(res);
  if (ok) {
    try {
      const data = JSON.parse(res.body);
      globalAccessToken = data.token;
      globalRefreshToken = data.refreshToken;
      console.log(`✅ Login successful for ${account.email}`);
    } catch (e) {
      console.error(`❌ Login response parsing failed: ${e.message}`);
    }
  } else {
    console.error(`❌ Login failed: ${res.status} - ${res.body}`);
  }

  return res;
}

/**
 * =========================================
 * 실제 Popcorn API 호출 (문서 기준)
 * =========================================
 */

// 팝업 목록 조회
function api_popup_list() {
  const res = http.get(`${BASE_URL}/api/stores/v1/popups`, {
    headers: headers(),
    tags: { name: "popup_list" }
  });

  t_popup_list.add(res.timings.duration);
  check(res, { "popup_list 2xx": () => ok2xx(res) });
  return res;
}

// 팝업 상세 조회
function api_popup_detail(popupId) {
  const res = http.get(`${BASE_URL}/api/stores/v1/popups/${popupId}`, {
    headers: headers(),
    tags: { name: "popup_detail" }
  });

  t_popup_detail.add(res.timings.duration);
  check(res, { "popup_detail 2xx": () => ok2xx(res) });
  return res;
}

// 주문 생성
function api_order_create({ popupId }) {
  const body = JSON.stringify({
    orderType: "GOODS",
    popupId,
    paymentMethod: "CARD",
    items: [
      {
        orderItemType: "GOODS",
        qty: 1,
        unitPrice: 15000,
        goodsId: "sample-goods-id"
      }
    ]
  });

  const res = http.post(`${BASE_URL}/api/orders/v1/`, body, {
    headers: headers(),
    tags: { name: "order_create" }
  });

  t_order_create.add(res.timings.duration);
  check(res, { "order_create 2xx": () => ok2xx(res) });
  return res;
}

// 재고 예약/차감 (스토어 서비스)
function api_stock_reserve({ popupId, orderId }) {
  const body = JSON.stringify({
    popupId,
    orderId,
    items: [{ goodsId: "sample-goods-id", quantity: 1 }]
  });

  const res = http.post(`${BASE_URL}/api/stores/v1/stocks/reserve`, body, {
    headers: headers(),
    tags: { name: "stock_reserve" }
  });

  t_stock_reserve.add(res.timings.duration);
  check(res, { "stock_reserve 2xx": () => ok2xx(res) });
  return res;
}

// 결제 요청
function api_payment_request({ orderId }) {
  const body = JSON.stringify({ orderId });

  const res = http.post(`${BASE_URL}/api/payments/v1/request`, body, {
    headers: headers(),
    tags: { name: "payment_request" }
  });

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

/**
 * =========================================
 * 3단계 순차 실행 설정 (문서 기준)
 * =========================================
 */
function addDurations(a, b) {
  const ma = parseInt(String(a).replace("m", ""), 10);
  const mb = parseInt(String(b).replace("m", ""), 10);
  return `${ma + mb}m`;
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
    // 문서 목표
    r_fail: ["rate<0.01"],                 // 에러율 < 1%
    http_req_duration: ["p(95)<500"],      // 전체 p95 < 500ms

    // 엔드포인트별
    t_order_create: ["p(95)<1200"],
    t_payment_req: ["p(95)<1500"],
  },
};

/**
 * =========================================
 * Setup: 1회 로그인
 * =========================================
 */
export function setup() {
  if (AUTO_LOGIN) {
    console.log(`🔐 Auto login with ${LOGIN_ROLE} account...`);
    const loginRes = performLogin();
    if (globalAccessToken) {
      console.log("✅ Setup login successful - sharing token across all VUs");
      return {
        accessToken: globalAccessToken,
        refreshToken: globalRefreshToken
      };
    }
  }
  return null;
}

/**
 * =========================================
 * 단계별 실행 함수
 * =========================================
 */

export function stageSteady(data) {
  if (data && data.accessToken) {
    globalAccessToken = data.accessToken;
    globalRefreshToken = data.refreshToken;
  }

  group("stage1_steady", () => {
    runScenario(SCENARIO, "steady");
    sleep(0.2 + Math.random() * 0.8);
  });
}

export function stageRush(data) {
  if (data && data.accessToken) {
    globalAccessToken = data.accessToken;
    globalRefreshToken = data.refreshToken;
  }

  group("stage2_rush", () => {
    runScenario(SCENARIO, "rush");
    sleep(Math.random() * 0.5);
  });
}

export function stageSpike(data) {
  if (data && data.accessToken) {
    globalAccessToken = data.accessToken;
    globalRefreshToken = data.refreshToken;
  }

  group("stage3_spike", () => {
    runScenario(SCENARIO, "spike");
    sleep(Math.random() * 0.2);
  });
}

/**
 * =========================================
 * 시나리오 구현 (문서 기준)
 * =========================================
 */

// SCENARIO 1) hot : 오픈 러시(핫키/핫팝업)
function scenarioHot(stage) {
  // 공통: 핫팝업에 트래픽 집중
  api_popup_list();                 // 목록 조회
  api_popup_detail(POPUP_HOT_ID);   // 상세 조회

  // 단계별로 쓰기 비중 조절
  let writeProb = 0.15; // steady 기본
  if (stage === "rush") writeProb = 0.30;
  if (stage === "spike") writeProb = 0.55;

  if (Math.random() < writeProb) {
    const orderRes = api_order_create({ popupId: POPUP_HOT_ID });
    const orderId = extractOrderId(orderRes);

    // 재고(스토어) 병목 확인 포인트
    if (orderId) api_stock_reserve({ popupId: POPUP_HOT_ID, orderId });

    // 결제 요청
    if (orderId) api_payment_request({ orderId });
  }
}

// SCENARIO 2) dist : 정상 운영(여러 팝업 분산)
function scenarioDist(stage) {
  const popupId = pickDistributedPopup();

  // read-heavy
  api_popup_list();
  api_popup_detail(popupId);

  // 쓰기 비중은 낮게 유지
  let writeProb = 0.10;
  if (stage === "rush") writeProb = 0.15;
  if (stage === "spike") writeProb = 0.25;

  if (Math.random() < writeProb) {
    const orderRes = api_order_create({ popupId });
    const orderId = extractOrderId(orderRes);
    if (orderId) api_payment_request({ orderId });
  }
}

// SCENARIO 3) fault : 장애 내성
function scenarioFault(stage) {
  const popupId = stage === "rush" || stage === "spike" ? POPUP_HOT_ID : pickDistributedPopup();

  // 장애 상황에서도 사용자들은 계속 조회/주문 시도
  api_popup_detail(popupId);

  // 핵심 트랜잭션 유지
  let writeProb = 0.20;
  if (stage === "rush") writeProb = 0.35;
  if (stage === "spike") writeProb = 0.60;

  if (Math.random() < writeProb) {
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