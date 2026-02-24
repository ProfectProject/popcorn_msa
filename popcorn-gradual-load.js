import http from "k6/http";
import { check, sleep, group } from "k6";
import { Trend, Rate } from "k6/metrics";

/**
 * =========================================
 * Popcorn 점진적 부하 테스트 - 공개 API 우선
 * =========================================
 */

const BASE_URL = __ENV.BASE_URL || "https://api.goormpopcorn.shop";

// 점진적 부하 설정
const LOW_RPS = parseInt(__ENV.LOW_RPS || "1", 10);
const MED_RPS = parseInt(__ENV.MED_RPS || "3", 10);
const HIGH_RPS = parseInt(__ENV.HIGH_RPS || "5", 10);

const STAGE_DURATION = __ENV.STAGE_DURATION || "2m";

// ===== metrics =====
const t_health_check = new Trend("t_health_check");
const t_popup_list = new Trend("t_popup_list");
const t_popup_detail = new Trend("t_popup_detail");

const r_fail = new Rate("r_fail");
const r_success = new Rate("r_success");

// ===== helpers =====
function headers() {
  return { "Content-Type": "application/json" };
}

function ok2xx(res) {
  const ok = res.status >= 200 && res.status < 300;
  r_fail.add(!ok);
  r_success.add(ok);
  return ok;
}

/**
 * =========================================
 * 공개 API 호출 (인증 불필요)
 * =========================================
 */

// 헬스 체크
function api_health_check() {
  const res = http.get(`${BASE_URL}/actuator/health`, {
    headers: headers(),
    tags: { name: "health_check" }
  });

  t_health_check.add(res.timings.duration);
  const success = check(res, {
    "health_check 200": () => ok2xx(res),
    "health_check UP": () => {
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

// 팝업 목록 조회 (공개)
function api_popup_list() {
  const res = http.get(`${BASE_URL}/api/stores/v1/popups`, {
    headers: headers(),
    tags: { name: "popup_list" }
  });

  t_popup_list.add(res.timings.duration);
  const success = check(res, {
    "popup_list 2xx": () => ok2xx(res),
    "popup_list has data": () => {
      if (res.status < 200 || res.status >= 300) return false;
      try {
        const json = JSON.parse(res.body);
        const data = json?.data;
        return data && data.items && Array.isArray(data.items);
      } catch (_) {
        return false;
      }
    }
  });

  return res;
}

// 팝업 상세 조회 (공개)
function api_popup_detail() {
  // 실제 DB에 존재하는 OPEN 상태 팝업 ID들
  const sampleIds = [
    "07c79042-f179-452e-9318-0d3abb403c44", // 야식 배달 기술 전시
    "7e413857-3363-4bfc-b153-a2da54b7a94c", // 봄 시즌 한정 팝업
    "e534dfc8-23e7-4a7c-93c6-ce4ac6ec934d", // 플래그십 오픈 기념 이벤트
    "1bb5eef2-13ac-4f12-8003-35d0eecf9b36", // 럭셔리 패션 컬렉션
    "6b455543-d7dd-481e-9cea-f91e20bed808", // 게임과 야식의 만남
    "7ac19fc7-36aa-47b7-a283-f42d21e5a47d"  // 라면 아트 전시회
  ];

  const popupId = sampleIds[Math.floor(Math.random() * sampleIds.length)];

  const res = http.get(`${BASE_URL}/api/stores/v1/popups/${popupId}`, {
    headers: headers(),
    tags: { name: "popup_detail" }
  });

  t_popup_detail.add(res.timings.duration);
  const success = check(res, {
    "popup_detail 2xx": () => ok2xx(res),
    "popup_detail has id": () => {
      if (res.status < 200 || res.status >= 300) return false;
      try {
        const json = JSON.parse(res.body);
        return json?.data?.id !== undefined;
      } catch (_) {
        return false;
      }
    }
  });

  return res;
}

/**
 * =========================================
 * 점진적 부하 시나리오 설정
 * =========================================
 */

export const options = {
  scenarios: {
    // 1단계: 초저부하 (주로 헬스체크)
    low_load: {
      executor: "constant-arrival-rate",
      rate: LOW_RPS,
      timeUnit: "1s",
      duration: STAGE_DURATION,
      preAllocatedVUs: 5,
      maxVUs: 20,
      exec: "lowLoadTest",
      tags: { stage: "low", load: `${LOW_RPS}` },
    },

    // 2단계: 중간부하 (헬스체크 + 목록)
    med_load: {
      executor: "constant-arrival-rate",
      rate: MED_RPS,
      timeUnit: "1s",
      duration: STAGE_DURATION,
      preAllocatedVUs: 10,
      maxVUs: 50,
      exec: "medLoadTest",
      startTime: STAGE_DURATION,
      tags: { stage: "medium", load: `${MED_RPS}` },
    },

    // 3단계: 높은부하 (전체 API)
    high_load: {
      executor: "constant-arrival-rate",
      rate: HIGH_RPS,
      timeUnit: "1s",
      duration: STAGE_DURATION,
      preAllocatedVUs: 15,
      maxVUs: 100,
      exec: "highLoadTest",
      startTime: "2m",
      tags: { stage: "high", load: `${HIGH_RPS}` },
    },
  },

  thresholds: {
    // 더 관대한 목표
    r_fail: ["rate<0.20"],                 // 실패율 < 20%
    r_success: ["rate>0.80"],              // 성공률 > 80%
    http_req_duration: ["p(95)<1000"],     // p95 < 1초

    // 엔드포인트별
    t_health_check: ["p(95)<300"],
    t_popup_list: ["p(95)<800"],
    t_popup_detail: ["p(95)<1000"],
  },
};

/**
 * =========================================
 * 단계별 실행 함수
 * =========================================
 */

// 1단계: 초저부하 - 주로 헬스체크
export function lowLoadTest() {
  group("low_load", () => {
    api_health_check();

    // 10%만 목록 조회
    if (Math.random() < 0.1) {
      api_popup_list();
    }

    sleep(0.5 + Math.random());
  });
}

// 2단계: 중간부하 - 헬스체크 + 목록
export function medLoadTest() {
  group("med_load", () => {
    // 50% 헬스체크, 50% 목록
    if (Math.random() < 0.5) {
      api_health_check();
    } else {
      api_popup_list();
    }

    sleep(0.3 + Math.random() * 0.5);
  });
}

// 3단계: 높은부하 - 전체 API
export function highLoadTest() {
  group("high_load", () => {
    const x = Math.random();

    if (x < 0.3) {
      api_health_check();
    } else if (x < 0.7) {
      api_popup_list();
    } else {
      api_popup_detail();
    }

    sleep(0.1 + Math.random() * 0.3);
  });
}

export default function () {}