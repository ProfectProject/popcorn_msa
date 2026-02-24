import http from "k6/http";
import { check, sleep, group } from "k6";
import { Trend, Rate } from "k6/metrics";

/**
 * =========================================
 * 🎯 완전한 플로우 부하 테스트 - 100% 성공률 목표
 * =========================================
 * 1️⃣ 주문 생성 → 2️⃣ 결제 완료 처리 → 3️⃣ 재고 처리 → 100% 성공
 *
 * 💥 극한 부하: 250→800→1800 RPS (23분간)
 * 🎯 목표: 5xx 에러 39.4% → 0%
 */

const BASE_URL = __ENV.BASE_URL || "https://api.goormpopcorn.shop";
const SCENARIO = (__ENV.SCENARIO || "hot").toLowerCase();

const POPUP_HOT_ID = __ENV.POPUP_HOT_ID || "07c79042-f179-452e-9318-0d3abb403c44";
const POPUP_IDS = (__ENV.POPUP_IDS || "07c79042-f179-452e-9318-0d3abb403c44,7e413857-3363-4bfc-b153-a2da54b7a94c,e534dfc8-23e7-4a7c-93c6-ce4ac6ec934d,1bb5eef2-13ac-4f12-8003-35d0eecf9b36,6b455543-d7dd-481e-9cea-f91e20bed808,7ac19fc7-36aa-47b7-a283-f42d21e5a47d").split(",");

const STEADY_RPS = parseInt(__ENV.STEADY_RPS || "250", 10);
const RUSH_RPS = parseInt(__ENV.RUSH_RPS || "800", 10);
const SPIKE_RPS = parseInt(__ENV.SPIKE_RPS || "1800", 10);

const STEADY_DURATION = __ENV.STEADY_DURATION || "10m";
const RUSH_DURATION = __ENV.RUSH_DURATION || "10m";
const SPIKE_DURATION = __ENV.SPIKE_DURATION || "3m";

// 🔐 자동 로그인
const AUTO_LOGIN = __ENV.AUTO_LOGIN !== "false";
const LOGIN_ROLE = __ENV.LOGIN_ROLE || "customer";
const LOGIN_ACCOUNTS = {
  customer: { email: "popcorn1@popcorn.com", password: "test123" },
  admin: { email: "popcorn5@popcorn.com", password: "testPassword123" }
};

let globalAccessToken = "";

// ===== metrics =====
const t_login = new Trend("t_login");
const t_popup_list = new Trend("t_popup_list");
const t_popup_detail = new Trend("t_popup_detail");
const t_order_create = new Trend("t_order_create");
const t_stock_reserve = new Trend("t_stock_reserve");
const t_payment_complete = new Trend("t_payment_complete");

const r_fail = new Rate("r_fail");
const r_success = new Rate("r_success");
const r_login_success = new Rate("r_login_success");
const r_complete_flow_success = new Rate("r_complete_flow_success");

// ===== helpers =====
function headers() {
  const h = { "Content-Type": "application/json" };
  if (globalAccessToken) h["Authorization"] = `Bearer ${globalAccessToken}`;
  return h;
}

function ok2xx(res) {
  const ok = res.status >= 200 && res.status < 300;
  r_fail.add(!ok);
  r_success.add(ok);
  return ok;
}

function pickDistributedPopup() {
  return POPUP_IDS[Math.floor(Math.random() * POPUP_IDS.length)];
}

// 🔐 로그인 함수
function performLogin() {
  const account = LOGIN_ACCOUNTS[LOGIN_ROLE];
  const body = JSON.stringify(account);

  const res = http.post(`${BASE_URL}/api/users/v1/auth/login`, body, {
    headers: { "Content-Type": "application/json" },
    timeout: "30s",
    tags: { name: "login" }
  });

  t_login.add(res.timings.duration);
  const success = ok2xx(res);
  r_login_success.add(success);

  if (success) {
    try {
      const data = JSON.parse(res.body);
      globalAccessToken = data.token;
      console.log(`✅ 완전 플로우 로그인 성공: ${account.email}`);
      return true;
    } catch (e) {
      console.error(`❌ 로그인 파싱 실패: ${e}`);
      return false;
    }
  }

  console.error(`❌ 로그인 실패: ${res.status}`);
  return false;
}

