package ru.practicum.config;

import jakarta.servlet.MultipartConfigElement;
import jakarta.servlet.ServletRegistration;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Проверяет согласованность инфраструктуры развёртывания: Java-конфигурация для сервлет-контейнера
 * ({@link WebAppInitializer}) и web-дескриптор war-файла поднимают одни и те же Spring-контексты.
 */
class ServletContainerConfigTest {

    private static final String ANNOTATION_CONFIG_WEB_APPLICATION_CONTEXT =
            "org.springframework.web.context.support.AnnotationConfigWebApplicationContext";

    private static final File WEB_XML = new File("src/main/webapp/WEB-INF/web.xml");

    @Test
    void webAppInitializerRegistersRootAndServletContexts() {
        TestWebAppInitializer initializer = new TestWebAppInitializer();

        assertArrayEquals(new Class<?>[]{AppConfig.class}, initializer.rootConfigClasses());
        assertArrayEquals(new Class<?>[]{WebConfig.class}, initializer.servletConfigClasses());
        assertArrayEquals(new String[]{"/"}, initializer.servletMappings());
    }

    @Test
    void webXmlDeclaresSameContextsAndServletAsJavaConfiguration() throws Exception {
        assertTrue(WEB_XML.isFile(), "Ожидается web-дескриптор src/main/webapp/WEB-INF/web.xml");

        Document webXml = parse(WEB_XML);

        assertEquals("6.0", webXml.getDocumentElement().getAttribute("version"));

        List<String> paramValues = textsOf(webXml, "param-value");
        assertTrue(paramValues.contains(ANNOTATION_CONFIG_WEB_APPLICATION_CONTEXT),
                "contextClass/init-param должны включать AnnotationConfigWebApplicationContext");
        assertTrue(paramValues.contains(AppConfig.class.getName()), "web.xml должен поднимать AppConfig");
        assertTrue(paramValues.contains(WebConfig.class.getName()), "web.xml должен поднимать WebConfig");

        assertEquals(List.of("org.springframework.web.context.ContextLoaderListener"), textsOf(webXml, "listener-class"));
        assertEquals(List.of("org.springframework.web.servlet.DispatcherServlet"), textsOf(webXml, "servlet-class"));
        assertEquals(List.of("/"), textsOf(webXml, "url-pattern"));
        assertTrue(textsOf(webXml, "servlet-name").contains(new TestWebAppInitializer().servletName()),
                "имя DispatcherServlet в web.xml должно совпадать с именем в Java-конфигурации");
    }

    @Test
    void webXmlDeclaresMultipartConfigWithLimitsMatchingJavaConfiguration() throws Exception {
        Document webXml = parse(WEB_XML);

        assertEquals(1, webXml.getElementsByTagName("multipart-config").getLength(),
                "web.xml должен объявить <multipart-config> для DispatcherServlet, "
                        + "иначе контейнер не сможет разобрать multipart/form-data (PUT /api/posts/{id}/image)");
        assertEquals(List.of(String.valueOf(WebAppInitializer.MAX_FILE_SIZE)),
                textsOf(webXml, "max-file-size"));
        assertEquals(List.of(String.valueOf(WebAppInitializer.MAX_REQUEST_SIZE)),
                textsOf(webXml, "max-request-size"));
        assertEquals(List.of(String.valueOf(WebAppInitializer.FILE_SIZE_THRESHOLD)),
                textsOf(webXml, "file-size-threshold"));
    }

    @Test
    void javaConfigurationAppliesSameMultipartConfigWhenWebXmlIsAbsent() {
        AtomicReference<MultipartConfigElement> applied = new AtomicReference<>();
        ServletRegistration.Dynamic registration = (ServletRegistration.Dynamic) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{ServletRegistration.Dynamic.class},
                (proxy, method, args) -> {
                    if ("setMultipartConfig".equals(method.getName())) {
                        applied.set((MultipartConfigElement) args[0]);
                    }
                    return null;
                });

        new TestWebAppInitializer().applyMultipartConfiguration(registration);

        MultipartConfigElement config = applied.get();
        assertNotNull(config, "customizeRegistration должен настроить multipart-загрузку");
        assertEquals(WebAppInitializer.MAX_FILE_SIZE, config.getMaxFileSize());
        assertEquals(WebAppInitializer.MAX_REQUEST_SIZE, config.getMaxRequestSize());
        assertEquals(WebAppInitializer.FILE_SIZE_THRESHOLD, config.getFileSizeThreshold());
    }

    private static Document parse(File file) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(file);
    }

    private static List<String> textsOf(Document document, String tagName) {
        List<String> texts = new ArrayList<>();
        NodeList nodes = document.getElementsByTagName(tagName);
        for (int i = 0; i < nodes.getLength(); i++) {
            texts.add(nodes.item(i).getTextContent().trim());
        }
        return texts;
    }

    /** Открывает protected-методы инициализатора, чтобы проверить передаваемые контейнеру классы конфигурации. */
    private static class TestWebAppInitializer extends WebAppInitializer {

        Class<?>[] rootConfigClasses() {
            return getRootConfigClasses();
        }

        Class<?>[] servletConfigClasses() {
            return getServletConfigClasses();
        }

        String[] servletMappings() {
            return getServletMappings();
        }

        String servletName() {
            return getServletName();
        }

        void applyMultipartConfiguration(ServletRegistration.Dynamic registration) {
            customizeRegistration(registration);
        }
    }
}
