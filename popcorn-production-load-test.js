import http from "k6/http";
import { check, sleep, group } from "k6";
import { Trend, Rate, Counter } from "k6/metrics";

/**
 * 🚀 Popcorn MSA 프로덕션 부하 테스트 - 최적화 검증
 * 목표: 극한 최적화 설정으로 99.9% 성공률 달성
 */

// === 환경 설정 ===
const BASE_URL = __ENV.BASE_URL || "https://api.goormpopcorn.shop";
const SCENARIO = (__ENV.SCENARIO || "hot").toLowerCase();

// 프로덕션 실제 데이터
const POPUP_HOT_ID = "00000000-0000-0000-0000-000000000101";
const POPUP_IDS = [
  "00000000-0000-0000-0000-000000000101",
  "00000000-0000-0000-0000-000000000102",
  "00000000-0000-0000-0000-000000000103",
  "00000000-0000-0000-0000-000000000104"
];

const TEST_ACCOUNTS = [
    { email: "popcorn1@popcorn.com", password: "test123" },
    { email: "popcorn2@popcorn.com", password: "test123" },
    { email: "popcorn3@popcorn.com", password: "test123" },
    { email: "popcorn4@popcorn.com", password: "test123" },
    { email: "popcorn6@popcorn.com", password: "test123" }
];

// === 부하 레벨 설정 ===
const STEADY_RPS = parseInt(__ENV.STEADY_RPS || "150", 10);
const RUSH_RPS = parseInt(__ENV.RUSH_RPS || "300", 10);
const SPIKE_RPS = parseInt(__ENV.SPIKE_RPS || "500", 10);

const STEADY_DURATION = __ENV.STEADY_DURATION || "5m";
const RUSH_DURATION = __ENV.RUSH_DURATION || "3m";
const SPIKE_DURATION = __ENV.SPIKE_DURATION || "2m";

// === 메트릭 ===
const errorRate = new Rate('error_rate');
const popupListTime = new Trend('popup_list_duration');
const popupDetailTime = new Trend('popup_detail_duration');
const orderCreateTime = new Trend('order_create_duration');
const paymentReqTime = new Trend('payment_request_duration');
const successfulRequests = new Counter('successful_requests');

// 글로벌 토큰 캐시
let globalTokens = new Map();

// === k6 옵션 ===
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
      preAllocatedVUs: 200,
      maxVUs: 3000,
      exec: "stageSteady",
      tags: { stage: "steady", scenario: SCENARIO },
    },
    stage2_rush: {
      executor: "constant-arrival-rate",
      rate: RUSH_RPS,
      timeUnit: "1s",
      duration: RUSH_DURATION,
      preAllocatedVUs: 400,
      maxVUs: 6000,
      exec: "stageRush",
      startTime: STEADY_DURATION,
      tags: { stage: "rush", scenario: SCENARIO },
    },
    stage3_spike: {
      executor: "constant-arrival-rate",
      rate: SPIKE_RPS,
      timeUnit: "1s",
      duration: SPIKE_DURATION,
      preAllocatedVUs: 600,
      maxVUs: 9000,
      exec: "stageSpike",
      startTime: addDurations(STEADY_DURATION, RUSH_DURATION),
      tags: { stage: "spike", scenario: SCENARIO },
    },
  },

  thresholds: {
    'error_rate': ['rate<0.001'],                    // 0.1% 미만 실패율
    'http_req_duration': ['p(95)<800'],              // 95% 요청이 800ms 이내
    'popup_detail_duration': ['p(95)<600'],          // 팝업 상세 95% 600ms 이내
    'order_create_duration': ['p(95)<1200'],         // 주문 생성 95% 1.2초 이내
    'payment_request_duration': ['p(95)<1500'],      // 결제 요청 95% 1.5초 이내
  },
};

// === Setup ===
export function setup() {
    console.log('🚀 Popcorn MSA 프로덕션 부하 테스트 시작!');
    console.log(`🌐 대상: ${BASE_URL}`);
    console.log(`📊 시나리오: ${SCENARIO}`);
    console.log('⚡ 극한 최적화 적용 완료');

    // 프로덕션 토큰 사전 획득
    for (const account of TEST_ACCOUNTS) {
        const loginResponse = http.post(`${BASE_URL}/api/users/v1/auth/login`,
            JSON.stringify({
                email: account.email,
                password: account.password
            }),
            {
                headers: { 'Content-Type': 'application/json' },
                timeout: '10s'
            }
        );

        if (loginResponse.status === 200 && loginResponse.json('token')) {
            const token = loginResponse.json('token');
            globalTokens.set(account.email, token);
            console.log(`✅ 토큰 획득: ${account.email}`);
        } else {
            console.log(`⚠️ 로그인 실패: ${account.email} - ${loginResponse.status}`);
        }
    }

    return { tokens: Object.fromEntries(globalTokens) };
}

// === 헬퍼 함수 ===
function headers(token) {
  const h = { "Content-Type": "application/json" };
  if (token) h["Authorization"] = `Bearer ${token}`;
  return h;
}

function pickDistributedPopup() {
  return POPUP_IDS[Math.floor(Math.random() * POPUP_IDS.length)];
}

function getUserToken() {
    const userIndex = (__VU - 1) % TEST_ACCOUNTS.length;
    const account = TEST_ACCOUNTS[userIndex];
    return globalTokens.get(account.email);
}

