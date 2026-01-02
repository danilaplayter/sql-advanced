package ru.mentee.power.test;


import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public abstract class BaseIntegrationTest {

  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
      .withDatabaseName("mentee_power_test_db")
      .withUsername("mentee_power")
      .withPassword("password");

  @BeforeEach
  void setUp() {
  }

  protected Connection getTestConnection() throws SQLException {
    return DriverManager.getConnection(
        postgres.getJdbcUrl(),
        postgres.getUsername(),
        postgres.getPassword()
    );
  }

  protected String getSchemaName() {
    return "mentee_power";
  }
}