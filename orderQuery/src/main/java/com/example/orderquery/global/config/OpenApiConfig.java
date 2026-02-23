package com.example.orderquery.global.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme.In;
import io.swagger.v3.oas.models.security.SecurityScheme.Type;
import io.swagger.v3.oas.models.servers.Server;

@Configuration
@OpenAPIDefinition(
        info = @Info(title = "OrderQuery API", version = "v1"),
        security = @SecurityRequirement(name = "Bearer Authentication")
)
@SecurityScheme(
        name = "Bearer Authentication",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT"
)
@SecurityScheme(
        name = "passportAuth",
        type = SecuritySchemeType.APIKEY,
        in = SecuritySchemeIn.HEADER,
        paramName = "X-Passport"
)
public class OpenApiConfig {

    @Value("${app.gateway.url:${APP_GATEWAY_URL:https://api.goormpopcorn.shop}}")
    private String gatewayUrl;

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new io.swagger.v3.oas.models.info.Info().title("OrderQuery API").version("v1"))
                .components(new Components()
                        .addSecuritySchemes("Bearer Authentication",
                                new io.swagger.v3.oas.models.security.SecurityScheme()
                                        .type(Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("JWT 토큰을 입력하세요"))
                        .addSecuritySchemes("passportAuth",
                                new io.swagger.v3.oas.models.security.SecurityScheme()
                                        .type(Type.APIKEY)
                                        .in(In.HEADER)
                                        .name("X-Passport")
                                        .description("Gateway/내부 호출용 Passport 헤더")))
                .addSecurityItem(new io.swagger.v3.oas.models.security.SecurityRequirement().addList("Bearer Authentication"))
                .addSecurityItem(new io.swagger.v3.oas.models.security.SecurityRequirement().addList("passportAuth"))
                .servers(List.of(new Server().url(gatewayUrl).description("배포된 게이트웨이")));
    }
}
