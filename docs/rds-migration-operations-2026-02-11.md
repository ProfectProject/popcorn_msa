# Popcorn MSA RDS 작업 정리 (2026-02-11)

## 1) 작업 목적
- 로컬 Docker PostgreSQL(`popcorn-db`, `popcorn_db`) 데이터를 AWS RDS PostgreSQL(`popcorn_prod`)로 이관
- 이관 후 데이터 검증
- 앱 계정/마이그레이터(Flyway) 계정 권한 설정
- CDC 계정(`cdc_user`) 생성 및 읽기/복제 권한 설정

## 2) 최종 아키텍처/접속 경로
- 로컬에서 RDS 직접 `psql` 접속은 불가 (RDS 프라이빗 IP: `10.x.x.x`, timeout)
- 실제 이관/조회 경로:
  - `Docker pg_dump` -> `S3 업로드` -> `VPC 내부 Lambda(popcorn-migration)` -> `RDS 실행`

## 3) 데이터 이관 실행 내역
### 3-1. 덤프 생성
- 기본 덤프: `db-backup-20260211_192819-docker-to-rds.sql`
- INSERT 기반 덤프: `db-backup-20260211_193421-docker-to-rds-inserts.sql`
  - Lambda에서 실행하기 쉽도록 `--inserts --column-inserts` 형식 사용

### 3-2. S3 업로드
- 버킷: `s3://goorm-popcorn-tfstate`
- 키: `db-backup/db-backup-20260211_193421-docker-to-rds-inserts.sql`

### 3-3. Lambda 복원 결과
- 함수: `popcorn-migration` (VPC 연결됨)
- 결과:
  - `total_statements`: `1305`
  - `executed`: `1303`
  - `skipped`: `2` (`pg_stat_statements` 관련)
  - `error_count`: `0`
  - `table_count`: `30`
  - `elapsed_sec`: `36.92`

## 4) 데이터 검증 결과
### 4-1. DB/스키마/테이블
- DB 목록: `popcorn_prod`, `postgres`, `rdsadmin`, `template0`, `template1`
- 비시스템 스키마: `checkIns`, `checkins`, `coupons`, `order_query`, `orders`, `payment`, `public`, `store`, `stores`, `user_auth`, `users`
- 전체 베이스 테이블 수: `30`

### 4-2. 주요 테이블 건수
- `orders.p_orders`: `122`
- `orders.p_order_goods`: `182`
- `payment.payments`: `0`
- `store.popups`: `62`
- `coupons.coupons`: `9`
- `user_auth.users`: `53`

### 4-3. store 데이터 샘플 확인
- `store.stores`: `17`
- `store.popups`: `62`
- `store.goods_variants`: `38`
- `store.popup_schedules`: `59`

## 5) 권한 작업 내역
## 5-1. 생성/업데이트된 앱 계정
- `coupon_app`
- `order_app`
- `payment_app`
- `store_app`
- `user_auth_app`
- `qr_app`
- `order_query_app`

## 5-2. 생성/업데이트된 Flyway(migrator) 계정
- `coupon_migrator`
- `order_migrator`
- `payment_migrator`
- `store_migrator`
- `user_auth_migrator`
- `qr_migrator`
- `order_query_migrator`

## 5-3. 부여한 권한
- 공통:
  - `GRANT CONNECT ON DATABASE popcorn_prod`
- 앱 계정:
  - 대상 스키마 `USAGE`
  - 대상 스키마 모든 테이블 `SELECT, INSERT, UPDATE, DELETE`
  - 대상 스키마 모든 시퀀스 `USAGE, SELECT, UPDATE`
  - `ALTER DEFAULT PRIVILEGES` (신규 테이블/시퀀스 기본 권한)
- migrator 계정:
  - 대상 스키마 `USAGE, CREATE`
  - 대상 스키마 모든 테이블 `ALL PRIVILEGES`
  - 대상 스키마 모든 시퀀스 `USAGE, SELECT, UPDATE`
  - `ALTER DEFAULT PRIVILEGES` 반영

## 5-4. CDC 계정
- 계정: `cdc_user`
- 적용:
  - 로그인 가능 계정 생성/업데이트
  - `GRANT CONNECT ON DATABASE popcorn_prod TO cdc_user`
  - `GRANT rds_replication TO cdc_user`
  - 주요 스키마(`coupons`, `orders`, `payment`, `store`, `stores`, `user_auth`, `checkins`, `"checkIns"`, `order_query`, `users`)에 대해
    - `USAGE`
    - 모든 테이블 `SELECT`
    - 모든 시퀀스 `SELECT`
    - `ALTER DEFAULT PRIVILEGES` (신규 객체 기본 `SELECT`)

