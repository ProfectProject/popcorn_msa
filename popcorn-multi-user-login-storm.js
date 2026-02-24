import http from "k6/http";
import { check, sleep, group } from "k6";
import { Trend, Rate } from "k6/metrics";

/**
 * =========================================
 * 🚨 동시 다중 사용자 로그인 극한 부하 테스트
 * =========================================
 * 5명의 실제 사용자 계정으로 동시 로그인 폭탄
 * - popcorn1~6@popcorn.com (popcorn5 제외)
 * - 과부하 심하게 걸어서 성공률 테스트
 * - 로그인 성공 -> 팝업 조회 -> 주문 생성 -> 재고 예약
 */

const BASE_URL = __ENV.BASE_URL || "https://api.goormpopcorn.shop";

// 🎯 극한 부하 설정 (사용자 요청)
const STORM_RPS = parseInt(__ENV.STORM_RPS || "1000", 10);  // 초당 1000번 로그인
const STORM_DURATION = __ENV.STORM_DURATION || "5m";        // 5분간 지속

// 🔐 실제 사용자 계정들 (사용자 확인됨)
const USER_ACCOUNTS = [
  { email: "popcorn1@popcorn.com", password: "test123" },
  { email: "popcorn2@popcorn.com", password: "test123" },
  { email: "popcorn3@popcorn.com", password: "test123" },
  { email: "popcorn4@popcorn.com", password: "test123" },
  { email: "popcorn6@popcorn.com", password: "test123" }
];

const POPUP_HOT_ID = __ENV.POPUP_HOT_ID || "07c79042-f179-452e-9318-0d3abb403c44";

// ===== metrics =====
const t_login = new Trend("t_login");
const t_popup_list = new Trend("t_popup_list");
const t_popup_detail = new Trend("t_popup_detail");
const t_order_create = new Trend("t_order_create");
const t_stock_reserve = new Trend("t_stock_reserve");

const r_fail = new Rate("r_fail");
const r_login_success = new Rate("r_login_success");

// ===== helpers =====
function randomAccount() {
  return USER_ACCOUNTS[Math.floor(Math.random() * USER_ACCOUNTS.length)];
}

function ok2xx(res) {
  const ok = res.status >= 200 && res.status < 300;
  r_fail.add(!ok);
  return ok;
}

// 🔐 동시 다중 로그인 함수
function performRandomLogin() {
  const account = randomAccount();
  const body = JSON.stringify(account);

  const res = http.post(`${BASE_URL}/api/users/v1/auth/login`, body, {
    headers: { "Content-Type": "application/json" },
    timeout: "30s", // 타임아웃 단축 (빠른 실패)
    tags: { name: "multi_user_login", user: account.email }
  });

  t_login.add(res.timings.duration);
  const success = ok2xx(res);
  r_login_success.add(success);

  if (success) {
    try {
      const data = JSON.parse(res.body);
      console.log(`✅ 동시 로그인 성공: ${account.email}`);
      return { token: data.token, email: account.email };
    } catch (e) {
      console.error(`❌ 로그인 응답 파싱 실패: ${account.email}`);
      return null;
    }
  }

  console.error(`❌ 동시 로그인 실패: ${account.email} - ${res.status}`);
  return null;
}

// API 호출 함수들
function api_popup_list(token) {
  const headers = { "Content-Type": "application/json" };
  if (token) headers["Authorization"] = `Bearer ${token}`;

  const res = http.get(`${BASE_URL}/api/stores/v1/popups`, {
    headers,
    tags: { name: "popup_list" },
    timeout: "30s"
  });

  t_popup_list.add(res.timings.duration);
  check(res, { "popup_list 2xx": () => ok2xx(res) });
  return res;
}

function api_popup_detail(token, popupId) {
  const headers = { "Content-Type": "application/json" };
  if (token) headers["Authorization"] = `Bearer ${token}`;

  const res = http.get(`${BASE_URL}/api/stores/v1/popups/${popupId}`, {
    headers,
    tags: { name: "popup_detail" },
    timeout: "30s"
  });

  t_popup_detail.add(res.timings.duration);
  check(res, { "popup_detail 2xx": () => ok2xx(res) });
  return res;
}

function api_order_create(token, popupId) {
  const headers = { "Content-Type": "application/json" };
  if (token) headers["Authorization"] = `Bearer ${token}`;

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
    headers,
    tags: { name: "order_create" },
    timeout: "30s"
  });

  t_order_create.add(res.timings.duration);
  check(res, { "order_create 2xx": () => ok2xx(res) });
  return res;
}

function api_stock_reserve(token, popupId, orderId) {
  const headers = { "Content-Type": "application/json" };
  if (token) headers["Authorization"] = `Bearer ${token}`;

  const body = JSON.stringify({
    popupId,
    orderId,
    items: [{ goodsId: "sample-goods-id", quantity: 1 }]
  });

  const res = http.post(`${BASE_URL}/api/stores/v1/stocks/reserve`, body, {
    headers,
    tags: { name: "stock_reserve" },
    timeout: "30s"
  });

  t_stock_reserve.add(res.timings.duration);
  check(res, { "stock_reserve 2xx": () => ok2xx(res) });
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

export const options = {
  scenarios: {
    // 🚨 극한 동시 로그인 폭탄
    multi_user_login_storm: {
      executor: "constant-arrival-rate",
      rate: STORM_RPS,
      timeUnit: "1s",
      duration: STORM_DURATION,
      preAllocatedVUs: 2000,    // 충분한 VU 할당
      maxVUs: 15000,            // 최대 15,000 동시 사용자
      exec: "multiUserStorm",
      tags: { scenario: "multi_user_login_storm" },
    },
  },

  thresholds: {
    // 🎯 극한 부하에서의 성공률 목표
    r_fail: ["rate<0.05"],                  // 에러율 < 5% (관대하게)
    r_login_success: ["rate>0.90"],         // 로그인 성공률 > 90%
    http_req_duration: ["p(95)<10000"],     // p95 < 10초 (관대하게)

    // 엔드포인트별
    t_login: ["p(95)<10000"],               // 로그인 p95 < 10초
    t_popup_list: ["p(95)<5000"],
    t_popup_detail: ["p(95)<5000"],
    t_order_create: ["p(95)<5000"],
    t_stock_reserve: ["p(95)<5000"],
  },
};

/**
 * =========================================
 * 극한 동시 로그인 시나리오
 * =========================================
 */
export function multiUserStorm() {
  group("multi_user_login_storm", () => {
    // 1단계: 랜덤 사용자로 로그인
    const loginResult = performRandomLogin();

    if (!loginResult) {
      // 로그인 실패 시 바로 종료
      sleep(0.1);
      return;
    }

    const { token, email } = loginResult;

    // 2단계: 로그인 성공 시 팝업 조회
    api_popup_list(token);
    api_popup_detail(token, POPUP_HOT_ID);

    // 3단계: 20% 확률로 주문 생성
    if (Math.random() < 0.2) {
      const orderRes = api_order_create(token, POPUP_HOT_ID);
      const orderId = extractOrderId(orderRes);

      // 4단계: 주문 성공 시 재고 예약
      if (orderId) {
        api_stock_reserve(token, POPUP_HOT_ID, orderId);
      }
    }

    // 짧은 대기 (극한 부하)
    sleep(0.05 + Math.random() * 0.05);
  });
}

export default function () {}