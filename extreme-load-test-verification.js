import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Counter, Trend } from 'k6/metrics';

// 🚀 극한 부하 테스트 - 99.95% 실패율 해결 검증
// 목표: 350 RPS, 9000 VUs에서 성공률 99.9%+ 달성

const errorRate = new Rate('r_fail');
const popupDetailTime = new Trend('t_popup_detail');
const successfulPopupDetails = new Counter('successful_popup_details');

const USER_SERVICE = 'http://localhost:8082';
const STORES_SERVICE = 'http://localhost:8083';

// 테스트 계정 정보 (최적화된 5개 계정)
const TEST_ACCOUNTS = [
    { email: "popcorn1@popcorn.com", password: "test123", role: "CUSTOMER" },
    { email: "popcorn2@popcorn.com", password: "test123", role: "MANAGER" },
    { email: "popcorn3@popcorn.com", password: "test123", role: "CUSTOMER" },
    { email: "popcorn4@popcorn.com", password: "test123", role: "CUSTOMER" },
    { email: "popcorn6@popcorn.com", password: "test123", role: "OWNER" }
];

// 극한 부하 테스트 시나리오 (사용자 제공 스펙 기반)
export const options = {
    scenarios: {
        stage1_steady: {
            executor: 'constant-arrival-rate',
            rate: 80,                    // 80 iterations/s
            timeUnit: '1s',
            duration: '2m',
            preAllocatedVUs: 300,
            maxVUs: 3000,
            exec: 'stageSteady',
        },
        stage2_rush: {
            executor: 'constant-arrival-rate',
            rate: 180,                   // 180 iterations/s
            timeUnit: '1s',
            duration: '2m',
            startTime: '2m',
            preAllocatedVUs: 600,
            maxVUs: 6000,
            exec: 'stageRush',
        },
        stage3_spike: {
            executor: 'constant-arrival-rate',
            rate: 350,                   // 350 iterations/s (극한 부하)
            timeUnit: '1s',
            duration: '1m',
            startTime: '4m',
            preAllocatedVUs: 900,
            maxVUs: 9000,
            exec: 'stageSpike',
        }
    },
    thresholds: {
        'http_req_duration': ['p(95)<500'],      // 95% 요청이 500ms 이내
        'r_fail': ['rate<0.01'],                 // 실패율 1% 미만 (99%+ 성공)
        't_popup_detail': ['p(95)<1200'],        // 팝업 상세 95% 1.2초 이내
    },
};

// 사용자별 토큰 캐시 (글로벌)
let globalTokens = new Map();
let tokenExpiry = new Map();

export function setup() {
    console.log('🚀 극한 부하 테스트 - 99.95% 실패율 해결 검증 시작!');
    console.log('🎯 목표: 350 RPS, 9000 VUs에서 성공률 99.9%+ 달성');
    console.log('⚡ 적용된 최적화:');
    console.log('   • HikariCP: User/Stores 80 connections');
    console.log('   • Tomcat: 500 threads');
    console.log('   • Redis: 50 active connections');
    console.log('   • 로깅: ERROR 레벨 최소화');

    // 토큰 사전 획득 (셋업 단계에서)
    for (const account of TEST_ACCOUNTS) {
        const loginResponse = http.post(`${USER_SERVICE}/api/users/v1/auth/login`,
            JSON.stringify({
                email: account.email,
                password: account.password
            }),
            {
                headers: { 'Content-Type': 'application/json' },
                timeout: '10s'
            }
        );

        if (loginResponse.status === 200 && loginResponse.json('token')) {
            const token = loginResponse.json('token');
            globalTokens.set(account.email, token);
            tokenExpiry.set(account.email, Date.now() + 3500000);
            console.log(`✅ ${account.role} 토큰 사전 획득: ${account.email}`);
        } else {
            console.log(`⚠️ ${account.role} 로그인 실패: ${account.email}`);
        }
    }

    return { tokens: Object.fromEntries(globalTokens) };
}

