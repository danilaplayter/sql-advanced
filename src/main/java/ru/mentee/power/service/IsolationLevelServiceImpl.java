package ru.mentee.power.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ru.mentee.power.model.*;
import ru.mentee.power.repository.IsolationLevelRepository;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@RequiredArgsConstructor
public class IsolationLevelServiceImpl implements IsolationLevelService {

  private final IsolationLevelRepository repository;

  private static final String SELECT_ACCOUNT_BALANCE =
      "SELECT balance FROM accounts WHERE id = ?";

  private static final String UPDATE_ACCOUNT_BALANCE =
      "UPDATE accounts SET balance = balance + ?, last_updated = CURRENT_TIMESTAMP WHERE id = ?";

  private static final String SELECT_ORDERS_BY_STATUS =
      "SELECT COUNT(*) as order_count FROM orders WHERE status = ?";

  private static final String INSERT_ORDER =
      "INSERT INTO orders (user_id, total_amount, status, created_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP)";

  private static final String SELECT_PRODUCT_STOCK =
      "SELECT available_quantity, reserved_quantity FROM products WHERE id = ? FOR UPDATE";

  private static final String UPDATE_PRODUCT_STOCK =
      """
      UPDATE products 
      SET available_quantity = available_quantity - ?, 
          reserved_quantity = reserved_quantity + ?,
          last_updated = CURRENT_TIMESTAMP 
      WHERE id = ? AND available_quantity >= ?
      """;

  private static final String INSERT_BOOKING =
      "INSERT INTO concurrent_bookings (user_id, product_id, quantity, booking_time) VALUES (?, ?, ?, CURRENT_TIMESTAMP)";

  @Override
  public DirtyReadResult demonstrateDirtyReads(Long accountId) {
    log.info("Demonstrating dirty reads for account: {}", accountId);

    final double[] intermediateBalance = new double[1];
    final boolean[] dirtyReadDetected = new boolean[1];

    CompletableFuture<Void> transaction1 = CompletableFuture.runAsync(() -> {
      repository.executeWithIsolationLevel(
          IsolationLevel.READ_COMMITTED,
          connection -> {
            try {
              try (PreparedStatement ps = connection.prepareStatement(UPDATE_ACCOUNT_BALANCE)) {
                ps.setDouble(1, 1000.00);
                ps.setLong(2, accountId);
                int rows = ps.executeUpdate();
                log.debug("Transaction 1 updated {} rows", rows);
              }

              Thread.sleep(3000);

              connection.rollback();
              log.info("Transaction 1 rolled back");

            } catch (SQLException | InterruptedException e) {
              log.error("Transaction 1 failed", e);
              Thread.currentThread().interrupt();
            }
            return null;
          }
      );
    });

    CompletableFuture<Double> transaction2 = CompletableFuture.supplyAsync(() -> {
      return repository.executeWithIsolationLevel(
          IsolationLevel.READ_UNCOMMITTED,
          connection -> {
            try {
              double initialBalance = 0;
              try (PreparedStatement ps = connection.prepareStatement(SELECT_ACCOUNT_BALANCE)) {
                ps.setLong(1, accountId);
                try (ResultSet rs = ps.executeQuery()) {
                  if (rs.next()) {
                    initialBalance = rs.getDouble("balance");
                  }
                }
              }

              log.debug("Transaction 2 initial balance: {}", initialBalance);

              Thread.sleep(1000);

              double uncommittedBalance = 0;
              try (PreparedStatement ps = connection.prepareStatement(SELECT_ACCOUNT_BALANCE)) {
                ps.setLong(1, accountId);
                try (ResultSet rs = ps.executeQuery()) {
                  if (rs.next()) {
                    uncommittedBalance = rs.getDouble("balance");
                  }
                }
              }

              intermediateBalance[0] = uncommittedBalance;
              log.debug("Transaction 2 intermediate balance (uncommitted): {}", uncommittedBalance);

              Thread.sleep(3000);

              double finalBalance = 0;
              try (PreparedStatement ps = connection.prepareStatement(SELECT_ACCOUNT_BALANCE)) {
                ps.setLong(1, accountId);
                try (ResultSet rs = ps.executeQuery()) {
                  if (rs.next()) {
                    finalBalance = rs.getDouble("balance");
                  }
                }
              }

              log.debug("Transaction 2 final balance: {}", finalBalance);

              dirtyReadDetected[0] = Math.abs(uncommittedBalance - finalBalance) > 0.01 &&
                  Math.abs(uncommittedBalance - initialBalance) > 0.01;

              if (dirtyReadDetected[0]) {
                log.info("Dirty read detected! Uncommitted: {}, Final: {}",
                    uncommittedBalance, finalBalance);
              }

              return finalBalance;

            } catch (SQLException | InterruptedException e) {
              log.error("Transaction 2 failed", e);
              Thread.currentThread().interrupt();
              return 0.0;
            }
          }
      );
    });

    try {
      transaction1.get(10, TimeUnit.SECONDS);
      double finalBalance = transaction2.get(10, TimeUnit.SECONDS);

      return DirtyReadResult.builder()
          .sessionId(Thread.currentThread().getId())
          .isolationLevel("READ UNCOMMITTED")
          .initialBalance(0.0) // We don't track initial in this demo
          .intermediateBalance(intermediateBalance[0])
          .finalBalance(finalBalance)
          .dirtyReadDetected(dirtyReadDetected[0])
          .operationStartTime(LocalDateTime.now().minusSeconds(10))
          .operationEndTime(LocalDateTime.now())
          .description(dirtyReadDetected[0] ?
              "Dirty read detected - saw uncommitted data that was rolled back" :
              "No dirty read detected")
          .build();

    } catch (Exception e) {
      log.error("Failed to demonstrate dirty reads", e);
      throw new RuntimeException("Dirty read demonstration failed", e);
    }
  }

