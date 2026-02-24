import http from "k6/http";
import { check, sleep, group } from "k6";
import { Trend, Rate } from "k6/metrics";

/**
 * =========================================
 * ENV
 * =========================================
 * BASE_URL        : API Gateway/Ingress base
 * AUTH_TOKEN      : Bearer token (optional)
 * POPUP_HOT_ID    : 핫팝업 popupId 1개
 * POPUP_IDS       : 분산 팝업 ids (콤마)
 *
 * SCENARIO        : hot | dist | fault
 *   - hot   : 핫팝업 집중(오픈 러시용)
 *   - dist  : 여러 팝업 분산(정상 운영용)
 *   - fault : 장애 내성(테스트 중 장애 주입하며 관측)
 *
 * STEADY_RPS      : 200~300 (default 250)
 * RUSH_RPS        : 500~1000 (default 800)
 * SPIKE_RPS       : 1500~2000 (default 1800)
 *
 * STEADY_DURATION : 10m (default 10m)
 * RUSH_DURATION   : 5m~10m (default 10m)
 * SPIKE_DURATION  : 2m~3m (default 3m)
 */

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
const AUTH_TOKEN = __ENV.AUTH_TOKEN || "";
const LOGIN_URL = __ENV.LOGIN_URL || `${BASE_URL}/api/users/v1/auth/login`;
const LOGIN_CREDENTIALS = (__ENV.LOGIN_CREDENTIALS || "")
  .split(",")
  .map((v) => v.trim())
  .filter(Boolean);
const LOGIN_MAX_RETRIES = parseInt(__ENV.LOGIN_MAX_RETRIES || "2", 10);
const LOGIN_RETRY_SLEEP_SEC = parseFloat(__ENV.LOGIN_RETRY_SLEEP_SEC || "1");
const LOGIN_REQUIRED = (__ENV.LOGIN_REQUIRED || "false").toLowerCase() === "true";
const LOGIN_DEBUG = (__ENV.LOGIN_DEBUG || "false").toLowerCase() === "true";
const SCENARIO = (__ENV.SCENARIO || "hot").toLowerCase();

const POPUP_HOT_ID = __ENV.POPUP_HOT_ID || "07c79042-f179-452e-9318-0d3abb403c44";
const POPUP_IDS = (__ENV.POPUP_IDS || "07c79042-f179-452e-9318-0d3abb403c44,7e413857-3363-4bfc-b153-a2da54b7a94c,e534dfc8-23e7-4a7c-93c6-ce4ac6ec934d").split(",");
const HTTP_TIMEOUT = __ENV.HTTP_TIMEOUT || "30s";
const MAX_RETRIES = parseInt(__ENV.MAX_RETRIES || "2", 10);
const RETRY_SLEEP_SEC = parseFloat(__ENV.RETRY_SLEEP_SEC || "0.2");
const NO_CONNECTION_REUSE = (__ENV.NO_CONNECTION_REUSE || "false").toLowerCase() === "true";
const ENABLE_POPUP_LIST = (__ENV.ENABLE_POPUP_LIST || "false").toLowerCase() === "true";
const POPUP_LIST_RATIO = Math.max(0, Math.min(1, parseFloat(__ENV.POPUP_LIST_RATIO || "0.01")));
const POPUP_LIST_TIMEOUT = __ENV.POPUP_LIST_TIMEOUT || "3s";
const POPUP_LIST_MAX_RETRIES = parseInt(__ENV.POPUP_LIST_MAX_RETRIES || "0", 10);
const POPUP_LIST_BACKOFF_MS = parseInt(__ENV.POPUP_LIST_BACKOFF_MS || "60000", 10);
const POPUP_LIST_API = __ENV.POPUP_LIST_API || "/api/popups/v1/popups";
const POPUP_DETAIL_API_TEMPLATE = __ENV.POPUP_DETAIL_API_TEMPLATE || "/api/popups/v1/popups/{popupId}";

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
const t_stock_reserve = new Trend("t_stock_reserve");
const t_payment_req = new Trend("t_payment_req");

const r_fail = new Rate("r_fail");
let popupListDisabledUntil = 0;

