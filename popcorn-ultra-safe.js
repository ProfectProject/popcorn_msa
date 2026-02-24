import http from "k6/http";
import { check, sleep, group } from "k6";
import { Trend, Rate } from "k6/metrics";

/**
 * 🚨 Ultra Safe 부하 테스트 - 5 RPS부터 시작
 * 목표: 100% 성공률을 유지하면서 점진적 증가
 */

const BASE_URL = __ENV.BASE_URL || "https://api.goormpopcorn.shop";

// Ultra Safe 설정: 5 RPS부터 시작
const STAGE1_RPS = parseInt(__ENV.STAGE1_RPS || "5", 10);
const STAGE2_RPS = parseInt(__ENV.STAGE2_RPS || "10", 10);
const STAGE3_RPS = parseInt(__ENV.STAGE3_RPS || "20", 10);
const STAGE_DURATION = __ENV.STAGE_DURATION || "3m";

// 실제 팝업 IDs
const POPUP_IDS = [
  "07c79042-f179-452e-9318-0d3abb403c44", // 야식 배달 기술 전시
  "7e413857-3363-4bfc-b153-a2da54b7a94c", // 봄 시즌 한정 팝업
  "e534dfc8-23e7-4a7c-93c6-ce4ac6ec934d", // 플래그십 오픈 기념 이벤트
  "1bb5eef2-13ac-4f12-8003-35d0eecf9b36", // 럭셔리 패션 컬렉션
  "6b455543-d7dd-481e-9cea-f91e20bed808", // 게임과 야식의 만남
  "7ac19fc7-36aa-47b7-a283-f42d21e5a47d"  // 라면 아트 전시회
];

// 로그인
const AUTO_LOGIN = __ENV.AUTO_LOGIN === "true";
const LOGIN_ROLE = __ENV.LOGIN_ROLE || "customer";
const LOGIN_ACCOUNTS = {
  customer: { email: "popcorn1@popcorn.com", password: "test123" },
  admin: { email: "popcorn5@popcorn.com", password: "testPassword123" }
};

let globalAccessToken = "";

// metrics
const t_health = new Trend("t_health");
const t_popup_list = new Trend("t_popup_list");
const t_popup_detail = new Trend("t_popup_detail");
const r_fail = new Rate("r_fail");
const r_success = new Rate("r_success");

// helpers
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

function performLogin() {
  const account = LOGIN_ACCOUNTS[LOGIN_ROLE];
  const body = JSON.stringify(account);

  const res = http.post(`${BASE_URL}/api/users/v1/auth/login`, body, {
    headers: { "Content-Type": "application/json" },
    timeout: "30s"
  });

  if (ok2xx(res)) {
    try {
      const data = JSON.parse(res.body);
      globalAccessToken = data.token;
      console.log(`✅ Login success: ${account.email}`);
      return true;
    } catch (e) {
      console.error(`❌ Login parse failed: ${e}`);
      return false;
    }
  }

  console.error(`❌ Login failed: ${res.status}`);
  return false;
}

// Ultra Safe API 호출
function api_health() {
  const res = http.get(`${BASE_URL}/actuator/health`, {
    headers: headers(),
    tags: { name: "health" },
    timeout: "30s"
  });

  t_health.add(res.timings.duration);
  check(res, {
    "health 200": () => ok2xx(res),
    "health UP": () => {
      try { return JSON.parse(res.body).status === "UP"; }
      catch (_) { return false; }
    }
  });
  return res;
}

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

function api_popup_detail() {
  const popupId = POPUP_IDS[Math.floor(Math.random() * POPUP_IDS.length)];
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

export const options = {
  scenarios: {
    stage1_ultra_safe: {
      executor: "constant-arrival-rate",
      rate: STAGE1_RPS,
      timeUnit: "1s",
      duration: STAGE_DURATION,
      preAllocatedVUs: 5,
      maxVUs: 10,
      exec: "stageUltraSafe",
      tags: { stage: "ultra_safe", rps: `${STAGE1_RPS}` },
    },
    stage2_safe: {
      executor: "constant-arrival-rate",
      rate: STAGE2_RPS,
      timeUnit: "1s",
      duration: STAGE_DURATION,
      preAllocatedVUs: 10,
      maxVUs: 20,
      exec: "stageSafe",
      startTime: STAGE_DURATION,
      tags: { stage: "safe", rps: `${STAGE2_RPS}` },
    },
    stage3_moderate: {
      executor: "constant-arrival-rate",
      rate: STAGE3_RPS,
      timeUnit: "1s",
      duration: STAGE_DURATION,
      preAllocatedVUs: 20,
      maxVUs: 30,
      exec: "stageModerate",
      startTime: "6m",
      tags: { stage: "moderate", rps: `${STAGE3_RPS}` },
    },
  },

  thresholds: {
    // 100% 성공률 목표
    r_fail: ["rate<0.001"],              // 실패율 < 0.1%
    r_success: ["rate>0.999"],           // 성공률 > 99.9%
    http_req_duration: ["p(95)<5000"],   // p95 < 5초

    t_health: ["p(95)<1000"],            // 헬스체크 p95 < 1초
    t_popup_list: ["p(95)<3000"],        // 목록 p95 < 3초
    t_popup_detail: ["p(95)<3000"],      // 상세 p95 < 3초
  },
};

export function setup() {
  if (AUTO_LOGIN) {
    console.log(`🔐 Ultra safe login with ${LOGIN_ROLE}...`);
    for (let i = 0; i < 3; i++) {
      if (performLogin()) {
        return { accessToken: globalAccessToken };
      }
      sleep(3);
    }
    console.error("❌ Setup failed after 3 attempts");
  }
  return null;
}

export function stageUltraSafe(data) {
  if (data?.accessToken) globalAccessToken = data.accessToken;

  group("ultra_safe", () => {
    // 90% 헬스체크, 10% 목록
    if (Math.random() < 0.9) {
      api_health();
    } else {
      api_popup_list();
    }
    sleep(3 + Math.random() * 2); // 3-5초 대기
  });
}

export function stageSafe(data) {
  if (data?.accessToken) globalAccessToken = data.accessToken;

  group("safe", () => {
    // 50% 헬스, 40% 목록, 10% 상세
    const x = Math.random();
    if (x < 0.5) {
      api_health();
    } else if (x < 0.9) {
      api_popup_list();
    } else {
      api_popup_detail();
    }
    sleep(2 + Math.random() * 2); // 2-4초 대기
  });
}

export function stageModerate(data) {
  if (data?.accessToken) globalAccessToken = data.accessToken;

  group("moderate", () => {
    // 30% 헬스, 50% 목록, 20% 상세
    const x = Math.random();
    if (x < 0.3) {
      api_health();
    } else if (x < 0.8) {
      api_popup_list();
    } else {
      api_popup_detail();
    }
    sleep(1 + Math.random() * 2); // 1-3초 대기
  });
}

export default function () {}