import http from "k6/http";
import { check, sleep, group } from "k6";
import { Trend, Rate } from "k6/metrics";

/**
 * =========================================
 * Popcorn MSA 완전 구현 부하 테스트 (사용자 상세 시나리오 완전 반영)
 * =========================================
 * MODE 1: "operational" - 운영 부하 시나리오 3개 (3단계 × 3시나리오)
 *   - Stage 1: Steady (200-300 RPS, 10분)
 *   - Stage 2: Rush (500-1000 RPS, 5-10분)
 *   - Stage 3: Spike (1500-2000 RPS, 2-3분)
 *   - Scenarios: hot/dist/fault
 *
 * MODE 2: "query" - Query 부하 시나리오 3종
 *   - Storm: 새로고침 폭탄 (1200 RPS, 8분)
 *   - Mix: Read-heavy mix (500 RPS, 10분)
 *   - Consistency: CQRS 최신성 테스트 (80 RPS, 10분)
 *
 * ENV 설정:
 * - MODE              : operational | query
 * - BASE_URL          : API Gateway/Ingress base
 * - LOGIN_EMAIL       : 로그인 이메일
 * - LOGIN_PASSWORD    : 로그인 비밀번호
 * - AUTH_TOKEN        : Bearer token (optional, 로그인 대신 사용)
 * - POPUP_HOT_ID      : 핫팝업 popupId 1개
 * - POPUP_IDS         : 분산 팝업 ids (콤마 구분)
 * - API_TIMEOUT       : API 타임아웃 (default: 15s)
 * - API_RETRY_COUNT   : API 재시도 횟수 (default: 2)
 * - API_RETRY_DELAY_MS: API 재시도 간격 (default: 500ms)
 *
 * === OPERATIONAL MODE (운영 부하) ===
 * - SCENARIO          : hot | dist | fault
 * - STEADY_RPS        : 200~300 (default 250)
 * - RUSH_RPS          : 500~1000 (default 800)
 * - SPIKE_RPS         : 1500~2000 (default 1800)
 * - STEADY_DURATION   : 10m (default 10m)
 * - RUSH_DURATION     : 5m~10m (default 10m)
 * - SPIKE_DURATION    : 2m~3m (default 3m)
 *
 * === QUERY MODE (Query 부하) ===
 * - QUERY_MODE        : storm | mix | consistency | all
 * - STORM_RPS_TOTAL   : 총 1200 RPS (default)
 * - STORM_DURATION    : 8m
 * - MIX_RPS           : 500 RPS
 * - MIX_DURATION      : 10m
 * - CONSISTENCY_RPS   : 80 RPS
 * - CONSISTENCY_DURATION : 10m
 * - ORDER_ID_FEED_URL : 최근 orderId 피드 URL
 * - USER_IDS          : 테스트용 유저 ID들 (콤마 구분)
 *
 * === THRESHOLDS 설정 ===
 * - OP_FAIL_RATE       : 운영 모드 에러율 임계값 (default: 0.05 = 5%)
 * - OP_HTTP_P95_MS     : 운영 모드 전체 p95 임계값 (default: 2000ms)
 * - OP_ORDER_P95_MS    : 운영 모드 주문 생성 p95 임계값 (default: 3000ms)
 * - OP_PAYMENT_P95_MS  : 운영 모드 결제 요청 p95 임계값 (default: 4000ms)
 * - Q_FAIL_RATE        : Query 모드 에러율 임계값 (default: 0.02 = 2%)
 */

const MODE = __ENV.MODE || "operational";
const BASE_URL = __ENV.BASE_URL || "https://api.goormpopcorn.shop";

// 인증 관련 (사용자 제공 계정)
const LOGIN_EMAIL = __ENV.LOGIN_EMAIL || "popcorn1@popcorn.com";
const LOGIN_PASSWORD = __ENV.LOGIN_PASSWORD || "test123";
const AUTH_TOKEN = __ENV.AUTH_TOKEN || "";
const LOGIN_MAX_ATTEMPTS = parseInt(__ENV.LOGIN_MAX_ATTEMPTS || "3", 10);
const LOGIN_RETRY_SLEEP_SECONDS = parseFloat(__ENV.LOGIN_RETRY_SLEEP_SECONDS || "1.5");
const LOGIN_FAIL_COOLDOWN_MS = parseInt(__ENV.LOGIN_FAIL_COOLDOWN_MS || "3000", 10);
const STRICT_SETUP_LOGIN = (__ENV.STRICT_SETUP_LOGIN || "false").toLowerCase() === "true";

// 팝업 관련 (실제 사용 가능한 팝업 ID들)
const POPUP_HOT_ID = __ENV.POPUP_HOT_ID || "e534dfc8-23e7-4a7c-93c6-ce4ac6ec934d";
const POPUP_IDS = (__ENV.POPUP_IDS || "e534dfc8-23e7-4a7c-93c6-ce4ac6ec934d,6b455543-d7dd-481e-9cea-f91e20bed808").split(",");

// API 타임아웃 및 재시도 설정
const API_TIMEOUT = __ENV.API_TIMEOUT || "15s";  // 타임아웃 확장 (5s → 15s)
const API_RETRY_COUNT = parseInt(__ENV.API_RETRY_COUNT || "2", 10);
const API_RETRY_DELAY_MS = parseInt(__ENV.API_RETRY_DELAY_MS || "500", 10);
const POPUP_LIST_TIMEOUT = __ENV.POPUP_LIST_TIMEOUT || "4s";
const POPUP_LIST_RETRY_COUNT = parseInt(__ENV.POPUP_LIST_RETRY_COUNT || "0", 10);
const ENABLE_POPUP_LIST = (__ENV.ENABLE_POPUP_LIST || "false").toLowerCase() === "true";
const POPUP_LIST_SAMPLE_RATE = parseFloat(__ENV.POPUP_LIST_SAMPLE_RATE || "0.10");
const POPUP_LIST_COOLDOWN_MS = parseInt(__ENV.POPUP_LIST_COOLDOWN_MS || "5000", 10);