// === API 호출 함수 ===
function api_popup_list(token) {
  const response = http.get(`${BASE_URL}/api/stores/v1/popups`, {
    headers: headers(token),
    tags: { name: "popup_list" },
    timeout: '10s'
  });

  popupListTime.add(response.timings.duration);
  const success = check(response, {
    'popup_list 2xx': (r) => r.status >= 200 && r.status < 300,
  });

  if (!success) errorRate.add(1);
  else successfulRequests.add(1);

  return response;
}

function api_popup_detail(popupId, token) {
  const response = http.get(`${BASE_URL}/api/stores/v1/popups/${popupId}`, {
    headers: headers(token),
    tags: { name: "popup_detail" },
    timeout: '10s'
  });

  popupDetailTime.add(response.timings.duration);
  const success = check(response, {
    'popup_detail 2xx': (r) => r.status >= 200 && r.status < 300,
  });

  if (!success) errorRate.add(1);
  else successfulRequests.add(1);

  return response;
}

function api_order_create(popupId, token) {
  const body = JSON.stringify({
    popupId: popupId,
    orderType: "RESERVATION",
    orderItems: [{
      goodsId: "00000000-0000-0000-0000-000000000001",
      quantity: 1,
      price: 10000
    }]
  });

  const response = http.post(`${BASE_URL}/api/orders/v1/orders`, body, {
    headers: headers(token),
    tags: { name: "order_create" },
    timeout: '15s'
  });

  orderCreateTime.add(response.timings.duration);
  const success = check(response, {
    'order_create 2xx': (r) => r.status >= 200 && r.status < 300,
  });

  if (!success) errorRate.add(1);
  else successfulRequests.add(1);

  return response;
}

function api_payment_request(orderId, token) {
  const body = JSON.stringify({
    orderId: orderId,
    amount: 10000,
    customerKey: `customer_${__VU}_${Date.now()}`
  });

  const response = http.post(`${BASE_URL}/api/payments/v1/payments/request`, body, {
    headers: headers(token),
    tags: { name: "payment_request" },
    timeout: '20s'
  });

  paymentReqTime.add(response.timings.duration);
  const success = check(response, {
    'payment_request 2xx': (r) => r.status >= 200 && r.status < 300,
  });

  if (!success) errorRate.add(1);
  else successfulRequests.add(1);

  return response;
}

function extractOrderId(orderRes) {
  try {
    const json = JSON.parse(orderRes.body);
    return json?.data?.orderId || json?.orderId || json?.data?.id || null;
  } catch (_) {
    return null;
  }
}

// === 시나리오 구현 ===

// 시나리오 1: 핫팝업 집중 (오픈 러시)
function scenarioHot(stage) {
  const token = getUserToken();
  if (!token) return;

  // 핫팝업에 트래픽 집중
  api_popup_list(token);
  api_popup_detail(POPUP_HOT_ID, token);

  // 단계별 쓰기 비중 조절
  let writeProb = 0.10;
  if (stage === "rush") writeProb = 0.20;
  if (stage === "spike") writeProb = 0.30;

  if (Math.random() < writeProb) {
    const orderRes = api_order_create(POPUP_HOT_ID, token);
    const orderId = extractOrderId(orderRes);
    if (orderId && orderRes.status >= 200 && orderRes.status < 300) {
      api_payment_request(orderId, token);
    }
  }
}

// 시나리오 2: 분산 팝업 (정상 운영)
function scenarioDist(stage) {
  const token = getUserToken();
  if (!token) return;

  const popupId = pickDistributedPopup();

  api_popup_list(token);
  api_popup_detail(popupId, token);

  let writeProb = 0.05;
  if (stage === "rush") writeProb = 0.10;
  if (stage === "spike") writeProb = 0.15;

  if (Math.random() < writeProb) {
    const orderRes = api_order_create(popupId, token);
    const orderId = extractOrderId(orderRes);
    if (orderId && orderRes.status >= 200 && orderRes.status < 300) {
      api_payment_request(orderId, token);
    }
  }
}

function runScenario(name, stage) {
  if (name === "hot") return scenarioHot(stage);
  if (name === "dist") return scenarioDist(stage);
  return scenarioHot(stage);
}

// === 단계별 실행 함수 ===
export function stageSteady() {
  group("stage1_steady", () => {
    runScenario(SCENARIO, "steady");
    sleep(0.1 + Math.random() * 0.2);
  });
}

export function stageRush() {
  group("stage2_rush", () => {
    runScenario(SCENARIO, "rush");
    sleep(0.05 + Math.random() * 0.15);
  });
}

export function stageSpike() {
  group("stage3_spike", () => {
    runScenario(SCENARIO, "spike");
    sleep(0.02 + Math.random() * 0.08);
  });
}

export function teardown(data) {
    console.log('\n🎯 Popcorn MSA 프로덕션 부하 테스트 결과');
    console.log('=' .repeat(60));
    console.log(`🌐 대상: ${BASE_URL}`);
    console.log(`✅ 성공한 요청: ${successfulRequests.count}회`);
    console.log('📊 극한 최적화 검증:');
    console.log('   • Users: Tomcat 500, HikariCP 80, Redis 50');
    console.log('   • Stores: Tomcat 500, HikariCP 30, Redis 50');
    console.log('   • Orders: Tomcat 500, HikariCP 80');
    console.log('   • Payment: Tomcat 500, HikariCP 80');

    if (data && data.tokens) {
        console.log(`🔑 사용된 토큰: ${Object.keys(data.tokens).length}개`);
    }

    console.log('\n🚀 Popcorn MSA 극한 최적화 검증 완료!');
}

export default function () {}