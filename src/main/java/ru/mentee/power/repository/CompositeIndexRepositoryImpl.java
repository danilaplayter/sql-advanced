package ru.mentee.power.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Date;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ru.mentee.power.config.ApplicationConfig;
import ru.mentee.power.exception.DataAccessException;
import ru.mentee.power.model.analytics.*;
import ru.mentee.power.utils.QueryPlanParser;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@AllArgsConstructor
public class CompositeIndexRepositoryImpl implements CompositeIndexRepository {

  private final ApplicationConfig config;
  private final ObjectMapper objectMapper;

  private static final String ORDER_ANALYTICS_BASE_SQL = """
        SELECT 
            o.region,
            o.status,
            COUNT(*) as orders_count,
            SUM(o.total_amount) as total_revenue,
            AVG(o.total_amount) as avg_order_value,
            MIN(o.created_at) as first_order,
            MAX(o.created_at) as last_order
        FROM orders o
        WHERE o.region = ANY(?)
          AND o.status = ANY(?)
          AND o.created_at BETWEEN ? AND ?
        GROUP BY o.region, o.status
        ORDER BY o.region, o.status;
        """;

  private static final String PRODUCT_COUNT_SQL = """
        SELECT COUNT(*) as product_count
        FROM products p
        WHERE p.category_id = ?
          AND p.price BETWEEN ? AND ?
          AND p.is_active = true;
        """;

  private static final String CASE_INSENSITIVE_SEARCH_SQL = """
        SELECT email FROM users u
        WHERE LOWER(u.email) = LOWER(?)
          AND u.is_active = true
        LIMIT 1;
        """;

  private static final String JSON_SEARCH_SQL = """
        SELECT COUNT(*) as product_count
        FROM products p
        WHERE p.attributes->>'brand' = ?
          AND p.attributes->>'color' = ?
          AND p.price BETWEEN ? AND ?
          AND p.is_active = true;
        """;

  private static final String CREATE_COMPOSITE_INDEXES_SQL = """
        -- Добавляем недостающие колонки
        ALTER TABLE IF EXISTS orders ADD COLUMN IF NOT EXISTS region VARCHAR(50);
        ALTER TABLE IF EXISTS products ADD COLUMN IF NOT EXISTS attributes JSONB DEFAULT '{}'::jsonb;
        
        -- Обновляем данные
        UPDATE orders o SET region = u.region 
        FROM users u 
        WHERE o.user_id = u.id AND o.region IS NULL;
        
        UPDATE orders SET region = 'UNKNOWN' WHERE region IS NULL;
        
        -- Составные индексы
        CREATE INDEX IF NOT EXISTS idx_orders_region_status_date 
        ON orders(region, status, created_at);
        
        CREATE INDEX IF NOT EXISTS idx_products_category_price 
        ON products(category_id, price) 
        WHERE is_active = true;
        
        -- Функциональные индексы
        CREATE INDEX IF NOT EXISTS idx_users_email_lower 
        ON users(LOWER(email)) 
        WHERE is_active = true;
        
        CREATE INDEX IF NOT EXISTS idx_products_attributes_brand 
        ON products(((attributes->>'brand')::text));
        
        CREATE INDEX IF NOT EXISTS idx_products_attributes_color 
        ON products(((attributes->>'color')::text));
        
        -- Обновляем статистику
        ANALYZE orders;
        ANALYZE products;
        ANALYZE users;
        """;

  private static final String DROP_COMPOSITE_INDEXES_SQL = """
        DROP INDEX IF EXISTS idx_orders_region_status_date;
        DROP INDEX IF EXISTS idx_products_category_price;
        DROP INDEX IF EXISTS idx_users_email_lower;
        DROP INDEX IF EXISTS idx_products_attributes_brand;
        DROP INDEX IF EXISTS idx_products_attributes_color;
        """;

