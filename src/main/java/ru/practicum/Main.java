package ru.practicum;

import org.apache.catalina.Context;
import org.apache.catalina.Wrapper;
import org.apache.catalina.startup.Tomcat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;
import ru.practicum.config.AppConfig;
import ru.practicum.config.WebConfig;

import java.io.File;

/**
 * Точка входа: поднимает встроенный Tomcat и регистрирует DispatcherServlet,
 * поэтому отдельный контейнер сервлетов и web.xml не нужны.
 */
public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    private static final int PORT = 8080;

    public static void main(String[] args) throws Exception {
        Tomcat tomcat = startServer(PORT);

        log.info("Blog backend started: http://localhost:{}/api/posts?search=&pageNumber=1&pageSize=5", PORT);
        tomcat.getServer().await();
    }

    /** Собирает и запускает сервер так же, как это делает servlet-контейнер при развёртывании war-файла:
     *  корневой контекст ({@link AppConfig}) + контекст DispatcherServlet ({@link WebConfig}) с родителем. */
    static Tomcat startServer(int port) throws Exception {
        Tomcat tomcat = new Tomcat();
        tomcat.setPort(port);
        tomcat.getConnector();

        String webappDirLocation = "src/main/webapp/WEB-INF";
        Context context = tomcat.addWebapp("", new File(webappDirLocation).getAbsolutePath());

        AnnotationConfigWebApplicationContext rootContext = new AnnotationConfigWebApplicationContext();
        rootContext.register(AppConfig.class);
        rootContext.refresh();

        AnnotationConfigWebApplicationContext dispatcherContext = new AnnotationConfigWebApplicationContext();
        dispatcherContext.setParent(rootContext);
        dispatcherContext.register(WebConfig.class);

        Wrapper dispatcherServlet = Tomcat.addServlet(context, "dispatcher", new DispatcherServlet(dispatcherContext));
        dispatcherServlet.setLoadOnStartup(1);
        context.addServletMappingDecoded("/*", "dispatcher");

        tomcat.start();
        return tomcat;
    }
}
