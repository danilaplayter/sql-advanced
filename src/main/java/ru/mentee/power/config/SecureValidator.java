/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.config;

import static ru.mentee.power.config.DatabaseConfig.DB_PASSWORD;
import static ru.mentee.power.config.DatabaseConfig.DB_URL;
import static ru.mentee.power.config.DatabaseConfig.DB_USERNAME;

import java.util.Properties;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import ru.mentee.power.exception.SASTException;

@Slf4j
public class SecureValidator {
    private static final Set<String> COMMON_PASSWORDS =
            Set.of("password", "123456", "admin", "root", "qwerty", "password123");

    private final Properties properties;

    public SecureValidator(Properties properties) {
        this.properties = properties;
    }

    public void validate() {
        checkForHardcodedSecrets();
        checkPasswordStrength();
        validateRequiredProperties();
        log.info("Валидация конфигурации пройдена успешно");
    }

    private void checkForHardcodedSecrets() {
        properties.forEach(
                (key, value) -> {
                    String keyStr = key.toString();
                    String valueStr = value.toString();

                    if (keyStr.toLowerCase().contains("password")
                            && !valueStr.isEmpty()
                            && !keyStr.equals(DB_PASSWORD)) {
                        throw new SASTException(
                                "Пароль обнаружен в публичном конфигурационном файле: " + keyStr);
                    }
                });
    }

    private void checkPasswordStrength() {
        String password = properties.getProperty(DB_PASSWORD);
        if (password != null && COMMON_PASSWORDS.contains(password.toLowerCase())) {
            log.warn("Обнаружен слабый пароль. Рекомендуется использовать более сложный пароль");
        }
    }

    private void validateRequiredProperties() {
        String url = properties.getProperty(DB_URL);
        String username = properties.getProperty(DB_USERNAME);

        if (url == null || url.trim().isEmpty()) {
            throw new SASTException("URL базы данных не задан");
        }

        if (username == null || username.trim().isEmpty()) {
            throw new SASTException("Имя пользователя базы данных не задано");
        }
    }
}
