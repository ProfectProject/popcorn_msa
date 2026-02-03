FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

COPY build/libs/*.jar app.jar

EXPOSE 8087
ENV TZ=Asia/Seoul
ENTRYPOINT ["java", "-jar", "app.jar"]
