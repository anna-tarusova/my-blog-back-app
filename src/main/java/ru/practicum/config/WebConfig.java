package ru.practicum.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

/**
 * Конфигурация web-слоя (контекст DispatcherServlet): контроллеры и обработка ошибок.
 * Сервисы и DAO берутся из родительского корневого контекста ({@link AppConfig}).
 *
 * <p>Jackson (jackson-databind) есть в classpath,
 * поэтому MappingJackson2HttpMessageConverter подключается автоматически.
 */
@Configuration
@EnableWebMvc
@ComponentScan(basePackages = {
        "ru.practicum.controller",
        "ru.practicum.web"})
public class WebConfig {
}

