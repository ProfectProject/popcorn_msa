# Popcorn 부하테스트 상세 결과 보고서

- 작성일시: 2026-02-24 (KST)
- 테스트 위치: 로컬 `k6`
- 대상 서버: `https://api.goormpopcorn.shop`
- 적용 스크립트: `popcorn-load.js`, `popcorn-query-load.js`, `popcorn-complete-load-scenarios.js`

---

## 1. 요청사항 반영 내역

### 1.1 popup_detail 503 대응 코드 수정

#### A) Redis 실패 내성 강화
- 파일: `stores/src/main/java/com/popcorn/store/domain/popup/service/PopupService.java`
- 변경:
  - `getRemainingCapacityFromRedisBulk()`에 예외 처리 추가
  - `getRemainingCapacityFromRedis()`에 예외 처리 추가
  - Redis timeout/연결 실패 시에도 popup detail 응답이 실패하지 않고 DB 값 기반으로 응답 지속

#### B) hot-path DB 추가 조회 억제
- 파일: `stores/src/main/java/com/popcorn/store/domain/popup/service/PopupService.java`
- 변경:
  - `ENABLE_DB_REMAINING_FALLBACK=false` 기본 적용
  - 부분 캐시 미스 시 추가 DB fallback 조회를 기본 비활성화하여 burst 구간 DB 부하 감소

#### C) 운영 프로파일 튜닝
- 파일: `stores/src/main/resources/security/application-prod.yml`
- 변경:
  - Hikari pool 설정 명시(`maxPoolSize=30`, `minIdle=10`, `connection-timeout=3000ms`, `validation-timeout=1000ms`)
  - 운영 로그 레벨 완화(DEBUG -> INFO/WARN)로 I/O 오버헤드 감소

---

### 1.2 orderQuery 장애 수정

#### A) Security filter 체인 순서 오류 수정
- 파일: `orderQuery/src/main/java/com/example/orderquery/global/config/SecurityConfig.java`
- 변경:
  - `HeaderAuthenticationFilter` 기준 상대 정렬에서 발생하던 필터 순서 예외 해결
  - `UsernamePasswordAuthenticationFilter` 기준으로 before/after 재배치

#### B) item_type 길이 이슈 방어 마이그레이션 추가
- 파일: `orderQuery/src/main/resources/db/migration/schema/V4__ensure_item_type_length.sql`
- 변경:
  - `order_query.popup_order_items_view.item_type`가 20 미만인 경우 `varchar(20)`으로 확장
  - 기존 데이터/환경 편차에 대한 안전장치

---

### 1.3 k6 스크립트 정리 (요청사항 반영)
- 유지 파일:
  - `popcorn-load.js`
  - `popcorn-query-load.js`
  - `popcorn-complete-load-scenarios.js`
- 삭제 파일:
  - `local-load-test.js`
  - `fixed-local-load-test.js`
  - `multi-user-load-test.js`
  - `fixed-multi-user-load-test.js`
  - `load-test/popcorn-load-cached.js`
  - `load-test/test-login.js`
  - `load-test/popcorn-query-load.js`
  - `load-test/user-only-test.js`
  - `load-test/popcorn-load.js`
  - `backend/k6-tests/extreme-load-test.js`
  - `backend/k6-tests/payment-stress-test.js`
  - `backend/k6-tests/ultimate-extreme-test.js`

---

## 2. 테스트 실행 결과

## 2.1 운영 시나리오 (3단계 축약 실행)

### 실행 조건
- 스크립트: `popcorn-complete-load-scenarios.js`
- 모드: `MODE=operational`
- 계정: `popcorn1`, `popcorn3`, `popcorn4`
- 단계: `steady(1m) -> rush(1m) -> spike(1m)`

### 결과 요약
- `http_req_duration p95 = 205.34ms` (목표 500ms 이내 달성)
- `t_popup_detail p95 = 160.31ms` (양호)
- `t_order_create p95 = 986.17ms` (목표 이내)
- `r_fail = 2.37%` (목표 `<1%` 실패)

### 실패 분포
- `popup_detail 2xx`: `598 성공 / 15 실패`
- 주요 실패 형태: 간헐 `503`/timeout burst

### 해석
- 평균 성능(p95)은 충분히 빠르지만, burst 구간의 간헐적 오류 때문에 `에러율` 목표를 미달.

---

## 2.2 Query 시나리오 (기존 통합 스크립트)

### 1차 결과(수정 전)
- `r_query_fail = 94.62%`
- 원인: Query 시나리오가 store 경로로 트래픽을 보내는 부분 + timeout 대량 발생

### 2차 결과(경로 수정 후)
- `r_query_fail = 100%`
- 실패 코드: `404` 집중
- 호출 경로:
  - `/api/order-query/v1/popups/{popupId}/stock`
  - `/api/order-query/v1/users/{userId}/orders`

### 해석
- 성능 문제 이전에, 게이트웨이에서 해당 order-query 경로가 미노출/불일치 상태.
- 즉, 현재 Query 실패는 서버 성능이 아니라 **라우팅/계약 미일치**가 1차 원인.

---

## 2.3 신규 query 스크립트 스모크

### 실행 조건
- 스크립트: `popcorn-query-load.js`
- 모드: `MODE=mix`
- 부하: `MIX_RPS=10`, `MIX_DURATION=30s`

### 결과
- `http_req_duration p95 = 47.09ms`
- `r_query_fail = 100%`
- 실패 원인: `404` (경로 미노출)

### 해석
- 스크립트 동작 자체는 정상, 응답속도는 빠르지만 API 미존재로 2xx가 전무.

---

## 3. 현재 블로커

1. `popup_detail` hot-path의 간헐 timeout/503 (에러율 목표 미달 핵심 원인)
2. order-query read endpoint gateway 라우팅 미노출(404)
3. Query 시나리오 목표 달성 불가 상태 (`r_query_fail` 임계치 초과)

---

## 4. 즉시 실행 권장 사항

1. `stores` 변경분 배포 후 동일 운영 시나리오 재측정
   - 기대: `popup_detail` 오류율 감소
2. Gateway 라우팅 점검/반영
   - `/api/order-query/v1/popups/{popupId}/stock`
   - `/api/order-query/v1/users/{userId}/orders`
3. 라우팅 반영 후 query 시나리오 재실행
   - `storm`, `mix`, `consistency` 순서

---

## 5. 참고 커맨드

### 운영 시나리오
```bash
k6 run \
  -e BASE_URL=https://api.goormpopcorn.shop \
  -e AUTH_TOKEN=YOUR_TOKEN \
  -e SCENARIO=hot \
  -e POPUP_HOT_ID=e534dfc8-23e7-4a7c-93c6-ce4ac6ec934d \
  -e POPUP_IDS=e534dfc8-23e7-4a7c-93c6-ce4ac6ec934d,6b455543-d7dd-481e-9cea-f91e20bed808 \
  popcorn-load.js
```

### Query 시나리오
```bash
k6 run \
  -e MODE=mix \
  -e BASE_URL=https://api.goormpopcorn.shop \
  -e AUTH_TOKEN=YOUR_TOKEN \
  -e POPUP_HOT_ID=e534dfc8-23e7-4a7c-93c6-ce4ac6ec934d \
  -e MIX_RPS=500 -e MIX_DURATION=10m \
  -e QUERY_P95_MS=400 \
  popcorn-query-load.js
```

