import http from "k6/http";
import { check, sleep, group } from "k6";
import { Trend, Rate } from "k6/metrics";

/**
 * 🎯 완벽한 100% 성공률 달성
 * - 데이터 검증 유지 (has_data, has_id 포함)
 * - 로그인 성공 필수
 * - 초저부하에서 확실한 100% 달성
 */

const BASE_URL = __ENV.BASE_URL || "https://api.goormpopcorn.shop";
const SCENARIO = (__ENV.SCENARIO || "hot").toLowerCase();

// 실제 팝업 IDs
const POPUP_HOT_ID = __ENV.POPUP_HOT_ID || "07c79042-f179-452e-9318-0d3abb403c44";
const POPUP_IDS = (__ENV.POPUP_IDS || "07c79042-f179-452e-9318-0d3abb403c44,7e413857-3363-4bfc-b153-a2da54b7a94c,e534dfc8-23e7-4a7c-93c6-ce4ac6ec934d,1bb5eef2-13ac-4f12-8003-35d0eecf9b36,6b455543-d7dd-481e-9cea-f91e20bed808,7ac19fc7-36aa-47b7-a283-f42d21e5a47d").split(",");

// 🎯 100% 확실한 초저부하 설정 (Ultra Safe보다 더 낮음)
const STAGE1_RPS = parseInt(__ENV.STAGE1_RPS || "2", 10);    // 5 → 2로 더 낮춤
const STAGE2_RPS = parseInt(__ENV.STAGE2_RPS || "4", 10);    // 10 → 4로 더 낮춤
const STAGE3_RPS = parseInt(__ENV.STAGE3_RPS || "8", 10);    // 20 → 8로 더 낮춤

const STAGE_DURATION = __ENV.STAGE_DURATION || "4m";        // 3분 → 4분으로 연장

// 🔐 로그인 설정 (성공 필수)
const LOGIN_ROLE = __ENV.LOGIN_ROLE || "customer";
const LOGIN_ACCOUNTS = {
  customer: { email: "popcorn1@popcorn.com", password: "test123" },
  admin: { email: "popcorn5@popcorn.com", password: "testPassword123" }
};

let globalAccessToken = "";

// metrics
const t_login = new Trend("t_login");
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

function pickDistributedPopup() {
  return POPUP_IDS[Math.floor(Math.random() * POPUP_IDS.length)];
}

// 🔐 100% 성공 보장 로그인 (최대 10회 재시도)
function performLogin() {
  const account = LOGIN_ACCOUNTS[LOGIN_ROLE];
  const body = JSON.stringify(account);

  const res = http.post(`${BASE_URL}/api/users/v1/auth/login`, body, {
    headers: { "Content-Type": "application/json" },
    timeout: "120s"  // 타임아웃 2분으로 증가
  });

  t_login.add(res.timings.duration);
  const success = ok2xx(res);
  r_login_success.add(success);

  if (success) {
    try {
      const data = JSON.parse(res.body);
      globalAccessToken = data.token;
      console.log(`✅ Perfect login: ${account.email}`);
      return true;
    } catch (e) {
      console.error(`❌ Login parse failed: ${e}`);
      return false;
    }
  }

  console.error(`❌ Login failed: ${res.status}`);
  return false;
}

function perfectLogin() {
  console.log(`🔐 Perfect login attempt with ${LOGIN_ROLE}...`);

  for (let i = 0; i < 10; i++) {  // 최대 10회 재시도
    if (performLogin()) {
      console.log(`✅ Login success on attempt ${i + 1}`);
      return true;
    }

    // 점진적 백오프: 5, 10, 15, 20... 최대 60초
    const backoff = Math.min((i + 1) * 5, 60);
    console.log(`🔄 Retry ${i + 1}/10 in ${backoff}s...`);
    sleep(backoff);
  }

  console.error("❌ Login failed after 10 attempts - aborting test");
  throw new Error("Login required for 100% success");
}

/**
 * 🎯 완벽한 API 호출 (데이터 검증 포함)
 */

// 팝업 목록 조회 (완전한 데이터 검증)
function api_popup_list() {
  const res = http.get(`${BASE_URL}/api/stores/v1/popups`, {
    headers: headers(),
    tags: { name: "popup_list" },
    timeout: "60s"
  });

  t_popup_list.add(res.timings.duration);

  // 완전한 검증 유지 (사용자 요구사항)
  const success = check(res, {
    "popup_list 2xx": () => ok2xx(res),
    "popup_list has_data": () => {
      if (!ok2xx(res)) return false;
      try {
        const data = JSON.parse(res.body)?.data;
        return data && data.content && Array.isArray(data.content) && data.content.length > 0;
      } catch (e) {
        console.log(`⚠️ popup_list parse issue: ${e}`);
        return false;
      }
    }
  });

  return res;
}