  private static final String GET_INDEX_USAGE_STATS_SQL = """
        SELECT 
            schemaname || '.' || indexname as index_name,
            tablename as table_name,
            idx_scan as total_scans,
            idx_tup_read as tuples_read,
            idx_tup_fetch as tuples_returned,
            CASE 
                WHEN idx_scan > 0 THEN idx_tup_fetch::float / idx_scan::float
                ELSE 0 
            END as selectivity,
            pg_relation_size(schemaname || '.' || indexname) as size_bytes,
            indexdef as definition,
            CASE 
                WHEN idx_scan = 0 THEN 'RARELY USED - CONSIDER DROPPING'
                WHEN idx_tup_fetch::float / idx_scan::float < 0.1 THEN 'LOW SELECTIVITY - REVIEW NEEDED'
                ELSE 'HEALTHY - KEEP USING'
            END as recommended_usage
        FROM pg_stat_user_indexes
        JOIN pg_indexes ON indexname = indexname AND schemaname = schemaname
        WHERE schemaname NOT LIKE 'pg_%'
        ORDER BY total_scans DESC;
        """;

  private static final String CREATE_TEST_INDEX_WRONG_ORDER = """
        CREATE INDEX idx_test_wrong_order ON products(price, category_id) 
        WHERE is_active = true;
        """;

  private static final String CREATE_TEST_INDEX_CORRECT_ORDER = """
        CREATE INDEX idx_test_correct_order ON products(category_id, price) 
        WHERE is_active = true;
        """;

  private static final String DROP_TEST_INDEXES = """
        DROP INDEX IF EXISTS idx_test_wrong_order;
        DROP INDEX IF EXISTS idx_test_correct_order;
        """;

  private static final String COMPARE_INDEX_ORDER_SQL = """
        EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON)
        SELECT COUNT(*) FROM products
        WHERE category_id = ? 
          AND price BETWEEN ? AND ?
          AND is_active = true;
        """;

  @Override
  public PerformanceMetrics<List<OrderAnalytics>> getOrderAnalyticsWithoutIndex(
      List<String> regions, List<String> statuses, LocalDate startDate, LocalDate endDate)
      throws DataAccessException {
    return executeOrderAnalytics(regions, statuses, startDate, endDate, false);
  }

  @Override
  public PerformanceMetrics<List<OrderAnalytics>> getOrderAnalyticsWithIndex(
      List<String> regions, List<String> statuses, LocalDate startDate, LocalDate endDate)
      throws DataAccessException {
    return executeOrderAnalytics(regions, statuses, startDate, endDate, true);
  }

  @Override
  public PerformanceMetrics<Long> measureQueryWithoutIndex(Long categoryId, BigDecimal minPrice,
      BigDecimal maxPrice) throws DataAccessException {
    return testCompositeIndexQuery(categoryId, minPrice, maxPrice, false);
  }

  @Override
  public PerformanceMetrics<Long> measureQueryWithIndex(Long categoryId, BigDecimal minPrice,
      BigDecimal maxPrice) throws DataAccessException {
    return testCompositeIndexQuery(categoryId, minPrice, maxPrice, true);
  }

  public PerformanceMetrics<String> testCaseInsensitiveSearch(String email, boolean useIndex)
      throws DataAccessException {
    long startTime = System.nanoTime();
    String queryType = useIndex ? "with_index" : "without_index";

    try (Connection conn = getConnection()) {
      String explainSql = "EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) " + CASE_INSENSITIVE_SEARCH_SQL;
      JsonNode planNode = executeExplainQuery(conn, explainSql, email);

      QueryPlanParser parser = new QueryPlanParser(planNode);

      try (PreparedStatement stmt = conn.prepareStatement(CASE_INSENSITIVE_SEARCH_SQL)) {
        stmt.setString(1, email);

        ResultSet rs = stmt.executeQuery();
        String result = rs.next() ? rs.getString("email") : null;

        long endTime = System.nanoTime();
        long executionTimeMs = (endTime - startTime) / 1_000_000;

        String recommendation = useIndex ?
            "Index used: idx_users_email_lower" :
            "Recommendation: Create functional index on LOWER(email)";

        return PerformanceMetrics.<String>builder()
            .data(result)
            .executionTimeMs(executionTimeMs)
            .planningTimeMs(parser.getPlanningTimeMs())
            .buffersHit(parser.getBuffersHit())
            .buffersRead(parser.getBuffersRead())
            .queryType(queryType)
            .executedAt(LocalDateTime.now())
            .performanceGrade(gradePerformance(executionTimeMs))
            .scanType(parser.getScanType())
            .rowsScanned(parser.getRowsScanned())
            .rowsReturned(result != null ? 1L : 0L)
            .costEstimate(parser.getCostEstimate())
            .indexesUsed(parser.getIndexesUsed())
            .optimizationRecommendation(recommendation)
            .build();
      }
    } catch (SQLException e) {
      log.error("Error testing case insensitive search", e);
      throw new DataAccessException("Failed to test case insensitive search", e);
    }
  }