  @Override
  public NonRepeatableReadResult demonstrateNonRepeatableReads(Long accountId) {
    log.info("Demonstrating non-repeatable reads for account: {}", accountId);

    Map<String, Double> readings = new LinkedHashMap<>();
    AtomicInteger readCount = new AtomicInteger(0);
    boolean[] nonRepeatableDetected = new boolean[1];

    CompletableFuture<Void> updater = CompletableFuture.runAsync(() -> {
      repository.executeWithIsolationLevel(
          IsolationLevel.READ_COMMITTED,
          connection -> {
            try {
              Thread.sleep(1000);

              try (PreparedStatement ps = connection.prepareStatement(UPDATE_ACCOUNT_BALANCE)) {
                ps.setDouble(1, 500.00);
                ps.setLong(2, accountId);
                ps.executeUpdate();
              }

              connection.commit();
              log.info("Updater committed balance change");

            } catch (Exception e) {
              log.error("Updater failed", e);
            }
            return null;
          }
      );
    });

    NonRepeatableReadResult result = repository.executeWithIsolationLevel(
        IsolationLevel.READ_COMMITTED,
        connection -> {
          LocalDateTime startTime = LocalDateTime.now();

          try {
            double balance1 = readAccountBalance(connection, accountId);
            readings.put("Read 1", balance1);
            readCount.incrementAndGet();
            log.debug("First read: {}", balance1);

            Thread.sleep(2000);

            double balance2 = readAccountBalance(connection, accountId);
            readings.put("Read 2", balance2);
            readCount.incrementAndGet();
            log.debug("Second read: {}", balance2);

            nonRepeatableDetected[0] = Math.abs(balance1 - balance2) > 0.01;

            if (nonRepeatableDetected[0]) {
              log.info("Non-repeatable read detected! {} != {}", balance1, balance2);
            }

            return NonRepeatableReadResult.builder()
                .transactionId(Thread.currentThread().getId())
                .isolationLevel("READ COMMITTED")
                .balanceReadings(readings)
                .nonRepeatableReadDetected(nonRepeatableDetected[0])
                .startTime(startTime)
                .endTime(LocalDateTime.now())
                .readCount(readCount.get())
                .build();

          } catch (Exception e) {
            log.error("Reader failed", e);
            throw new RuntimeException("Non-repeatable read demonstration failed", e);
          }
        }
    );

    try {
      updater.get(10, TimeUnit.SECONDS);
    } catch (Exception e) {
      log.warn("Updater didn't complete normally", e);
    }

    return result;
  }

