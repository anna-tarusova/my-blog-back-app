package ru.practicum.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.multipart.MultipartResolver;
import org.springframework.web.multipart.support.StandardServletMultipartResolver;

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

    /**
     * MultipartResolver для Servlet 3.0+: оборачивает запрос контейнера в {@code MultipartHttpServletRequest}
     * и читает части через {@code request.getParts()}, что позволяет принимать файлы
     * ({@code PUT /api/posts/{id}/image}). Имя бина — «multipartResolver»: именно его ищет DispatcherServlet.
     *
     * <p>Лимиты размера задаются сервлет-контейнером: {@code <multipart-config>} в web.xml
     * либо {@link WebAppInitializer#customizeRegistration} при программной регистрации.
     */
    @Bean
    public MultipartResolver multipartResolver() {
        return new StandardServletMultipartResolver();
    }
}