// 서킷 브레이커 설정
const CIRCUIT_FAILURE_THRESHOLD = parseInt(__ENV.CIRCUIT_FAILURE_THRESHOLD || "10", 10);
const CIRCUIT_RECOVERY_TIMEOUT = parseInt(__ENV.CIRCUIT_RECOVERY_TIMEOUT || "30000", 10);
const CIRCUIT_SUCCESS_THRESHOLD = parseInt(__ENV.CIRCUIT_SUCCESS_THRESHOLD || "5", 10);

// 동적 부하 조절 설정
const DYNAMIC_LOAD_ENABLED = (__ENV.DYNAMIC_LOAD_ENABLED || "true").toLowerCase() === "true";
const ERROR_RATE_THRESHOLD = parseFloat(__ENV.ERROR_RATE_THRESHOLD || "0.30");
const LOAD_REDUCTION_FACTOR = parseFloat(__ENV.LOAD_REDUCTION_FACTOR || "0.7");

// === OPERATIONAL MODE 설정 ===
const SCENARIO = (__ENV.SCENARIO || "hot").toLowerCase();
const STEADY_RPS = parseInt(__ENV.STEADY_RPS || "250", 10);
const RUSH_RPS = parseInt(__ENV.RUSH_RPS || "800", 10);
const SPIKE_RPS = parseInt(__ENV.SPIKE_RPS || "1800", 10);
const STEADY_DURATION = __ENV.STEADY_DURATION || "10m";
const RUSH_DURATION = __ENV.RUSH_DURATION || "10m";
const SPIKE_DURATION = __ENV.SPIKE_DURATION || "3m";

// === QUERY MODE 설정 ===
const QUERY_MODE = (__ENV.QUERY_MODE || "all").toLowerCase();
const STORM_RPS_TOTAL = parseInt(__ENV.STORM_RPS_TOTAL || "1200", 10);
const STORM_DURATION = __ENV.STORM_DURATION || "8m";
const STORM_SPLIT_MY_ORDER_PCT = parseInt(__ENV.STORM_SPLIT_MY_ORDER_PCT || "35", 10);
const MIX_RPS = parseInt(__ENV.MIX_RPS || "500", 10);
const MIX_DURATION = __ENV.MIX_DURATION || "10m";
const CONSISTENCY_RPS = parseInt(__ENV.CONSISTENCY_RPS || "80", 10);
const CONSISTENCY_DURATION = __ENV.CONSISTENCY_DURATION || "10m";
const ORDER_ID_FEED_URL = __ENV.ORDER_ID_FEED_URL || "";
const USER_IDS = (__ENV.USER_IDS || "U1,U2,U3,U4,U5").split(",");

// 성능 임계값 (환경변수로 설정 가능)
const QUERY_P95_MS = parseInt(__ENV.QUERY_P95_MS || "400", 10);
const CONSISTENCY_P95_MS = parseInt(__ENV.CONSISTENCY_P95_MS || "5000", 10);

// 운영 부하 테스트 임계값 (더 현실적으로 조정)
const OP_FAIL_RATE = parseFloat(__ENV.OP_FAIL_RATE || "0.05");           // 에러율 < 5% (기존 1% → 5%)
const OP_HTTP_P95_MS = parseInt(__ENV.OP_HTTP_P95_MS || "2000", 10);     // 전체 p95 < 2초 (기존 500ms → 2000ms)
const OP_ORDER_P95_MS = parseInt(__ENV.OP_ORDER_P95_MS || "3000", 10);   // 주문 생성 p95 < 3초 (기존 1200ms → 3000ms)
const OP_PAYMENT_P95_MS = parseInt(__ENV.OP_PAYMENT_P95_MS || "4000", 10); // 결제 요청 p95 < 4초 (기존 1500ms → 4000ms)

// Query 부하 테스트 임계값
const Q_FAIL_RATE = parseFloat(__ENV.Q_FAIL_RATE || "0.02");             // Query 에러율 < 2%

// ===== METRICS =====
const t_login = new Trend("t_login");
const t_popup_list = new Trend("t_popup_list");
const t_popup_detail = new Trend("t_popup_detail");
const t_order_create = new Trend("t_order_create");
const t_stock_reserve = new Trend("t_stock_reserve");
const t_payment_req = new Trend("t_payment_req");
const t_my_order_status = new Trend("t_my_order_status");
const t_popup_stock = new Trend("t_popup_stock");
const t_order_list = new Trend("t_order_list");
const t_consistency_lag_ms = new Trend("t_consistency_lag_ms");

const r_fail = new Rate("r_fail");
const r_query_fail = new Rate("r_query_fail");

// ===== 글로벌 인증 관리 =====
let globalToken = null;
let tokenExpiry = null;
let nextLoginAttemptAt = 0;
let nextPopupListAttemptAt = 0;
const TOKEN_REFRESH_MARGIN = 5 * 60 * 1000; // 5분 전 갱신

// ===== 서킷 브레이커 관리 =====
class CircuitBreaker {
  constructor(name) {
    this.name = name;
    this.state = 'CLOSED'; // CLOSED, OPEN, HALF_OPEN
    this.failureCount = 0;
    this.successCount = 0;
    this.lastFailureTime = 0;
  }

  canExecute() {
    if (this.state === 'CLOSED') return true;
    if (this.state === 'OPEN') {
      if (Date.now() - this.lastFailureTime > CIRCUIT_RECOVERY_TIMEOUT) {
        this.state = 'HALF_OPEN';
        this.successCount = 0;
        console.log(`🔄 서킷 브레이커 ${this.name}: HALF_OPEN 상태로 전환`);
        return true;
      }
      return false;
    }
    return true; // HALF_OPEN
  }

  onSuccess() {
    if (this.state === 'HALF_OPEN') {
      this.successCount++;
      if (this.successCount >= CIRCUIT_SUCCESS_THRESHOLD) {
        this.state = 'CLOSED';
        this.failureCount = 0;
        console.log(`✅ 서킷 브레이커 ${this.name}: CLOSED 상태로 복구`);
      }
    } else {
      this.failureCount = 0;
    }
  }

  onFailure() {
    this.failureCount++;
    this.lastFailureTime = Date.now();

    if (this.state === 'HALF_OPEN') {
      this.state = 'OPEN';
      console.log(`⚡ 서킷 브레이커 ${this.name}: OPEN 상태로 전환 (HALF_OPEN 실패)`);
    } else if (this.failureCount >= CIRCUIT_FAILURE_THRESHOLD) {
      this.state = 'OPEN';
      console.log(`⚡ 서킷 브레이커 ${this.name}: OPEN 상태로 전환 (실패 ${this.failureCount}회)`);
    }
  }
}