// ===== helpers =====
export function setup() {
  if (AUTH_TOKEN) return { token: AUTH_TOKEN };
  if (LOGIN_CREDENTIALS.length === 0) return { token: "" };

  for (const cred of LOGIN_CREDENTIALS) {
    const idx = cred.indexOf(":");
    if (idx < 1 || idx >= cred.length - 1) continue;
    const email = cred.slice(0, idx).trim();
    const password = cred.slice(idx + 1).trim();

    const payloads = [
      { email, password },
      { username: email, password },
      { loginId: email, password },
      { userEmail: email, userPassword: password },
      { email, passwd: password },
    ];

    for (let attempt = 0; attempt <= LOGIN_MAX_RETRIES; attempt++) {
      for (const payload of payloads) {
        const res = http.post(
          LOGIN_URL,
          JSON.stringify(payload),
          {
            headers: { "Content-Type": "application/json" },
            timeout: HTTP_TIMEOUT,
            responseType: "text",
            tags: { name: "login" },
          }
        );

        if (LOGIN_DEBUG) {
          console.log(`[login] status=${res.status} payloadKeys=${Object.keys(payload).join(",")} body=${(res.body || "").slice(0, 300)}`);
        }

        if (res.status < 200 || res.status >= 300) continue;
        const token = extractTokenFromLoginResponse(res);
        if (token) return { token };
      }
      if (attempt < LOGIN_MAX_RETRIES) sleep(LOGIN_RETRY_SLEEP_SEC * (attempt + 1));
    }
  }
  if (LOGIN_REQUIRED) {
    throw new Error("Automatic login failed. Check LOGIN_URL and LOGIN_CREDENTIALS.");
  }
  console.warn("Automatic login failed; running without token. Set LOGIN_REQUIRED=true to fail-fast.");
  return { token: "" };
}

function pickFirstHeaderValue(headerValue) {
  if (!headerValue) return null;
  if (Array.isArray(headerValue)) return headerValue[0] || null;
  return headerValue;
}

function extractTokenFromLoginResponse(res) {
  try {
    const parsed = JSON.parse(res.body || "{}");
    const tokenFromBody =
      parsed?.token ||
      parsed?.accessToken ||
      parsed?.jwt ||
      parsed?.data?.token ||
      parsed?.data?.accessToken ||
      parsed?.data?.jwt ||
      parsed?.result?.token ||
      parsed?.result?.accessToken ||
      null;
    if (tokenFromBody) return String(tokenFromBody);
  } catch (_) {
    // ignore body parsing error
  }

  const authHeader = pickFirstHeaderValue(res.headers?.Authorization || res.headers?.authorization);
  if (authHeader) {
    const bearer = String(authHeader).match(/Bearer\s+(.+)/i);
    if (bearer?.[1]) return bearer[1].trim();
  }

  const setCookie = pickFirstHeaderValue(res.headers?.["Set-Cookie"] || res.headers?.["set-cookie"]);
  if (setCookie) {
    const m = String(setCookie).match(/(?:access_token|accessToken|token)=([^;]+)/i);
    if (m?.[1]) return m[1];
  }
  return null;
}

function headers(setupData) {
  const h = { "Content-Type": "application/json" };
  const token = setupData?.token || AUTH_TOKEN;
  if (token) h["Authorization"] = `Bearer ${token}`;
  return h;
}
function reqParams(name, setupData) {
  return { headers: headers(setupData), tags: { name }, timeout: HTTP_TIMEOUT };
}
function requestWithRetry(method, url, body, params, maxRetries = MAX_RETRIES) {
  let res;
  for (let attempt = 0; attempt <= maxRetries; attempt++) {
    if (method === "GET") res = http.get(url, params);
    else res = http.post(url, body, params);
    if (res && !res.error) return res;
    if (attempt < maxRetries) sleep(RETRY_SLEEP_SEC * (attempt + 1));
  }
  return res;
}
function ok2xx(res) {
  const ok = res.status >= 200 && res.status < 300;
  r_fail.add(!ok);
  return ok;
}
function pickDistributedPopup() {
  return POPUP_IDS[Math.floor(Math.random() * POPUP_IDS.length)];
}

/**
 * =========================================
 * API 호출 (교체 포인트: 너희 엔드포인트/바디로 수정)
 * =========================================
 * 아래는 예시 경로야.
 */

function api_popup_list(setupData) {
  if (Date.now() < popupListDisabledUntil) return null;
  const res = requestWithRetry(
    "GET",
    `${BASE_URL}${POPUP_LIST_API}`,
    null,
    { ...reqParams("popup_list", setupData), timeout: POPUP_LIST_TIMEOUT },
    POPUP_LIST_MAX_RETRIES
  );
  if (res?.error || res.status >= 500) {
    popupListDisabledUntil = Date.now() + POPUP_LIST_BACKOFF_MS;
  }
  if (!res) return null;
  t_popup_list.add(res.timings.duration);
  check(res, { "popup_list 2xx": () => ok2xx(res) });
  return res;
}

