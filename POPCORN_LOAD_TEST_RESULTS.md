# Popcorn MSA 부하 테스트 결과 보고서

**테스트 일자**: 2026-02-23
**테스트 도구**: k6
**서버**: https://api.goormpopcorn.shop
**계정**: popcorn1@popcorn.com (orders) / popcorn5@popcorn.com (order-query)

---

## 📊 테스트 실행 결과 요약

### ✅ **성공한 부분**
1. **인증 시스템 정상 작동**
   - 로그인 성공: ✅ (토큰 길이 187)
   - JWT 토큰 관리 정상
   - 자동 토큰 갱신 로직 작동

2. **k6 스크립트 완성도**
   - 3단계 부하 증가 시나리오 구현 완료
   - 재시도 로직 및 타임아웃 처리 구현 완료
   - 운영/쿼리 모드 분리 구현 완료

3. **모니터링 및 로깅**
   - 상세한 에러 추적 및 재시도 로그
   - API 응답시간 측정 구조 완성
   - 임계값 설정 시스템 구축

### ❌ **발견된 주요 문제점**

#### 🔐 **1. 권한 및 인증 문제**
```
❌ order_create 클라이언트 오류 (403) - 재시도 중단
❌ order_list(U1, page=0) 클라이언트 오류 (400) - 재시도 중단
```

**원인 분석:**
- 주문 생성 API에 대한 권한 부족
- 일부 계정이 특정 기능에 제한적 접근
- API 엔드포인트 경로 또는 파라미터 불일치

**권장 해결책:**
- 관리자 권한이 있는 테스트 계정 생성
- API 문서와 실제 구현 간 일치성 검증
- 권한별 테스트 시나리오 분리

#### 🔥 **2. 서버 안정성 문제**
```
🔄 popup_list 재시도 1/2 (상태: 500)
Request Failed error="request timeout"
💥 popup_list 최종 실패 (3회 시도)
```

**문제 현황:**
- **500 Internal Server Error** 빈발
- **API 타임아웃** (15초 설정에도 지속 발생)
- **연쇄 실패** (cascade failure) 패턴

**성능 임계값 분석:**
| 지표 | 설정값 | 실제 결과 | 평가 |
|------|--------|-----------|------|
| 에러율 | < 30% | ~90% | ❌ 심각 |
| API 타임아웃 | 15초 | 지속 초과 | ❌ 심각 |
| 응답 성공률 | > 70% | ~10% | ❌ 심각 |

#### 🚨 **3. 인프라 및 아키텍처 이슈**

**근본 원인 추정:**
1. **데이터베이스 병목**
   - 커넥션 풀 고갈
   - 슬로우 쿼리 누적
   - 인덱스 최적화 부족

2. **메모리/CPU 자원 부족**
   - Pod 리소스 한계 도달
   - GC 압박으로 인한 지연
   - OOM 발생 가능성

3. **네트워크 계층 문제**
   - 로드밸런서 설정 오류
   - 서비스 메시 통신 장애
   - DNS 해석 지연

---

## 🔧 **즉시 해결 필요 사항**

### **우선순위 1: 긴급 (서버 안정화)**
```bash
# 1. 서버 상태 점검
kubectl get pods -A
kubectl logs <pod-name> --tail=100

# 2. 리소스 사용량 확인
kubectl top pods
kubectl describe pod <failing-pod>

# 3. 데이터베이스 성능 확인
SELECT * FROM pg_stat_activity WHERE state = 'active';
```

### **우선순위 2: 권한 문제 해결**
```bash
# 테스트용 관리자 계정 생성 또는 권한 부여 필요
# API 엔드포인트 경로 재검증
curl -H "Authorization: Bearer $TOKEN" \
     https://api.goormpopcorn.shop/api/orders/v1/orders
```

### **우선순위 3: 모니터링 강화**
```bash
# 실시간 로그 모니터링 설정
kubectl logs -f deployment/store-service
kubectl logs -f deployment/order-service
kubectl logs -f deployment/payment-service
```

---

## 📈 **개선된 테스트 계획**

### **Phase 1: 안정성 확보 (우선 실행)**
```bash
# 1. 최소 부하로 기본 기능 검증
k6 run -e STEADY_RPS=1 -e RUSH_RPS=2 -e SPIKE_RPS=3 script.js

# 2. 단일 API 개별 테스트
k6 run -e MODE=operational -e SCENARIO=hot \
       -e API_TIMEOUT=30s -e API_RETRY_COUNT=5 script.js
```

### **Phase 2: 점진적 부하 증가**
```bash
# 서버 안정화 후 실행 권장
k6 run -e STEADY_RPS=10 -e RUSH_RPS=20 -e SPIKE_RPS=30 script.js
```

### **Phase 3: 목표 부하 달성**
```bash
# 최종 목표: 문서 요구사항 달성
k6 run -e STEADY_RPS=250 -e RUSH_RPS=800 -e SPIKE_RPS=1800 script.js
```

---

## 🛠 **기술적 개선사항**

### **1. 재시도 로직 개선**
현재 구현된 재시도 로직은 우수하나, 추가 개선 가능:

