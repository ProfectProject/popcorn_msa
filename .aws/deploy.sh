#!/bin/bash

# ECS Task Definition 배포 스크립트
# 사용법: ./deploy.sh [service-name] [environment] [image-tag]

set -e

# 기본값 설정
ENVIRONMENT=${2:-dev}
IMAGE_TAG=${3:-latest}
SERVICE_NAME=${1}
AWS_REGION="ap-northeast-2"
AWS_ACCOUNT_ID="375896310755"

# 색상 코드
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 로그 함수
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 도움말 출력
show_help() {
    echo "ECS Task Definition 배포 스크립트"
    echo ""
    echo "사용법:"
    echo "  $0 [service-name] [environment] [image-tag]"
    echo ""
    echo "매개변수:"
    echo "  service-name  : 배포할 서비스 이름"
    echo "  environment   : 환경 (dev, prod) [기본값: dev]"
    echo "  image-tag     : 이미지 태그 [기본값: latest]"
    echo ""
    echo "예시:"
    echo "  $0 api-gateway dev latest"
    echo "  $0 user-service prod v1.2.3"
    echo "  $0 all dev feature-auth-abc123"
    echo ""
    echo "지원되는 서비스:"
    echo "  - api-gateway"
    echo "  - user-service"
    echo "  - store-service"
    echo "  - order-service"
    echo "  - payment-service"
    echo "  - checkin-service"
    echo "  - order-query"
    echo "  - all (모든 서비스)"
}

escape_sed() {
    printf '%s' "$1" | sed -e 's/[\\/&|]/\\&/g'
}

# 인프라 정보 가져오기 (Terraform 사용 안 함)
get_infrastructure_info() {
    log_info "인프라 정보 가져오는 중..."

    DB_HOST=${DB_HOST_TASK:-""}
    DB_PORT=${DB_PORT_TASK:-"5432"}
    DB_NAME=${DB_NAME_TASK:-""}
    DB_USER=${DB_USER_TASK:-""}
    DB_PASSWORD=${DB_PASSWORD_TASK:-""}
    REDIS_PRIMARY_ENDPOINT=${REDIS_HOST_TASK:-""}
    REDIS_PORT=${REDIS_PORT_TASK:-"6379"}
    KAFKA_BOOTSTRAP_SERVERS=${KAFKA_BOOTSTRAP_SERVERS_TASK:-""}
    JWT_SECRET=${JWT_SECRET_TASK:-""}
    PASSPORT_SECRET=${PASSPORT_SECRET_TASK:-""}
    GATEWAY_URL=${GATEWAY_URL_TASK:-""}
    GATEWAY_SERVICE_URL=${GATEWAY_SERVICE_URL_TASK:-""}
    APP_GATEWAY_URL=${APP_GATEWAY_URL_TASK:-""}

    USER_AUTH_DB_USERNAME=${USER_AUTH_DB_USERNAME_TASK:-""}
    USER_AUTH_DB_PASSWORD=${USER_AUTH_DB_PASSWORD_TASK:-""}
    USER_AUTH_FLYWAY_USERNAME=${USER_AUTH_FLYWAY_USERNAME_TASK:-""}
    USER_AUTH_FLYWAY_PASSWORD=${USER_AUTH_FLYWAY_PASSWORD_TASK:-""}

    STORE_DB_USERNAME=${STORE_DB_USERNAME_TASK:-""}
    STORE_DB_PASSWORD=${STORE_DB_PASSWORD_TASK:-""}
    STORE_FLYWAY_USERNAME=${STORE_FLYWAY_USERNAME_TASK:-""}
    STORE_FLYWAY_PASSWORD=${STORE_FLYWAY_PASSWORD_TASK:-""}

    ORDER_DB_USERNAME=${ORDER_DB_USERNAME_TASK:-""}
    ORDER_DB_PASSWORD=${ORDER_DB_PASSWORD_TASK:-""}
    ORDER_FLYWAY_USERNAME=${ORDER_FLYWAY_USERNAME_TASK:-""}
    ORDER_FLYWAY_PASSWORD=${ORDER_FLYWAY_PASSWORD_TASK:-""}

    PAYMENT_DB_USERNAME=${PAYMENT_DB_USERNAME_TASK:-""}
    PAYMENT_DB_PASSWORD=${PAYMENT_DB_PASSWORD_TASK:-""}
    PAYMENT_FLYWAY_USERNAME=${PAYMENT_FLYWAY_USERNAME_TASK:-""}
    PAYMENT_FLYWAY_PASSWORD=${PAYMENT_FLYWAY_PASSWORD_TASK:-""}
    TOSS_PAYMENTS_CLIENT_KEY=${TOSS_PAYMENTS_CLIENT_KEY_TASK:-""}
    TOSS_PAYMENTS_SECRET_KEY=${TOSS_PAYMENTS_SECRET_KEY_TASK:-""}

    QR_DB_USERNAME=${QR_DB_USERNAME_TASK:-""}
    QR_DB_PASSWORD=${QR_DB_PASSWORD_TASK:-""}
    QR_FLYWAY_USERNAME=${QR_FLYWAY_USERNAME_TASK:-""}
    QR_FLYWAY_PASSWORD=${QR_FLYWAY_PASSWORD_TASK:-""}

    ORDER_QUERY_DB_USERNAME=${ORDER_QUERY_DB_USERNAME_TASK:-""}
    ORDER_QUERY_DB_PASSWORD=${ORDER_QUERY_DB_PASSWORD_TASK:-""}
    ORDER_QUERY_FLYWAY_USERNAME=${ORDER_QUERY_FLYWAY_USERNAME_TASK:-""}
    ORDER_QUERY_FLYWAY_PASSWORD=${ORDER_QUERY_FLYWAY_PASSWORD_TASK:-""}

    if [[ -z "$DB_HOST" || -z "$DB_NAME" || -z "$DB_USER" || -z "$DB_PASSWORD" ]]; then
        log_error "필수 DB 환경 변수 누락: DB_HOST_TASK/DB_NAME_TASK/DB_USER_TASK/DB_PASSWORD_TASK"
        exit 1
    fi
}