// 공통 팝업 상세 조회 함수
function testPopupDetail(token, popupId) {
    if (!token) {
        errorRate.add(1);
        return false;
    }

    const startTime = Date.now();

    const response = http.get(`${STORES_SERVICE}/api/stores/v1/popups/${popupId}`, {
        headers: { 'Authorization': `Bearer ${token}` },
        tags: { endpoint: 'popup_detail' },
        timeout: '5s'
    });

    const duration = Date.now() - startTime;
    popupDetailTime.add(duration);

    const success = check(response, {
        'popup_detail 2xx': (r) => r.status >= 200 && r.status < 300,
    });

    if (success) {
        successfulPopupDetails.add(1);
        return true;
    } else {
        errorRate.add(1);
        return false;
    }
}

// 1단계: 안정적 부하 (80 RPS)
export function stageSteady() {
    const userIndex = (__VU - 1) % TEST_ACCOUNTS.length;
    const account = TEST_ACCOUNTS[userIndex];
    const token = globalTokens.get(account.email);

    // 기본 팝업 ID들 (실제 존재하는 ID 사용)
    const popupIds = [
        '00000000-0000-0000-0000-000000000101',
        '00000000-0000-0000-0000-000000000102',
        '00000000-0000-0000-0000-000000000103'
    ];

    const popupId = popupIds[Math.floor(Math.random() * popupIds.length)];
    testPopupDetail(token, popupId);

    sleep(0.1 + Math.random() * 0.1); // 100-200ms 대기
}

// 2단계: 급증 부하 (180 RPS)
export function stageRush() {
    const userIndex = (__VU - 1) % TEST_ACCOUNTS.length;
    const account = TEST_ACCOUNTS[userIndex];
    const token = globalTokens.get(account.email);

    const popupIds = [
        '00000000-0000-0000-0000-000000000101',
        '00000000-0000-0000-0000-000000000102',
        '00000000-0000-0000-0000-000000000103',
        '00000000-0000-0000-0000-000000000104'
    ];

    const popupId = popupIds[Math.floor(Math.random() * popupIds.length)];
    testPopupDetail(token, popupId);

    sleep(0.05 + Math.random() * 0.05); // 50-100ms 대기
}

// 3단계: 극한 부하 (350 RPS, 9000 VUs)
export function stageSpike() {
    const userIndex = (__VU - 1) % TEST_ACCOUNTS.length;
    const account = TEST_ACCOUNTS[userIndex];
    const token = globalTokens.get(account.email);

    const popupIds = [
        '00000000-0000-0000-0000-000000000101',
        '00000000-0000-0000-0000-000000000102',
        '00000000-0000-0000-0000-000000000103',
        '00000000-0000-0000-0000-000000000104',
        '00000000-0000-0000-0000-000000000105'
    ];

    const popupId = popupIds[Math.floor(Math.random() * popupIds.length)];
    const success = testPopupDetail(token, popupId);

    // 극한 부하에서는 대기 시간 최소화
    sleep(0.01 + Math.random() * 0.02); // 10-30ms 대기

    // 성능 통계 로깅 (매 1000회마다)
    if (__ITER % 1000 === 0 && success) {
        console.log(`🚀 [극한부하] VU=${__VU}, 성공적 처리 중...`);
    }
}

export function teardown(data) {
    console.log('\n🎯 극한 부하 테스트 결과 분석');
    console.log('=' .repeat(60));
    console.log(`✅ 성공한 팝업 조회: ${successfulPopupDetails.count}회`);
    console.log('📊 최적화 효과:');
    console.log('   • HikariCP 80 connections');
    console.log('   • Tomcat 500 threads');
    console.log('   • Redis 50 active connections');
    console.log('   • ERROR 레벨 로깅');

    if (data && data.tokens) {
        console.log(`🔑 사용된 토큰: ${Object.keys(data.tokens).length}개`);
    }

    console.log('\n🚀 Popcorn 마이크로서비스 극한 최적화 검증 완료!');
}