```javascript
// 현재: 고정 재시도 간격
sleep(API_RETRY_DELAY_MS / 1000);

// 개선: 지수 백오프 + 지터
const backoffMs = Math.min(
  API_RETRY_DELAY_MS * Math.pow(2, attempt),
  10000  // 최대 10초
) * (0.8 + Math.random() * 0.4);  // ±20% 지터
sleep(backoffMs / 1000);
```

### **2. 서킷 브레이커 패턴 도입**
```javascript
let failureCount = 0;
const FAILURE_THRESHOLD = 5;
let circuitOpen = false;

function circuitBreakerCall(apiCall) {
  if (circuitOpen) {
    console.log("🔒 서킷 브레이커 열림 - 호출 건너뜀");
    return null;
  }

  const result = apiCall();
  if (!result || result.status >= 500) {
    failureCount++;
    if (failureCount >= FAILURE_THRESHOLD) {
      circuitOpen = true;
      console.log("⚡ 서킷 브레이커 활성화");
    }
  } else {
    failureCount = 0;
  }

  return result;
}
```

### **3. 동적 부하 조절**
```javascript
// 에러율 기반 자동 부하 조절
const errorRate = r_fail.rate;
if (errorRate > 0.50) {  // 50% 초과 시
  currentRPS = Math.max(currentRPS * 0.7, 1);  // 30% 감소
  console.log(`📉 부하 자동 감소: ${currentRPS} RPS`);
}
```

---

## 📋 **체크리스트: 서버 복구 후 실행**

### **사전 점검사항**
- [ ] 서버 Health Check 통과 (`curl https://api.goormpopcorn.shop/health`)
- [ ] 데이터베이스 연결 정상
- [ ] 로그 수집 시스템 준비
- [ ] 모니터링 대시보드 확인

### **테스트 실행 순서**
1. [ ] **단계 1**: 개별 API 기본 기능 확인 (1 RPS)
2. [ ] **단계 2**: 인증 및 권한 문제 해결 확인
3. [ ] **단계 3**: 저부하 안정성 테스트 (10 RPS, 5분)
4. [ ] **단계 4**: 중간 부하 스트레스 테스트 (50 RPS, 10분)
5. [ ] **단계 5**: 목표 부하 달성 테스트 (250+ RPS)

### **성공 기준**
| 단계 | 에러율 | 평균 응답시간 | P95 응답시간 |
|------|--------|---------------|-------------|
| 1-2단계 | < 5% | < 200ms | < 500ms |
| 3단계 | < 10% | < 500ms | < 1500ms |
| 4단계 | < 15% | < 1000ms | < 3000ms |
| 5단계 | < 20% | < 2000ms | < 5000ms |

---

## 🎯 **결론 및 권장사항**

### **현재 상태 평가: ⚠️ 주의 필요**
- **인증 시스템**: ✅ 양호
- **API 안정성**: ❌ 심각한 문제
- **성능**: ❌ 목표 대비 크게 미달
- **확장성**: ❓ 평가 불가 (서버 불안정)

### **즉시 조치 사항**
1. **서버 안정화 최우선**
   - 로그 분석을 통한 500 에러 원인 규명
   - 리소스 모니터링 및 스케일링
   - 데이터베이스 성능 튜닝

2. **권한 시스템 정비**
   - 테스트 계정 권한 재설정
   - API 엔드포인트 경로 검증
   - 에러 메시지 개선

3. **부하 테스트 인프라 구축**
   - Grafana 대시보드 연동
   - 실시간 알림 시스템 구축
   - 자동화된 테스트 파이프라인

### **장기 개선 계획**
1. **성능 최적화**
   - 캐시 레이어 강화 (Redis)
   - 데이터베이스 인덱스 최적화
   - API 응답 크기 최적화

2. **가용성 향상**
   - 마이크로서비스 회복탄력성 강화
   - 서킷 브레이커 패턴 도입
   - 카나리 배포 체계 구축

3. **모니터링 고도화**
   - SLO/SLI 지표 정의
   - 예측적 스케일링 구현
   - 장애 자동 복구 시스템

---

## 📞 **문제 해결 지원**

### **긴급 연락처**
- DevOps 팀: 서버 인프라 관련 이슈
- Backend 팀: API 및 데이터베이스 관련 이슈
- QA 팀: 테스트 시나리오 및 데이터 검증

### **유용한 명령어**
```bash
# 실시간 서버 상태 모니터링
watch -n 1 'curl -s -o /dev/null -w "Status: %{http_code}, Time: %{time_total}s\n" https://api.goormpopcorn.shop'

# k6 간단 테스트
k6 run --vus 1 --duration 10s -e BASE_URL=https://api.goormpopcorn.shop popcorn-complete-load-scenarios.js

# 로그 수집
kubectl logs deployment/order-service --since=1h > order-service.log
```

---

**다음 단계**: 서버 안정화 완료 후 본 가이드의 개선된 테스트 계획을 순차적으로 실행하여 목표 성능 달성을 목표로 합니다. 🚀