# 환경 변수 치환 함수
substitute_variables() {
    local task_def_file=$1
    local temp_file="/tmp/task-definition-${SERVICE_NAME}-${ENVIRONMENT}.json"
    
    local esc_db_host esc_db_port esc_db_name esc_db_user esc_db_password esc_redis esc_redis_port esc_kafka
    local esc_jwt_secret esc_passport_secret esc_gateway_url esc_gateway_service_url esc_app_gateway_url
    local esc_user_auth_db_username esc_user_auth_db_password esc_user_auth_flyway_username esc_user_auth_flyway_password
    local esc_store_db_username esc_store_db_password esc_store_flyway_username esc_store_flyway_password
    local esc_order_db_username esc_order_db_password esc_order_flyway_username esc_order_flyway_password
    local esc_payment_db_username esc_payment_db_password esc_payment_flyway_username esc_payment_flyway_password
    local esc_toss_payments_client_key esc_toss_payments_secret_key
    local esc_qr_db_username esc_qr_db_password esc_qr_flyway_password
    local esc_order_query_db_username esc_order_query_db_password esc_order_query_flyway_username esc_order_query_flyway_password
    esc_db_host=$(escape_sed "$DB_HOST")
    esc_db_port=$(escape_sed "$DB_PORT")
    esc_db_name=$(escape_sed "$DB_NAME")
    esc_db_user=$(escape_sed "$DB_USER")
    esc_db_password=$(escape_sed "$DB_PASSWORD")
    esc_redis=$(escape_sed "$REDIS_PRIMARY_ENDPOINT")
    esc_redis_port=$(escape_sed "$REDIS_PORT")
    esc_kafka=$(escape_sed "$KAFKA_BOOTSTRAP_SERVERS")
    esc_jwt_secret=$(escape_sed "$JWT_SECRET")
    esc_passport_secret=$(escape_sed "$PASSPORT_SECRET")
    esc_gateway_url=$(escape_sed "$GATEWAY_URL")
    esc_gateway_service_url=$(escape_sed "$GATEWAY_SERVICE_URL")
    esc_app_gateway_url=$(escape_sed "$APP_GATEWAY_URL")
    esc_user_auth_db_username=$(escape_sed "$USER_AUTH_DB_USERNAME")
    esc_user_auth_db_password=$(escape_sed "$USER_AUTH_DB_PASSWORD")
    esc_user_auth_flyway_username=$(escape_sed "$USER_AUTH_FLYWAY_USERNAME")
    esc_user_auth_flyway_password=$(escape_sed "$USER_AUTH_FLYWAY_PASSWORD")
    esc_store_db_username=$(escape_sed "$STORE_DB_USERNAME")
    esc_store_db_password=$(escape_sed "$STORE_DB_PASSWORD")
    esc_store_flyway_username=$(escape_sed "$STORE_FLYWAY_USERNAME")
    esc_store_flyway_password=$(escape_sed "$STORE_FLYWAY_PASSWORD")
    esc_order_db_username=$(escape_sed "$ORDER_DB_USERNAME")
    esc_order_db_password=$(escape_sed "$ORDER_DB_PASSWORD")
    esc_order_flyway_username=$(escape_sed "$ORDER_FLYWAY_USERNAME")
    esc_order_flyway_password=$(escape_sed "$ORDER_FLYWAY_PASSWORD")
    esc_payment_db_username=$(escape_sed "$PAYMENT_DB_USERNAME")
    esc_payment_db_password=$(escape_sed "$PAYMENT_DB_PASSWORD")
    esc_payment_flyway_username=$(escape_sed "$PAYMENT_FLYWAY_USERNAME")
    esc_payment_flyway_password=$(escape_sed "$PAYMENT_FLYWAY_PASSWORD")
    esc_toss_payments_client_key=$(escape_sed "$TOSS_PAYMENTS_CLIENT_KEY")
    esc_toss_payments_secret_key=$(escape_sed "$TOSS_PAYMENTS_SECRET_KEY")
    esc_qr_db_username=$(escape_sed "$QR_DB_USERNAME")
    esc_qr_db_password=$(escape_sed "$QR_DB_PASSWORD")
    esc_qr_flyway_username=$(escape_sed "$QR_FLYWAY_USERNAME")
    esc_qr_flyway_password=$(escape_sed "$QR_FLYWAY_PASSWORD")
    esc_order_query_db_username=$(escape_sed "$ORDER_QUERY_DB_USERNAME")
    esc_order_query_db_password=$(escape_sed "$ORDER_QUERY_DB_PASSWORD")
    esc_order_query_flyway_username=$(escape_sed "$ORDER_QUERY_FLYWAY_USERNAME")
    esc_order_query_flyway_password=$(escape_sed "$ORDER_QUERY_FLYWAY_PASSWORD")

    # 환경 변수 치환
    sed -e "s|\${AWS_ACCOUNT_ID}|${AWS_ACCOUNT_ID}|g" \
        -e "s|\${ENVIRONMENT}|${ENVIRONMENT}|g" \
        -e "s|\${IMAGE_TAG}|${IMAGE_TAG}|g" \
        -e "s|\${DB_HOST}|${esc_db_host}|g" \
        -e "s|\${DB_PORT}|${esc_db_port}|g" \
        -e "s|\${DB_NAME}|${esc_db_name}|g" \
        -e "s|\${DB_USER}|${esc_db_user}|g" \
        -e "s|\${DB_PASSWORD}|${esc_db_password}|g" \
        -e "s|\${REDIS_PRIMARY_ENDPOINT}|${esc_redis}|g" \
        -e "s|\${REDIS_HOST}|${esc_redis}|g" \
        -e "s|\${REDIS_PORT}|${esc_redis_port}|g" \
        -e "s|\${KAFKA_BOOTSTRAP_SERVERS}|${esc_kafka}|g" \
        -e "s|\${JWT_SECRET}|${esc_jwt_secret}|g" \
        -e "s|\${PASSPORT_SECRET}|${esc_passport_secret}|g" \
        -e "s|\${GATEWAY_URL}|${esc_gateway_url}|g" \
        -e "s|\${GATEWAY_SERVICE_URL}|${esc_gateway_service_url}|g" \
        -e "s|\${APP_GATEWAY_URL}|${esc_app_gateway_url}|g" \
        -e "s|\${USER_AUTH_DB_USERNAME}|${esc_user_auth_db_username}|g" \
        -e "s|\${USER_AUTH_DB_PASSWORD}|${esc_user_auth_db_password}|g" \
        -e "s|\${USER_AUTH_FLYWAY_USERNAME}|${esc_user_auth_flyway_username}|g" \
        -e "s|\${USER_AUTH_FLYWAY_PASSWORD}|${esc_user_auth_flyway_password}|g" \
        -e "s|\${STORE_DB_USERNAME}|${esc_store_db_username}|g" \
        -e "s|\${STORE_DB_PASSWORD}|${esc_store_db_password}|g" \
        -e "s|\${STORE_FLYWAY_USERNAME}|${esc_store_flyway_username}|g" \
        -e "s|\${STORE_FLYWAY_PASSWORD}|${esc_store_flyway_password}|g" \
        -e "s|\${ORDER_DB_USERNAME}|${esc_order_db_username}|g" \
        -e "s|\${ORDER_DB_PASSWORD}|${esc_order_db_password}|g" \
        -e "s|\${ORDER_FLYWAY_USERNAME}|${esc_order_flyway_username}|g" \
        -e "s|\${ORDER_FLYWAY_PASSWORD}|${esc_order_flyway_password}|g" \
        -e "s|\${PAYMENT_DB_USERNAME}|${esc_payment_db_username}|g" \
        -e "s|\${PAYMENT_DB_PASSWORD}|${esc_payment_db_password}|g" \
        -e "s|\${PAYMENT_FLYWAY_USERNAME}|${esc_payment_flyway_username}|g" \
        -e "s|\${PAYMENT_FLYWAY_PASSWORD}|${esc_payment_flyway_password}|g" \
        -e "s|\${TOSS_PAYMENTS_CLIENT_KEY}|${esc_toss_payments_client_key}|g" \
        -e "s|\${TOSS_PAYMENTS_SECRET_KEY}|${esc_toss_payments_secret_key}|g" \
        -e "s|\${QR_DB_USERNAME}|${esc_qr_db_username}|g" \
        -e "s|\${QR_DB_PASSWORD}|${esc_qr_db_password}|g" \
        -e "s|\${QR_FLYWAY_USERNAME}|${esc_qr_flyway_username}|g" \
        -e "s|\${QR_FLYWAY_PASSWORD}|${esc_qr_flyway_password}|g" \
        -e "s|\${ORDER_QUERY_DB_USERNAME}|${esc_order_query_db_username}|g" \
        -e "s|\${ORDER_QUERY_DB_PASSWORD}|${esc_order_query_db_password}|g" \
        -e "s|\${ORDER_QUERY_FLYWAY_USERNAME}|${esc_order_query_flyway_username}|g" \
        -e "s|\${ORDER_QUERY_FLYWAY_PASSWORD}|${esc_order_query_flyway_password}|g" \
        "$task_def_file" > "$temp_file"
    
    echo "$temp_file"
}

