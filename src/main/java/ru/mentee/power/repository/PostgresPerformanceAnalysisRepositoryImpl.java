package ru.mentee.power.repository;

import ru.mentee.power.model.analytics.*;
import ru.mentee.power.exception.DataAccessException;
import ru.mentee.power.config.ApplicationConfig;
import ru.mentee.power.config.ConfigFilePath;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class PostgresPerformanceAnalysisRepositoryImpl implements PerformanceAnalysisRepository {

  private final ApplicationConfig config;

  private static final String HEAVY_USER_ORDERS_QUERY = """
        SELECT 
            u.id as user_id,
            u.name as user_name,
            u.email,
            COUNT(o.id) as orders_count,
            SUM(o.total) as total_spent,
            AVG(o.total) as avg_order_value
        FROM users u
        JOIN orders o ON u.id = o.user_id
        WHERE u.city = ? 
          AND o.created_at >= ?
          AND o.status = 'DELIVERED'
        GROUP BY u.id, u.name, u.email
        HAVING COUNT(o.id) > ?
        ORDER BY total_spent DESC
        LIMIT 20
        """;

  private static final String EXPLAIN_ANALYZE_WRAPPER = """
        EXPLAIN (ANALYZE, BUFFERS, VERBOSE, FORMAT JSON) %s
        """;

  private static final String CREATE_PERFORMANCE_INDEXES = """
        DROP INDEX IF EXISTS idx_users_city;
        DROP INDEX IF EXISTS idx_orders_user_date_status;
        DROP INDEX IF EXISTS idx_orders_status_date;
        
        CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_users_city ON users(city);
        CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_orders_user_date_status 
            ON orders(user_id, created_at, status);
        CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_orders_status_date 
            ON orders(status, created_at);
        """;

  private static final String DROP_PERFORMANCE_INDEXES = """
        DROP INDEX IF EXISTS idx_users_city;
        DROP INDEX IF EXISTS idx_orders_user_date_status;
        DROP INDEX IF EXISTS idx_orders_status_date;
        """;

  public PostgresPerformanceAnalysisRepositoryImpl() throws Exception {
    this.config = new ApplicationConfig(new Properties(), new ConfigFilePath());
  }

  public PostgresPerformanceAnalysisRepositoryImpl(ApplicationConfig config) {
    this.config = config;
  }

  private Connection getConnection() throws SQLException {
    return DriverManager.getConnection(
        config.getUrl(),
        config.getUsername(),
        config.getPassword()
    );
  }

  private void logSql(String sql) {
    if (config.getShowSql()) {
      System.out.println("[SQL] " + sql);
    }
  }

  @Override
  public PerformanceMetrics<String> createOptimizationIndexes() throws DataAccessException {
    long startTime = System.nanoTime();

    try (Connection connection = getConnection();
        Statement statement = connection.createStatement()) {

      logSql(CREATE_PERFORMANCE_INDEXES);
      statement.execute(CREATE_PERFORMANCE_INDEXES);

      long executionTimeMs = (System.nanoTime() - startTime) / 1_000_000;

      return PerformanceMetrics.<String>builder()
          .data("Индексы успешно созданы")
          .executionTimeMs(executionTimeMs)
          .queryType("CREATE INDEX")
          .executedAt(LocalDateTime.now())
          .performanceGrade("EXCELLENT")
          .build();

    } catch (SQLException e) {
      throw new DataAccessException("Ошибка создания индексов", e);
    }
  }

  @Override
  public PerformanceMetrics<String> dropOptimizationIndexes() throws DataAccessException {
    long startTime = System.nanoTime();

    try (Connection connection = getConnection();
        Statement statement = connection.createStatement()) {

      logSql(DROP_PERFORMANCE_INDEXES);
      statement.execute(DROP_PERFORMANCE_INDEXES);

      long executionTimeMs = (System.nanoTime() - startTime) / 1_000_000;

      return PerformanceMetrics.<String>builder()
          .data("Индексы успешно удалены")
          .executionTimeMs(executionTimeMs)
          .queryType("DROP INDEX")
          .executedAt(LocalDateTime.now())
          .performanceGrade("EXCELLENT")
          .build();

    } catch (SQLException e) {
      throw new DataAccessException("Ошибка удаления индексов", e);
    }
  }

  private PerformanceMetrics<List<UserOrderStats>> executeUserOrderStatsQuery(
      String city, LocalDate startDate, Integer minOrders, boolean withIndexes)
      throws DataAccessException {

    long startTime = System.nanoTime();
    List<UserOrderStats> result = new ArrayList<>();

    try (Connection connection = getConnection()) {

      try (PreparedStatement ps = connection.prepareStatement(HEAVY_USER_ORDERS_QUERY)) {
        ps.setString(1, city);
        ps.setDate(2, Date.valueOf(startDate));
        ps.setInt(3, minOrders);

        logSql(HEAVY_USER_ORDERS_QUERY.replace("?", city)
            .replace("?", startDate.toString())
            .replace("?", minOrders.toString()));

        try (ResultSet rs = ps.executeQuery()) {
          while (rs.next()) {
            UserOrderStats stats = UserOrderStats.builder()
                .userId(rs.getLong("user_id"))
                .userName(rs.getString("user_name"))
                .email(rs.getString("email"))
                .ordersCount(rs.getInt("orders_count"))
                .totalSpent(rs.getBigDecimal("total_spent"))
                .avgOrderValue(rs.getBigDecimal("avg_order_value"))
                .build();
            result.add(stats);
          }
        }
      }

      long executionTimeMs = (System.nanoTime() - startTime) / 1_000_000;

      QueryExecutionPlan plan = getExecutionPlanForQuery(city, startDate, minOrders);

      String performanceGrade = determinePerformanceGrade(executionTimeMs, plan);

      return PerformanceMetrics.<List<UserOrderStats>>builder()
          .data(result)
          .executionTimeMs(executionTimeMs)
          .planningTimeMs(plan.getPlanningTime() != null ?
              plan.getPlanningTime().longValue() : 0)
          .buffersHit(plan.getBuffersHit())
          .buffersRead(plan.getBuffersRead())
          .queryType(withIndexes ? "OPTIMIZED_WITH_INDEXES" : "SLOW_WITHOUT_INDEXES")
          .executedAt(LocalDateTime.now())
          .performanceGrade(performanceGrade)
          .build();

    } catch (SQLException e) {
      throw new DataAccessException("Ошибка выполнения запроса статистики", e);
    }
  }

  private QueryExecutionPlan getExecutionPlanForQuery(
      String city, LocalDate startDate, Integer minOrders) throws DataAccessException {

    String queryWithParams = String.format(
        "SELECT u.id as user_id, u.name as user_name, u.email, " +
            "COUNT(o.id) as orders_count, SUM(o.total) as total_spent, " +
            "AVG(o.total) as avg_order_value " +
            "FROM users u JOIN orders o ON u.id = o.user_id " +
            "WHERE u.city = '%s' AND o.created_at >= '%s' AND o.status = 'DELIVERED' " +
            "GROUP BY u.id, u.name, u.email HAVING COUNT(o.id) > %d " +
            "ORDER BY SUM(o.total) DESC LIMIT 20",
        city, startDate, minOrders
    );

    return getExecutionPlan(queryWithParams);
  }

  private String determinePerformanceGrade(long executionTimeMs, QueryExecutionPlan plan) {
    if (executionTimeMs < 100) {
      return "EXCELLENT";
    } else if (executionTimeMs < 500) {
      return "GOOD";
    } else if (executionTimeMs < 2000) {
      return "POOR";
    } else {
      return "CRITICAL";
    }
  }

  @Override
  public PerformanceMetrics<List<UserOrderStats>> getSlowUserOrderStats(
      String city, LocalDate startDate, Integer minOrders) throws DataAccessException {

    dropOptimizationIndexes();

    try { Thread.sleep(100); } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    return executeUserOrderStatsQuery(city, startDate, minOrders, false);
  }

  @Override
  public PerformanceMetrics<List<UserOrderStats>> getFastUserOrderStats(
      String city, LocalDate startDate, Integer minOrders) throws DataAccessException {

    createOptimizationIndexes();

    try { Thread.sleep(100); } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    return executeUserOrderStatsQuery(city, startDate, minOrders, true);
  }

  @Override
  public QueryExecutionPlan getExecutionPlan(String query) throws DataAccessException {
    String explainQuery = String.format(EXPLAIN_ANALYZE_WRAPPER, query);

    try (Connection connection = getConnection();
        Statement statement = connection.createStatement()) {

      logSql(explainQuery);

      try (ResultSet rs = statement.executeQuery(explainQuery)) {
        StringBuilder jsonPlan = new StringBuilder();
        while (rs.next()) {
          jsonPlan.append(rs.getString(1));
        }

        return parseExplainAnalyzeJson(jsonPlan.toString(), query);
      }

    } catch (SQLException e) {
      throw new DataAccessException(
          "Ошибка получения плана выполнения для запроса: " + query, e);
    }
  }

  private QueryExecutionPlan parseExplainAnalyzeJson(String json, String originalQuery) {

    QueryExecutionPlan plan = QueryExecutionPlan.builder()
        .query(originalQuery)
        .planText(json)
        .performanceAnalysis("Для детального анализа требуется JSON парсер")
        .recommendations(List.of(
            "Добавьте зависимость Jackson в pom.xml",
            "Создайте POJO классы для структуры EXPLAIN JSON",
            "Реализуйте полный парсинг вложенной структуры"
        ))
        .build();

    extractBasicMetrics(plan, json);

    return plan;
  }

  private void extractBasicMetrics(QueryExecutionPlan plan, String json) {
    try {
      int execTimeStart = json.indexOf("\"Execution Time\": ");
      if (execTimeStart > 0) {
        int execTimeEnd = json.indexOf(",", execTimeStart);
        String execTimeStr = json.substring(execTimeStart + 18, execTimeEnd);
        plan.setExecutionTime(new java.math.BigDecimal(execTimeStr.trim()));
      }

      int planningTimeStart = json.indexOf("\"Planning Time\": ");
      if (planningTimeStart > 0) {
        int planningTimeEnd = json.indexOf(",", planningTimeStart);
        String planningTimeStr = json.substring(planningTimeStart + 17, planningTimeEnd);
        plan.setPlanningTime(new java.math.BigDecimal(planningTimeStr.trim()));
      }

      int buffersStart = json.indexOf("\"Buffers\": ");
      if (buffersStart > 0) {
        String buffersSection = json.substring(buffersStart);
        int sharedHitStart = buffersSection.indexOf("\"shared hit\": ");
        if (sharedHitStart > 0) {
          int sharedHitEnd = buffersSection.indexOf(",", sharedHitStart);
          String sharedHitStr = buffersSection.substring(
              sharedHitStart + 14, sharedHitEnd);
          plan.setBuffersHit(Long.parseLong(sharedHitStr.trim()));
        }

        int sharedReadStart = buffersSection.indexOf("\"shared read\": ");
        if (sharedReadStart > 0) {
          int sharedReadEnd = buffersSection.indexOf(",", sharedReadStart);
          String sharedReadStr = buffersSection.substring(
              sharedReadStart + 15, sharedReadEnd);
          plan.setBuffersRead(Long.parseLong(sharedReadStr.trim()));
        }
      }

    } catch (Exception e) {
      System.err.println("Ошибка парсинга JSON плана: " + e.getMessage());
    }
  }
}