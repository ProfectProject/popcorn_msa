import http from "k6/http";
import { check, sleep, group } from "k6";
import { Trend, Rate } from "k6/metrics";

/**
 * =========================================
 * Popcorn MSA 부하 테스트 스크립트 (실제 API 버전)
 * =========================================
 *
 * ENV 변수:
 * BASE_URL        : Gateway URL (default: http://localhost:8080)
 * AUTH_TOKEN      : JWT Bearer token
 * POPUP_HOT_ID    : 핫팝업 UUID
 * POPUP_IDS       : 분산 팝업 UUIDs (콤마 구분)
 * USER_ID         : 테스트용 사용자 ID
 *
 * SCENARIO        : hot | dist | fault
 * STEADY_RPS      : 200~300 (default 250)
 * RUSH_RPS        : 500~1000 (default 800)
 * SPIKE_RPS       : 1500~2000 (default 1800)
 */

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
const AUTH_TOKEN = __ENV.AUTH_TOKEN || "";
const SCENARIO = (__ENV.SCENARIO || "hot").toLowerCase();

// 자동 로그인 설정
const AUTO_LOGIN = __ENV.AUTO_LOGIN === "true" || !AUTH_TOKEN; // TOKEN이 없으면 자동 로그인
const LOGIN_EMAIL = __ENV.LOGIN_EMAIL || "popcorn1@popcorn.com";
const LOGIN_PASSWORD = __ENV.LOGIN_PASSWORD || "test123";
const LOGIN_ROLE = __ENV.LOGIN_ROLE || "customer"; // customer | admin

// 로그인 계정 설정
const LOGIN_ACCOUNTS = {
  customer: { email: "popcorn1@popcorn.com", password: "test123" },
  admin: { email: "popcorn5@popcorn.com", password: "testPassword123" }
};

const POPUP_HOT_ID = __ENV.POPUP_HOT_ID || "07c79042-f179-452e-9318-0d3abb403c44"; // 야식 배달 기술 전시 (핫팝업)
const POPUP_IDS = (__ENV.POPUP_IDS || "07c79042-f179-452e-9318-0d3abb403c44,7e413857-3363-4bfc-b153-a2da54b7a94c,e534dfc8-23e7-4a7c-93c6-ce4ac6ec934d,1bb5eef2-13ac-4f12-8003-35d0eecf9b36,6b455543-d7dd-481e-9cea-f91e20bed808,7ac19fc7-36aa-47b7-a283-f42d21e5a47d").split(",");
const USER_ID = __ENV.USER_ID || "1";

const STEADY_RPS = parseInt(__ENV.STEADY_RPS || "250", 10);
const RUSH_RPS = parseInt(__ENV.RUSH_RPS || "800", 10);
const SPIKE_RPS = parseInt(__ENV.SPIKE_RPS || "1800", 10);

const STEADY_DURATION = __ENV.STEADY_DURATION || "10m";
const RUSH_DURATION = __ENV.RUSH_DURATION || "10m";
const SPIKE_DURATION = __ENV.SPIKE_DURATION || "3m";

// ===== metrics =====
const t_popup_list = new Trend("t_popup_list");
const t_popup_detail = new Trend("t_popup_detail");
const t_order_create = new Trend("t_order_create");
const t_order_status = new Trend("t_order_status");
const t_my_orders = new Trend("t_my_orders");
const t_login = new Trend("t_login");
const t_refresh = new Trend("t_refresh");

const r_fail = new Rate("r_fail");

// ===== 글로벌 토큰 관리 =====
let globalAccessToken = AUTH_TOKEN;
let globalRefreshToken = "";
let tokenExpiresAt = 0; // timestamp