# Task Definition 등록 함수
register_task_definition() {
    local service=$1
    local task_def_file="task-definitions/${service}.json"
    
    if [[ ! -f "$task_def_file" ]]; then
        log_error "Task Definition 파일을 찾을 수 없습니다: $task_def_file"
        return 1
    fi
    
    log_info "Task Definition 등록 중: $service"
    
    # 환경 변수 치환
    local processed_file=$(substitute_variables "$task_def_file")
    
    # Task Definition 등록
    local task_def_arn=$(aws ecs register-task-definition \
        --region "$AWS_REGION" \
        --cli-input-json "file://${processed_file}" \
        --query 'taskDefinition.taskDefinitionArn' \
        --output text)
    
    if [[ $? -eq 0 ]]; then
        log_success "Task Definition 등록 완료: $task_def_arn"
        
        # ECS 서비스 업데이트
        local cluster_name="goorm-popcorn-${ENVIRONMENT}-cluster"
        local service_name="goorm-popcorn-${ENVIRONMENT}-${service}"
        
        log_info "ECS 서비스 업데이트 중: $service_name"
        
        aws ecs update-service \
            --region "$AWS_REGION" \
            --cluster "$cluster_name" \
            --service "$service_name" \
            --task-definition "$task_def_arn" \
            --query 'service.serviceName' \
            --output text > /dev/null
        
        if [[ $? -eq 0 ]]; then
            log_success "ECS 서비스 업데이트 완료: $service_name"
        else
            log_warning "ECS 서비스 업데이트 실패: $service_name (Task Definition은 등록됨)"
        fi
    else
        log_error "Task Definition 등록 실패: $service"
        return 1
    fi
    
    # 임시 파일 정리
    rm -f "$processed_file"
}

