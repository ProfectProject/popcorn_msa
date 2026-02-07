package com.popcorn.checkIns;

import io.github.cdimascio.dotenv.Dotenv;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ComponentScan(basePackages = {"com.popcorn.checkIns", "com.popcorn.common"})
@EnableAsync
@EnableScheduling
@Slf4j
public class CheckInsApplication {

    public static void main(String[] args) {
        // Spring 애플리케이션이 시작되기 전에 .env 파일을 로드
        loadDotEnvFile();

        SpringApplication app = new SpringApplication(CheckInsApplication.class);
        app.run(args);
    }

    private static void loadDotEnvFile() {
        try {
            String currentDir = System.getProperty("user.dir");
            Dotenv dotenv = null;

            // 1순위: checkIns 모듈 디렉터리의 .env 파일
            try {
                String checkInsDir = currentDir.endsWith("checkIns") ? currentDir : currentDir + "/checkIns";
                dotenv = Dotenv.configure()
                    .directory(checkInsDir)
                    .filename(".env")
                    .ignoreIfMissing()
                    .load();
            } catch (Exception e) {
                // checkIns/.env 로드 실패시 상위 디렉터리 시도
            }

            // 2순위: 현재 디렉터리의 .env 파일 (상위 프로젝트 .env)
            if (dotenv == null || dotenv.entries().isEmpty()) {
                dotenv = Dotenv.configure()
                    .directory(currentDir)
                    .filename(".env")
                    .ignoreIfMissing()
                    .load();
            }

            // .env 값들을 시스템 프로퍼티로 설정
            log.info("=== checkIns .env 파일 로드 확인 ===");
            log.info("현재 작업 디렉터리: {}", currentDir);

            String checkInsDir = currentDir.endsWith("checkIns") ? currentDir : currentDir + "/checkIns";
            log.info("checkIns 모듈 디렉터리: {}", checkInsDir);

            if (!dotenv.entries().isEmpty()) {
                log.info("로드된 .env 파일 위치: {}", dotenv.entries().size() > 0 ? "checkIns 모듈 또는 상위 디렉터리" : "없음");

                // checkIns 관련 환경변수만 로그에 표시
                dotenv.entries().forEach(entry -> {
                    if (System.getProperty(entry.getKey()) == null) {
                        System.setProperty(entry.getKey(), entry.getValue());
                        // checkIns 관련 환경변수만 로그 출력
                        if (entry.getKey().startsWith("QR_") || entry.getKey().equals("DB_HOST") ||
                            entry.getKey().equals("DB_PORT") || entry.getKey().equals("DB_NAME") ||
                            entry.getKey().equals("PASSPORT_SECRET") || entry.getKey().equals("JWT_SECRET")) {
                            log.info("✅ 로드됨: {}={}", entry.getKey(), entry.getValue());
                        }
                    }
                });
            } else {
                log.info("❌ checkIns .env 파일을 찾을 수 없거나 비어있습니다.");
            }
            log.info("=====================================");

        } catch (Exception e) {
            log.error("=== .env 파일 로드 실패: {} ===", e.getMessage());
            // .env 로드에 실패해도 애플리케이션은 계속 실행 (기본값 사용)
        }
    }

}