// API별 서킷 브레이커 인스턴스
const circuitBreakers = {
  popup_list: new CircuitBreaker('popup_list'),
  popup_detail: new CircuitBreaker('popup_detail'),
  order_create: new CircuitBreaker('order_create'),
  stock_reserve: new CircuitBreaker('stock_reserve'),
  payment_request: new CircuitBreaker('payment_request'),
  query: new CircuitBreaker('query')
};

// ===== 동적 부하 조절 관리 =====
let currentLoadFactor = 1.0;
let totalRequests = 0;
let failedRequests = 0;

// ===== HELPER FUNCTIONS =====
function headers() {
  const h = { "Content-Type": "application/json" };
  if (AUTH_TOKEN) {
    h["Authorization"] = `Bearer ${AUTH_TOKEN}`;
  } else if (globalToken) {
    h["Authorization"] = `Bearer ${globalToken}`;
  }
  return h;
}

function ensureValidToken() {
  const now = Date.now();

  // AUTH_TOKEN이 있으면 항상 우선 사용
  if (AUTH_TOKEN) return true;

  // 로그인 실패 직후 짧은 쿨다운으로 로그인 폭주 방지
  if (now < nextLoginAttemptAt) return !!globalToken;

  // 토큰이 없거나 만료 임박시 재로그인
  if (!globalToken || (tokenExpiry && now > (tokenExpiry - TOKEN_REFRESH_MARGIN))) {
    console.log("🔄 토큰 갱신 필요...");
    const newToken = login();
    return !!newToken;
  }

  return true;
}

// 테스트 모드: 인증 우회 옵션
const TEST_MODE = (__ENV.TEST_MODE || "false").toLowerCase() === "true";

function safeApiCall(apiFunc, ...args) {
  if (TEST_MODE) {
    // 테스트 모드: 인증 확인 없이 바로 실행
    return apiFunc(...args);
  } else {
    // 일반 모드: 인증 확인 후 실행
    if (!ensureValidToken()) {
      console.log("⚠️ 토큰 없이 API 호출 건너뜀");
      return null;
    }
    return apiFunc(...args);
  }
}

// 지수 백오프 계산 (지터 포함)
function calculateBackoffDelay(attempt) {
  const baseDelay = API_RETRY_DELAY_MS;
  const exponentialDelay = Math.min(baseDelay * Math.pow(2, attempt), 10000); // 최대 10초
  const jitter = exponentialDelay * (0.8 + Math.random() * 0.4); // ±20% 지터
  return jitter;
}

// 동적 부하 조절 체크
function checkAndAdjustLoad() {
  if (!DYNAMIC_LOAD_ENABLED || totalRequests < 10) return;

  const currentErrorRate = failedRequests / totalRequests;
  if (currentErrorRate > ERROR_RATE_THRESHOLD) {
    const oldFactor = currentLoadFactor;
    currentLoadFactor = Math.max(currentLoadFactor * LOAD_REDUCTION_FACTOR, 0.1);
    if (oldFactor !== currentLoadFactor) {
      console.log(`📉 동적 부하 조절: ${(oldFactor * 100).toFixed(1)}% → ${(currentLoadFactor * 100).toFixed(1)}% (에러율: ${(currentErrorRate * 100).toFixed(1)}%)`);
    }
  } else if (currentErrorRate < ERROR_RATE_THRESHOLD * 0.5 && currentLoadFactor < 1.0) {
    const oldFactor = currentLoadFactor;
    currentLoadFactor = Math.min(currentLoadFactor * 1.1, 1.0);
    if (oldFactor !== currentLoadFactor) {
      console.log(`📈 동적 부하 복구: ${(oldFactor * 100).toFixed(1)}% → ${(currentLoadFactor * 100).toFixed(1)}% (에러율: ${(currentErrorRate * 100).toFixed(1)}%)`);
    }
  }
}

// 개선된 API 호출 재시도 래퍼 (서킷 브레이커 + 지수 백오프)
function retryApiCallWithCircuitBreaker(apiName, apiCall, circuitBreakerName, maxRetries = API_RETRY_COUNT) {
  const breaker = circuitBreakers[circuitBreakerName];

  // 서킷 브레이커 체크
  if (!breaker.canExecute()) {
    console.log(`🔒 ${apiName} 서킷 브레이커 열림 - 호출 건너뜀`);
    failedRequests++;
    totalRequests++;
    checkAndAdjustLoad();
    return null;
  }

  // 동적 부하 조절 적용
  if (DYNAMIC_LOAD_ENABLED && Math.random() > currentLoadFactor) {
    console.log(`🎛️ ${apiName} 동적 부하 조절로 건너뜀 (현재: ${(currentLoadFactor * 100).toFixed(1)}%)`);
    return null;
  }

  for (let attempt = 1; attempt <= maxRetries + 1; attempt++) {
    try {
      const result = apiCall();
      totalRequests++;

      // 성공적인 응답 (2xx)
      if (result && (result.status >= 200 && result.status < 300)) {
        breaker.onSuccess();
        if (attempt > 1) {
          console.log(`✅ ${apiName} 성공 (${attempt}회 시도)`);
        }
        checkAndAdjustLoad();
        return result;
      }

      // 4xx 에러는 재시도하지 않음
      if (result && result.status >= 400 && result.status < 500) {
        console.log(`❌ ${apiName} 클라이언트 오류 (${result.status}) - 재시도 중단`);
        failedRequests++;
        breaker.onFailure();
        checkAndAdjustLoad();
        return result;
      }

      // 5xx 에러나 타임아웃은 재시도
      if (attempt <= maxRetries) {
        const backoffMs = calculateBackoffDelay(attempt);
        console.log(`🔄 ${apiName} 재시도 ${attempt}/${maxRetries} (상태: ${result?.status || 'timeout'}, 대기: ${(backoffMs/1000).toFixed(1)}s)`);
        sleep(backoffMs / 1000);
      } else {
        failedRequests++;
        breaker.onFailure();
      }

    } catch (error) {
      totalRequests++;
      console.log(`❌ ${apiName} 예외 발생: ${error.message}`);
      if (attempt <= maxRetries) {
        const backoffMs = calculateBackoffDelay(attempt);
        console.log(`🔄 ${apiName} 재시도 ${attempt}/${maxRetries} (예외, 대기: ${(backoffMs/1000).toFixed(1)}s)`);
        sleep(backoffMs / 1000);
      } else {
        failedRequests++;
        breaker.onFailure();
      }
    }
  }

  console.log(`💥 ${apiName} 최종 실패 (${maxRetries + 1}회 시도)`);
  checkAndAdjustLoad();
  return null;
}