/**
 * =========================================
 * 📦 API 호출 (완전한 플로우)
 * =========================================
 */

function api_popup_list() {
  const res = http.get(`${BASE_URL}/api/stores/v1/popups`, {
    headers: headers(),
    tags: { name: "popup_list" },
    timeout: "30s"
  });
  t_popup_list.add(res.timings.duration);
  check(res, {
    "popup_list 2xx": () => ok2xx(res),
    "popup_list has_data": () => {
      if (!ok2xx(res)) return false;
      try {
        const data = JSON.parse(res.body)?.data;
        return data?.content && Array.isArray(data.content) && data.content.length > 0;
      } catch (_) { return false; }
    }
  });
  return res;
}

function api_popup_detail(popupId) {
  const res = http.get(`${BASE_URL}/api/stores/v1/popups/${popupId}`, {
    headers: headers(),
    tags: { name: "popup_detail" },
    timeout: "30s"
  });
  t_popup_detail.add(res.timings.duration);
  check(res, {
    "popup_detail 2xx": () => ok2xx(res),
    "popup_detail has_id": () => {
      if (!ok2xx(res)) return false;
      try { return JSON.parse(res.body)?.data?.id !== undefined; }
      catch (_) { return false; }
    }
  });
  return res;
}

// 1️⃣ 주문 생성
function api_order_create({ popupId }) {
  const body = JSON.stringify({
    orderType: "GOODS",
    popupId,
    paymentMethod: "CARD",
    items: [{
      orderItemType: "GOODS",
      qty: 1,
      unitPrice: 15000,
      goodsId: "sample-goods-id"
    }]
  });

  const res = http.post(`${BASE_URL}/api/orders/v1/`, body, {
    headers: headers(),
    tags: { name: "order_create" },
    timeout: "30s"
  });

  t_order_create.add(res.timings.duration);
  check(res, { "order_create 2xx": () => ok2xx(res) });
  return res;
}

// 2️⃣ 재고 예약
function api_stock_reserve({ popupId, orderId }) {
  const body = JSON.stringify({
    popupId,
    orderId,
    items: [{ goodsId: "sample-goods-id", quantity: 1 }]
  });

  const res = http.post(`${BASE_URL}/api/stores/v1/stocks/reserve`, body, {
    headers: headers(),
    tags: { name: "stock_reserve" },
    timeout: "30s"
  });

  t_stock_reserve.add(res.timings.duration);
  check(res, { "stock_reserve 2xx": () => ok2xx(res) });
  return res;
}

