FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

COPY build/libs/gateway-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080
ENV TZ=Asia/Seoul
ENTRYPOINT ["java", "-jar", "app.jar"]