## 6) 운영 중 자주 발생한 이슈와 해결
### 6-1. 로컬 `psql` timeout
- 증상: `connection ... failed: timeout expired`
- 원인: RDS가 프라이빗 서브넷
- 해결: VPC Lambda 경유 실행

### 6-2. bash에서 `!~` 오류
- 증상: `-bash: !~: event not found`
- 원인: bash 히스토리 확장
- 해결: `!~` 대신 `NOT LIKE 'pg_%'` 사용

### 6-3. heredoc 종료 실패
- 증상: 프롬프트가 `>` 상태로 계속 유지
- 원인: 종료 토큰(`SQL`) 앞 공백, 또는 닫는 따옴표 누락
- 해결: `SQL` 라인은 맨 앞 컬럼(공백 없음), 한 줄 쿼리 사용 권장

### 6-4. Lambda JSON 직렬화 오류
- 증상: `Object of type datetime/UUID is not JSON serializable`
- 해결: 조회 시 `::text` 캐스팅 사용

## 7) 현재 조회 방법 (권장)
- `rdsq` 함수로 Lambda 경유 SELECT 실행
- 예시:
```bash
rdsq "SELECT COUNT(*) FROM user_auth.users;"
rdsq "SELECT popup_id::text, store_id::text, title, status, created_at::text FROM store.popups ORDER BY created_at DESC LIMIT 20;"
```

## 8) 보안 메모
- 문서에 비밀번호 평문은 기록하지 않음
- 실제 자격 증명은 Secrets Manager/SSM Parameter Store로 이전 권장

## 9) 실제 데이터 확인 내역 (쿼리 + 결과)
### 9-1. 전체 테이블 목록 확인
```bash
rdsq "SELECT table_schema, table_name
      FROM information_schema.tables
      WHERE table_schema NOT IN ('pg_catalog','information_schema')
        AND table_type='BASE TABLE'
      ORDER BY table_schema, table_name;"
```
- 결과: 총 `30`개 테이블 확인
- 포함 스키마: `checkIns`, `checkins`, `coupons`, `order_query`, `orders`, `payment`, `store`, `user_auth`

### 9-2. 전체 테이블 row count 확인
```bash
rdsq "SELECT t.table_schema, t.table_name, (xpath('/row/c/text()', query_to_xml(format('SELECT count(*) AS c FROM %I.%I', t.table_schema, t.table_name), false, true, '')))[1]::text::bigint AS row_count FROM information_schema.tables t WHERE t.table_schema NOT IN ('pg_catalog','information_schema') AND t.table_type='BASE TABLE' ORDER BY t.table_schema, t.table_name;"
```
- 운영 중 heredoc 입력 오류가 자주 발생해 최종적으로 한 줄 쿼리로 확인

### 9-3. user 데이터 확인
```bash
rdsq "SELECT COUNT(*) AS user_count FROM user_auth.users;"
rdsq "SELECT user_id::text, name, phone, email, role, is_active, created_at::text AS created_at, updated_at::text AS updated_at FROM user_auth.users ORDER BY created_at DESC LIMIT 20;"
```
- 결과:
  - 총 사용자 수: `53`
  - 최근 사용자 샘플 20건 조회 성공

### 9-4. store 데이터 확인
```bash
rdsq "SELECT (SELECT COUNT(*) FROM store.stores) AS stores_cnt, (SELECT COUNT(*) FROM store.popups) AS popups_cnt, (SELECT COUNT(*) FROM store.goods_variants) AS goods_variants_cnt, (SELECT COUNT(*) FROM store.popup_schedules) AS popup_schedules_cnt;"
rdsq "SELECT store_id::text, user_id::text, store_name, status, reason, created_at::text AS created_at, updated_at::text AS updated_at FROM store.stores ORDER BY created_at DESC LIMIT 20;"
rdsq "SELECT popup_id::text, store_id::text, title, category, status, reservation_open_at::text AS reservation_open_at, created_at::text AS created_at FROM store.popups ORDER BY created_at DESC LIMIT 20;"
rdsq "SELECT goods_id::text, popup_id::text, goods_name, goods_price, stock, reservation_stock, is_active, created_at::text AS created_at FROM store.goods_variants ORDER BY created_at DESC LIMIT 20;"
```
- 결과:
  - `store.stores`: `17`
  - `store.popups`: `62`
  - `store.goods_variants`: `38`
  - `store.popup_schedules`: `59`

### 9-5. 참고 (JSON 출력 인코딩)
- Lambda JSON 출력에서 한글 문자열이 `\uXXXX` 형태로 표시될 수 있음
- 실제 DB 데이터 손상은 아니며, 클라이언트 JSON 렌더링 방식 차이
