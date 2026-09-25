package ru.practicum.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Корневая конфигурация приложения (контекст ContextLoaderListener): сервисный слой, DAO
 * и подключение к PostgreSQL. Web-слой живёт в отдельном контексте — {@link WebConfig}.
 */
@Configuration
@ComponentScan(basePackages = {
        "ru.practicum.service",
        "ru.practicum.dao"})
@Import(JdbcConfig.class)
public class AppConfig {
}

