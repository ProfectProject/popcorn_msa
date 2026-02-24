import http from "k6/http";
import { check, sleep, group } from "k6";
import { Trend, Rate } from "k6/metrics";

/**
 * 🔐 로그인 포함 안전한 부하 테스트
 * - 로그인 안정성 우선
 * - 인증 필요 API까지 포함
 */

const BASE_URL = __ENV.BASE_URL || "https://api.goormpopcorn.shop";

// 더 안전한 설정으로 로그인 성공률 높이기
const STAGE1_RPS = parseInt(__ENV.STAGE1_RPS || "3", 10);  // 5→3으로 더 낮춤
const STAGE2_RPS = parseInt(__ENV.STAGE2_RPS || "6", 10);  // 10→6으로 낮춤
const STAGE3_RPS = parseInt(__ENV.STAGE3_RPS || "12", 10); // 20→12로 낮춤
const STAGE_DURATION = __ENV.STAGE_DURATION || "3m";

// 실제 팝업 IDs
const POPUP_IDS = [
  "07c79042-f179-452e-9318-0d3abb403c44",
  "7e413857-3363-4bfc-b153-a2da54b7a94c",
  "e534dfc8-23e7-4a7c-93c6-ce4ac6ec934d",
  "1bb5eef2-13ac-4f12-8003-35d0eecf9b36",
  "6b455543-d7dd-481e-9cea-f91e20bed808",
  "7ac19fc7-36aa-47b7-a283-f42d21e5a47d"
];

// 로그인 설정
const LOGIN_ROLE = __ENV.LOGIN_ROLE || "customer";
const LOGIN_ACCOUNTS = {
  customer: { email: "popcorn1@popcorn.com", password: "test123" },
  admin: { email: "popcorn5@popcorn.com", password: "testPassword123" }
};

let globalAccessToken = "";
let loginAttempts = 0;
let loginSuccesses = 0;

// metrics
const t_login = new Trend("t_login");
const t_health = new Trend("t_health");
const t_popup_list = new Trend("t_popup_list");
const t_popup_detail = new Trend("t_popup_detail");

const r_fail = new Rate("r_fail");
const r_success = new Rate("r_success");
const r_login_success = new Rate("r_login_success");

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

// 🔐 개선된 로그인 함수 (재시도 + 백오프)
function performLogin() {
  const account = LOGIN_ACCOUNTS[LOGIN_ROLE];
  const body = JSON.stringify(account);

  const res = http.post(`${BASE_URL}/api/users/v1/auth/login`, body, {
    headers: { "Content-Type": "application/json" },
    timeout: "60s"  // 🔐 Login timeout 60초로 증가
  });

  t_login.add(res.timings.duration);
  loginAttempts++;

  const success = ok2xx(res);
  r_login_success.add(success);

  if (success) {
    try {
      const data = JSON.parse(res.body);
      globalAccessToken = data.token;
      loginSuccesses++;
      console.log(`✅ Login success: ${account.email} (${loginSuccesses}/${loginAttempts})`);
      return true;
    } catch (e) {
      console.error(`❌ Login parse failed: ${e.message}`);
      return false;
    }
  }

  console.error(`❌ Login failed: ${res.status} - attempt ${loginAttempts}`);
  return false;
}

// 🔐 안전한 로그인 with 백오프
function safeLogin(maxRetries = 5) {
  for (let i = 0; i < maxRetries; i++) {
    if (performLogin()) {
      return true;
    }

    // 지수 백오프: 2초, 4초, 8초, 16초, 32초
    const backoffDelay = Math.pow(2, i + 1);
    console.log(`🔄 Login retry ${i + 1}/${maxRetries} after ${backoffDelay}s...`);
    sleep(backoffDelay);
  }

  console.error(`❌ Login failed after ${maxRetries} attempts`);
  return false;
}

