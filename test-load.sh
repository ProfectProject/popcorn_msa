#!/bin/bash

# Popcorn MSA 부하 테스트 실행 스크립트
echo "🚀 Popcorn MSA Load Test Runner"

# Docker 컨테이너가 실행 중인지 확인
echo "📋 Checking Docker services..."
docker ps --format "table {{.Names}}\t{{.Status}}" | grep popcorn

# Gateway가 응답하는지 확인
echo "🔍 Testing Gateway connectivity..."
curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/actuator/health || echo "❌ Gateway not responding"

# 부하 테스트 모드 선택
MODE=${1:-"quick"}

case "$MODE" in
  "quick")
    echo "⚡ Running QUICK test (low load, 2 minutes)..."
    k6 run \
      -e BASE_URL=http://localhost:8080 \
      -e AUTO_LOGIN=true \
      -e LOGIN_ROLE=customer \
      -e SCENARIO=hot \
      -e POPUP_HOT_ID=ff31f6d6-1234-5678-9abc-123456789abc \
      -e STEADY_RPS=10 -e STEADY_DURATION=1m \
      -e RUSH_RPS=20   -e RUSH_DURATION=30s \
      -e SPIKE_RPS=30  -e SPIKE_DURATION=30s \
      ./popcorn-load-test.js
    ;;

  "hot")
    echo "🔥 Running HOT popup test (real scenario)..."
    k6 run \
      -e BASE_URL=http://localhost:8080 \
      -e AUTO_LOGIN=true \
      -e LOGIN_ROLE=customer \
      -e SCENARIO=hot \
      -e POPUP_HOT_ID=ff31f6d6-1234-5678-9abc-123456789abc \
      -e STEADY_RPS=250 -e STEADY_DURATION=10m \
      -e RUSH_RPS=800   -e RUSH_DURATION=10m \
      -e SPIKE_RPS=1800 -e SPIKE_DURATION=3m \
      ./popcorn-load-test.js
    ;;

  "query")
    echo "📊 Running Query service test..."
    k6 run \
      -e MODE=storm \
      -e BASE_URL=http://localhost:8080 \
      -e AUTO_LOGIN=true \
      -e LOGIN_ROLE=admin \
      -e POPUP_HOT_ID=ff31f6d6-1234-5678-9abc-123456789abc \
      -e STORE_ID=1 \
      -e STORM_RPS_TOTAL=100 \
      -e STORM_DURATION=2m \
      ./popcorn-query-load-test.js
    ;;

  "debug")
    echo "🐛 Running DEBUG mode (single request test)..."
    k6 run \
      -e BASE_URL=http://localhost:8080 \
      -e AUTO_LOGIN=true \
      -e LOGIN_ROLE=customer \
      -e SCENARIO=hot \
      -e POPUP_HOT_ID=ff31f6d6-1234-5678-9abc-123456789abc \
      -e STEADY_RPS=1 -e STEADY_DURATION=10s \
      -e RUSH_RPS=1   -e RUSH_DURATION=10s \
      -e SPIKE_RPS=1  -e SPIKE_DURATION=10s \
      ./popcorn-load-test.js
    ;;

  *)
    echo "❓ Usage: $0 [quick|hot|query|debug]"
    echo "   quick: Low load test (2min)"
    echo "   hot: Full hot popup scenario (23min)"
    echo "   query: Query service test (2min)"
    echo "   debug: Single request debug (30s)"
    exit 1
    ;;
esac

echo "✅ Load test completed!"