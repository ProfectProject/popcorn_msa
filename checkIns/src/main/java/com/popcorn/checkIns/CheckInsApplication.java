package com.popcorn.checkIns;

import io.github.cdimascio.dotenv.Dotenv;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.popcorn.checkIns", "com.popcorn.common"})
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
            // 현재 디렉터리에서 .env 파일을 먼저 찾음
            Dotenv dotenv = Dotenv.configure()
                .filename(".env")
                .ignoreIfMissing()
                .load();

            // 현재 디렉터리에서 찾지 못하면 checkIns 디렉터리에서 찾음
            if (dotenv.entries().isEmpty()) {
                dotenv = Dotenv.configure()
                    .directory("./checkIns")
                    .filename(".env")
                    .ignoreIfMissing()
                    .load();
            }

            // .env 값들을 시스템 프로퍼티로 설정
            log.info("=== .env 파일 로드 확인 ===");
            log.info("현재 작업 디렉터리: {}", System.getProperty("user.dir"));

            if (!dotenv.entries().isEmpty()) {
                dotenv.entries().forEach(entry -> {
                    // 이미 시스템 프로퍼티에 설정되어 있지 않은 경우만 설정
                    if (System.getProperty(entry.getKey()) == null) {
                        System.setProperty(entry.getKey(), entry.getValue());
                        log.info("로드됨: {}={}", entry.getKey(), entry.getValue());
                    }
                });
            } else {
                log.info(".env 파일을 찾을 수 없거나 비어있습니다.");
            }
            log.info("=========================");

        } catch (Exception e) {
            log.error("=== .env 파일 로드 실패: {} ===", e.getMessage());
            // .env 로드에 실패해도 애플리케이션은 계속 실행 (기본값 사용)
        }
    }

}
