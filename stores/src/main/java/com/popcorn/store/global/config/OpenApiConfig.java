package com.popcorn.store.global.config;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;

@Configuration
public class OpenApiConfig {

    @Value("${app.gateway.url:${APP_GATEWAY_URL:https://api.goormpopcorn.shop}}")
    private String gatewayUrl;

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
            .servers(List.of(new Server().url(gatewayUrl).description("배포된 게이트웨이")))
            .components(new Components()
                .addSecuritySchemes("BearerAuth",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")));
    }

    @Bean
    public GroupedOpenApi ownerApi() {
        return GroupedOpenApi.builder()
                .group("owner")
                .pathsToMatch("/api/stores/v1/owner/**")
                .addOpenApiCustomizer(openApi ->
                    openApi.addSecurityItem(new SecurityRequirement().addList("BearerAuth")))
                .build();
    }

    @Bean
    public GroupedOpenApi managerApi() {
        return GroupedOpenApi.builder()
                .group("manager")
                .pathsToMatch("/api/v1/manager/**")
                .addOpenApiCustomizer(openApi ->
                    openApi.addSecurityItem(new SecurityRequirement().addList("BearerAuth")))
                .build();
    }

    @Bean
    public GroupedOpenApi publicApi() {
        return GroupedOpenApi.builder()
                .group("public")
                .pathsToMatch("/api/stores/v1/**")
                .pathsToExclude("/api/stores/v1/owner/**")
                .build();
    }
}
