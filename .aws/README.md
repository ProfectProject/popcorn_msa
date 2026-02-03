# ECS Task Definition 및 배포 관리

이 디렉토리는 Goorm Popcorn 마이크로서비스의 ECS Task Definition과 배포 스크립트를 중앙 집중식으로 관리합니다.

## 📁 디렉토리 구조

```
.aws/
├── task-definitions/           # ECS Task Definition 파일들
│   ├── api-gateway.json       # API Gateway 서비스
│   ├── user-service.json      # User Service
│   ├── store-service.json     # Store Service
│   ├── order-service.json     # Order Service
│   ├── payment-service.json   # Payment Service
│   ├── qr-service.json        # QR Service
│   └── order-query.json       # Order Query Service
├── environments/              # 환경별 설정 파일
│   ├── dev.env               # 개발 환경 설정
│   └── prod.env              # 프로덕션 환경 설정
├── deploy.sh                 # 배포 스크립트
└── README.md                 # 이 문서
```

## 🚀 배포 스크립트 사용법

### 기본 사용법

```bash
# 개별 서비스 배포
./deploy.sh [service-name] [environment] [image-tag]

# 예시
./deploy.sh user-service dev latest
./deploy.sh api-gateway prod v1.2.3

# 모든 서비스 배포
./deploy.sh all dev latest
```

### 매개변수 설명

- **service-name**: 배포할 서비스 이름 또는 `all`
- **environment**: 환경 (`dev`, `prod`)
- **image-tag**: 이미지 태그 (기본값: `latest`)

### 지원되는 서비스

- `api-gateway`: API Gateway 서비스
- `user-service`: 사용자 관리 서비스
- `store-service`: 매장 관리 서비스
- `order-service`: 주문 처리 서비스
- `payment-service`: 결제 처리 서비스
- `qr-service`: QR 코드 생성 서비스
- `order-query`: 주문 조회 서비스
- `all`: 모든 서비스

## 🔧 Task Definition 구성

### 공통 설정

모든 Task Definition은 다음 공통 설정을 사용합니다:

```json
{
  "family": "goorm-popcorn-${ENVIRONMENT}-{service-name}",
  "networkMode": "awsvpc",
  "requiresCompatibilities": ["FARGATE"],
  "executionRoleArn": "arn:aws:iam::${AWS_ACCOUNT_ID}:role/goorm-popcorn-${ENVIRONMENT}-ecs-task-execution-role",
  "taskRoleArn": "arn:aws:iam::${AWS_ACCOUNT_ID}:role/goorm-popcorn-${ENVIRONMENT}-ecs-task-role"
}
```

### 환경 변수 치환

배포 스크립트는 다음 변수들을 자동으로 치환합니다:

| 변수 | 설명 | 예시 |
|------|------|------|
| `${AWS_ACCOUNT_ID}` | AWS 계정 ID | `375896310755` |
| `${ENVIRONMENT}` | 환경 | `dev`, `prod` |
| `${IMAGE_TAG}` | 이미지 태그 | `latest`, `v1.2.3` |
| `${DB_HOST}` | RDS 엔드포인트 | Terraform 출력에서 자동 획득 |
| `${DB_PORT}` | 데이터베이스 포트 | `5432` |
| `${DB_NAME}` | 데이터베이스 이름 | `goorm_popcorn_db` |
| `${DB_SECRET_ARN}` | RDS 비밀번호 Secret ARN | Terraform 출력에서 자동 획득 |
| `${REDIS_PRIMARY_ENDPOINT}` | ElastiCache 엔드포인트 | Terraform 출력에서 자동 획득 |
| `${KAFKA_BOOTSTRAP_SERVERS}` | Kafka 브로커 주소 | Terraform 출력에서 자동 획득 |

## 📊 서비스별 리소스 할당

### 개발 환경 (dev)