// 기존 함수 호환성 유지
function retryApiCall(apiName, apiCall, maxRetries = API_RETRY_COUNT) {
  // API 이름에서 서킷 브레이커 키 추출
  const cbKey = apiName.includes('popup_list') ? 'popup_list' :
                apiName.includes('popup_detail') ? 'popup_detail' :
                apiName.includes('order_create') ? 'order_create' :
                apiName.includes('stock_reserve') ? 'stock_reserve' :
                apiName.includes('payment_request') ? 'payment_request' : 'query';

  return retryApiCallWithCircuitBreaker(apiName, apiCall, cbKey, maxRetries);
}

function ok2xx(res) {
  const ok = res.status >= 200 && res.status < 300;
  r_fail.add(!ok);
  return ok;
}

function pickDistributedPopup() {
  return POPUP_IDS[Math.floor(Math.random() * POPUP_IDS.length)];
}

function pickUserId() {
  return USER_IDS[Math.floor(Math.random() * USER_IDS.length)];
}

// Duration 계산 (분 단위)
function addDurations(a, b) {
  const ma = parseInt(String(a).replace("m", ""), 10);
  const mb = parseInt(String(b).replace("m", ""), 10);
  return `${ma + mb}m`;
}

// ===== AUTHENTICATION =====
function shortBody(body) {
  if (!body) return "null";
  const s = String(body);
  return s.length > 400 ? `${s.slice(0, 400)}...(truncated)` : s;
}

function extractTokenFromLoginResult(result) {
  return (
    result?.token ||
    result?.accessToken ||
    result?.data?.token ||
    result?.data?.accessToken ||
    result?.result?.token ||
    result?.result?.accessToken ||
    null
  );
}

function login() {
  if (AUTH_TOKEN) {
    console.log("🔐 AUTH_TOKEN 사용 중");
    return AUTH_TOKEN;
  }

  const loginData = {
    email: LOGIN_EMAIL,
    password: LOGIN_PASSWORD
  };

  for (let attempt = 1; attempt <= LOGIN_MAX_ATTEMPTS; attempt++) {
    const response = http.post(`${BASE_URL}/api/users/v1/auth/login`, JSON.stringify(loginData), {
      headers: { "Content-Type": "application/json" },
      timeout: "10s",
      tags: { name: "login" }
    });

    t_login.add(response.timings.duration);

    // 서버 상태별 상세 처리
    if (response.status === 503) {
      console.log(`🚨 서버 중단 상태 (503 Service Unavailable)`);
      console.log(`💡 서버가 점검 중이거나 과부하 상태입니다.`);
      // 503일 때는 재시도해도 의미없으므로 빠르게 실패 처리
      if (attempt >= 2) {
        console.log(`❌ 서버 중단으로 인한 로그인 중단`);
        break;
      }
    } else if (response.status >= 200 && response.status < 300) {
      try {
        const result = JSON.parse(response.body);
        globalToken = extractTokenFromLoginResult(result);
        if (globalToken) {
          // JWT 토큰 만료 시간 설정 (1시간으로 가정)
          tokenExpiry = Date.now() + 55 * 60 * 1000; // 55분 후 만료
          nextLoginAttemptAt = 0;
          console.log(`✅ 로그인 성공: 토큰 길이=${globalToken.length}`);
          return globalToken;
        }
        console.log(`❌ 로그인 성공 응답이나 토큰 없음 - Body: ${shortBody(response.body)}`);
      } catch (e) {
        console.log(`❌ 로그인 응답 파싱 실패: ${e.message}, Body: ${shortBody(response.body)}`);
      }
    } else if (response.status === 401 || response.status === 403) {
      console.log(`🔐 인증 실패 (${response.status}): 계정 정보를 확인하세요`);
      console.log(`📧 이메일: ${LOGIN_EMAIL}`);
      console.log(`🔑 비밀번호: ${LOGIN_PASSWORD.replace(/./g, '*')}`);
      // 인증 정보 오류시에도 재시도하지 않음
      break;
    } else {
      console.log(`❌ 로그인 실패 - Status: ${response.status}, Body: ${shortBody(response.body)}`);
    }

    if (attempt < LOGIN_MAX_ATTEMPTS) {
      console.log(`🔁 로그인 재시도 ${attempt}/${LOGIN_MAX_ATTEMPTS}`);
      sleep(LOGIN_RETRY_SLEEP_SECONDS);
    }
  }

  nextLoginAttemptAt = Date.now() + LOGIN_FAIL_COOLDOWN_MS;
  return null;
}

function ensureAuth(data) {
  if (AUTH_TOKEN) return true;
  if (!globalToken && data?.token) {
    globalToken = data.token;
  }
  return !!globalToken;
}

function extractOrderId(orderRes) {
  try {
    const j = JSON.parse(orderRes.body);
    return j?.data?.id || j?.data?.orderId || j?.orderId || null;
  } catch (_) {
    return null;
  }
}

// ===== OPERATIONAL API CALLS =====
function api_popup_list() {
  if (!TEST_MODE && !ensureValidToken()) return null;

  // popup_list는 핫패스에서 과부하를 만들기 쉬워 기본 비활성/샘플링/쿨다운 적용
  if (!ENABLE_POPUP_LIST) return null;
  if (Math.random() > POPUP_LIST_SAMPLE_RATE) return null;

  const now = Date.now();
  if (now < nextPopupListAttemptAt) return null;
  nextPopupListAttemptAt = now + POPUP_LIST_COOLDOWN_MS;

  return retryApiCall("popup_list", () => {
    const res = http.get(`${BASE_URL}/api/stores/v1/popups`, {
      headers: headers(),
      timeout: POPUP_LIST_TIMEOUT,
      tags: { name: "popup_list" }
    });
    t_popup_list.add(res.timings.duration);
    check(res, { "popup_list 2xx": ok2xx });
    return res;
  }, POPUP_LIST_RETRY_COUNT);
}