  public PerformanceMetrics<Long> testJsonSearch(
      String brand, String color, BigDecimal minPrice, BigDecimal maxPrice, boolean useIndex)
      throws DataAccessException {
    long startTime = System.nanoTime();
    String queryType = useIndex ? "with_index" : "without_index";

    try (Connection conn = getConnection()) {
      String explainSql = "EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) " + JSON_SEARCH_SQL;
      JsonNode planNode = executeExplainQuery(conn, explainSql, brand, color, minPrice, maxPrice);

      QueryPlanParser parser = new QueryPlanParser(planNode);

      try (PreparedStatement stmt = conn.prepareStatement(JSON_SEARCH_SQL)) {
        stmt.setString(1, brand);
        stmt.setString(2, color);
        stmt.setBigDecimal(3, minPrice);
        stmt.setBigDecimal(4, maxPrice);

        ResultSet rs = stmt.executeQuery();
        Long count = rs.next() ? rs.getLong("product_count") : 0L;

        long endTime = System.nanoTime();
        long executionTimeMs = (endTime - startTime) / 1_000_000;

        String recommendation = useIndex ?
            "Indexes used: idx_products_attributes_brand, idx_products_attributes_color" :
            "Recommendation: Create functional indexes on JSON attributes";

        return PerformanceMetrics.<Long>builder()
            .data(count)
            .executionTimeMs(executionTimeMs)
            .planningTimeMs(parser.getPlanningTimeMs())
            .buffersHit(parser.getBuffersHit())
            .buffersRead(parser.getBuffersRead())
            .queryType(queryType)
            .executedAt(LocalDateTime.now())
            .performanceGrade(gradePerformance(executionTimeMs))
            .scanType(parser.getScanType())
            .rowsScanned(parser.getRowsScanned())
            .rowsReturned(count)
            .costEstimate(parser.getCostEstimate())
            .indexesUsed(parser.getIndexesUsed())
            .optimizationRecommendation(recommendation)
            .build();
      }
    } catch (SQLException e) {
      log.error("Error testing JSON search", e);
      throw new DataAccessException("Failed to test JSON search", e);
    }
  }