// 3️⃣ 결제 완료 처리 (임의로 성공)
function api_payment_complete({ orderId }) {
  // 🎯 사용자 요청: 결제를 임의로 성공 처리
  const body = JSON.stringify({
    orderId,
    paymentStatus: "COMPLETED",
    paymentMethod: "CARD",
    amount: 15000,
    // 임의로 성공 처리를 위한 더미 데이터
    transactionId: `tx_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
    approvalNumber: `approval_${Date.now()}`,
    completedAt: new Date().toISOString()
  });

  const res = http.post(`${BASE_URL}/api/payments/v1/complete`, body, {
    headers: headers(),
    tags: { name: "payment_complete" },
    timeout: "30s"
  });

  t_payment_complete.add(res.timings.duration);
  check(res, { "payment_complete 2xx": () => ok2xx(res) });
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
 * 🎯 완전한 플로우 실행 (주문→재고→결제완료)
 * =========================================
 */
function executeCompleteFlow(popupId) {
  let flowSuccess = false;

  try {
    // 1️⃣ 주문 생성
    const orderRes = api_order_create({ popupId });
    const orderId = extractOrderId(orderRes);

    if (!orderId) {
      console.log("⚠️ 주문 생성 실패 - orderId 없음");
      return false;
    }

    // 2️⃣ 재고 예약
    const stockRes = api_stock_reserve({ popupId, orderId });
    if (!ok2xx(stockRes)) {
      console.log("⚠️ 재고 예약 실패");
      return false;
    }

    // 3️⃣ 결제 완료 처리 (임의로 성공)
    const paymentRes = api_payment_complete({ orderId });
    if (!ok2xx(paymentRes)) {
      console.log("⚠️ 결제 완료 처리 실패");
      return false;
    }

    flowSuccess = true;
    console.log(`✅ 완전 플로우 성공: 주문(${orderId}) → 재고 → 결제완료`);

  } catch (e) {
    console.error(`❌ 완전 플로우 오류: ${e}`);
    flowSuccess = false;
  }

  r_complete_flow_success.add(flowSuccess);
  return flowSuccess;
}

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
    // 🎯 100% 성공률 목표 (5xx 에러 0%)
    r_fail: ["rate<0.001"],               // 실패율 < 0.1%
    r_success: ["rate>0.999"],            // 성공률 > 99.9%
    r_login_success: ["rate>0.95"],       // 로그인 성공률 > 95%
    r_complete_flow_success: ["rate>0.90"], // 완전 플로우 성공률 > 90%
    http_req_duration: ["p(95)<10000"],   // p95 < 10초 (관대)

    // 엔드포인트별
    t_login: ["p(95)<30000"],
    t_popup_list: ["p(95)<5000"],
    t_popup_detail: ["p(95)<5000"],
    t_order_create: ["p(95)<5000"],
    t_stock_reserve: ["p(95)<5000"],
    t_payment_complete: ["p(95)<5000"],
  },
};

/**
 * =========================================
 * Setup: 로그인
 * =========================================
 */
export function setup() {
  if (AUTO_LOGIN) {
    console.log(`🎯 완전 플로우 로그인 시작 with ${LOGIN_ROLE}...`);

    for (let i = 0; i < 5; i++) {
      if (performLogin()) {
        console.log("✅ 완전 플로우 로그인 성공");
        return { accessToken: globalAccessToken };
      }

      if (i < 4) {
        console.log(`🔄 로그인 재시도 ${i + 1}/5 in 10s...`);
        sleep(10);
      }
    }

    console.error("❌ 완전 플로우 로그인 실패");
  }
  return null;
}

/**
 * =========================================
 * 시나리오 구현 (완전한 플로우)
 * =========================================
 */

// SCENARIO 1) hot : 핫팝업 집중 (완전 플로우)
function scenarioHot(stage) {
  api_popup_list();
  api_popup_detail(POPUP_HOT_ID);

  // 단계별 완전 플로우 비중
  let flowProb = 0.10; // steady 기본
  if (stage === "rush") flowProb = 0.20;
  if (stage === "spike") flowProb = 0.35;

  if (Math.random() < flowProb) {
    executeCompleteFlow(POPUP_HOT_ID);
  }
}

// SCENARIO 2) dist : 분산 팝업 (완전 플로우)
function scenarioDist(stage) {
  const popupId = pickDistributedPopup();

  api_popup_list();
  api_popup_detail(popupId);

  let flowProb = 0.08;
  if (stage === "rush") flowProb = 0.15;
  if (stage === "spike") flowProb = 0.25;

  if (Math.random() < flowProb) {
    executeCompleteFlow(popupId);
  }
}

// SCENARIO 3) fault : 장애 내성 (완전 플로우)
function scenarioFault(stage) {
  const popupId = stage === "rush" || stage === "spike" ? POPUP_HOT_ID : pickDistributedPopup();

  api_popup_detail(popupId);

  let flowProb = 0.15;
  if (stage === "rush") flowProb = 0.25;
  if (stage === "spike") flowProb = 0.45;

  if (Math.random() < flowProb) {
    executeCompleteFlow(popupId);
  }
}

function runScenario(name, stage) {
  if (name === "hot") return scenarioHot(stage);
  if (name === "dist") return scenarioDist(stage);
  if (name === "fault") return scenarioFault(stage);
  return scenarioHot(stage);
}

export function stageSteady(data) {
  if (data?.accessToken) globalAccessToken = data.accessToken;

  group("stage1_steady_complete_flow", () => {
    runScenario(SCENARIO, "steady");
    sleep(0.2 + Math.random() * 0.8);
  });
}

export function stageRush(data) {
  if (data?.accessToken) globalAccessToken = data.accessToken;

  group("stage2_rush_complete_flow", () => {
    runScenario(SCENARIO, "rush");
    sleep(Math.random() * 0.5);
  });
}

export function stageSpike(data) {
  if (data?.accessToken) globalAccessToken = data.accessToken;

  group("stage3_spike_complete_flow", () => {
    runScenario(SCENARIO, "spike");
    sleep(Math.random() * 0.2);
  });
}

export default function () {}