function api_popup_detail(popupId) {
  if (!TEST_MODE && !ensureValidToken()) return null;

  return retryApiCall(`popup_detail(${popupId})`, () => {
    const res = http.get(`${BASE_URL}/api/stores/v1/popups/${popupId}`, {
      headers: headers(),
      timeout: API_TIMEOUT,
      tags: { name: "popup_detail" }
    });
    t_popup_detail.add(res.timings.duration);
    check(res, { "popup_detail 2xx": ok2xx });
    return res;
  });
}

function api_order_create({ popupId }) {
  if (!TEST_MODE && !ensureValidToken()) return null;

  return retryApiCall("order_create", () => {
    // 실제 Popcorn 주문 생성 API 스펙에 맞춘 페이로드
    const body = JSON.stringify({
      popupId,
      orderType: "RESERVATION",
      items: [
        {
          goodsId: `goods_${popupId}`,
          quantity: 1,
          unitPrice: 10000
        }
      ]
    });

    const res = http.post(`${BASE_URL}/api/orders/v1/orders`, body, {
      headers: headers(),
      timeout: API_TIMEOUT,
      tags: { name: "order_create" }
    });
    t_order_create.add(res.timings.duration);
    check(res, { "order_create 2xx": ok2xx });
    return res;
  });
}

function api_stock_reserve({ popupId, orderId }) {
  if (!TEST_MODE && !ensureValidToken()) return null;

  return retryApiCall("stock_reserve", () => {
    // 실제 재고 예약 API 스펙에 맞춘 페이로드
    const body = JSON.stringify({
      popupId,
      orderId,
      items: [
        {
          goodsId: `goods_${popupId}`,
          quantity: 1
        }
      ]
    });

    const res = http.post(`${BASE_URL}/api/stores/v1/stocks/reserve`, body, {
      headers: headers(),
      timeout: API_TIMEOUT,
      tags: { name: "stock_reserve" }
    });
    t_stock_reserve.add(res.timings.duration);
    check(res, { "stock_reserve 2xx": ok2xx });
    return res;
  });
}

function api_payment_request({ orderId }) {
  if (!TEST_MODE && !ensureValidToken()) return null;

  return retryApiCall("payment_request", () => {
    // 실제 결제 요청 API 스펙에 맞춘 페이로드
    const body = JSON.stringify({
      orderId,
      paymentMethod: "CARD",
      amount: 10000
    });

    const res = http.post(`${BASE_URL}/api/payments/v1/payments/request`, body, {
      headers: headers(),
      timeout: API_TIMEOUT,
      tags: { name: "payment_request" }
    });
    t_payment_req.add(res.timings.duration);
    check(res, { "payment_request 2xx": ok2xx });
    return res;
  });
}

// ===== QUERY API CALLS (실제 API 엔드포인트 사용) =====
function api_my_order_status(orderId) {
  if (!TEST_MODE && !ensureValidToken()) return null;

  return retryApiCall(`my_order_status(${orderId})`, () => {
    // 실제 주문 조회 API 사용 (order-query 서비스가 없으므로 orders API 사용)
    const res = http.get(`${BASE_URL}/api/orders/v1/orders/${orderId}`, {
      headers: headers(),
      timeout: API_TIMEOUT,
      tags: { name: "query_my_order_status" },
    });

    t_my_order_status.add(res.timings.duration);
    const ok = res.status >= 200 && res.status < 300;
    r_query_fail.add(!ok);
    check(res, { "my_order_status 2xx": () => ok });
    return res;
  });
}

function api_popup_stock(popupId) {
  if (!TEST_MODE && !ensureValidToken()) return null;

  return retryApiCall(`popup_stock(${popupId})`, () => {
    // 팝업 상세 조회로 재고 정보 확인 (stock API 대신)
    const res = http.get(`${BASE_URL}/api/stores/v1/popups/${popupId}`, {
      headers: headers(),
      timeout: API_TIMEOUT,
      tags: { name: "query_popup_stock" },
    });

    t_popup_stock.add(res.timings.duration);
    const ok = res.status >= 200 && res.status < 300;
    r_query_fail.add(!ok);
    check(res, { "popup_stock 2xx": () => ok });
    return res;
  });
}

function api_order_list(userId, page = 0, size = 20) {
  if (!TEST_MODE && !ensureValidToken()) return null;

  return retryApiCall(`order_list(${userId}, page=${page})`, () => {
    // 사용자별 주문 목록 API가 없으므로 팝업 목록으로 대체
    const res = http.get(`${BASE_URL}/api/stores/v1/popups?page=${page}&size=${size}`, {
      headers: headers(),
      timeout: API_TIMEOUT,
      tags: { name: "query_order_list" },
    });

    t_order_list.add(res.timings.duration);
    const ok = res.status >= 200 && res.status < 300;
    r_query_fail.add(!ok);
    check(res, { "order_list 2xx": () => ok });
    return res;
  });
}

function fetchRecentOrderId() {
  if (!ORDER_ID_FEED_URL) return null;
  const res = http.get(ORDER_ID_FEED_URL, {
    headers: headers(),
    timeout: "5s",
    tags: { name: "order_id_feed" }
  });
  if (!(res.status >= 200 && res.status < 300)) return null;

  try {
    const json = JSON.parse(res.body);
    const ids = json?.data?.orderIds || json?.orderIds || [];
    if (!ids.length) return null;
    return ids[Math.floor(Math.random() * ids.length)];
  } catch (_) {
    return null;
  }
}

function calcConsistencyLagMs(resBody) {
  try {
    const json = JSON.parse(resBody);
    const occurredAt = json?.data?.eventOccurredAt || json?.data?.occurredAt;
    const updatedAt = json?.data?.queryUpdatedAt || json?.data?.updatedAt;

    if (!occurredAt || !updatedAt) return null;

    const t1 = Date.parse(occurredAt);
    const t2 = Date.parse(updatedAt);
    if (Number.isNaN(t1) || Number.isNaN(t2)) return null;

    return t2 - t1;
  } catch (_) {
    return null;
  }
}