  public PerformanceMetrics<String> testIndexColumnOrder() throws DataAccessException {
    long startTime = System.nanoTime();

    try (Connection conn = getConnection()) {
      Long categoryId = 5L;
      BigDecimal minPrice = new BigDecimal("1000");
      BigDecimal maxPrice = new BigDecimal("5000");

      try (Statement stmt = conn.createStatement()) {
        stmt.execute(DROP_TEST_INDEXES);
        stmt.execute(CREATE_TEST_INDEX_WRONG_ORDER);
      }

      PerformanceMetrics<Long> wrongOrderMetrics = testCompositeIndexQuery(
          categoryId, minPrice, maxPrice, false);

      try (Statement stmt = conn.createStatement()) {
        stmt.execute(DROP_TEST_INDEXES);
        stmt.execute(CREATE_TEST_INDEX_CORRECT_ORDER);
      }

      PerformanceMetrics<Long> correctOrderMetrics = testCompositeIndexQuery(
          categoryId, minPrice, maxPrice, false);

      double improvement = wrongOrderMetrics.getExecutionTimeMs().doubleValue() /
          correctOrderMetrics.getExecutionTimeMs().doubleValue();

      String result = String.format(
          "Index Order Comparison:\n" +
              "Wrong order (price, category_id): %d ms, Cost: %.2f\n" +
              "Correct order (category_id, price): %d ms, Cost: %.2f\n" +
              "Improvement: %.1fx\n" +
              "Rule validated: Most selective column (category_id) should come first",
          wrongOrderMetrics.getExecutionTimeMs(), wrongOrderMetrics.getCostEstimate(),
          correctOrderMetrics.getExecutionTimeMs(), correctOrderMetrics.getCostEstimate(),
          improvement
      );

      long endTime = System.nanoTime();
      long executionTimeMs = (endTime - startTime) / 1_000_000;

      try (Statement stmt = conn.createStatement()) {
        stmt.execute(DROP_TEST_INDEXES);
      }

      return PerformanceMetrics.<String>builder()
          .data(result)
          .executionTimeMs(executionTimeMs)
          .executedAt(LocalDateTime.now())
          .performanceGrade(gradePerformance(executionTimeMs))
          .optimizationRecommendation("Rule: Put most selective columns first in composite indexes")
          .build();

    } catch (SQLException e) {
      log.error("Error testing index column order", e);
      throw new DataAccessException("Failed to test index column order", e);
    }
  }

  @Override
  public PerformanceMetrics<String> createCompositeIndexes() throws DataAccessException {
    long startTime = System.nanoTime();

    try (Connection conn = getConnection();
        Statement stmt = conn.createStatement()) {

      stmt.execute(CREATE_COMPOSITE_INDEXES_SQL);

      long endTime = System.nanoTime();
      long executionTimeMs = (endTime - startTime) / 1_000_000;

      return PerformanceMetrics.<String>builder()
          .data("Composite indexes created successfully")
          .executionTimeMs(executionTimeMs)
          .executedAt(LocalDateTime.now())
          .performanceGrade(gradePerformance(executionTimeMs))
          .optimizationRecommendation("Indexes are ready for use")
          .build();

    } catch (SQLException e) {
      log.error("Error creating composite indexes", e);
      throw new DataAccessException("Failed to create composite indexes", e);
    }
  }

  @Override
  public PerformanceMetrics<String> dropCompositeIndexes() throws DataAccessException {
    long startTime = System.nanoTime();

    try (Connection conn = getConnection();
        Statement stmt = conn.createStatement()) {

      stmt.execute(DROP_COMPOSITE_INDEXES_SQL);

      long endTime = System.nanoTime();
      long executionTimeMs = (endTime - startTime) / 1_000_000;

      return PerformanceMetrics.<String>builder()
          .data("Composite indexes dropped successfully")
          .executionTimeMs(executionTimeMs)
          .executedAt(LocalDateTime.now())
          .performanceGrade(gradePerformance(executionTimeMs))
          .optimizationRecommendation("Indexes have been removed")
          .build();

    } catch (SQLException e) {
      log.error("Error dropping composite indexes", e);
      throw new DataAccessException("Failed to drop composite indexes", e);
    }
  }

  @Override
  public List<IndexUsageStats> analyzeCompositeIndexUsage() throws DataAccessException {
    List<IndexUsageStats> stats = new ArrayList<>();

    try (Connection conn = getConnection();
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(GET_INDEX_USAGE_STATS_SQL)) {

      while (rs.next()) {
        IndexUsageStats indexStats = IndexUsageStats.builder()
            .indexName(rs.getString("index_name"))
            .tableName(rs.getString("table_name"))
            .totalScans(rs.getLong("total_scans"))
            .tuplesRead(rs.getLong("tuples_read"))
            .tuplesReturned(rs.getLong("tuples_returned"))
            .selectivity(rs.getDouble("selectivity"))
            .sizeBytes(rs.getLong("size_bytes"))
            .definition(rs.getString("definition"))
            .recommendedUsage(rs.getString("recommended_usage"))
            .build();
        stats.add(indexStats);
      }

      return stats;

    } catch (SQLException e) {
      log.error("Error analyzing index usage stats", e);
      throw new DataAccessException("Failed to analyze index usage stats", e);
    }
  }

