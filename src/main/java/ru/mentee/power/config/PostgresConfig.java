/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.config;

import java.util.Properties;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PostgresConfig implements DatabaseConfig {
    private final Properties properties;

    public PostgresConfig(Properties properties) {
        this.properties = properties;
    }

    @Override
    public String getUrl() {
        String url = properties.getProperty(DB_URL);
        if (url == null || url.trim().isEmpty()) {
            log.warn("URL базы данных не задан, используется значение по умолчанию");
            return "jdbc:postgresql://localhost:5432/mentee_db";
        }
        return url;
    }

    @Override
    public String getUsername() {
        String username = properties.getProperty(DB_USERNAME);
        if (username == null || username.trim().isEmpty()) {
            log.warn("Имя пользователя БД не задано");
        }
        return username;
    }

    @Override
    public String getPassword() {
        String password = properties.getProperty(DB_PASSWORD);
        if (password == null || password.trim().isEmpty()) {
            log.error("Пароль БД не задан!");
        }
        return password;
    }

    @Override
    public String getDriver() {
        String driver = properties.getProperty(DB_DRIVER);
        if (driver == null || driver.trim().isEmpty()) {
            return "org.postgresql.Driver";
        }
        return driver;
    }

    @Override
    public boolean getShowSql() {
        String showSql = properties.getProperty(DB_SHOW_SQL);
        return "true".equalsIgnoreCase(showSql);
    }
}
