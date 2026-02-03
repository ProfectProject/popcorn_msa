# 서비스별 CI/CD 트리거 가이드

## 📋 개요

각 서비스는 독립적으로 CI/CD가 실행되며, 해당 서비스의 코드나 설정이 변경될 때만 워크플로우가 트리거됩니다.

## 🎯 서비스별 트리거 조건

### 1. Backend Service

**트리거 파일:**
```yaml
워크플로우: backend-service.yml (삭제됨)
트리거 조건: 더 이상 사용되지 않음
```

### 2. User Service

**트리거 파일:**
```yaml
워크플로우: user-service.yml
트리거 조건:
  - users/** (모든 users 디렉토리 파일)
  - .aws/task-definitions/user-service.json
  - .github/workflows/user-service.yml
```

### 3. Store Service

**트리거 파일:**
```yaml
워크플로우: store-service.yml
트리거 조건:
  - stores/** (모든 stores 디렉토리 파일)
  - .aws/task-definitions/store-service.json
  - .github/workflows/store-service.yml
```

### 4. Order Service

**트리거 파일:**
```yaml
워크플로우: order-service.yml
트리거 조건:
  - order/** (모든 order 디렉토리 파일)
  - .aws/task-definitions/order-service.json
  - .github/workflows/order-service.yml
```

### 5. Order Query Service

**트리거 파일:**
```yaml
워크플로우: order-query.yml
트리거 조건:
  - orderQuery/** (모든 orderQuery 디렉토리 파일)
  - .aws/task-definitions/order-query.json
  - .github/workflows/order-query.yml
```

### 6. Payment Service

**트리거 파일:**
```yaml
워크플로우: payment-service.yml
트리거 조건:
  - payment/** (모든 payment 디렉토리 파일)
  - .aws/task-definitions/payment-service.json
  - .github/workflows/payment-service.yml
```

### 7. API Gateway

**트리거 파일:**
```yaml
워크플로우: api-gateway.yml
트리거 조건:
  - gateway/** (모든 gateway 디렉토리 파일)
  - .aws/task-definitions/api-gateway.json
  - .github/workflows/api-gateway.yml
```

### 8. CheckIn Service

**트리거 파일:**
```yaml
워크플로우: checkin-service.yml
트리거 조건:
  - checkIns/** (모든 checkIns 디렉토리 파일)
  - .aws/task-definitions/checkin-service.json
  - .github/workflows/checkin-service.yml
```

## 🔄 통합 워크플로우 (unified-cicd.yml)

통합 워크플로우는 다음 경우에만 실행됩니다:

### 자동 실행 조건

```yaml
1. 수동 실행 (workflow_dispatch)
2. 커밋 메시지에 [unified] 또는 [multi] 포함
3. 여러 서비스 동시 변경 시 (선택적)
```

### 사용 예시

```bash
# 통합 워크플로우 실행을 원하는 경우
git commit -m "feat: 여러 서비스 업데이트 [unified]"

# 또는
git commit -m "fix: 전체 시스템 수정 [multi]"

# 개별 서비스만 실행하려는 경우 (기본)
git commit -m "feat: 사용자 인증 기능 추가"
```

## 📊 실행 시나리오 예시

### 시나리오 1: 단일 서비스 수정

```bash
# 파일 수정
echo "// 새로운 기능" >> checkIns/src/main/java/CheckInService.java

# 커밋 & 푸시
git add checkIns/
git commit -m "feat: 체크인 QR 기능 추가"
git push origin develop

# 결과: checkin-service.yml만 실행 ✅
# 다른 워크플로우는 실행되지 않음 ❌
```

### 시나리오 2: Task Definition 수정

```bash
# Task Definition 수정
vim .aws/task-definitions/checkin-service.json

# 커밋 & 푸시
git add .aws/task-definitions/checkin-service.json
git commit -m "config: checkin service 메모리 증가"
git push origin develop

# 결과: checkin-service.yml만 실행 ✅
```

### 시나리오 3: 여러 서비스 동시 수정

```bash
# 여러 서비스 수정
echo "// API 변경" >> users/src/main/java/UserController.java
echo "// API 변경" >> order/src/main/java/OrderController.java

# 개별 실행을 원하는 경우
git add users/ order/
git commit -m "feat: API 스펙 변경"
git push origin develop

# 결과: user-service.yml과 order-service.yml 각각 실행 ✅

# 통합 실행을 원하는 경우
git commit -m "feat: API 스펙 변경 [unified]"
git push origin develop

# 결과: unified-cicd.yml만 실행 ✅
```

### 시나리오 4: 공통 파일 수정

```bash
# 공통 파일 수정 (예: README, 문서 등)
echo "# 업데이트" >> README.md

# 커밋 & 푸시
git add README.md
git commit -m "docs: README 업데이트"
git push origin develop

# 결과: 어떤 워크플로우도 실행되지 않음 ✅
# (서비스 코드가 변경되지 않았으므로)
```

## 🔍 트리거 확인 방법

### 1. GitHub Actions 탭에서 확인

1. GitHub 저장소 → Actions 탭
2. 실행된 워크플로우 목록 확인
3. 예상한 워크플로우만 실행되었는지 확인

### 2. 로컬에서 미리 확인

```bash
# 변경된 파일 확인
git diff --name-only HEAD~1 HEAD

# 특정 패턴 매칭 테스트
git diff --name-only HEAD~1 HEAD | grep "^users/"
```

### 3. 워크플로우 로그에서 확인

```yaml
# 워크플로우 실행 시 로그에서 확인 가능
- name: Show triggered files
  run: |
    echo "Changed files:"
    git diff --name-only ${{ github.event.before }} ${{ github.sha }}
```

## ⚠️ 주의사항

### 1. 의존성 있는 변경사항

```yaml
주의 상황:
  - 공통 라이브러리 변경 시
  - API 스펙 변경 시
  - 데이터베이스 스키마 변경 시

해결 방법:
  - [unified] 태그 사용
  - 수동으로 통합 워크플로우 실행
  - 의존성 순서 고려한 배포
```

### 2. 긴급 상황

```yaml
긴급 배포 시:
  - 개별 서비스 워크플로우 사용 (더 빠름)
  - 해당 서비스 디렉토리만 수정
  - 불필요한 파일 변경 피하기
```

### 3. 대규모 리팩토링

```yaml
대규모 변경 시:
  - [unified] 태그 사용 권장
  - 의존성 순서 고려
  - 스테이징 환경에서 충분한 테스트
```

## 🛠️ 커스터마이징

### 트리거 조건 수정

특정 파일을 제외하고 싶은 경우:

```yaml
on:
  push:
    branches: [develop, main]
    paths:
      - 'users/**'
      - '!users/docs/**'  # docs 디렉토리 제외
      - '!users/**/*.md'  # 마크다운 파일 제외
```

### 추가 트리거 조건

특정 브랜치에서만 실행하고 싶은 경우:

```yaml
on:
  push:
    branches: [develop, main, 'feature/user-*']  # user 관련 feature 브랜치
    paths:
      - 'users/**'
```

## 📈 모니터링

### 실행 빈도 확인

```bash
# 최근 워크플로우 실행 통계
gh run list --limit 50 --json workflowName,conclusion,createdAt
```

### 비용 최적화

```yaml
최적화 팁:
  - 불필요한 파일 변경 피하기
  - 테스트 파일만 변경 시 테스트만 실행
  - 문서 변경 시 CI/CD 실행 방지
```

---

**문서 버전**: 1.0  
**최종 업데이트**: 2024-01-26  
**작성자**: DevOps Team