  @Override
  public PhantomReadResult demonstratePhantomReads(String status) {
    log.info("Demonstrating phantom reads for status: {}", status);

    List<Integer> recordCounts = new ArrayList<>();
    Map<String, Integer> phantomRecords = new HashMap<>();

    CompletableFuture<Void> inserter = CompletableFuture.runAsync(() -> {
      repository.executeWithIsolationLevel(
          IsolationLevel.READ_COMMITTED,
          connection -> {
            try {
              Thread.sleep(1500);

              try (PreparedStatement ps = connection.prepareStatement(INSERT_ORDER)) {
                ps.setLong(1, 999L); // Test user
                ps.setDouble(2, 100.00);
                ps.setString(3, status);
                ps.executeUpdate();
              }

              connection.commit();
              log.info("Inserter added new order with status: {}", status);

            } catch (Exception e) {
              log.error("Inserter failed", e);
            }
            return null;
          }
      );
    });

    PhantomReadResult result = repository.executeWithIsolationLevel(
        IsolationLevel.READ_COMMITTED,
        connection -> {
          LocalDateTime startTime = LocalDateTime.now();

          try {
            int count1 = countOrdersByStatus(connection, status);
            recordCounts.add(count1);
            log.debug("First count: {}", count1);

            Thread.sleep(3000);

            int count2 = countOrdersByStatus(connection, status);
            recordCounts.add(count2);
            log.debug("Second count: {}", count2);

            boolean phantomDetected = count1 != count2;
            if (phantomDetected) {
              phantomRecords.put("New records inserted", count2 - count1);
              log.info("Phantom read detected! {} -> {}", count1, count2);
            }

            return PhantomReadResult.builder()
                .transactionId(Thread.currentThread().getId())
                .isolationLevel("READ COMMITTED")
                .recordCounts(recordCounts)
                .phantomRecords(phantomRecords)
                .phantomReadDetected(phantomDetected)
                .startTime(startTime)
                .endTime(LocalDateTime.now())
                .build();

          } catch (Exception e) {
            log.error("Reader failed", e);
            throw new RuntimeException("Phantom read demonstration failed", e);
          }
        }
    );

    try {
      inserter.get(10, TimeUnit.SECONDS);
    } catch (Exception e) {
      log.warn("Inserter didn't complete normally", e);
    }

    return result;
  }

