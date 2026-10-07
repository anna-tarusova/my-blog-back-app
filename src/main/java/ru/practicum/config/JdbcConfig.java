package ru.practicum.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.jdbc.repository.config.AbstractJdbcConfiguration;
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.DataSourceInitializer;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import ru.practicum.dao.PostRepository;

import javax.sql.DataSource;

/**
 * Слой доступа к данным: пул соединений HikariCP, шаблон JDBC-запросов, менеджер транзакций,
 * создание схемы БД и включение репозиториев Spring Data JDBC.
 *
 * <p>Настройки подключения берутся из {@code classpath:db.properties} и могут быть переопределены
 * системными свойствами или переменными окружения (например, {@code -Djdbc.url=...}).
 */
@Configuration
@PropertySource(value = "classpath:db.properties", ignoreResourceNotFound = true)
@EnableJdbcRepositories(basePackageClasses = PostRepository.class)
@EnableTransactionManagement
public class JdbcConfig extends AbstractJdbcConfiguration {

    private static final String DEFAULT_JDBC_URL = "jdbc:postgresql://localhost:5432/blog";
    private static final String DEFAULT_USERNAME = "blog";
    private static final String DEFAULT_PASSWORD = "blog";

    @Bean(destroyMethod = "close")
    public DataSource dataSource(Environment environment) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(environment.getProperty("jdbc.url", DEFAULT_JDBC_URL));
        config.setUsername(environment.getProperty("jdbc.username", DEFAULT_USERNAME));
        config.setPassword(resolvePassword(environment));
        config.setPoolName("blog-pool");
        return new HikariDataSource(config);
    }

    /**
     * Пароль к БД: сначала переменная окружения {@code DB_PASSWORD}, затем свойство {@code jdbc.password}
     * (системное свойство {@code -Djdbc.password}, переменная {@code JDBC_PASSWORD} или {@code db.properties}),
     * иначе значение по умолчанию. Так секрет передаётся при развёртывании и не хранится в репозитории.
     */
    private static String resolvePassword(Environment environment) {
        return environment.getProperty("DB_PASSWORD",
                environment.getProperty("jdbc.password", DEFAULT_PASSWORD));
    }

    /** Нужен репозиториям Spring Data JDBC и {@link AbstractJdbcConfiguration}. */
    @Bean
    public NamedParameterJdbcTemplate namedParameterJdbcTemplate(DataSource dataSource) {
        return new NamedParameterJdbcTemplate(dataSource);
    }

    @Bean
    public PlatformTransactionManager transactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    /** Создаёт таблицы из schema.sql при старте приложения; DDL идемпотентный (IF NOT EXISTS). */
    @Bean
    public DataSourceInitializer schemaInitializer(DataSource dataSource) {
        DataSourceInitializer initializer = new DataSourceInitializer();
        initializer.setDataSource(dataSource);
        initializer.setDatabasePopulator(new ResourceDatabasePopulator(new ClassPathResource("schema.sql")));
        return initializer;
    }
}
