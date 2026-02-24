import http from "k6/http";
import { check, sleep, group } from "k6";
import { Trend, Rate } from "k6/metrics";

/**
 * =========================================
 * Popcorn MSA 부하 테스트 - 인증 없는 공개 API 버전
 * =========================================
 */

const BASE_URL = __ENV.BASE_URL || "https://api.goormpopcorn.shop";
const POPUP_HOT_ID = __ENV.POPUP_HOT_ID || "ff31f6d6-1234-5678-9abc-123456789abc";
const POPUP_IDS = (__ENV.POPUP_IDS || "ff31f6d6-1234-5678-9abc-123456789abc,0d88920a-1234-5678-9abc-123456789abc").split(",");

const SCENARIO = (__ENV.SCENARIO || "hot").toLowerCase();
const STEADY_RPS = parseInt(__ENV.STEADY_RPS || "10", 10);
const RUSH_RPS = parseInt(__ENV.RUSH_RPS || "20", 10);
const SPIKE_RPS = parseInt(__ENV.SPIKE_RPS || "30", 10);

const STEADY_DURATION = __ENV.STEADY_DURATION || "30s";
const RUSH_DURATION = __ENV.RUSH_DURATION || "30s";
const SPIKE_DURATION = __ENV.SPIKE_DURATION || "30s";

// ===== metrics =====
const t_popup_list = new Trend("t_popup_list");
const t_popup_detail = new Trend("t_popup_detail");
const t_health_check = new Trend("t_health_check");

const r_fail = new Rate("r_fail");

// ===== helpers =====
function headers() {
  return { "Content-Type": "application/json" };
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
 * 공개 API 호출 함수들
 * =========================================
 */

// 헬스 체크 (공개)
function api_health_check() {
  const res = http.get(
    `${BASE_URL}/actuator/health`,
    { headers: headers(), tags: { name: "health_check" } }
  );

  t_health_check.add(res.timings.duration);
  const ok = ok2xx(res);
  check(res, {
    "health_check 2xx": () => ok,
    "health_check UP": () => {
      if (!ok) return false;
      try {
        const data = JSON.parse(res.body);
        return data.status === "UP";
      } catch (_) {
        return false;
      }
    }
  });

  return res;
}

// 팝업 목록 조회 (공개 - 인증 불필요)
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

// 팝업 상세 조회 (공개 - 인증 불필요)
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
      return data && data.id;
    }
  });

  return res;
}

/**
 * =========================================
 * 시나리오 설정
 * =========================================
 */

function addDurations(a, b) {
  const ma = parseInt(String(a).replace(/[ms]/g, ""), 10);
  const mb = parseInt(String(b).replace(/[ms]/g, ""), 10);

  if (String(a).includes('s')) {
    return `${ma + mb}s`;
  }
  return `${ma + mb}m`;
}

export const options = {
  scenarios: {
    stage1_steady: {
      executor: "constant-arrival-rate",
      rate: STEADY_RPS,
      timeUnit: "1s",
      duration: STEADY_DURATION,
      preAllocatedVUs: 50,
      maxVUs: 500,
      exec: "stageSteady",
      tags: { stage: "steady", scenario: SCENARIO },
    },
    stage2_rush: {
      executor: "constant-arrival-rate",
      rate: RUSH_RPS,
      timeUnit: "1s",
      duration: RUSH_DURATION,
      preAllocatedVUs: 100,
      maxVUs: 800,
      exec: "stageRush",
      startTime: STEADY_DURATION,
      tags: { stage: "rush", scenario: SCENARIO },
    },
    stage3_spike: {
      executor: "constant-arrival-rate",
      rate: SPIKE_RPS,
      timeUnit: "1s",
      duration: SPIKE_DURATION,
      preAllocatedVUs: 150,
      maxVUs: 1000,
      exec: "stageSpike",
      startTime: addDurations(STEADY_DURATION, RUSH_DURATION),
      tags: { stage: "spike", scenario: SCENARIO },
    },
  },

  thresholds: {
    r_fail: ["rate<0.05"],                 // 에러율 < 5%
    http_req_duration: ["p(95)<1000"],     // 전체 p95 < 1초

    // 공개 API 목표치
    t_health_check: ["p(95)<500"],
    t_popup_list: ["p(95)<800"],
    t_popup_detail: ["p(95)<1000"],
  },
};

/**
 * =========================================
 * 단계별 실행 함수
 * =========================================
 */

export function stageSteady() {
  group("stage1_steady", () => {
    runScenario(SCENARIO, "steady");
    sleep(0.1 + Math.random() * 0.3);
  });
}

export function stageRush() {
  group("stage2_rush", () => {
    runScenario(SCENARIO, "rush");
    sleep(Math.random() * 0.2);
  });
}

export function stageSpike() {
  group("stage3_spike", () => {
    runScenario(SCENARIO, "spike");
    sleep(Math.random() * 0.1);
  });
}

/**
 * =========================================
 * 시나리오 구현
 * =========================================
 */

function scenarioHot(stage) {
  // 헬스 체크 (간헐적으로)
  if (Math.random() < 0.1) {
    api_health_check();
  }

  // 핫팝업에 집중된 트래픽
  api_popup_list(1, 20);                    // 목록 조회
  api_popup_detail(POPUP_HOT_ID);           // 상세 조회 (핫팝업 집중)

  // 단계별로 추가 조회
  if (stage === "rush") {
    api_popup_list(Math.floor(Math.random() * 3) + 1, 15);
  }
  if (stage === "spike") {
    api_popup_detail(POPUP_HOT_ID); // 중복 조회
  }
}

function scenarioDist(stage) {
  // 헬스 체크
  if (Math.random() < 0.05) {
    api_health_check();
  }

  // 분산된 팝업들
  const popupId = pickDistributedPopup();
  api_popup_list(Math.floor(Math.random() * 5) + 1, 20);  // 다양한 페이지
  api_popup_detail(popupId);                               // 분산된 팝업

  // 추가 조회 패턴
  if (Math.random() < 0.3) {
    api_popup_list(Math.floor(Math.random() * 3) + 1, 10);
  }
}

function scenarioFault(stage) {
  // 장애 상황 시뮬레이션
  const popupId = stage === "rush" || stage === "spike" ? POPUP_HOT_ID : pickDistributedPopup();

  // 헬스 체크 빈도 증가
  if (Math.random() < 0.2) {
    api_health_check();
  }

  // 반복적인 조회 (장애 시 사용자 행동)
  api_popup_detail(popupId);
  api_popup_list(1, 20);

  if (stage === "spike") {
    // 스파이크 시 중복 요청
    api_popup_detail(popupId);
  }
}

function runScenario(name, stage) {
  if (name === "hot") return scenarioHot(stage);
  if (name === "dist") return scenarioDist(stage);
  if (name === "fault") return scenarioFault(stage);

  return scenarioHot(stage);
}

export default function () {}