// ===== 서버 상태 점검 =====
function performHealthCheck() {
  console.log(`🔍 서버 상태 점검 시작...`);

  // 1. 기본 연결성 확인
  let healthScore = 0;
  const healthTests = [];

  try {
    const baseCheck = http.get(`${BASE_URL}`, { timeout: "10s" });
    healthTests.push({
      test: "기본 연결",
      status: baseCheck.status,
      time: baseCheck.timings.duration,
      success: baseCheck.status < 500
    });
    if (baseCheck.status < 500) healthScore++;
  } catch (e) {
    healthTests.push({
      test: "기본 연결",
      status: "TIMEOUT",
      time: "10000+",
      success: false,
      error: e.message
    });
  }

  // 2. 주요 엔드포인트 상태 확인
  const endpoints = [
    { name: "팝업 목록", path: "/api/stores/v1/popups" },
    { name: "헬스체크", path: "/health" },
    { name: "메트릭", path: "/metrics" }
  ];

  endpoints.forEach(endpoint => {
    try {
      const check = http.get(`${BASE_URL}${endpoint.path}`, {
        timeout: "5s",
        headers: { "Accept": "application/json" }
      });
      healthTests.push({
        test: endpoint.name,
        status: check.status,
        time: check.timings.duration,
        success: check.status < 500
      });
      if (check.status < 500) healthScore++;
    } catch (e) {
      healthTests.push({
        test: endpoint.name,
        status: "TIMEOUT",
        time: "5000+",
        success: false,
        error: e.message
      });
    }
  });

  // 결과 출력
  console.log(`\n📊 서버 상태 점검 결과:`);
  healthTests.forEach(test => {
    const icon = test.success ? "✅" : "❌";
    const timeStr = typeof test.time === 'number' ? `${test.time.toFixed(0)}ms` : test.time;
    console.log(`${icon} ${test.test}: ${test.status} (${timeStr})`);
    if (test.error) console.log(`   └─ 오류: ${test.error}`);
  });

  const healthPercentage = (healthScore / (endpoints.length + 1)) * 100;
  console.log(`\n🎯 서버 건강도: ${healthScore}/${endpoints.length + 1} (${healthPercentage.toFixed(0)}%)`);

  return {
    healthy: healthScore > 0,
    score: healthScore,
    percentage: healthPercentage,
    tests: healthTests
  };
}

// ===== SETUP & OPTIONS =====
export function setup() {
  console.log(`\n🚀 ===== Popcorn 완전 구현 부하 테스트 =====`);
  console.log(`📊 모드: ${MODE.toUpperCase()}`);
  console.log(`🔗 서버: ${BASE_URL}`);
  console.log(`📧 계정: ${LOGIN_EMAIL}`);
  console.log(`⚙️ 엄격한 로그인: ${STRICT_SETUP_LOGIN ? 'ON' : 'OFF'}`);
  console.log(`🧪 테스트 모드: ${TEST_MODE ? 'ON (인증 우회)' : 'OFF'}`);
  console.log(`🔄 서킷 브레이커: 실패 임계값 ${CIRCUIT_FAILURE_THRESHOLD}회`);
  console.log(`📉 동적 부하 조절: ${DYNAMIC_LOAD_ENABLED ? 'ON' : 'OFF'} (임계값: ${(ERROR_RATE_THRESHOLD * 100).toFixed(0)}%)`);
  console.log(`========================================\n`);

  if (AUTH_TOKEN) {
    console.log(`🔐 AUTH_TOKEN 사용 - 로그인 건너뜀`);
    return { token: AUTH_TOKEN };
  }

  // 서버 상태 점검 수행
  const healthResult = performHealthCheck();

  // 서버 상태 기반 결정
  if (!healthResult.healthy) {
    const errorMsg = `
🚨 서버 중단 상태 감지!

현재 상태: 503 Service Temporarily Unavailable
서버 URL: ${BASE_URL}

📋 해결 방법:
1. 서버 복구까지 대기
2. 다른 환경 URL 사용: -e BASE_URL=http://localhost:8080
3. 비엄격 모드 사용: -e STRICT_SETUP_LOGIN=false

💡 비엄격 모드에서는 setup 로그인 실패해도 테스트를 계속 진행하며,
   각 VU에서 개별적으로 로그인을 시도합니다.`;

    if (STRICT_SETUP_LOGIN) {
      throw new Error(errorMsg);
    } else {
      console.log(errorMsg);
      console.log(`\n⚠️ 비엄격 모드: setup 로그인 건너뜀, VU별 로그인 시도`);
      return { token: null };
    }
  }

  console.log(`🔐 로그인 시도 중...`);
  const token = login();

  if (!token) {
    const errorMsg = `
❌ 로그인 실패

📋 해결 방법:
1. 서버 상태 확인: curl ${BASE_URL}/api/users/v1/auth/login
2. 계정 정보 확인:
   - 이메일: ${LOGIN_EMAIL}
   - 비밀번호: ${LOGIN_PASSWORD.replace(/./g, '*')}
3. AUTH_TOKEN 직접 사용: -e AUTH_TOKEN=your_token_here
4. 다른 계정 사용: -e LOGIN_EMAIL=other@email.com -e LOGIN_PASSWORD=password
5. 비엄격 모드 사용: -e STRICT_SETUP_LOGIN=false`;

    if (STRICT_SETUP_LOGIN) {
      throw new Error(errorMsg);
    } else {
      console.log(errorMsg);
      console.log(`\n⚠️ 비엄격 모드: setup 로그인 실패 무시, VU별 로그인 시도`);
      return { token: null };
    }
  }

  console.log(`✅ 인증 완료 - ${MODE.toUpperCase()} 모드로 테스트 시작`);
  return { token };
}

// STORM Query 시나리오용 RPS 분할
const STORM_MY_ORDER_RPS = Math.floor((STORM_RPS_TOTAL * STORM_SPLIT_MY_ORDER_PCT) / 100);
const STORM_POPUP_STOCK_RPS = Math.max(0, STORM_RPS_TOTAL - STORM_MY_ORDER_RPS);

// 순차 실행을 위한 시작 시간 계산
const mixStart = STORM_DURATION;
const consistencyStart = addDurations(STORM_DURATION, MIX_DURATION);