// ===== helpers =====
function headers() {
  const h = { "Content-Type": "application/json" };

  // setup에서 받은 토큰 사용 (만료 체크 없이 계속 사용)
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

function extractDataFromResponse(resBody) {
  try {
    const json = JSON.parse(resBody);
    return json?.data || null;
  } catch (_) {
    return null;
  }
}

/**
 * =========================================
 * 인증 관련 API 호출 함수들
 * =========================================
 */

// 로그인 (실제: POST /api/users/v1/auth/login)
function performLogin() {
  const account = LOGIN_ACCOUNTS[LOGIN_ROLE] || LOGIN_ACCOUNTS.customer;

  const body = JSON.stringify({
    email: account.email,
    password: account.password
  });

  const res = http.post(
    `${BASE_URL}/api/users/v1/auth/login`,
    body,
    { headers: { "Content-Type": "application/json" }, tags: { name: "login" } }
  );

  t_login.add(res.timings.duration);
  const ok = ok2xx(res);

  if (ok) {
    try {
      const data = JSON.parse(res.body);
      globalAccessToken = data.token;
      globalRefreshToken = data.refreshToken;
      // JWT는 1시간 만료이므로 50분 후 갱신하도록 설정
      tokenExpiresAt = Date.now() + (50 * 60 * 1000);

      console.log(`✅ Login successful for ${account.email}`);
    } catch (e) {
      console.error(`❌ Login response parsing failed: ${e.message}`);
    }
  } else {
    console.error(`❌ Login failed: ${res.status} - ${res.body}`);
  }

  check(res, {
    "login 2xx": () => ok,
    "login has token": () => {
      if (!ok) return false;
      try {
        const data = JSON.parse(res.body);
        return data.token && data.refreshToken;
      } catch (_) {
        return false;
      }
    }
  });

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

  t_refresh.add(res.timings.duration);
  const ok = ok2xx(res);

  if (ok) {
    try {
      const data = JSON.parse(res.body);
      globalAccessToken = data.accessToken;
      // JWT는 1시간 만료이므로 50분 후 갱신하도록 설정
      tokenExpiresAt = Date.now() + (50 * 60 * 1000);

      console.log("✅ Token refresh successful");
    } catch (e) {
      console.error(`❌ Refresh response parsing failed: ${e.message}`);
      // Refresh 실패 시 새로 로그인
      return performLogin();
    }
  } else {
    console.error(`❌ Token refresh failed: ${res.status}, performing fresh login...`);
    // Refresh 실패 시 새로 로그인
    return performLogin();
  }

  check(res, {
    "refresh 2xx": () => ok,
    "refresh has accessToken": () => {
      if (!ok) return false;
      try {
        const data = JSON.parse(res.body);
        return data.accessToken;
      } catch (_) {
        return false;
      }
    }
  });

  return res;
}

/**
 * =========================================
 * 실제 Popcorn API 호출 함수들
 * =========================================
 */

// 팝업 목록 조회 (실제: GET /api/stores/v1/popups)
function api_popup_list(page = 1, size = 20) {
  const res = http.get(
    `${BASE_URL}/api/stores/v1/popups?page=${page}&size=${size}&withTotal=true`,
    { headers: headers(), tags: { name: "popup_list" } }
  );

  t_popup_list.add(res.timings.duration);
  const ok = ok2xx(res);
  check(res, {
    "popup_list 2xx": () => ok,
    "popup_list has data": () => {
      if (!ok) return false;
      const data = extractDataFromResponse(res.body);
      return data && data.items && Array.isArray(data.items);
    }
  });

  return res;
}

// 팝업 상세 조회 (실제: GET /api/stores/v1/popups/{popupId})
function api_popup_detail(popupId) {
  const res = http.get(
    `${BASE_URL}/api/stores/v1/popups/${popupId}`,
    { headers: headers(), tags: { name: "popup_detail" } }
  );

  t_popup_detail.add(res.timings.duration);
  const ok = ok2xx(res);
  check(res, {
    "popup_detail 2xx": () => ok,
    "popup_detail has data": () => {
      if (!ok) return false;
      const data = extractDataFromResponse(res.body);
      return data && data.id === popupId;
    }
  });

  return res;
}

// 주문 생성 (실제: POST /api/orders/v1/)
function api_order_create({ popupId, goodsId = null, sessionId = null }) {
  // 주문 아이템 구성 (예시)
  const items = [];

  if (goodsId) {
    items.push({
      orderItemType: "GOODS",
      qty: Math.floor(Math.random() * 3) + 1, // 1-3개
      unitPrice: 15000, // 실제로는 상품 가격을 가져와야 함
      goodsId: goodsId
    });
  }

  if (sessionId) {
    items.push({
      orderItemType: "RESERVATION",
      qty: 1,
      unitPrice: 25000,
      sessionId: sessionId
    });
  }

  // 기본값: 굿즈 주문
  if (items.length === 0) {
    items.push({
      orderItemType: "GOODS",
      qty: 2,
      unitPrice: 12000,
      goodsId: "sample-goods-id"
    });
  }

  const body = JSON.stringify({
    orderType: sessionId ? "RESERVATION" : "GOODS",
    popupId: popupId,
    paymentMethod: "CARD",
    items: items
  });

  const res = http.post(
    `${BASE_URL}/api/orders/v1/`,
    body,
    { headers: headers(), tags: { name: "order_create" } }
  );

  t_order_create.add(res.timings.duration);
  const ok = ok2xx(res);
  check(res, {
    "order_create 2xx": () => ok,
    "order_create has orderId": () => {
      if (!ok) return false;
      const data = extractDataFromResponse(res.body);
      return data && (data.id || data.orderId);
    }
  });

  return res;
}

// 주문 상태 조회 (실제: GET /api/orders/v1/{orderId})
function api_order_status(orderId) {
  const res = http.get(
    `${BASE_URL}/api/orders/v1/${orderId}`,
    { headers: headers(), tags: { name: "order_status" } }
  );

  t_order_status.add(res.timings.duration);
  const ok = ok2xx(res);
  check(res, {
    "order_status 2xx": () => ok,
    "order_status has data": () => {
      if (!ok) return false;
      const data = extractDataFromResponse(res.body);
      return data && data.id;
    }
  });

  return res;
}

// 내 주문 목록 (실제: GET /api/orders/v1/me)
function api_my_orders(page = 0, size = 20) {
  const res = http.get(
    `${BASE_URL}/api/orders/v1/me?page=${page}&size=${size}`,
    { headers: headers(), tags: { name: "my_orders" } }
  );

  t_my_orders.add(res.timings.duration);
  const ok = ok2xx(res);
  check(res, {
    "my_orders 2xx": () => ok,
    "my_orders has content": () => {
      if (!ok) return false;
      const data = extractDataFromResponse(res.body);
      return data && data.content && Array.isArray(data.content);
    }
  });

  return res;
}

// 주문 ID 추출
function extractOrderId(orderRes) {
  const data = extractDataFromResponse(orderRes.body);
  return data?.id || data?.orderId || null;
}

/**
 * =========================================
 * 단계별 시나리오 설정
 * =========================================
 */
function addDurations(a, b) {
  const ma = parseInt(String(a).replace(/[ms]/g, ""), 10);
  const mb = parseInt(String(b).replace(/[ms]/g, ""), 10);

  // 초 단위 처리
  if (String(a).includes('s')) {
    return `${ma + mb}s`;
  }
  // 분 단위 처리
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
    r_fail: ["rate<0.01"],                 // 에러율 < 1%
    http_req_duration: ["p(95)<500"],      // 전체 p95 < 500ms

    // 엔드포인트별 목표
    t_popup_list: ["p(95)<300"],
    t_popup_detail: ["p(95)<400"],
    t_order_create: ["p(95)<1200"],
    t_order_status: ["p(95)<800"],
    t_my_orders: ["p(95)<600"],
  },
};

