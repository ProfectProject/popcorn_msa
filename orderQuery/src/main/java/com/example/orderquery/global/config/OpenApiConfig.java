package com.example.orderquery.global.config;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.OpenApiCustomizer;

@Configuration
public class OpenApiConfig {
    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Value("${app.gateway.url:${APP_GATEWAY_URL:https://api.goormpopcorn.shop}}")
    private String gatewayUrl;

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info().title("OrderQuery API").version("v1"))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME,
                                new SecurityScheme()
                                        .name("Authorization")
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("JWT Token Authentication")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .servers(List.of(new Server().url(gatewayUrl).description("배포된 게이트웨이")));
    }

    @Bean
    public OpenApiCustomizer normalizeSecuritySchemeNames() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }
            for (PathItem pathItem : openApi.getPaths().values()) {
                if (pathItem == null || pathItem.readOperations() == null) {
                    continue;
                }
                for (Operation operation : pathItem.readOperations()) {
                    if (operation.getSecurity() == null || operation.getSecurity().isEmpty()) {
                        continue;
                    }
                    List<SecurityRequirement> normalized = operation.getSecurity().stream()
                            .map(this::normalizeRequirement)
                            .collect(Collectors.toList());
                    operation.setSecurity(normalized);
                }
            }
        };
    }

    private SecurityRequirement normalizeRequirement(SecurityRequirement requirement) {
        SecurityRequirement normalized = new SecurityRequirement();
        for (Map.Entry<String, List<String>> entry : requirement.entrySet()) {
            String scheme = entry.getKey();
            if ("bearer-token".equals(scheme) || "Bearer Authentication".equals(scheme)) {
                normalized.addList(SECURITY_SCHEME_NAME, entry.getValue());
            } else {
                normalized.addList(scheme, entry.getValue());
            }
        }
        return normalized;
    }
}