  @Override
  public ConcurrentBookingResult performConcurrentBooking(Long productId, Long userId,
      Integer quantity, String isolationLevel) {
    log.info("Performing concurrent booking for product: {}, user: {}, quantity: {}, isolation: {}",
        productId, userId, quantity, isolationLevel);

    LocalDateTime startTime = LocalDateTime.now();
    long startNano = System.nanoTime();

    IsolationLevel level = Arrays.stream(IsolationLevel.values())
        .filter(il -> il.getSql().equals(isolationLevel))
        .findFirst()
        .orElse(IsolationLevel.READ_COMMITTED);

    try {
      return repository.executeWithIsolationLevel(level, connection -> {
        try {
          int availableQuantity = 0;
          int reservedQuantity = 0;

          try (PreparedStatement ps = connection.prepareStatement(SELECT_PRODUCT_STOCK)) {
            ps.setLong(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
              if (rs.next()) {
                availableQuantity = rs.getInt("available_quantity");
                reservedQuantity = rs.getInt("reserved_quantity");
              }
            }
          }

          log.debug("Product {}: available={}, requested={}",
              productId, availableQuantity, quantity);

          if (availableQuantity < quantity) {
            return ConcurrentBookingResult.builder()
                .status(ConcurrentBookingResult.BookingStatus.INSUFFICIENT_STOCK)
                .userId(userId)
                .productId(productId)
                .requestedQuantity(quantity)
                .actualQuantity(0)
                .remainingStock(availableQuantity)
                .isolationLevel(isolationLevel)
                .bookingTime(LocalDateTime.now())
                .executionTimeMs((System.nanoTime() - startNano) / 1_000_000.0)
                .concurrencyIssues("Insufficient stock")
                .build();
          }

          try (PreparedStatement ps = connection.prepareStatement(UPDATE_PRODUCT_STOCK)) {
            ps.setInt(1, quantity);
            ps.setInt(2, quantity);
            ps.setLong(3, productId);
            ps.setInt(4, quantity);

            int rowsAffected = ps.executeUpdate();

            if (rowsAffected == 0) {
              return ConcurrentBookingResult.builder()
                  .status(BookingStatus.RACE_CONDITION)
                  .userId(userId)
                  .productId(productId)
                  .requestedQuantity(quantity)
                  .actualQuantity(0)
                  .remainingStock(availableQuantity) // Might be outdated
                  .isolationLevel(isolationLevel)
                  .bookingTime(LocalDateTime.now())
                  .executionTimeMs((System.nanoTime() - startNano) / 1_000_000.0)
                  .concurrencyIssues("Race condition - stock modified by another transaction")
                  .build();
            }
          }

          // Record the booking
          try (PreparedStatement ps = connection.prepareStatement(INSERT_BOOKING)) {
            ps.setLong(1, userId);
            ps.setLong(2, productId);
            ps.setInt(3, quantity);
            ps.executeUpdate();
          }

          // Get final stock
          int finalStock = availableQuantity - quantity;

          return ConcurrentBookingResult.builder()
              .status(ConcurrentBookingResult.BookingStatus.SUCCESS)
              .userId(userId)
              .productId(productId)
              .requestedQuantity(quantity)
              .actualQuantity(quantity)
              .remainingStock(finalStock)
              .isolationLevel(isolationLevel)
              .bookingTime(LocalDateTime.now())
              .executionTimeMs((System.nanoTime() - startNano) / 1_000_000.0)
              .concurrencyIssues("None")
              .build();

        } catch (SQLException e) {
          log.error("Booking failed", e);

          return ConcurrentBookingResult.builder()
              .status(BookingStatus.FAILED)
              .userId(userId)
              .productId(productId)
              .requestedQuantity(quantity)
              .actualQuantity(0)
              .remainingStock(0)
              .isolationLevel(isolationLevel)
              .bookingTime(LocalDateTime.now())
              .executionTimeMs((System.nanoTime() - startNano) / 1_000_000.0)
              .concurrencyIssues("SQL Exception: " + e.getMessage())
              .build();
        }
      });

    } catch (Exception e) {
      log.error("Booking transaction failed", e);

      return ConcurrentBookingResult.builder()
          .status(BookingStatus.FAILED)
          .userId(userId)
          .productId(productId)
          .requestedQuantity(quantity)
          .actualQuantity(0)
          .remainingStock(0)
          .isolationLevel(isolationLevel)
          .bookingTime(LocalDateTime.now())
          .executionTimeMs((System.nanoTime() - startNano) / 1_000_000.0)
          .concurrencyIssues("Transaction failed: " + e.getMessage())
          .build();
    }
  }