/**
 * =========================================
 * 단계별 실행 함수
 * =========================================
 */

export function stageSteady(data) {
  // Setup에서 받은 토큰 사용
  if (data && data.accessToken) {
    globalAccessToken = data.accessToken;
    globalRefreshToken = data.refreshToken;
    tokenExpiresAt = data.expiresAt;
  }

  group("stage1_steady", () => {
    runScenario(SCENARIO, "steady");
    sleep(0.2 + Math.random() * 0.8);
  });
}

export function stageRush(data) {
  // Setup에서 받은 토큰 사용
  if (data && data.accessToken) {
    globalAccessToken = data.accessToken;
    globalRefreshToken = data.refreshToken;
    tokenExpiresAt = data.expiresAt;
  }

  group("stage2_rush", () => {
    runScenario(SCENARIO, "rush");
    sleep(Math.random() * 0.5);
  });
}

export function stageSpike(data) {
  // Setup에서 받은 토큰 사용
  if (data && data.accessToken) {
    globalAccessToken = data.accessToken;
    globalRefreshToken = data.refreshToken;
    tokenExpiresAt = data.expiresAt;
  }

  group("stage3_spike", () => {
    runScenario(SCENARIO, "spike");
    sleep(Math.random() * 0.2);
  });
}

/**
 * =========================================
 * 시나리오 구현
 * =========================================
 */