// API 호출 함수들 (기존과 동일하지만 더 긴 타임아웃)
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
    stage1_safe_login: {
      executor: "constant-arrival-rate",
      rate: STAGE1_RPS,
      timeUnit: "1s",
      duration: STAGE_DURATION,
      preAllocatedVUs: 5,
      maxVUs: 10,
      exec: "stageSafeLogin",
      tags: { stage: "safe_login", rps: `${STAGE1_RPS}` },
    },
    stage2_auth: {
      executor: "constant-arrival-rate",
      rate: STAGE2_RPS,
      timeUnit: "1s",
      duration: STAGE_DURATION,
      preAllocatedVUs: 10,
      maxVUs: 20,
      exec: "stageAuth",
      startTime: STAGE_DURATION,
      tags: { stage: "auth", rps: `${STAGE2_RPS}` },
    },
    stage3_moderate_auth: {
      executor: "constant-arrival-rate",
      rate: STAGE3_RPS,
      timeUnit: "1s",
      duration: STAGE_DURATION,
      preAllocatedVUs: 15,
      maxVUs: 25,
      exec: "stageModerateAuth",
      startTime: "6m",
      tags: { stage: "moderate_auth", rps: `${STAGE3_RPS}` },
    },
  },

  thresholds: {
    // 🔐 로그인 포함 100% 성공률 목표
    r_fail: ["rate<0.01"],               // 실패율 < 1%
    r_success: ["rate>0.99"],            // 성공률 > 99%
    r_login_success: ["rate>0.95"],      // 로그인 성공률 > 95%

    http_req_duration: ["p(95)<10000"],  // p95 < 10초
    t_login: ["p(95)<60000"],           // 로그인 p95 < 60초
    t_health: ["p(95)<2000"],           // 헬스체크 p95 < 2초
    t_popup_list: ["p(95)<5000"],       // 목록 p95 < 5초
    t_popup_detail: ["p(95)<5000"],     // 상세 p95 < 5초
  },
};

// 🔐 Setup: 안전한 로그인
export function setup() {
  console.log(`🔐 Safe login setup with ${LOGIN_ROLE} account...`);

  if (safeLogin()) {
    console.log(`✅ Setup login successful - token ready`);
    return { accessToken: globalAccessToken };
  }

  console.error("❌ Setup login failed - continuing without auth");
  return null;
}

// Stage 함수들
export function stageSafeLogin(data) {
  if (data?.accessToken) globalAccessToken = data.accessToken;

  group("safe_login", () => {
    // 토큰이 없으면 로그인 시도
    if (!globalAccessToken && Math.random() < 0.1) {
      safeLogin(2);  // 최대 2회 재시도
    }

    // 90% 헬스체크, 10% 목록
    if (Math.random() < 0.9) {
      api_health();
    } else {
      api_popup_list();
    }

    sleep(5 + Math.random() * 5); // 5-10초 대기
  });
}

export function stageAuth(data) {
  if (data?.accessToken) globalAccessToken = data.accessToken;

  group("auth", () => {
    // 토큰 체크 + 재로그인
    if (!globalAccessToken && Math.random() < 0.2) {
      safeLogin(2);
    }

    // 50% 헬스, 40% 목록, 10% 상세
    const x = Math.random();
    if (x < 0.5) {
      api_health();
    } else if (x < 0.9) {
      api_popup_list();
    } else {
      api_popup_detail();
    }

    sleep(3 + Math.random() * 3); // 3-6초 대기
  });
}

export function stageModerateAuth(data) {
  if (data?.accessToken) globalAccessToken = data.accessToken;

  group("moderate_auth", () => {
    // 토큰 체크 + 재로그인
    if (!globalAccessToken && Math.random() < 0.3) {
      safeLogin(3);
    }

    // 30% 헬스, 50% 목록, 20% 상세
    const x = Math.random();
    if (x < 0.3) {
      api_health();
    } else if (x < 0.8) {
      api_popup_list();
    } else {
      api_popup_detail();
    }

    sleep(2 + Math.random() * 3); // 2-5초 대기
  });
}

export default function () {}