function api_popup_detail(popupId, setupData) {
  const detailPath = POPUP_DETAIL_API_TEMPLATE.replace("{popupId}", encodeURIComponent(popupId));
  const res = requestWithRetry("GET", `${BASE_URL}${detailPath}`, null, reqParams("popup_detail", setupData));
  t_popup_detail.add(res.timings.duration);
  check(res, { "popup_detail 2xx": () => ok2xx(res) });
  return res;
}

function api_order_create({ popupId }, setupData) {
  const body = JSON.stringify({
    popupId,
    // TODO: 실제 주문 생성 payload로 교체
    lines: [{ goodsId: "G1", quantity: 1 }],
    orderType: "RESERVATION",
  });

  const res = requestWithRetry("POST", `${BASE_URL}/api/orders/v1/orders`, body, {
    ...reqParams("order_create", setupData),
    responseType: "text",
  });
  t_order_create.add(res.timings.duration);
  check(res, { "order_create 2xx": () => ok2xx(res) });
  return res;
}

function api_stock_reserve({ popupId, orderId }, setupData) {
  const body = JSON.stringify({
    popupId,
    orderId,
    // TODO: 실제 재고 예약/차감 payload로 교체
    items: [{ goodsId: "G1", quantity: 1 }],
  });

  const res = requestWithRetry("POST", `${BASE_URL}/api/stores/v1/stocks/reserve`, body, reqParams("stock_reserve", setupData));
  t_stock_reserve.add(res.timings.duration);
  check(res, { "stock_reserve 2xx": () => ok2xx(res) });
  return res;
}

function api_payment_request({ orderId }, setupData) {
  const body = JSON.stringify({ orderId });

  const res = requestWithRetry("POST", `${BASE_URL}/api/payments/v1/payments/request`, body, reqParams("payment_request", setupData));
  t_payment_req.add(res.timings.duration);
  check(res, { "payment_request 2xx": () => ok2xx(res) });
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

/**
 * =========================================
 * 단계(부하 레벨) = 3단계 순차 실행
 * - 1단계 steady  (10m, 200~300 RPS)
 * - 2단계 rush    (5~10m, 500~1000 RPS)
 * - 3단계 spike   (2~3m, 1500~2000 RPS)
 * =========================================
 * startTime으로 "완전히" 순차 실행됨
 */
export const options = {
  discardResponseBodies: true,
  noConnectionReuse: NO_CONNECTION_REUSE,
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
    // 단계별 목표
    "http_req_duration{stage:steady}": ["p(95)<500"],
    "http_req_duration{stage:rush}": ["p(95)<1500"],
    "http_req_duration{stage:spike}": ["p(95)<4000"],
    "r_fail{stage:steady}": ["rate<0.01"],
    "r_fail{stage:rush}": ["rate<0.01"],
    "r_fail{stage:spike}": ["rate<0.01"],

    // 엔드포인트별(더 세밀하게)
    "http_req_duration{name:popup_detail,stage:steady}": ["p(95)<400"],
    "http_req_duration{name:popup_detail,stage:rush}": ["p(95)<1200"],
    "http_req_duration{name:popup_detail,stage:spike}": ["p(95)<3000"],
    "http_req_duration{name:popup_list,stage:steady}": ["p(95)<600"],
    "http_req_duration{name:popup_list,stage:rush}": ["p(95)<1800"],
    "http_req_duration{name:popup_list,stage:spike}": ["p(95)<4000"],
    "http_req_failed{name:popup_detail,stage:steady}": ["rate<0.01"],
    "http_req_failed{name:popup_detail,stage:rush}": ["rate<0.02"],
    "http_req_failed{name:popup_detail,stage:spike}": ["rate<0.05"],

    // 쓰기 경로 참고
    t_order_create: ["p(95)<1200"],
    t_payment_req: ["p(95)<1500"],
  },
};

/**
 * k6는 startTime에 "문자열 duration"을 넣을 수 있는데,
 * stage3 startTime은 (steady + rush) 합이 필요함.
 * 간단히 minutes 단위("10m")만 받는 형태로 구현.
 * (너희 문서도 분 단위라 이걸로 충분)
 */
function addDurations(a, b) {
  // "10m" 형태만 지원
  const ma = parseInt(String(a).replace("m", ""), 10);
  const mb = parseInt(String(b).replace("m", ""), 10);
  return `${ma + mb}m`;
}

/**
 * =========================================
 * 단계별 실행 함수
 * =========================================
 * stageSteady / stageRush / stageSpike
 *
 * - stage는 "부하 레벨"이고
 * - SCENARIO는 "트래픽 모양"임
 */