// SCENARIO 1) hot : 오픈 러시(핫키/핫팝업)
function scenarioHot(stage) {
  // 핫팝업에 집중된 트래픽
  api_popup_list(1, 20);                    // 목록 조회
  api_popup_detail(POPUP_HOT_ID);           // 상세 조회 (핫팝업 집중)

  // 단계별 쓰기 비중 조절
  let writeProb = 0.15; // steady 기본
  if (stage === "rush") writeProb = 0.30;
  if (stage === "spike") writeProb = 0.55;

  if (Math.random() < writeProb) {
    // 주문 생성 (핫팝업)
    const orderRes = api_order_create({
      popupId: POPUP_HOT_ID,
      goodsId: "sample-goods-" + Math.floor(Math.random() * 5)
    });

    const orderId = extractOrderId(orderRes);
    if (orderId) {
      // 주문 상태 확인
      api_order_status(orderId);
    }
  }

  // 읽기 추가 (내 주문 목록)
  if (Math.random() < 0.3) {
    api_my_orders(Math.floor(Math.random() * 3), 10);
  }
}

// SCENARIO 2) dist : 정상 운영(여러 팝업 분산)
function scenarioDist(stage) {
  const popupId = pickDistributedPopup();

  // read-heavy 패턴
  api_popup_list(Math.floor(Math.random() * 5) + 1, 20);  // 다양한 페이지
  api_popup_detail(popupId);                               // 분산된 팝업

  // 쓰기 비중은 낮게 유지
  let writeProb = 0.10;
  if (stage === "rush") writeProb = 0.15;
  if (stage === "spike") writeProb = 0.25;

  if (Math.random() < writeProb) {
    const orderRes = api_order_create({
      popupId: popupId,
      goodsId: "goods-" + Math.floor(Math.random() * 10)
    });

    const orderId = extractOrderId(orderRes);
    if (orderId) {
      api_order_status(orderId);
    }
  }

  // 마이페이지 조회도 분산
  if (Math.random() < 0.25) {
    api_my_orders(0, 20);
  }
}

// SCENARIO 3) fault : 장애 내성
function scenarioFault(stage) {
  const popupId = stage === "rush" || stage === "spike" ? POPUP_HOT_ID : pickDistributedPopup();

  // 장애 상황에서도 사용자는 계속 시도
  api_popup_detail(popupId);

  let writeProb = 0.20;
  if (stage === "rush") writeProb = 0.35;
  if (stage === "spike") writeProb = 0.60;

  if (Math.random() < writeProb) {
    const orderRes = api_order_create({
      popupId: popupId,
      goodsId: "fault-test-goods"
    });

    const orderId = extractOrderId(orderRes);
    if (orderId) {
      // 장애 상황에서 상태 조회가 중요
      api_order_status(orderId);
    }
  }

  // 장애 시에도 내 주문을 자주 확인
  if (Math.random() < 0.4) {
    api_my_orders(0, 10);
  }
}

function runScenario(name, stage) {
  if (name === "hot") return scenarioHot(stage);
  if (name === "dist") return scenarioDist(stage);
  if (name === "fault") return scenarioFault(stage);

  // 기본값
  return scenarioHot(stage);
}

// 테스트 시작 전 1회 실행 - 로그인
export function setup() {
  if (AUTO_LOGIN) {
    console.log(`🔐 Auto login enabled with ${LOGIN_ROLE} account...`);
    const loginRes = performLogin();
    if (globalAccessToken) {
      console.log("✅ Initial login successful - will use this token for all VUs");
      console.log(`🎫 Access token length: ${globalAccessToken.length}`);
      console.log(`🔄 Refresh token length: ${globalRefreshToken.length}`);
      return {
        accessToken: globalAccessToken,
        refreshToken: globalRefreshToken,
        expiresAt: tokenExpiresAt
      };
    } else {
      console.error("❌ Initial login failed");
      return null;
    }
  }
  return null;
}

export default function () {}