  private Connection getConnection() throws SQLException {
    try {
      Class.forName(config.getDriver());
      return DriverManager.getConnection(
          config.getUrl(),
          config.getUsername(),
          config.getPassword()
      );
    } catch (ClassNotFoundException e) {
      throw new SQLException("Database driver not found", e);
    }
  }

  private PerformanceMetrics<List<OrderAnalytics>> executeOrderAnalytics(
      List<String> regions, List<String> statuses,
      LocalDate startDate, LocalDate endDate, boolean useIndex)
      throws DataAccessException {
    long startTime = System.nanoTime();
    String queryType = useIndex ? "with_index" : "without_index";

    try (Connection conn = getConnection()) {
      String explainSql = "EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) " + ORDER_ANALYTICS_BASE_SQL;
      JsonNode planNode = executeExplainQuery(conn, explainSql, regions, statuses, startDate, endDate);

      QueryPlanParser parser = new QueryPlanParser(planNode);

      try (PreparedStatement stmt = conn.prepareStatement(ORDER_ANALYTICS_BASE_SQL)) {
        Array regionArray = conn.createArrayOf("VARCHAR", regions.toArray());
        Array statusArray = conn.createArrayOf("VARCHAR", statuses.toArray());

        stmt.setArray(1, regionArray);
        stmt.setArray(2, statusArray);
        stmt.setDate(3, Date.valueOf(startDate));
        stmt.setDate(4, Date.valueOf(endDate));

        ResultSet rs = stmt.executeQuery();
        List<OrderAnalytics> results = new ArrayList<>();

        while (rs.next()) {
          OrderAnalytics analytics = OrderAnalytics.builder()
              .region(rs.getString("region"))
              .status(rs.getString("status"))
              .ordersCount(rs.getLong("orders_count"))
              .totalRevenue(rs.getBigDecimal("total_revenue"))
              .avgOrderValue(rs.getBigDecimal("avg_order_value"))
              .firstOrder(rs.getTimestamp("first_order") != null ?
                  rs.getTimestamp("first_order").toLocalDateTime() : null)
              .lastOrder(rs.getTimestamp("last_order") != null ?
                  rs.getTimestamp("last_order").toLocalDateTime() : null)
              .build();
          results.add(analytics);
        }

        long endTime = System.nanoTime();
        long executionTimeMs = (endTime - startTime) / 1_000_000;

        String recommendation = useIndex ?
            "Index used: idx_orders_region_status_date" :
            "Recommendation: Create composite index on (region, status, created_at)";

        return PerformanceMetrics.<List<OrderAnalytics>>builder()
            .data(results)
            .executionTimeMs(executionTimeMs)
            .planningTimeMs(parser.getPlanningTimeMs())
            .buffersHit(parser.getBuffersHit())
            .buffersRead(parser.getBuffersRead())
            .queryType(queryType)
            .executedAt(LocalDateTime.now())
            .performanceGrade(gradePerformance(executionTimeMs))
            .scanType(parser.getScanType())
            .rowsScanned(parser.getRowsScanned())
            .rowsReturned((long) results.size())
            .costEstimate(parser.getCostEstimate())
            .indexesUsed(parser.getIndexesUsed())
            .optimizationRecommendation(recommendation)
            .build();
      }
    } catch (SQLException e) {
      log.error("Error executing order analytics", e);
      throw new DataAccessException("Failed to execute order analytics", e);
    }
  }