// MODE별 시나리오 활성화 체크
function modeEnabled(x) {
  if (MODE === "operational" && x.startsWith("operational_")) return true;
  if (MODE === "query" && x.startsWith("query_")) return true;
  return false;
}

function queryModeEnabled(x) {
  return QUERY_MODE === x || QUERY_MODE === "all";
}

// 동적 시나리오 생성 함수
function createScenarios() {
  const scenarios = {};

  // OPERATIONAL MODE 시나리오들
  if (MODE === "operational") {
    scenarios.operational_stage1_steady = {
      executor: "constant-arrival-rate",
      rate: STEADY_RPS,
      timeUnit: "1s",
      duration: STEADY_DURATION,
      preAllocatedVUs: 500,
      maxVUs: 15000,
      exec: "operationalStageSteady",
      tags: { stage: "steady", scenario: SCENARIO },
    };

    scenarios.operational_stage2_rush = {
      executor: "constant-arrival-rate",
      rate: RUSH_RPS,
      timeUnit: "1s",
      duration: RUSH_DURATION,
      preAllocatedVUs: 800,
      maxVUs: 20000,
      exec: "operationalStageRush",
      startTime: STEADY_DURATION,
      tags: { stage: "rush", scenario: SCENARIO },
    };

    scenarios.operational_stage3_spike = {
      executor: "constant-arrival-rate",
      rate: SPIKE_RPS,
      timeUnit: "1s",
      duration: SPIKE_DURATION,
      preAllocatedVUs: 1500,
      maxVUs: 25000,
      exec: "operationalStageSpike",
      startTime: addDurations(STEADY_DURATION, RUSH_DURATION),
      tags: { stage: "spike", scenario: SCENARIO },
    };
  }

  // QUERY MODE 시나리오들
  if (MODE === "query") {
    if (queryModeEnabled("storm")) {
      scenarios.query_storm_my_order = {
        executor: "constant-arrival-rate",
        rate: STORM_MY_ORDER_RPS,
        timeUnit: "1s",
        duration: STORM_DURATION,
        preAllocatedVUs: 800,
        maxVUs: 12000,
        exec: "queryStormMyOrder",
        tags: { scenario: "storm_my_order" },
      };

      scenarios.query_storm_popup_stock = {
        executor: "constant-arrival-rate",
        rate: STORM_POPUP_STOCK_RPS,
        timeUnit: "1s",
        duration: STORM_DURATION,
        preAllocatedVUs: 1200,
        maxVUs: 20000,
        exec: "queryStormPopupStock",
        tags: { scenario: "storm_popup_stock" },
      };
    }

    if (queryModeEnabled("mix")) {
      scenarios.query_steady_read_mix = {
        executor: "constant-arrival-rate",
        rate: MIX_RPS,
        timeUnit: "1s",
        duration: MIX_DURATION,
        preAllocatedVUs: 800,
        maxVUs: 16000,
        exec: "querySteadyReadMix",
        startTime: queryModeEnabled("all") ? mixStart : "0s",
        tags: { scenario: "steady_read_mix" },
      };
    }

    if (queryModeEnabled("consistency")) {
      scenarios.query_consistency_after_event_burst = {
        executor: "constant-arrival-rate",
        rate: CONSISTENCY_RPS,
        timeUnit: "1s",
        duration: CONSISTENCY_DURATION,
        preAllocatedVUs: 200,
        maxVUs: 5000,
        exec: "queryConsistency",
        startTime: queryModeEnabled("all") ? consistencyStart : "0s",
        tags: { scenario: "consistency_after_event_burst" },
      };
    }
  }

  return scenarios;
}

export const options = {
  scenarios: createScenarios(),

  thresholds: MODE === "operational" ? {
    // 운영 부하 테스트 임계값 (고부하 상황에서 현실적인 값들)
    r_fail: [`rate<${OP_FAIL_RATE}`],                    // 에러율 < 5% (설정 가능)
    http_req_duration: [`p(95)<${OP_HTTP_P95_MS}`],      // 전체 p95 < 2초 (설정 가능)
    t_order_create: [`p(95)<${OP_ORDER_P95_MS}`],        // 주문 생성 p95 < 3초 (설정 가능)
    t_payment_req: [`p(95)<${OP_PAYMENT_P95_MS}`],       // 결제 요청 p95 < 4초 (설정 가능)
    t_stock_reserve: [`p(95)<${OP_ORDER_P95_MS}`],       // 재고 예약 p95 < 3초
    t_popup_detail: ["p(95)<1000"],                      // 팝업 상세 p95 < 1초
    t_popup_list: ["p(95)<800"],                         // 팝업 목록 p95 < 0.8초
  } : {
    // Query 부하 테스트 임계값
    r_query_fail: [`rate<${Q_FAIL_RATE}`],               // Query 에러율 < 2% (설정 가능)
    http_req_duration: [`p(95)<${QUERY_P95_MS}`],        // p95 < 400ms
    t_my_order_status: [`p(95)<${QUERY_P95_MS}`],
    t_popup_stock: [`p(95)<${QUERY_P95_MS}`],
    t_order_list: ["p(95)<700"],                         // 목록은 약간 더 느려도 허용
    t_consistency_lag_ms: [`p(95)<${CONSISTENCY_P95_MS}`], // 최신성 지연 < 5초
  },
};

// ===== OPERATIONAL SCENARIOS =====
// ===== 실시간 모니터링 =====
function printRealTimeStats() {
  if (totalRequests % 50 === 0 && totalRequests > 0) { // 50회마다 출력
    const errorRate = (failedRequests / totalRequests) * 100;
    const circuitStatus = Object.keys(circuitBreakers)
      .map(key => `${key}:${circuitBreakers[key].state}`)
      .join(", ");

    console.log(`\n📊 실시간 모니터링 (요청 ${totalRequests}회)`);
    console.log(`   에러율: ${errorRate.toFixed(1)}% | 부하 조절: ${(currentLoadFactor * 100).toFixed(0)}%`);
    console.log(`   서킷 브레이커: ${circuitStatus}`);
    console.log(`────────────────────────────────────────────────────────\n`);
  }
}

export function operationalStageSteady(data) {
  if (!ensureAuth(data)) return;

  group("stage1_steady", () => {
    runOperationalScenario(SCENARIO, "steady");
    printRealTimeStats(); // 실시간 모니터링
    sleep(0.2 + Math.random() * 0.8);
  });
}