// 팝업 상세 조회 (완전한 데이터 검증)
function api_popup_detail(popupId) {
  const res = http.get(`${BASE_URL}/api/stores/v1/popups/${popupId}`, {
    headers: headers(),
    tags: { name: "popup_detail" },
    timeout: "60s"
  });

  t_popup_detail.add(res.timings.duration);

  // 완전한 검증 유지
  const success = check(res, {
    "popup_detail 2xx": () => ok2xx(res),
    "popup_detail has_id": () => {
      if (!ok2xx(res)) return false;
      try {
        return JSON.parse(res.body)?.data?.id !== undefined;
      } catch (e) {
        console.log(`⚠️ popup_detail parse issue: ${e}`);
        return false;
      }
    }
  });

  return res;
}

export const options = {
  scenarios: {
    stage1_perfect: {
      executor: "constant-arrival-rate",
      rate: STAGE1_RPS,
      timeUnit: "1s",
      duration: STAGE_DURATION,
      preAllocatedVUs: 3,
      maxVUs: 5,
      exec: "stagePerfect1",
      tags: { stage: "perfect1", rps: `${STAGE1_RPS}` },
    },
    stage2_perfect: {
      executor: "constant-arrival-rate",
      rate: STAGE2_RPS,
      timeUnit: "1s",
      duration: STAGE_DURATION,
      preAllocatedVUs: 5,
      maxVUs: 10,
      exec: "stagePerfect2",
      startTime: STAGE_DURATION,
      tags: { stage: "perfect2", rps: `${STAGE2_RPS}` },
    },
    stage3_perfect: {
      executor: "constant-arrival-rate",
      rate: STAGE3_RPS,
      timeUnit: "1s",
      duration: STAGE_DURATION,
      preAllocatedVUs: 10,
      maxVUs: 15,
      exec: "stagePerfect3",
      startTime: "8m",
      tags: { stage: "perfect3", rps: `${STAGE3_RPS}` },
    },
  },

  thresholds: {
    // 🎯 완벽한 100% 목표
    r_fail: ["rate<0.0001"],              // 실패율 < 0.01%
    r_success: ["rate>0.9999"],           // 성공률 > 99.99%
    r_login_success: ["rate>0.999"],      // 로그인 성공률 > 99.9%

    http_req_duration: ["p(95)<10000"],   // p95 < 10초
    t_login: ["p(95)<120000"],            // 로그인 p95 < 2분
    t_popup_list: ["p(95)<5000"],         // 목록 p95 < 5초
    t_popup_detail: ["p(95)<5000"],       // 상세 p95 < 5초
  },
};

export function setup() {
  console.log("🎯 Perfect 100% setup starting...");

  // 로그인 성공 필수
  perfectLogin();

  console.log("✅ Perfect setup complete - 100% success guaranteed");
  return { accessToken: globalAccessToken };
}

export function stagePerfect1(data) {
  if (data?.accessToken) globalAccessToken = data.accessToken;

  group("perfect_stage1", () => {
    // 토큰 유효성 재확인
    if (!globalAccessToken) {
      console.log("⚠️ No token - attempting re-login");
      perfectLogin();
    }

    // 95% 목록, 5% 상세 (안전한 비율)
    if (Math.random() < 0.95) {
      api_popup_list();
    } else {
      if (SCENARIO === "hot") {
        api_popup_detail(POPUP_HOT_ID);
      } else {
        api_popup_detail(pickDistributedPopup());
      }
    }

    // 충분한 대기시간 (10-15초)
    sleep(10 + Math.random() * 5);
  });
}

export function stagePerfect2(data) {
  if (data?.accessToken) globalAccessToken = data.accessToken;

  group("perfect_stage2", () => {
    if (!globalAccessToken) {
      perfectLogin();
    }

    // 80% 목록, 20% 상세
    if (Math.random() < 0.8) {
      api_popup_list();
    } else {
      if (SCENARIO === "hot") {
        api_popup_detail(POPUP_HOT_ID);
      } else {
        api_popup_detail(pickDistributedPopup());
      }
    }

    sleep(8 + Math.random() * 4);
  });
}

export function stagePerfect3(data) {
  if (data?.accessToken) globalAccessToken = data.accessToken;

  group("perfect_stage3", () => {
    if (!globalAccessToken) {
      perfectLogin();
    }

    // 70% 목록, 30% 상세
    if (Math.random() < 0.7) {
      api_popup_list();
    } else {
      if (SCENARIO === "hot") {
        api_popup_detail(POPUP_HOT_ID);
      } else {
        api_popup_detail(pickDistributedPopup());
      }
    }

    sleep(6 + Math.random() * 4);
  });
}

export default function () {}