| 서비스 | CPU | Memory | 특징 |
|--------|-----|--------|------|
| api-gateway | 512 | 1024 | 모든 요청의 진입점 |
| user-service | 256 | 512 | 사용자 인증/관리 |
| store-service | 256 | 512 | 매장 정보 관리 |
| order-service | 512 | 1024 | 복잡한 주문 로직 |
| payment-service | 512 | 1024 | 결제 처리 |
| qr-service | 256 | 512 | QR 코드 생성 |
| order-query | 256 | 512 | 주문 조회 최적화 |

### 프로덕션 환경 (prod)

| 서비스 | CPU | Memory | 특징 |
|--------|-----|--------|------|
| api-gateway | 1024 | 2048 | 높은 트래픽 처리 |
| user-service | 512 | 1024 | 확장된 사용자 기능 |
| store-service | 512 | 1024 | 매장 관리 확장 |
| order-service | 1024 | 2048 | 복잡한 비즈니스 로직 |
| payment-service | 1024 | 2048 | 높은 안정성 요구 |
| qr-service | 256 | 512 | 경량 서비스 유지 |
| order-query | 512 | 1024 | 읽기 성능 최적화 |

## 🔍 헬스체크 설정

모든 서비스는 Spring Boot Actuator를 사용한 헬스체크를 구성합니다:

```json
{
  "healthCheck": {
    "command": ["CMD-SHELL", "curl -f http://localhost:8080/actuator/health || exit 1"],
    "interval": 30,
    "timeout": 5,
    "retries": 3,
    "startPeriod": 90
  }
}
```

### 서비스별 시작 대기 시간

- **API Gateway**: 90초 (모든 서비스 연결 대기)
- **User Service**: 60초 (데이터베이스 연결)
- **Store Service**: 60초 (데이터베이스 + S3 연결)
- **Order Service**: 120초 (데이터베이스 + Kafka 연결)
- **Payment Service**: 90초 (데이터베이스 + 외부 API 연결)
- **QR Service**: 45초 (Redis 연결)
- **Order Query**: 60초 (데이터베이스 + 캐시 연결)

## 📝 로깅 설정

### CloudWatch 로그 그룹

각 서비스별로 별도의 로그 그룹이 생성됩니다:

```
/aws/ecs/goorm-popcorn-{environment}/{service-name}
```

예시:
- `/aws/ecs/goorm-popcorn-dev/api-gateway`
- `/aws/ecs/goorm-popcorn-prod/user-service`

### 로그 레벨 설정

**개발 환경**:
- Root Level: `INFO`
- Application Level: `DEBUG`
- 보존 기간: 7일

**프로덕션 환경**:
- Root Level: `WARN`
- Application Level: `INFO`
- 보존 기간: 30일

## 🔐 보안 설정

### Secrets Manager 통합

민감한 정보는 AWS Secrets Manager를 통해 관리됩니다:

```json
{
  "secrets": [
    {
      "name": "DB_PASSWORD",
      "valueFrom": "${DB_SECRET_ARN}:password::"
    },
    {
      "name": "JWT_SECRET",
      "valueFrom": "arn:aws:secretsmanager:ap-northeast-2:375896310755:secret:goorm-popcorn-jwt-secret"
    }
  ]
}
```

### IAM 역할

**Task Execution Role**: ECR, CloudWatch, Secrets Manager 접근
**Task Role**: S3, SES, 기타 AWS 서비스 접근

## 🌐 서비스 디스커버리

AWS Cloud Map을 사용한 서비스 간 통신:

```yaml
네임스페이스: goormpopcorn.local
서비스 URL 패턴: http://{service-name}.goormpopcorn.local:8080

예시:
- http://user-service.goormpopcorn.local:8080
- http://order-service.goormpopcorn.local:8080
```

## 🚀 배포 프로세스

### 1. 인프라 정보 자동 획득

배포 스크립트는 Terraform 출력에서 인프라 정보를 자동으로 가져옵니다:

```bash
# Terraform 출력 디렉토리 경로
terraform_dir="../popcorn-terraform-feature/envs/${ENVIRONMENT}"

# 자동 획득되는 정보
DB_HOST=$(terraform output -raw rds_endpoint)
REDIS_PRIMARY_ENDPOINT=$(terraform output -raw elasticache_primary_endpoint)
KAFKA_BOOTSTRAP_SERVERS=$(terraform output -raw kafka_bootstrap_servers)
```

### 2. Task Definition 등록

```bash
# 1. 환경 변수 치환
# 2. Task Definition 등록
# 3. ECS 서비스 업데이트
# 4. 배포 상태 확인
```

### 3. 배포 순서 (다중 서비스 배포 시)

```yaml
Group 1 (병렬):
  - user-service
  - qr-service

Group 2 (병렬):
  - store-service
  - order-service
  - order-query

Group 3 (순차):
  - payment-service
  - api-gateway
```

## 🔧 사용 예시

### 개발 환경 배포

```bash
# 단일 서비스
./deploy.sh user-service dev feature-auth-123

# 모든 서비스
./deploy.sh all dev latest
```

### 프로덕션 배포

```bash
# 단일 서비스 (신중하게)
./deploy.sh payment-service prod v1.2.3

# 단계적 배포 (권장)
./deploy.sh user-service prod v1.2.3
./deploy.sh store-service prod v1.2.3
./deploy.sh order-service prod v1.2.3
./deploy.sh payment-service prod v1.2.3
./deploy.sh api-gateway prod v1.2.3
```

## 🐛 문제 해결

### 일반적인 오류

**1. Terraform 출력 오류**
```bash
Error: terraform output failed

해결방법:
1. Terraform 디렉토리 경로 확인
2. terraform init 및 apply 상태 확인
3. 수동으로 환경 변수 설정
```

**2. Task Definition 등록 실패**
```bash
Error: Invalid task definition

해결방법:
1. JSON 구문 검사
2. IAM 권한 확인
3. ECR 이미지 존재 확인
```

**3. 서비스 업데이트 실패**
```bash
Error: Service not found

해결방법:
1. ECS 클러스터 및 서비스 존재 확인
2. 서비스 이름 일치 확인
3. 네트워크 설정 확인
```

### 디버깅 명령어

```bash
# ECS 서비스 상태 확인
aws ecs describe-services \
  --cluster goorm-popcorn-dev-cluster \
  --services goorm-popcorn-dev-user-service

# Task Definition 확인
aws ecs describe-task-definition \
  --task-definition goorm-popcorn-dev-user-service

# CloudWatch 로그 확인
aws logs describe-log-streams \
  --log-group-name "/aws/ecs/goorm-popcorn-dev/user-service"
```

## 📈 모니터링

### CloudWatch 메트릭

자동으로 수집되는 메트릭:
- CPU 사용률
- 메모리 사용률
- 네트워크 I/O
- 태스크 수

### 커스텀 메트릭

애플리케이션에서 전송하는 메트릭:
- API 응답 시간
- 에러율
- 비즈니스 메트릭 (주문 수, 결제 성공률 등)

## 🔄 CI/CD 통합

이 배포 스크립트는 GitHub Actions 워크플로우에서 사용됩니다:

```yaml
- name: Deploy to ECS
  working-directory: .aws
  run: |
    chmod +x deploy.sh
    ./deploy.sh ${{ env.SERVICE_NAME }} ${{ env.ENVIRONMENT }} ${{ github.sha }}
```

자세한 CI/CD 설정은 [GitHub Actions 가이드](../.github/workflows/README.md)를 참조하세요.

## 📚 관련 문서

- [GitHub Actions CI/CD 워크플로우](../.github/workflows/README.md)
- [CI/CD 설정 가이드](../.github/SETUP.md)
- [ECS Task Definition 관리 가이드](../../popcorn-terraform-feature/docs/ecs-task-definition-management.md)
- [CI/CD 아키텍처 설계](../../popcorn-terraform/docs/cicd-architecture.md)

---

**문서 버전**: 1.0  
**최종 업데이트**: 2026-01-26  
**작성자**: DevOps Team