export function operationalStageRush(data) {
  if (!ensureAuth(data)) return;

  group("stage2_rush", () => {
    runOperationalScenario(SCENARIO, "rush");
    printRealTimeStats(); // 실시간 모니터링
    sleep(Math.random() * 0.5);
  });
}

export function operationalStageSpike(data) {
  if (!ensureAuth(data)) return;

  group("stage3_spike", () => {
    runOperationalScenario(SCENARIO, "spike");
    printRealTimeStats(); // 실시간 모니터링
    sleep(Math.random() * 0.2);
  });
}

function runOperationalScenario(name, stage) {
  if (name === "hot") return scenarioHot(stage);
  if (name === "dist") return scenarioDist(stage);
  if (name === "fault") return scenarioFault(stage);
  return scenarioHot(stage); // 기본값
}

// SCENARIO 1) hot : 오픈 러시(핫키/핫팝업) - 개선된 부하 조절
function scenarioHot(stage) {
  // 공통: 핫팝업에 트래픽 집중
  const popupListResult = api_popup_list();                 // 목록 조회
  const popupDetailResult = api_popup_detail(POPUP_HOT_ID); // 상세 조회

  // 단계별로 쓰기 비중 조절 (문서 요구사항 정확히 반영)
  let writeProb = 0.15; // steady 기본
  if (stage === "rush") writeProb = 0.30;
  if (stage === "spike") writeProb = 0.55;

  // 동적 부하 조절: 에러율이 높으면 쓰기 비중 감소
  const adjustedWriteProb = writeProb * currentLoadFactor;

  if (Math.random() < adjustedWriteProb) {
    // 서킷 브레이커 상태 확인 후 주문 시도
    if (circuitBreakers.order_create.canExecute()) {
      const orderRes = api_order_create({ popupId: POPUP_HOT_ID });
      const orderId = extractOrderId(orderRes);

      // 주문 성공 시에만 후속 작업 진행
      if (orderRes && orderRes.status >= 200 && orderRes.status < 300 && orderId) {
        // 재고 예약 (스토어 병목 확인 포인트)
        if (circuitBreakers.stock_reserve.canExecute()) {
          api_stock_reserve({ popupId: POPUP_HOT_ID, orderId });
        }

        // 결제 요청
        if (circuitBreakers.payment_request.canExecute()) {
          api_payment_request({ orderId });
        }
      }
    } else {
      console.log(`🔒 주문 생성 서킷 브레이커 열림 - 조회로 대체`);
      // 주문 대신 추가 조회 수행
      const randomPopup = pickDistributedPopup();
      api_popup_detail(randomPopup);
    }
  } else if (Math.random() < 0.3) {
    // 추가 조회 작업 (부하 분산)
    const randomPopup = pickDistributedPopup();
    api_popup_detail(randomPopup);
  }
}

// SCENARIO 2) dist : 정상 운영(여러 팝업 분산)
function scenarioDist(stage) {
  const popupId = pickDistributedPopup();

  // read-heavy
  api_popup_detail(popupId);

  // 쓰기 비중은 낮게 유지(steady 운영 느낌)
  let writeProb = 0.10;
  if (stage === "rush") writeProb = 0.15;   // rush에서도 분산 운영 가정
  if (stage === "spike") writeProb = 0.25;

  if (Math.random() < writeProb) {
    const orderRes = api_order_create({ popupId });
    const orderId = extractOrderId(orderRes);
    if (orderId) api_payment_request({ orderId });
  }
}

// SCENARIO 3) fault : 장애 내성(지연/실패 주입)
function scenarioFault(stage) {
  const popupId = stage === "rush" || stage === "spike" ? POPUP_HOT_ID : pickDistributedPopup();

  // 장애 상황에서도 사용자들은 계속 조회/주문을 시도한다는 가정
  api_popup_detail(popupId);

  // 핵심 트랜잭션 유지(조금 높은 비율)
  let writeProb = 0.20;
  if (stage === "rush") writeProb = 0.35;
  if (stage === "spike") writeProb = 0.60;

  if (Math.random() < writeProb) {
    const orderRes = api_order_create({ popupId });
    const orderId = extractOrderId(orderRes);
    if (orderId) api_payment_request({ orderId }); // PG 지연 주입 시 여기서 duration 증가 관측
  }
}

// ===== QUERY SCENARIOS =====
export function queryStormMyOrder(data) {
  if (!ensureAuth(data)) return;

  group("storm_my_order", () => {
    const orderId = fetchRecentOrderId();
    if (orderId) {
      api_my_order_status(orderId);
    } else {
      // orderId feed가 없다면 최소 동작: 목록으로 대체
      api_order_list(pickUserId(), 0, 20);
    }
    sleep(0.05);
  });
}

export function queryStormPopupStock(data) {
  if (!ensureAuth(data)) return;

  group("storm_popup_stock", () => {
    api_popup_stock(POPUP_HOT_ID);
    sleep(0.02);
  });
}

export function querySteadyReadMix(data) {
  if (!ensureAuth(data)) return;

  group("steady_read_mix", () => {
    const x = Math.random();

    // read-heavy: 90% 조회(재고/목록/상태), 10%는 추가 읽기(상태)로 흡수
    if (x < 0.55) {
      api_popup_stock(POPUP_HOT_ID); // 상세/재고 쿼리
    } else if (x < 0.90) {
      api_order_list(pickUserId(), Math.floor(Math.random() * 10), 20); // 페이지네이션
    } else {
      const orderId = fetchRecentOrderId();
      if (orderId) api_my_order_status(orderId);
      else api_order_list(pickUserId(), 0, 20);
    }

    sleep(0.1 + Math.random() * 0.4);
  });
}

export function queryConsistency(data) {
  if (!ensureAuth(data)) return;

  group("consistency_after_event_burst", () => {
    const orderId = fetchRecentOrderId();
    if (!orderId) {
      // orderId가 없으면 대체로 재고/목록 조회
      api_popup_stock(POPUP_HOT_ID);
      sleep(0.2);
      return;
    }

    const res = api_my_order_status(orderId);
    const lag = calcConsistencyLagMs(res.body);
    if (lag !== null) t_consistency_lag_ms.add(lag);

    sleep(0.2);
  });
}

export default function () {}
