import http from "k6/http";
import { check, sleep, group } from "k6";
import { Trend, Rate } from "k6/metrics";

/**
 * =========================================
 * Popcorn 100% 성공률 달성 시나리오
 * =========================================
 * 초저부하로 시작해서 100% 성공률 확인 후 점진적 증가
 */

const BASE_URL = __ENV.BASE_URL || "https://api.goormpopcorn.shop";
const SCENARIO = (__ENV.SCENARIO || "dist").toLowerCase();

// 실제 OPEN 상태 팝업 IDs
const POPUP_HOT_ID = __ENV.POPUP_HOT_ID || "07c79042-f179-452e-9318-0d3abb403c44";
const POPUP_IDS = (__ENV.POPUP_IDS || "07c79042-f179-452e-9318-0d3abb403c44,7e413857-3363-4bfc-b153-a2da54b7a94c,e534dfc8-23e7-4a7c-93c6-ce4ac6ec934d,1bb5eef2-13ac-4f12-8003-35d0eecf9b36,6b455543-d7dd-481e-9cea-f91e20bed808,7ac19fc7-36aa-47b7-a283-f42d21e5a47d").split(",");

// 100% 성공률 달성을 위한 안전한 부하 설정
const SAFE_RPS = parseInt(__ENV.SAFE_RPS || "3", 10);
const MED_RPS = parseInt(__ENV.MED_RPS || "8", 10);
const HIGH_RPS = parseInt(__ENV.HIGH_RPS || "15", 10);

const STAGE_DURATION = __ENV.STAGE_DURATION || "2m";

// Auto login
const AUTH_TOKEN = __ENV.AUTH_TOKEN || "";
const AUTO_LOGIN = __ENV.AUTO_LOGIN === "true" || !AUTH_TOKEN;
const LOGIN_ROLE = __ENV.LOGIN_ROLE || "customer"; // customer로 가벼운 권한 사용

const LOGIN_ACCOUNTS = {
  customer: { email: "popcorn1@popcorn.com", password: "test123" },
  admin: { email: "popcorn5@popcorn.com", password: "testPassword123" }
};

// 토큰 관리
let globalAccessToken = AUTH_TOKEN;
let globalRefreshToken = "";

// ===== metrics =====
const t_health_check = new Trend("t_health_check");
const t_popup_list = new Trend("t_popup_list");
const t_popup_detail = new Trend("t_popup_detail");

const r_fail = new Rate("r_fail");
const r_success = new Rate("r_success");

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
  r_success.add(ok);
  return ok;
}

function pickDistributedPopup() {
  return POPUP_IDS[Math.floor(Math.random() * POPUP_IDS.length)];
}

// 로그인 함수
function performLogin() {
  const account = LOGIN_ACCOUNTS[LOGIN_ROLE] || LOGIN_ACCOUNTS.customer;
  const body = JSON.stringify({
    email: account.email,
    password: account.password
  });

  console.log(`🔐 Attempting login with ${account.email}...`);

  const res = http.post(`${BASE_URL}/api/users/v1/auth/login`, body, {
    headers: { "Content-Type": "application/json" },
    tags: { name: "login" },
    timeout: "30s" // 로그인 타임아웃 증가
  });

  const ok = ok2xx(res);
  if (ok) {
    try {
      const data = JSON.parse(res.body);
      globalAccessToken = data.token;
      globalRefreshToken = data.refreshToken;
      console.log(`✅ Login SUCCESS for ${account.email}`);
      return true;
    } catch (e) {
      console.error(`❌ Login response parse failed: ${e.message}`);
      return false;
    }
  } else {
    console.error(`❌ Login FAILED: ${res.status} - ${res.body?.substring(0, 200)}`);
    return false;
  }
}

/**
 * =========================================
 * 안전한 API 호출 (타임아웃 & 재시도)
 * =========================================
 */

