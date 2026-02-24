# 🚀 JVM 최적화 가이드 - 극한 부하 대응

## 🎯 현재 성과
- **1단계 완벽 성공**: 80 RPS, 0% 실패율 달성
- **99.95% → 0%**: 극적인 실패율 개선
- **높은 효율성**: 10-15 VUs로 80 RPS 처리

## 🔧 추가 JVM 최적화 (더 높은 부하 대응)

### 1. JVM 힙 메모리 설정

```bash
# User 서비스 (8082)
export JAVA_OPTS="-Xms4g -Xmx4g -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+UseStringDeduplication"

# Stores 서비스 (8083)
export JAVA_OPTS="-Xms4g -Xmx4g -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+UseStringDeduplication"

# Order 서비스 (8084)
export JAVA_OPTS="-Xms2g -Xmx2g -XX:+UseG1GC -XX:MaxGCPauseMillis=200"

# Payment 서비스 (8085)
export JAVA_OPTS="-Xms2g -Xmx2g -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
```

### 2. G1GC 세부 조정

```bash
# 극한 성능 G1GC 설정
-XX:+UseG1GC
-XX:MaxGCPauseMillis=100          # 더 짧은 GC 일시정지
-XX:G1HeapRegionSize=32m          # 힙 리전 크기 최적화
-XX:G1NewSizePercent=30           # Young Generation 비율
-XX:G1MaxNewSizePercent=40        # Young Generation 최대 비율
-XX:+G1UseAdaptiveIHOP           # 적응형 IHOP
-XX:G1MixedGCCountTarget=8       # Mixed GC 목표 횟수
```

### 3. JIT 컴파일러 최적화

```bash
# HotSpot JIT 최적화
-XX:+TieredCompilation           # 계층화된 컴파일
-XX:TieredStopAtLevel=4         # 최고 레벨까지 컴파일
-XX:+UseInlineCaches            # 인라인 캐시 사용
-XX:+AggressiveOpts             # 적극적 최적화
```

### 4. 메모리 풀 최적화

```bash
# 메타스페이스 및 압축된 OOP
-XX:MetaspaceSize=256m          # 메타스페이스 초기 크기
-XX:MaxMetaspaceSize=512m       # 메타스페이스 최대 크기
-XX:+UseCompressedOops          # 압축된 객체 포인터
-XX:+UseCompressedClassPointers # 압축된 클래스 포인터
```

### 5. 네이티브 메모리 추적

```bash
# 메모리 사용량 모니터링
-XX:NativeMemoryTracking=summary
-XX:+UnlockDiagnosticVMOptions
-XX:+PrintGCDetails
-XX:+PrintGCTimeStamps
-XX:+UseGCLogFileRotation
-XX:NumberOfGCLogFiles=5
-XX:GCLogFileSize=100M
```

## 🐳 Docker Compose 메모리 설정

```yaml
version: '3.8'
services:
  user-service:
    image: user-service:latest
    ports:
      - "8082:8082"
    environment:
      JAVA_OPTS: "-Xms4g -Xmx4g -XX:+UseG1GC -XX:MaxGCPauseMillis=100"
    mem_limit: 5g                 # 컨테이너 메모리 제한
    mem_reservation: 4g           # 메모리 예약
    cpus: '2.0'                   # CPU 제한

  stores-service:
    image: stores-service:latest
    ports:
      - "8083:8083"
    environment:
      JAVA_OPTS: "-Xms4g -Xmx4g -XX:+UseG1GC -XX:MaxGCPauseMillis=100"
    mem_limit: 5g
    mem_reservation: 4g
    cpus: '2.0'

  order-service:
    image: order-service:latest
    ports:
      - "8084:8084"
    environment:
      JAVA_OPTS: "-Xms2g -Xmx2g -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
    mem_limit: 3g
    mem_reservation: 2g
    cpus: '1.5'

  payment-service:
    image: payment-service:latest
    ports:
      - "8085:8085"
    environment:
      JAVA_OPTS: "-Xms2g -Xmx2g -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
    mem_limit: 3g
    mem_reservation: 2g
    cpus: '1.5'
```

## 📊 모니터링 설정

### 1. GC 로그 분석
```bash
# GC 로그 분석 도구
java -jar gcviewer.jar gc.log

# 또는 온라인 도구
# https://gceasy.io/
```

### 2. 힙 덤프 분석
```bash
# 힙 덤프 생성
jcmd <pid> GC.run_finalization
jcmd <pid> VM.gc
jmap -dump:format=b,file=heap.hprof <pid>

# Eclipse MAT로 분석
```

### 3. 런타임 메트릭
```bash
# JVM 메트릭 모니터링
jstat -gc <pid> 1s
jstat -gcutil <pid> 1s

# 네이티브 메모리 추적
jcmd <pid> VM.native_memory summary
```

## 🎯 성능 튜닝 체크리스트

### ✅ 이미 완료된 최적화
- [x] HikariCP 80 connections
- [x] Tomcat 500 threads
- [x] Redis 50 active connections
- [x] 로깅 ERROR 레벨
- [x] 지능적 캐싱 시스템

### 🔄 추가 적용 권장
- [ ] JVM 힙 메모리 4GB 설정
- [ ] G1GC 100ms pause target
- [ ] Docker 메모리 제한 설정
- [ ] GC 로그 모니터링 활성화
- [ ] 힙 덤프 자동 생성 설정

## 📈 예상 성능 향상

현재 **80 RPS에서 0% 실패율**을 달성했으므로, JVM 최적화 추가 적용 시:

- **180 RPS**: 95%+ 성공률 예상
- **350 RPS**: 90%+ 성공률 예상
- **500+ RPS**: 추가 최적화로 가능

**현재 최적화만으로도 극적인 성능 향상을 달성했으며, JVM 튜닝으로 더욱 향상 가능합니다!** 🚀