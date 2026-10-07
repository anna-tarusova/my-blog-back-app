package ru.practicum.config;

import org.springframework.context.annotation.*;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * Корневая конфигурация приложения (контекст ContextLoaderListener): сервисный слой, DAO
 * и подключение к PostgreSQL. Web-слой живёт в отдельном контексте — {@link WebConfig}.
 */
@Configuration
@ComponentScan(basePackages = {
        "ru.practicum.service",
        "ru.practicum.dao"})
@Import(JdbcConfig.class)
@PropertySource(value = "classpath:application.properties", ignoreResourceNotFound = true)
public class AppConfig {

    /**
     * Бин для CORS-фильтра.
     * ВАЖНО: Имя метода должно быть "corsFilter", чтобы совпадать с <filter-name> в web.xml.
     */
    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        // Разрешаю запросы с любых источников
        config.addAllowedOriginPattern("*");
        config.addAllowedHeader("*");
        config.addAllowedMethod("*"); // GET, POST, PUT, DELETE, OPTIONS

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return new CorsFilter(source);
    }
}
