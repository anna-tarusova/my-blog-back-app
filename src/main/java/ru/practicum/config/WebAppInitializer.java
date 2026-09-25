package ru.practicum.config;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import org.springframework.web.servlet.support.AbstractAnnotationConfigDispatcherServletInitializer;

/**
 * Программная (Java-based) интеграция со сервлет-контейнером: поднимает те же два контекста,
 * что описаны в {@code WEB-INF/web.xml} — корневой {@link AppConfig} (сервисы и DAO)
 * и контекст {@link WebConfig} для DispatcherServlet (контроллеры).
 *
 * <p>Servlet-контейнеры начиная с Servlet 3.0 находят этот класс автоматически
 * (через {@code SpringServletContainerInitializer} из spring-web), поэтому web.xml не обязателен.
 * Если же приложение разворачивается с web.xml (как в war-сборке), контекст уже поднимает
 * {@code ContextLoaderListener} и Servlet уже зарегистрирован — в этом случае {@link #onStartup} ничего не делает,
 * чтобы приложение не поднималось дважды.
 */
public class WebAppInitializer extends AbstractAnnotationConfigDispatcherServletInitializer {

    private static final String DISPATCHER_SERVLET_NAME = "dispatcher";

    @Override
    public void onStartup(ServletContext servletContext) throws ServletException {
        if (servletContext.getServletRegistration(getServletName()) == null) {
            super.onStartup(servletContext);
            return;
        }
        servletContext.log("Servlet '" + getServletName() + "' is already declared in web.xml, "
                + "Spring context is created by ContextLoaderListener");
    }

    @Override
    protected Class<?>[] getRootConfigClasses() {
        return new Class<?>[]{AppConfig.class};
    }

    @Override
    protected Class<?>[] getServletConfigClasses() {
        return new Class<?>[]{WebConfig.class};
    }

    @Override
    protected String getServletName() {
        return DISPATCHER_SERVLET_NAME;
    }

    @Override
    protected String[] getServletMappings() {
        return new String[]{"/"};
    }
}
