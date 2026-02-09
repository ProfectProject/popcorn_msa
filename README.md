<p align="center">
  <img src="logo.png" alt="PopCorn Logo" width="500"/>
</p>

# 🍿 PopCorn Backend



<p align="center">
  팝업 예약·주문·결제·QR·체크인까지 연결하는 오프라인 이벤트 이커머스 플랫폼입니다.
</p>

<p align="center">
  <strong>Java 17</strong> · <strong>Spring Boot 3.5.9</strong> · <strong>PostgreSQL</strong> · <strong>JPA</strong>
</p>

## Getting Started
```bash
cd backend
./gradlew bootRun
```

## Overview
- 프로젝트 기간: 2025-12-22 ~ 2026-01-09
- 목표: 정보 탐색 → 방문 예약 → 체크인 → 굿즈 주문 흐름을 하나로 연결

## Quick Links
- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs

## Team
| 김리연(팀장) | 김세헌 | 서원지 | 오채영 | 이준범 | 홍준표 |
|:---:|:---:|:---:|:---:|:---:|:---:|
| Manager 구현 CI/CD | Owner(스토어/팝업/스케줄) , Real Model 구현 및 고도화(MSA 분리, Kafka, Redis) | Order & Reservation 구현 및 Payment, Checkin 고도화(MSA 분리, Kafka, Redis) | 회원가입/로그인, 인증·인가, Gateway 구현 및 Order 고도화(MSA 분리, Kafka, Redis)| 굿즈(Merch) 및 인프라 모니터링 구축 | Product & QR, 결제(Pay) |

## Key Features
- 회원가입/로그인(JWT), 인증·인가
- Owner: 스토어 CRUD, 팝업 CRUD, 스케줄 관리
- 주문/예약 생성 및 상태 변경
- 결제 생성/승인/실패/취소, 결제 조회
- QR 발급/조회/검증, 체크인 조회

## Roles
- CUSTOMER: 예약/주문 생성, 내 예약/주문 조회, 취소 요청
- OWNER: 내 행사 예약/내 가게 주문 조회, 운영 상태 변경
- MANAGER: 할당된 store/popup 범위 내 조회/운영 변경/체크인
- ADMIN (MASTER): 전체 조회, 예외/강제 상태 변경, 감사 로그 조회


## Tech Stack
- Language: Java 17
- Framework: Spring Boot 3.5.9 (WebMVC)
- ORM: Spring Data JPA
- Database: PostgreSQL 18.1
- Migration: Flyway
- Cache: Caffeine
- API Docs: SpringDoc OpenAPI (Swagger)
- Build: Gradle

## Spring Boot Dependencies
```gradle
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-security'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    implementation 'org.flywaydb:flyway-core'
    implementation 'org.postgresql:postgresql'
    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'
}
```

### 개발 환경
- **IDE**: IntelliJ IDEA / VS Code
- **Java Version**: 17 (LTS)
- **Container**: Docker (PostgreSQL)
- **Version Control**: Git


## Project Structure
```plaintext
backend/
├── src/main/java/com/popcorn/demo
│   ├── domain
│   │   ├── auth
│   │   ├── users
│   │   ├── store
│   │   ├── popup
│   │   ├── goods
│   │   ├── order
│   │   ├── payment
│   │   ├── qr
│   │   └── checkin
│   ├── global
│   └── common
└── src/test/java/...
```

## ERD
<p align="center">
  <img src="ERD.png" alt="PopCorn ERD" width="90%"/>
</p>

## Environment
```plaintext
backend/.env
backend/.env.example
```
필수 변수:
- DB_HOST
- DB_PORT
- DB_NAME
- DB_USERNAME
- DB_PASSWORD

