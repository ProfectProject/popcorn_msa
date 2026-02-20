package com.popcorn.checkIns.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Value("${app.version:0.0.1-SNAPSHOT}")
    private String appVersion;

    @Value("${app.gateway.url:${APP_GATEWAY_URL:https://api.goormpopcorn.shop}}")
    private String gatewayUrl;

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .servers(List.of(new Server().url(gatewayUrl).description("배포된 게이트웨이")))
                .info(new Info()
                        .title("QRCheckIn Swagger")
                        .description("QR 코드 및 체크인 관리 API")
                        .version(appVersion))
                .addSecurityItem(new SecurityRequirement().addList("bearer-jwt"))
                .components(new Components()
                        .addSecuritySchemes("bearer-jwt",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("JWT Bearer 토큰을 입력하세요")));
    }
}
