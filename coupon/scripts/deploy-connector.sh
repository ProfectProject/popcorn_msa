#!/bin/bash

# 쿠폰 서비스 Debezium 커넥터 배포 스크립트
# Usage: ./scripts/deploy-connector.sh

set -e

CONNECT_URL="http://localhost:8084"
CONNECTOR_NAME="coupon-outbox-connector"
CONFIG_FILE="./docker/debezium/coupon-outbox-connector-fixed.json"

echo "🚀 쿠폰 서비스 Debezium 커넥터 배포 시작..."

# 1. Kafka Connect 상태 확인
echo "1️⃣ Kafka Connect 상태 확인..."
if curl -f -s ${CONNECT_URL}/connectors > /dev/null; then
    echo "✅ Kafka Connect 연결 성공"
else
    echo "❌ Kafka Connect 연결 실패 - ${CONNECT_URL} 확인 필요"
    echo "   Docker Compose 서비스가 실행 중인지 확인하세요:"
    echo "   docker-compose ps kafka-connect"
    exit 1
fi

# 2. 기존 커넥터 삭제 (있다면)
echo "2️⃣ 기존 커넥터 확인 및 삭제..."
if curl -f -s ${CONNECT_URL}/connectors/${CONNECTOR_NAME} > /dev/null; then
    echo "⚠️  기존 커넥터 발견 - 삭제 중..."
    curl -X DELETE ${CONNECT_URL}/connectors/${CONNECTOR_NAME}
    echo "🗑️  기존 커넥터 삭제 완료"
    sleep 3
else
    echo "✅ 기존 커넥터 없음"
fi

# 3. PostgreSQL 연결 테스트
echo "3️⃣ PostgreSQL 데이터베이스 연결 테스트..."
if docker exec coupon-postgresql pg_isready -U postgres > /dev/null; then
    echo "✅ PostgreSQL 연결 성공"
else
    echo "❌ PostgreSQL 연결 실패"
    echo "   docker-compose ps coupon-db로 상태를 확인하세요"
    exit 1
fi

# 4. 테이블 존재 확인
echo "4️⃣ Outbox 테이블 존재 확인..."
TABLE_EXISTS=$(docker exec coupon-postgresql psql -U postgres -d popcorn_db -t -c "SELECT EXISTS (SELECT FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'coupon_outbox_events');" | xargs)

if [ "$TABLE_EXISTS" = "t" ]; then
    echo "✅ coupon_outbox_events 테이블 존재 확인"
else
    echo "❌ coupon_outbox_events 테이블 없음"
    echo "   PostgreSQL 초기화 스크립트를 실행하세요:"
    echo "   docker exec coupon-postgresql psql -U postgres -d popcorn_db -f /docker-entrypoint-initdb.d/01-debezium-setup.sql"
    exit 1
fi

# 5. WAL 레벨 확인
echo "5️⃣ PostgreSQL WAL 레벨 확인..."
WAL_LEVEL=$(docker exec coupon-postgresql psql -U postgres -d popcorn_db -t -c "SHOW wal_level;" | xargs)
if [ "$WAL_LEVEL" = "logical" ]; then
    echo "✅ WAL 레벨: logical (CDC 가능)"
else
    echo "⚠️  WAL 레벨: $WAL_LEVEL (logical이 아님)"
    echo "   PostgreSQL 설정 파일에서 wal_level=logical로 설정하고 재시작 필요"
fi

# 6. 새 커넥터 배포
echo "6️⃣ 새 커넥터 배포..."
if [ -f "$CONFIG_FILE" ]; then
    echo "📄 설정 파일: $CONFIG_FILE"

    # 커넥터 생성
    RESPONSE=$(curl -s -X POST \
        -H "Content-Type: application/json" \
        --data @${CONFIG_FILE} \
        ${CONNECT_URL}/connectors)

    echo "📡 커넥터 생성 응답: $RESPONSE"

    if echo "$RESPONSE" | grep -q "error"; then
        echo "❌ 커넥터 생성 실패"
        echo "$RESPONSE" | jq '.' 2>/dev/null || echo "$RESPONSE"
        exit 1
    else
        echo "✅ 커넥터 생성 성공"
    fi
else
    echo "❌ 설정 파일 없음: $CONFIG_FILE"
    exit 1
fi

# 7. 커넥터 상태 확인
echo "7️⃣ 커넥터 상태 확인..."
sleep 5

STATUS_RESPONSE=$(curl -s ${CONNECT_URL}/connectors/${CONNECTOR_NAME}/status)
echo "📊 커넥터 상태:"
echo "$STATUS_RESPONSE" | jq '.' 2>/dev/null || echo "$STATUS_RESPONSE"

STATE=$(echo "$STATUS_RESPONSE" | jq -r '.connector.state' 2>/dev/null || echo "UNKNOWN")
if [ "$STATE" = "RUNNING" ]; then
    echo "✅ 커넥터 상태: RUNNING"
elif [ "$STATE" = "FAILED" ]; then
    echo "❌ 커넥터 상태: FAILED"
    echo "🔍 실패 원인:"
    echo "$STATUS_RESPONSE" | jq '.connector.trace' 2>/dev/null || echo "상세 정보 없음"
    exit 1
else
    echo "⚠️  커넥터 상태: $STATE (확인 필요)"
fi

# 8. 토픽 생성 확인
echo "8️⃣ Kafka 토픽 확인..."
sleep 3

TOPICS=$(docker exec coupon-kafka kafka-topics --bootstrap-server localhost:9092 --list | grep coupon || echo "")
if [ -n "$TOPICS" ]; then
    echo "✅ 쿠폰 관련 토픽 생성됨:"
    echo "$TOPICS"
else
    echo "⚠️  쿠폰 관련 토픽 아직 생성 안됨 (이벤트 발생 시 자동 생성)"
fi

# 9. 테스트 이벤트 생성
echo "9️⃣ 테스트 이벤트 생성..."
docker exec coupon-postgresql psql -U postgres -d popcorn_db -c "
INSERT INTO public.coupon_outbox_events (
    aggregate_type,
    aggregate_id,
    event_type,
    event_data,
    created_at
) VALUES (
    'COUPON',
    'test-coupon-$(date +%s)',
    'COUPON_TEST_EVENT',
    '{\"message\": \"Debezium 커넥터 테스트 이벤트\", \"timestamp\": \"$(date -Iseconds)\"}',
    NOW()
);"

echo "🧪 테스트 이벤트 생성 완료"

echo ""
echo "🎉 Debezium 커넥터 배포 완료!"
echo ""
echo "📋 다음 명령어로 상태를 모니터링하세요:"
echo "   - 커넥터 상태: curl ${CONNECT_URL}/connectors/${CONNECTOR_NAME}/status | jq"
echo "   - 토픽 목록: docker exec coupon-kafka kafka-topics --bootstrap-server localhost:9092 --list"
echo "   - 토픽 메시지: docker exec coupon-kafka kafka-console-consumer --bootstrap-server localhost:9092 --topic coupon-events --from-beginning"
echo ""
echo "🛠️  문제 발생 시:"
echo "   - 로그 확인: docker logs coupon-kafka-connect"
echo "   - PostgreSQL 로그: docker logs coupon-postgresql"
echo "   - 커넥터 재시작: curl -X POST ${CONNECT_URL}/connectors/${CONNECTOR_NAME}/restart"
echo ""