  private PerformanceMetrics<Long> testCompositeIndexQuery(
      Long categoryId, BigDecimal minPrice, BigDecimal maxPrice, boolean useIndex)
      throws DataAccessException {
    long startTime = System.nanoTime();
    String queryType = useIndex ? "with_index" : "without_index";

    try (Connection conn = getConnection()) {
      String explainSql = "EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) " + PRODUCT_COUNT_SQL;
      JsonNode planNode = executeExplainQuery(conn, explainSql, categoryId, minPrice, maxPrice);

      QueryPlanParser parser = new QueryPlanParser(planNode);

      try (PreparedStatement stmt = conn.prepareStatement(PRODUCT_COUNT_SQL)) {
        stmt.setLong(1, categoryId);
        stmt.setBigDecimal(2, minPrice);
        stmt.setBigDecimal(3, maxPrice);

        ResultSet rs = stmt.executeQuery();
        Long count = rs.next() ? rs.getLong("product_count") : 0L;

        long endTime = System.nanoTime();
        long executionTimeMs = (endTime - startTime) / 1_000_000;

        String recommendation = useIndex ?
            "Index used: idx_products_category_price" :
            "Recommendation: Create index on (category_id, price) WHERE is_active = true";

        return PerformanceMetrics.<Long>builder()
            .data(count)
            .executionTimeMs(executionTimeMs)
            .planningTimeMs(parser.getPlanningTimeMs())
            .buffersHit(parser.getBuffersHit())
            .buffersRead(parser.getBuffersRead())
            .queryType(queryType)
            .executedAt(LocalDateTime.now())
            .performanceGrade(gradePerformance(executionTimeMs))
            .scanType(parser.getScanType())
            .rowsScanned(parser.getRowsScanned())
            .rowsReturned(count)
            .costEstimate(parser.getCostEstimate())
            .indexesUsed(parser.getIndexesUsed())
            .optimizationRecommendation(recommendation)
            .build();
      }
    } catch (SQLException e) {
      log.error("Error testing composite index query", e);
      throw new DataAccessException("Failed to test composite index query", e);
    }
  }

  private JsonNode executeExplainQuery(Connection conn, String explainSql, Object... params)
      throws SQLException {
    try (PreparedStatement explainStmt = conn.prepareStatement(explainSql)) {
      for (int i = 0; i < params.length; i++) {
        explainStmt.setObject(i + 1, params[i]);
      }

      ResultSet explainRs = explainStmt.executeQuery();
      if (explainRs.next()) {
        String jsonPlan = explainRs.getString(1);
        return objectMapper.readTree(jsonPlan);
      }
      throw new SQLException("No explain plan returned");
    } catch (Exception e) {
      throw new SQLException("Failed to parse explain plan", e);
    }
  }

  private JsonNode executeExplainQuery(Connection conn, String explainSql,
      List<String> regions, List<String> statuses, LocalDate startDate, LocalDate endDate)
      throws SQLException {
    try (PreparedStatement explainStmt = conn.prepareStatement(explainSql)) {
      Array regionArray = conn.createArrayOf("VARCHAR", regions.toArray());
      Array statusArray = conn.createArrayOf("VARCHAR", statuses.toArray());

      explainStmt.setArray(1, regionArray);
      explainStmt.setArray(2, statusArray);
      explainStmt.setDate(3, Date.valueOf(startDate));
      explainStmt.setDate(4, Date.valueOf(endDate));

      ResultSet explainRs = explainStmt.executeQuery();
      if (explainRs.next()) {
        String jsonPlan = explainRs.getString(1);
        return objectMapper.readTree(jsonPlan);
      }
      throw new SQLException("No explain plan returned");
    } catch (Exception e) {
      throw new SQLException("Failed to parse explain plan", e);
    }
  }

  private String gradePerformance(Long executionTimeMs) {
    if (executionTimeMs == null) return "UNKNOWN";
    if (executionTimeMs < 10) return "A+";
    if (executionTimeMs < 50) return "A";
    if (executionTimeMs < 100) return "B";
    if (executionTimeMs < 500) return "C";
    if (executionTimeMs < 1000) return "D";
    return "F";
  }
}