// 헬스 체크 (최우선)
function api_health_check() {
  const res = http.get(`${BASE_URL}/actuator/health`, {
    headers: headers(),
    tags: { name: "health_check" },
    timeout: "10s"
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

// 팝업 목록 조회 (읽기 전용)
function api_popup_list() {
  const res = http.get(`${BASE_URL}/api/stores/v1/popups`, {
    headers: headers(),
    tags: { name: "popup_list" },
    timeout: "15s"
  });

  t_popup_list.add(res.timings.duration);
  const success = check(res, {
    "popup_list 2xx": () => ok2xx(res),
    "popup_list has data": () => {
      if (res.status < 200 || res.status >= 300) return false;
      try {
        const json = JSON.parse(res.body);
        const data = json?.data;
        return data && data.content && Array.isArray(data.content) && data.content.length > 0;
      } catch (_) {
        return false;
      }
    }
  });

  return res;
}

// 팝업 상세 조회 (읽기 전용)
function api_popup_detail(popupId) {
  const res = http.get(`${BASE_URL}/api/stores/v1/popups/${popupId}`, {
    headers: headers(),
    tags: { name: "popup_detail" },
    timeout: "15s"
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
 * 100% 성공률 목표 설정
 * =========================================
 */

export const options = {
  scenarios: {
    // 1단계: 안전 확인 (3 RPS)
    stage1_safe: {
      executor: "constant-arrival-rate",
      rate: SAFE_RPS,
      timeUnit: "1s",
      duration: STAGE_DURATION,
      preAllocatedVUs: 5,
      maxVUs: 10,
      exec: "stageSafe",
      tags: { stage: "safe", scenario: SCENARIO },
    },
    // 2단계: 중간 부하 (8 RPS)
    stage2_medium: {
      executor: "constant-arrival-rate",
      rate: MED_RPS,
      timeUnit: "1s",
      duration: STAGE_DURATION,
      preAllocatedVUs: 10,
      maxVUs: 20,
      exec: "stageMedium",
      startTime: STAGE_DURATION,
      tags: { stage: "medium", scenario: SCENARIO },
    },
    // 3단계: 높은 부하 (15 RPS)
    stage3_high: {
      executor: "constant-arrival-rate",
      rate: HIGH_RPS,
      timeUnit: "1s",
      duration: STAGE_DURATION,
      preAllocatedVUs: 20,
      maxVUs: 30,
      exec: "stageHigh",
      startTime: "4m",
      tags: { stage: "high", scenario: SCENARIO },
    },
  },

  thresholds: {
    // 100% 성공률 목표!
    r_fail: ["rate<0.01"],                    // 실패율 < 1%
    r_success: ["rate>0.99"],                 // 성공률 > 99%
    http_req_duration: ["p(95)<2000"],        // 넉넉한 응답시간

    // 엔드포인트별
    t_health_check: ["p(95)<500"],
    t_popup_list: ["p(95)<1500"],
    t_popup_detail: ["p(95)<2000"],
  },
};

/**
 * =========================================
 * Setup: 안정적인 로그인
 * =========================================
 */
export function setup() {
  if (AUTO_LOGIN) {
    console.log(`🔐 Starting safe login with ${LOGIN_ROLE} account...`);

    // 3회 재시도
    for (let i = 0; i < 3; i++) {
      const success = performLogin();
      if (success && globalAccessToken) {
        console.log(`✅ Setup complete - token ready (attempt ${i + 1})`);
        return {
          accessToken: globalAccessToken,
          refreshToken: globalRefreshToken
        };
      }

      if (i < 2) {
        console.log(`🔄 Login retry ${i + 1}/3 in 5 seconds...`);
        sleep(5);
      }
    }

    console.error("❌ Setup failed after 3 login attempts");
  }
  return null;
}

/**
 * =========================================
 * 단계별 실행: 100% 성공률 우선
 * =========================================
 */

export function stageSafe(data) {
  if (data && data.accessToken) {
    globalAccessToken = data.accessToken;
    globalRefreshToken = data.refreshToken;
  }

  group("stage1_safe", () => {
    // 가장 안전한 시나리오: 헬스체크 + 목록 위주
    api_health_check();

    if (Math.random() < 0.7) {
      api_popup_list();
    }

    if (Math.random() < 0.3) {
      const popupId = pickDistributedPopup();
      api_popup_detail(popupId);
    }

    sleep(1.0 + Math.random() * 2.0); // 충분한 간격
  });
}

export function stageMedium(data) {
  if (data && data.accessToken) {
    globalAccessToken = data.accessToken;
    globalRefreshToken = data.refreshToken;
  }

  group("stage2_medium", () => {
    if (SCENARIO === "dist") {
      // 분산 시나리오
      const popupId = pickDistributedPopup();
      api_popup_list();
      api_popup_detail(popupId);
    } else {
      // 핫팝업 시나리오
      api_popup_list();
      api_popup_detail(POPUP_HOT_ID);
    }

    sleep(0.5 + Math.random() * 1.0);
  });
}

export function stageHigh(data) {
  if (data && data.accessToken) {
    globalAccessToken = data.accessToken;
    globalRefreshToken = data.refreshToken;
  }

  group("stage3_high", () => {
    // 읽기 전용으로 안전하게
    api_popup_list();

    if (SCENARIO === "hot") {
      api_popup_detail(POPUP_HOT_ID);
    } else {
      const popupId = pickDistributedPopup();
      api_popup_detail(popupId);
    }

    sleep(0.3 + Math.random() * 0.5);
  });
}

export default function () {}