### 3. 애플리케이션 접속
- **서버**: http://localhost:8080
- **Swagger API 문서**: [역할별 API 문서](#-api-문서-swagger) 참조

## 🔧 레이어별 책임

| 레이어 | 책임 | 주요 컴포넌트 | Spring 어노테이션 |
|--------|------|---------------|-------------------|
| **Domain** | 비즈니스 규칙, 엔티티, 도메인 로직 | Entity, Repository Interface | `@Component` (컨트롤러용) |
| **Application** | 유스케이스 조정, 트랜잭션 관리 | UseCase, Application Service | `@Service`, `@Component` |
| **Infrastructure** | 데이터 영속성, 외부 서비스 연동 | JPA Repository, External Client | `@Repository`, `@Component` |


### Repository Pattern
- 도메인 레이어: 인터페이스 정의
- 인프라 레이어: JPA 구현체 제공

## 📊 환경별 설정

### Local (개발자 로컬)
- **Database**: PostgreSQL (localhost:5432)
- **Profile**: `local`
- **DDL**: `none` (Flyway 관리)
- **Seed 데이터**: Flyway로 자동 실행

### Dev (개발 서버)
- **Database**: 환경변수로 설정
- **Profile**: `dev`
- **DDL**: `validate`

### Prod (운영 서버)
- **Database**: 환경변수로 설정
- **Profile**: `prod`
- **DDL**: `validate`

## 🗄️ 데이터베이스 관리

### Flyway 마이그레이션
- **스키마**: `db/migration/schema/` - 테이블 구조 정의
- **Seed**: `db/migration/seed/` - 테스트/개발용 데이터

### 데이터 초기화
```bash
# 데이터베이스 초기화 (필요시)
docker exec -it popcorn-postgres psql -U postgres -d popcorn_db -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"

# 애플리케이션 재시작으로 스키마 + seed 데이터 자동 생성
./gradlew bootRun --args='--spring.profiles.active=local'
```

## 📚 API 문서 (Swagger)

### 역할별 API 문서 분리

POPCORN API는 사용자 역할에 따라 **3개의 별도 문서**로 분리되어 있습니다:

| 역할 | URL | 설명 |
|------|-----|------|
| 🛒 **고객용** | [`/swagger-ui/customer.html`](http://localhost:8080/swagger-ui/customer.html) | 고객이 사용하는 API (주문, 결제, 팝업 조회 등) |
| 🏪 **운영자용** | [`/swagger-ui/manager.html`](http://localhost:8080/swagger-ui/manager.html) | 매니저/오너가 사용하는 API (매장 관리, 승인 등) |
| 🔧 **전체** | [`/swagger-ui/admin.html`](http://localhost:8080/swagger-ui/admin.html) | 개발자용 전체 API 문서 |

### 기본 접속 URL
```bash
# 그룹 선택 가능한 기본 페이지
http://localhost:8080/swagger-ui.html

# 직접 역할별 접속
http://localhost:8080/swagger-ui/customer.html    # 고객용
http://localhost:8080/swagger-ui/manager.html     # 운영자용
http://localhost:8080/swagger-ui/admin.html       # 전체 API
```

### API 태그 구조

#### 🛒 고객용 API
```
1. Auth           🔐 고객 인증 (로그인)
2. User           👤 회원가입 및 프로필 (마이페이지, 주소 관리 등)
3. Popup          🎪 팝업 조회
4. Order          📦 내 주문 관리 (주문 생성, 조회, 취소)
5. Payments       💳 결제 관리
6. QR             📱 QR 코드 (검증)
```

#### 🏪 운영자용 API
```
1. Auth           🔐 운영자 인증
2. User           👤 사용자 관리 (회원가입만)
3. 유저매니저     👤 유저 매니저 (오너 승인, 사용자 강제 중지)
4. Popup          🎪 팝업 조회 및 관리
5. Order          📊 주문 관리 (매장 주문, 운영자 상태 변경)
6. Stores         🏪 매장 관리 (오너 스토어 관리)
7. Goods          📦 굿즈 관리
8. Inventory      📋 재고 관리
9. Checkin        ✅ 체크인 관리
10. 매니저팝업    🎪 팝업 관리 (승인, 거부, 강제 중지)
```

#### 🔧 전체 API (개발자용)
```
1. Auth           🔐 인증 관리
2. User           👤 사용자 관리
3. 유저매니저     👤 유저 매니저 (오너 승인, 사용자 관리)
4. Popup          🎪 팝업 관리
5. Order          📦 주문 관리
6. Payments       💳 결제 관리
7. QR             📱 QR 코드
8. Stores         🏪 매장 관리
9. Goods          📦 굿즈 관리
10. Inventory     📋 재고 관리
11. Checkin       ✅ 체크인 관리
12. OwnerCheckin  🔍 오너 체크인 조회
13. 매니저팝업    🎪 매니저 팝업 관리

※ 개발/테스트용 API는 제외됩니다 (chaos, extreme)
```

### 주요 특징

- **JWT 인증**: 모든 API에서 Bearer Token 방식 사용
- **역할별 분리**: 사용자 역할에 따른 API 접근 권한 분리
- **한글 태그명**: 직관적인 한글 태그로 API 그룹화
- **순서 정렬**: Auth → User 순으로 중요도에 따른 태그 배치

### 사용 예시

```bash
# 1. 고객용 API 확인 (주문, 결제 등)
curl http://localhost:8080/swagger-ui/customer.html

# 2. 운영자용 API 확인 (매장 관리, 승인 등)
curl http://localhost:8080/swagger-ui/manager.html

# 3. 개발자용 전체 API 확인
curl http://localhost:8080/swagger-ui/admin.html
```

