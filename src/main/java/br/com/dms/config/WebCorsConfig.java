package br.com.dms.config;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

@Configuration
public class WebCorsConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;
    private final String[] allowedMethods;
    private final String[] allowedHeaders;
    private final boolean allowCredentials;

    public WebCorsConfig(
        @Value("${dms.cors.allowed-origins:http://localhost:5173}") String allowedOrigins,
        @Value("${dms.cors.allowed-methods:GET,POST,PUT,DELETE,OPTIONS}") String allowedMethods,
        @Value("${dms.cors.allowed-headers:*}") String allowedHeaders,
        @Value("${dms.cors.allow-credentials:true}") boolean allowCredentials,
        @Value("${dms.security.environment:dev}") String environment,
        @Value("${dms.security.cors.fail-on-insecure-production-config:true}") boolean failOnInsecureProductionConfig
    ) {
        this.allowedOrigins = toArray(allowedOrigins);
        this.allowedMethods = toArray(allowedMethods);
        this.allowedHeaders = toArray(allowedHeaders);
        this.allowCredentials = allowCredentials;

        validateConfiguration(environment, failOnInsecureProductionConfig);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/v1/**")
            .allowedOrigins(allowedOrigins)
            .allowedMethods(allowedMethods)
            .allowedHeaders(allowedHeaders)
            .allowCredentials(allowCredentials);
    }

    private void validateConfiguration(String environment, boolean failOnInsecureProductionConfig) {
        if (!failOnInsecureProductionConfig || !"prod".equalsIgnoreCase(environment)) {
            return;
        }

        boolean hasInsecureWildcardOrigin = Arrays.stream(allowedOrigins)
            .anyMatch(origin -> "*".equals(origin));

        boolean hasLocalhostOrigin = Arrays.stream(allowedOrigins)
            .anyMatch(origin -> origin.contains("localhost") || origin.contains("127.0.0.1"));

        if (hasInsecureWildcardOrigin || hasLocalhostOrigin) {
            throw new IllegalStateException("Insecure CORS configuration for production environment.");
        }
    }

    private String[] toArray(String source) {
        if (StringUtils.isBlank(source)) {
            return new String[0];
        }
        return Arrays.stream(source.split(","))
            .map(String::trim)
            .filter(StringUtils::isNotBlank)
            .toArray(String[]::new);
    }
}
