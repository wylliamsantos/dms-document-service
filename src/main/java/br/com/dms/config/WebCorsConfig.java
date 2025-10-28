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
        @Value("${dms.cors.allow-credentials:true}") boolean allowCredentials
    ) {
        this.allowedOrigins = toArray(allowedOrigins);
        this.allowedMethods = toArray(allowedMethods);
        this.allowedHeaders = toArray(allowedHeaders);
        this.allowCredentials = allowCredentials;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/v1/**")
            .allowedOrigins(allowedOrigins)
            .allowedMethods(allowedMethods)
            .allowedHeaders(allowedHeaders)
            .allowCredentials(allowCredentials);
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