# 메인 실행 로직
main() {
    # 도움말 확인
    if [[ "$1" == "-h" || "$1" == "--help" ]]; then
        show_help
        exit 0
    fi
    
    # 매개변수 확인
    if [[ -z "$SERVICE_NAME" ]]; then
        log_error "서비스 이름을 지정해주세요."
        show_help
        exit 1
    fi
    
    # AWS CLI 확인
    if ! command -v aws &> /dev/null; then
        log_error "AWS CLI가 설치되어 있지 않습니다."
        exit 1
    fi
    
    # 스크립트 디렉토리로 이동
    cd "$(dirname "$0")"
    
    log_info "ECS Task Definition 배포 시작"
    log_info "환경: $ENVIRONMENT"
    log_info "이미지 태그: $IMAGE_TAG"
    log_info "AWS 계정: $AWS_ACCOUNT_ID"
    
    # 인프라 정보 가져오기
    get_infrastructure_info
    
    # 서비스 목록
    local services=("api-gateway" "user-service" "store-service" "order-service" "payment-service" "checkin-service" "order-query")
    
    if [[ "$SERVICE_NAME" == "all" ]]; then
        log_info "모든 서비스 배포 중..."
        for service in "${services[@]}"; do
            register_task_definition "$service"
        done
    else
        # 서비스 이름 유효성 검사
        if [[ ! " ${services[@]} " =~ " ${SERVICE_NAME} " ]]; then
            log_error "지원되지 않는 서비스 이름: $SERVICE_NAME"
            log_info "지원되는 서비스: ${services[*]}"
            exit 1
        fi
        
        register_task_definition "$SERVICE_NAME"
    fi
    
    log_success "배포 완료!"
}

# 스크립트 실행
main "$@"