  @Override
  public ConcurrencySimulationResult simulateHighConcurrency(Integer users, Integer operations,
      String isolationLevel) {
    log.info("Starting high concurrency simulation: users={}, operations={}, isolation={}",
        users, operations, isolationLevel);

    LocalDateTime startTime = LocalDateTime.now();
    ExecutorService executor = Executors.newFixedThreadPool(users);
    List<CompletableFuture<ConcurrentBookingResult>> futures = new ArrayList<>();

    AtomicInteger successfulOps = new AtomicInteger();
    AtomicInteger failedOps = new AtomicInteger();
    AtomicInteger deadlocks = new AtomicInteger();
    AtomicInteger serializationFailures = new AtomicInteger();
    List<Double> responseTimes = new CopyOnWriteArrayList<>();

    Long testProductId = 1L;
    resetProductStock(testProductId, 10);

    for (int i = 0; i < operations; i++) {
      final Long userId = (long) (i % users) + 1;
      final int operationNumber = i;

      CompletableFuture<ConcurrentBookingResult> future = CompletableFuture.supplyAsync(() -> {
        long opStart = System.nanoTime();

        try {
          int quantity = ThreadLocalRandom.current().nextInt(1, 4);

          ConcurrentBookingResult result = performConcurrentBooking(
              testProductId, userId, quantity, isolationLevel
          );

          double responseTime = (System.nanoTime() - opStart) / 1_000_000.0;
          responseTimes.add(responseTime);

          if (result.getStatus() == ConcurrentBookingResult.BookingStatus.SUCCESS) {
            successfulOps.incrementAndGet();
          } else {
            failedOps.incrementAndGet();

            if (result.getConcurrencyIssues().contains("deadlock")) {
              deadlocks.incrementAndGet();
            }
            if (result.getConcurrencyIssues().contains("serialization")) {
              serializationFailures.incrementAndGet();
            }
          }

          log.debug("Operation {} completed with status: {}",
              operationNumber, result.getStatus());

          return result;

        } catch (Exception e) {
          failedOps.incrementAndGet();
          log.error("Operation {} failed", operationNumber, e);
          return null;
        }
      }, executor);

      futures.add(future);
    }

    try {
      CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
          .get(60, TimeUnit.SECONDS);

      executor.shutdown();
      executor.awaitTermination(10, TimeUnit.SECONDS);

    } catch (Exception e) {
      log.error("Simulation timeout or error", e);
      executor.shutdownNow();
    }

    LocalDateTime endTime = LocalDateTime.now();

    double avgResponseTime = responseTimes.stream()
        .mapToDouble(Double::doubleValue)
        .average()
        .orElse(0.0);

    double minResponseTime = responseTimes.stream()
        .mapToDouble(Double::doubleValue)
        .min()
        .orElse(0.0);

    double maxResponseTime = responseTimes.stream()
        .mapToDouble(Double::doubleValue)
        .max()
        .orElse(0.0);

    Map<String, Object> additionalMetrics = new HashMap<>();
    additionalMetrics.put("total_response_times", responseTimes.size());
    additionalMetrics.put("thread_pool_size", users);
    additionalMetrics.put("product_id", testProductId);

    return ConcurrencySimulationResult.builder()
        .isolationLevel(isolationLevel)
        .totalOperations(operations)
        .successfulOperations(successfulOps.get())
        .failedOperations(failedOps.get())
        .deadlocksDetected(deadlocks.get())
        .serializationFailures(serializationFailures.get())
        .averageResponseTimeMs(avgResponseTime)
        .minResponseTimeMs(minResponseTime)
        .maxResponseTimeMs(maxResponseTime)
        .simulationStartTime(startTime)
        .simulationEndTime(endTime)
        .additionalMetrics(additionalMetrics)
        .build();

  }

  public void resetTestData() {
    repository.cleanupTestData();
    repository.setupTestData();
    log.info("Test data reset completed");
  }

  public Map<String, Object> getIsolationLevelComparison() {
    Map<String, Object> comparison = new HashMap<>();

    for (IsolationLevel level : IsolationLevel.values()) {
      Map<String, Object> levelStats = new HashMap<>();

      ConcurrencySimulationResult simulation = simulateHighConcurrency(
          10, 100, level.getSql()
      );

      levelStats.put("success_rate",
          (double) simulation.getSuccessfulOperations() / simulation.getTotalOperations() * 100);
      levelStats.put("avg_response_time", simulation.getAverageResponseTimeMs());
      levelStats.put("deadlocks", simulation.getDeadlocksDetected());
      levelStats.put("serialization_failures", simulation.getSerializationFailures());

      comparison.put(level.getSql(), levelStats);
    }

    return comparison;
  }

  private double readAccountBalance(Connection connection, Long accountId) throws SQLException {
    try (PreparedStatement ps = connection.prepareStatement(SELECT_ACCOUNT_BALANCE)) {
      ps.setLong(1, accountId);
      try (ResultSet rs = ps.executeQuery()) {
        if (rs.next()) {
          return rs.getDouble("balance");
        }
      }
    }
    return 0.0;
  }

  private int countOrdersByStatus(Connection connection, String status) throws SQLException {
    try (PreparedStatement ps = connection.prepareStatement(SELECT_ORDERS_BY_STATUS)) {
      ps.setString(1, status);
      try (ResultSet rs = ps.executeQuery()) {
        if (rs.next()) {
          return rs.getInt("order_count");
        }
      }
    }
    return 0;
  }

  private void resetProductStock(Long productId, int quantity) {
    repository.executeWithIsolationLevel(
        IsolationLevel.READ_COMMITTED,
        connection -> {
          try (PreparedStatement ps = connection.prepareStatement(
              "UPDATE products SET available_quantity = ? WHERE id = ?")) {
            ps.setInt(1, quantity);
            ps.setLong(2, productId);
            ps.executeUpdate();
          }
          return null;
        }
    );
  }
}