// 1단계: 정상 운영(분산 시나리오가 가장 자연스러움)
// 하지만 SCENARIO에 따라 트래픽 모양을 바꿀 수 있게 해놨음.
export function stageSteady(setupData) {
  group("stage1_steady", () => {
    runScenario(SCENARIO, "steady", setupData);
    sleep(0.2 + Math.random() * 0.8);
  });
}

// 2단계: 오픈 러시(핫팝업 집중이 핵심)
// SCENARIO=hot 권장, dist/fault도 선택 가능
export function stageRush(setupData) {
  group("stage2_rush", () => {
    runScenario(SCENARIO, "rush", setupData);
    sleep(Math.random() * 0.5);
  });
}

// 3단계: 스파이크(핵심 경로 집중)
export function stageSpike(setupData) {
  group("stage3_spike", () => {
    runScenario(SCENARIO, "spike", setupData);
    sleep(Math.random() * 0.2);
  });
}

/**
 * =========================================
 * 시나리오 정의 (문서 3개 반영)
 * =========================================
 */

// SCENARIO 1) hot : 오픈 러시(핫키/핫팝업)
function scenarioHot(stage, setupData) {
  // 공통: 핫팝업에 트래픽 집중
  if (ENABLE_POPUP_LIST && Math.random() < POPUP_LIST_RATIO) {
    api_popup_list(setupData); // 목록 조회(샘플링)
  }
  api_popup_detail(POPUP_HOT_ID, setupData);   // 상세 조회

  // 단계별로 쓰기 비중 조절
  let writeProb = 0.15; // steady 기본
  if (stage === "rush") writeProb = 0.30;
  if (stage === "spike") writeProb = 0.55;

  if (Math.random() < writeProb) {
    const orderRes = api_order_create({ popupId: POPUP_HOT_ID }, setupData);
    const orderId = extractOrderId(orderRes);

    // 재고(스토어) 병목 확인 포인트
    if (orderId) api_stock_reserve({ popupId: POPUP_HOT_ID, orderId }, setupData);

    // 결제 요청
    if (orderId) api_payment_request({ orderId }, setupData);
  }
}

// SCENARIO 2) dist : 정상 운영(여러 팝업 분산)
function scenarioDist(stage, setupData) {
  const popupId = pickDistributedPopup();

  // read-heavy
  if (ENABLE_POPUP_LIST && Math.random() < POPUP_LIST_RATIO) {
    api_popup_list(setupData); // 목록 조회(샘플링)
  }
  api_popup_detail(popupId, setupData);

  // 쓰기 비중은 낮게 유지(steady 운영 느낌)
  let writeProb = 0.10;
  if (stage === "rush") writeProb = 0.15;   // rush에서도 분산 운영 가정
  if (stage === "spike") writeProb = 0.25;

  if (Math.random() < writeProb) {
    const orderRes = api_order_create({ popupId }, setupData);
    const orderId = extractOrderId(orderRes);
    if (orderId) api_payment_request({ orderId }, setupData);
  }
}

// SCENARIO 3) fault : 장애 내성(지연/실패 주입)
// k6는 장애를 직접 "만드는" 도구가 아니라 "트래픽 유지 + 관측" 도구.
// 그래서 여기서는 핵심 경로를 계속 때리면서,
// 테스트 중에 실제로 PG 지연/Pod 재시작/Kafka 리밸런싱을 주입하면 됨.
function scenarioFault(stage, setupData) {
  const popupId = stage === "rush" || stage === "spike" ? POPUP_HOT_ID : pickDistributedPopup();

  // 장애 상황에서도 사용자들은 계속 조회/주문을 시도한다는 가정
  api_popup_detail(popupId, setupData);

  // 핵심 트랜잭션 유지(조금 높은 비율)
  let writeProb = 0.20;
  if (stage === "rush") writeProb = 0.35;
  if (stage === "spike") writeProb = 0.60;

  if (Math.random() < writeProb) {
    const orderRes = api_order_create({ popupId }, setupData);
    const orderId = extractOrderId(orderRes);
    if (orderId) api_payment_request({ orderId }, setupData); // PG 지연 주입 시 여기서 duration 증가 관측
  }
}

function runScenario(name, stage, setupData) {
  if (name === "hot") return scenarioHot(stage, setupData);
  if (name === "dist") return scenarioDist(stage, setupData);
  if (name === "fault") return scenarioFault(stage, setupData);

  // 기본값
  return scenarioHot(stage, setupData);
}

export default function () {}
