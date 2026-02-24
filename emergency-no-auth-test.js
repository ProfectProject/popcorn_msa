import http from "k6/http";
import { check, sleep, group } from "k6";
import { Trend, Rate, Counter } from "k6/metrics";

/**
 * 🚨 Emergency No-Auth 테스트 - User 서비스 다운 대응
 * 목표: 인증 없이도 작동하는 API들의 성능 검증
 */

// === 환경 설정 ===
const BASE_URL = __ENV.BASE_URL || "https://api.goormpopcorn.shop";
const SCENARIO = (__ENV.SCENARIO || "public").toLowerCase();

// 공개 API 테스트용 데이터
const POPUP_IDS = [
  "00000000-0000-0000-0000-000000000101",
  "00000000-0000-0000-0000-000000000102",
  "00000000-0000-0000-0000-000000000103",
  "00000000-0000-0000-0000-000000000104"
];

// === 부하 레벨 설정 ===
const STEADY_RPS = parseInt(__ENV.STEADY_RPS || "50", 10);
const RUSH_RPS = parseInt(__ENV.RUSH_RPS || "100", 10);
const SPIKE_RPS = parseInt(__ENV.SPIKE_RPS || "200", 10);

const STEADY_DURATION = __ENV.STEADY_DURATION || "2m";
const RUSH_DURATION = __ENV.RUSH_DURATION || "1m";
const SPIKE_DURATION = __ENV.SPIKE_DURATION || "30s";

// === 메트릭 ===
const errorRate = new Rate('error_rate');
const publicApiTime = new Trend('public_api_duration');
const successfulRequests = new Counter('successful_requests');

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
      preAllocatedVUs: 100,
      maxVUs: 2000,
      exec: "stageSteady",
      tags: { stage: "steady", scenario: SCENARIO },
    },
    stage2_rush: {
      executor: "constant-arrival-rate",
      rate: RUSH_RPS,
      timeUnit: "1s",
      duration: RUSH_DURATION,
      preAllocatedVUs: 200,
      maxVUs: 4000,
      exec: "stageRush",
      startTime: STEADY_DURATION,
      tags: { stage: "rush", scenario: SCENARIO },
    },
    stage3_spike: {
      executor: "constant-arrival-rate",
      rate: SPIKE_RPS,
      timeUnit: "1s",
      duration: SPIKE_DURATION,
      preAllocatedVUs: 400,
      maxVUs: 6000,
      exec: "stageSpike",
      startTime: addDurations(STEADY_DURATION, RUSH_DURATION),
      tags: { stage: "spike", scenario: SCENARIO },
    },
  },

  thresholds: {
    'error_rate': ['rate<0.05'],                     // 5% 미만 실패율
    'http_req_duration': ['p(95)<1000'],             // 95% 요청이 1초 이내
    'public_api_duration': ['p(95)<800'],            // 공개 API 95% 800ms 이내
  },
};

// === Setup ===
export function setup() {
    console.log('🚨 Emergency No-Auth 테스트 시작!');
    console.log(`🌐 대상: ${BASE_URL}`);
    console.log(`📊 시나리오: ${SCENARIO}`);
    console.log('⚡ User 서비스 다운으로 인한 Emergency 모드');
    return {};
}

// === 헬퍼 함수 ===
function headers() {
  return { "Content-Type": "application/json" };
}

function pickDistributedPopup() {
  return POPUP_IDS[Math.floor(Math.random() * POPUP_IDS.length)];
}

// === API 호출 함수 ===
function api_public_popup_list() {
  const response = http.get(`${BASE_URL}/api/stores/v1/popups`, {
    headers: headers(),
    tags: { name: "public_popup_list" },
    timeout: '10s'
  });

  publicApiTime.add(response.timings.duration);
  const success = check(response, {
    'public_popup_list 2xx': (r) => r.status >= 200 && r.status < 300,
  });

  if (!success) errorRate.add(1);
  else successfulRequests.add(1);

  return response;
}

function api_public_popup_detail(popupId) {
  const response = http.get(`${BASE_URL}/api/stores/v1/popups/${popupId}`, {
    headers: headers(),
    tags: { name: "public_popup_detail" },
    timeout: '10s'
  });

  publicApiTime.add(response.timings.duration);
  const success = check(response, {
    'public_popup_detail 2xx': (r) => r.status >= 200 && r.status < 300,
  });

  if (!success) errorRate.add(1);
  else successfulRequests.add(1);

  return response;
}

function api_health_check() {
  const response = http.get(`${BASE_URL}/health`, {
    headers: headers(),
    tags: { name: "health_check" },
    timeout: '5s'
  });

  publicApiTime.add(response.timings.duration);
  const success = check(response, {
    'health_check 2xx': (r) => r.status >= 200 && r.status < 300,
  });

  if (!success) errorRate.add(1);
  else successfulRequests.add(1);

  return response;
}

// === 시나리오 구현 ===
function scenarioPublic(stage) {
  // 공개 API만 테스트
  const choice = Math.random();

  if (choice < 0.6) {
    api_public_popup_list();
  } else if (choice < 0.9) {
    const popupId = pickDistributedPopup();
    api_public_popup_detail(popupId);
  } else {
    api_health_check();
  }
}

function runScenario(name, stage) {
  return scenarioPublic(stage);
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
    console.log('\n🚨 Emergency No-Auth 테스트 결과');
    console.log('=' .repeat(60));
    console.log(`🌐 대상: ${BASE_URL}`);
    console.log(`✅ 성공한 요청: ${successfulRequests.count}회`);
    console.log('📊 Emergency 최적화 검증:');
    console.log('   • Users: HikariCP 150 (서비스 다운)');
    console.log('   • Stores: HikariCP 200, Tomcat 500');
    console.log('   • 공개 API 성능 테스트 완료');

    console.log('\n🚀 Emergency 모드 테스트 완료!');
}

export default function () {}