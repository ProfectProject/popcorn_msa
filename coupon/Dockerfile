# =================================================================
# 🎫 Coupon Service Dockerfile
# =================================================================

# Build stage
FROM gradle:8.4-jdk17 AS build
WORKDIR /app
COPY build.gradle.kts settings.gradle.kts ./
COPY gradle.properties ./
COPY src ./src
RUN gradle clean build -x test --no-daemon

# Runtime stage
FROM amazoncorretto:17-alpine
WORKDIR /app

# Add user for security
RUN addgroup -g 1001 -S spring && \
    adduser -u 1001 -S spring -G spring

# Install curl for health checks
RUN apk add --no-cache curl

# JAR 파일 복사
COPY --from=build /app/build/libs/*.jar app.jar

# Change ownership
RUN chown -R spring:spring /app

# Switch to non-root user
USER spring:spring

# 포트 노출
EXPOSE 8088

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8088/actuator/health || exit 1

# 애플리케이션 실행
ENTRYPOINT ["java", "-jar", "/app/app.jar"]