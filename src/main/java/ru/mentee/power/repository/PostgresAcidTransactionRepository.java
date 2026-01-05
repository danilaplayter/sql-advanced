package ru.mentee.power.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import ru.mentee.power.exception.DataAccessException;
import ru.mentee.power.model.acid.*;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Repository
@RequiredArgsConstructor
public class PostgresAcidTransactionRepository implements AcidTransactionRepository {

  private final JdbcTemplate jdbcTemplate;
  private final TransactionTemplate transactionTemplate;

  private static final String GET_ACCOUNT_INFO =
      "SELECT id, user_id, balance, is_active FROM accounts WHERE id = ? AND is_active = true";

  private static final String GET_PRODUCT_INFO =
      "SELECT id, name, sku, price, stock_quantity FROM products WHERE id = ? AND status = 'ACTIVE'";

  private static final String UPDATE_ACCOUNT_BALANCE =
      "UPDATE accounts SET balance = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";

  private static final String UPDATE_PRODUCT_STOCK =
      "UPDATE products SET stock_quantity = ? WHERE id = ?";

  private static final String INSERT_TRANSACTION =
      "INSERT INTO transactions (from_account_id, to_account_id, amount, transaction_type, status, " +
          "description, created_at, processed_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

  private static final String INSERT_ORDER =
      "INSERT INTO orders (user_id, payment_account_id, total_amount, status, created_at, confirmed_at) " +
          "VALUES (?, ?, ?, ?, ?, ?)";

  private static final String INSERT_ORDER_ITEM =
      "INSERT INTO order_items (order_id, product_id, quantity, unit_price, total_price, status) " +
          "VALUES (?, ?, ?, ?, ?, ?)";

  private static final String GET_ORDER_INFO =
      "SELECT o.*, a.user_id as account_user_id FROM orders o " +
          "JOIN accounts a ON o.payment_account_id = a.id " +
          "WHERE o.id = ?";

  private static final String GET_ORDER_ITEMS =
      "SELECT oi.*, p.name as product_name, p.sku as product_sku " +
          "FROM order_items oi JOIN products p ON oi.product_id = p.id " +
          "WHERE oi.order_id = ?";

  private static final String UPDATE_ORDER_STATUS =
      "UPDATE orders SET status = ? WHERE id = ?";

  private static final String GET_TRANSACTION_HISTORY =
      "SELECT t.* FROM transactions t " +
          "WHERE t.from_account_id = ? OR t.to_account_id = ? " +
          "ORDER BY t.processed_at DESC LIMIT ?";

  @Override
  @Transactional
  public MoneyTransferResult executeAtomicMoneyTransfer(
      Long fromAccountId, Long toAccountId, BigDecimal amount, String description)
      throws DataAccessException {

    log.debug("Starting atomic money transfer from {} to {} amount {}",
        fromAccountId, toAccountId, amount);

    try {
      Map<String, Object> fromAccount = getAccountInfo(fromAccountId);
      Map<String, Object> toAccount = getAccountInfo(toAccountId);

      BigDecimal fromBalance = (BigDecimal) fromAccount.get("balance");
      BigDecimal toBalance = (BigDecimal) toAccount.get("balance");

      if (fromBalance.compareTo(amount) < 0) {
        throw new DataAccessException("Insufficient funds in account: " + fromAccountId);
      }

      BigDecimal newFromBalance = fromBalance.subtract(amount);
      jdbcTemplate.update(UPDATE_ACCOUNT_BALANCE, newFromBalance, fromAccountId);

      BigDecimal newToBalance = toBalance.add(amount);
      jdbcTemplate.update(UPDATE_ACCOUNT_BALANCE, newToBalance, toAccountId);

      LocalDateTime now = LocalDateTime.now();
      KeyHolder keyHolder = new GeneratedKeyHolder();
      jdbcTemplate.update(connection -> {
        PreparedStatement ps = connection.prepareStatement(
            INSERT_TRANSACTION, Statement.RETURN_GENERATED_KEYS);
        ps.setLong(1, fromAccountId);
        ps.setLong(2, toAccountId);
        ps.setBigDecimal(3, amount);
        ps.setString(4, "TRANSFER");
        ps.setString(5, "COMPLETED");
        ps.setString(6, description);
        ps.setTimestamp(7, Timestamp.valueOf(now));
        ps.setTimestamp(8, Timestamp.valueOf(now));
        return ps;
      }, keyHolder);

      Long transactionId = keyHolder.getKey() != null ? keyHolder.getKey().longValue() : null;

      log.debug("Money transfer completed successfully, transactionId: {}", transactionId);

      return MoneyTransferResult.builder()
          .success(true)
          .transactionId(transactionId != null ? transactionId.toString() : null)
          .fromAccountId(fromAccountId)
          .toAccountId(toAccountId)
          .amount(amount)
          .fromAccountNewBalance(newFromBalance)
          .toAccountNewBalance(newToBalance)
          .description(description)
          .processedAt(now)
          .status("COMPLETED")
          .build();

    } catch (Exception e) {
      log.error("Error during atomic money transfer", e);
      throw new DataAccessException("Failed to execute money transfer: " + e.getMessage(), e);
    }
  }

  @Override
  @Transactional
  public OrderCreationResult createOrderAtomically(
      Long userId, Long accountId, List<OrderItemRequest> orderItems)
      throws DataAccessException {

    log.debug("Starting atomic order creation for user {}, account {}", userId, accountId);

    try {
      Map<String, Object> account = getAccountInfo(accountId);
      BigDecimal accountBalance = (BigDecimal) account.get("balance");
      Long accountUserId = ((Number) account.get("user_id")).longValue();

      if (!accountUserId.equals(userId)) {
        throw new DataAccessException("Account does not belong to user");
      }

      List<Map<String, Object>> products = new ArrayList<>();
      BigDecimal totalAmount = BigDecimal.ZERO;
      List<OrderItemResult> itemResults = new ArrayList<>();

      for (OrderItemRequest itemRequest : orderItems) {
        Map<String, Object> product = getProductInfo(itemRequest.getProductId());
        BigDecimal unitPrice = (BigDecimal) product.get("price");
        Integer stockQuantity = (Integer) product.get("stock_quantity");

        if (stockQuantity < itemRequest.getQuantity()) {
          throw new DataAccessException(
              "Product " + product.get("id") + " has insufficient stock. " +
                  "Available: " + stockQuantity + ", Requested: " + itemRequest.getQuantity());
        }

        BigDecimal itemTotal = unitPrice.multiply(BigDecimal.valueOf(itemRequest.getQuantity()));
        totalAmount = totalAmount.add(itemTotal);

        product.put("requestedQuantity", itemRequest.getQuantity());
        product.put("unitPrice", unitPrice);
        product.put("itemTotal", itemTotal);
        products.add(product);
      }

      if (accountBalance.compareTo(totalAmount) < 0) {
        throw new DataAccessException(
            "Insufficient funds. Balance: " + accountBalance + ", Required: " + totalAmount);
      }

      LocalDateTime now = LocalDateTime.now();
      KeyHolder orderKeyHolder = new GeneratedKeyHolder();
      jdbcTemplate.update(connection -> {
        PreparedStatement ps = connection.prepareStatement(
            INSERT_ORDER, Statement.RETURN_GENERATED_KEYS);
        ps.setLong(1, userId);
        ps.setLong(2, accountId);
        ps.setBigDecimal(3, totalAmount);
        ps.setString(4, "PENDING");
        ps.setTimestamp(5, Timestamp.valueOf(now));
        ps.setTimestamp(6, Timestamp.valueOf(now));
        return ps;
      }, orderKeyHolder);

      Long orderId = orderKeyHolder.getKey() != null ? orderKeyHolder.getKey().longValue() : null;

      if (orderId == null) {
        throw new DataAccessException("Failed to create order, no ID generated");
      }

      for (int i = 0; i < orderItems.size(); i++) {
        OrderItemRequest itemRequest = orderItems.get(i);
        Map<String, Object> product = products.get(i);

        Integer currentStock = (Integer) product.get("stock_quantity");
        Integer requestedQuantity = itemRequest.getQuantity();
        Integer newStockQuantity = currentStock - requestedQuantity;

        jdbcTemplate.update(UPDATE_PRODUCT_STOCK, newStockQuantity, itemRequest.getProductId());

        BigDecimal unitPrice = (BigDecimal) product.get("unitPrice");
        BigDecimal itemTotal = (BigDecimal) product.get("itemTotal");

        jdbcTemplate.update(INSERT_ORDER_ITEM,
            orderId,
            itemRequest.getProductId(),
            requestedQuantity,
            unitPrice,
            itemTotal,
            "RESERVED");

        OrderItemResult itemResult = OrderItemResult.builder()
            .productId(itemRequest.getProductId())
            .productName((String) product.get("name"))
            .productSku((String) product.get("sku"))
            .quantityOrdered(requestedQuantity)
            .quantityReserved(requestedQuantity)
            .unitPrice(unitPrice)
            .totalPrice(itemTotal)
            .newStockQuantity(newStockQuantity)
            .status("RESERVED")
            .build();

        itemResults.add(itemResult);
      }

      BigDecimal newBalance = accountBalance.subtract(totalAmount);
      jdbcTemplate.update(UPDATE_ACCOUNT_BALANCE, newBalance, accountId);

      KeyHolder paymentKeyHolder = new GeneratedKeyHolder();
      jdbcTemplate.update(connection -> {
        PreparedStatement ps = connection.prepareStatement(
            INSERT_TRANSACTION, Statement.RETURN_GENERATED_KEYS);
        ps.setLong(1, accountId);
        ps.setLong(2, null);
        ps.setBigDecimal(3, totalAmount);
        ps.setString(4, "PAYMENT");
        ps.setString(5, "COMPLETED");
        ps.setString(6, "Payment for order #" + orderId);
        ps.setTimestamp(7, Timestamp.valueOf(now));
        ps.setTimestamp(8, Timestamp.valueOf(now));
        return ps;
      }, paymentKeyHolder);

      Long paymentTransactionId = paymentKeyHolder.getKey() != null ?
          paymentKeyHolder.getKey().longValue() : null;

      jdbcTemplate.update(UPDATE_ORDER_STATUS, "CONFIRMED", orderId);

      log.debug("Order created successfully, orderId: {}, paymentTransactionId: {}",
          orderId, paymentTransactionId);

      return OrderCreationResult.builder()
          .success(true)
          .orderId(orderId)
          .userId(userId)
          .totalAmount(totalAmount)
          .items(itemResults)
          .paymentTransactionId(
              paymentTransactionId != null ? paymentTransactionId.toString() : null)
          .accountNewBalance(newBalance)
          .orderStatus("CONFIRMED")
          .createdAt(now)
          .build();

    } catch (Exception e) {
      log.error("Error during atomic order creation", e);
      throw new DataAccessException("Failed to create order: " + e.getMessage(), e);
    }
  }

  @Override
  @Transactional
  public OrderCancellationResult cancelOrderAtomically(Long orderId, String reason)
      throws DataAccessException {

    log.debug("Starting atomic order cancellation for order {}", orderId);

    try {
      Map<String, Object> order = jdbcTemplate.queryForMap(GET_ORDER_INFO, orderId);
      String currentStatus = (String) order.get("status");
      Long accountId = ((Number) order.get("payment_account_id")).longValue();
      BigDecimal totalAmount = (BigDecimal) order.get("total_amount");

      if (!"CONFIRMED".equals(currentStatus) && !"PENDING".equals(currentStatus)) {
        throw new DataAccessException(
            "Order cannot be cancelled. Current status: " + currentStatus);
      }

      List<Map<String, Object>> orderItems = jdbcTemplate.queryForList(GET_ORDER_ITEMS, orderId);
      List<ProductRestoreResult> restoredProducts = new ArrayList<>();

      for (Map<String, Object> item : orderItems) {
        Long productId = ((Number) item.get("product_id")).longValue();
        Integer quantity = (Integer) item.get("quantity");

        Map<String, Object> product = getProductInfo(productId);
        Integer currentStock = (Integer) product.get("stock_quantity");
        Integer newStockQuantity = currentStock + quantity;

        jdbcTemplate.update(UPDATE_PRODUCT_STOCK, newStockQuantity, productId);

        ProductRestoreResult restoreResult = ProductRestoreResult.builder()
            .productId(productId)
            .productName((String) product.get("name"))
            .quantityRestored(quantity)
            .newStockQuantity(newStockQuantity)
            .status("RESTORED")
            .build();

        restoredProducts.add(restoreResult);
      }

      Map<String, Object> account = getAccountInfo(accountId);
      BigDecimal currentBalance = (BigDecimal) account.get("balance");
      BigDecimal newBalance = currentBalance.add(totalAmount);

      jdbcTemplate.update(UPDATE_ACCOUNT_BALANCE, newBalance, accountId);

      LocalDateTime now = LocalDateTime.now();
      KeyHolder refundKeyHolder = new GeneratedKeyHolder();
      jdbcTemplate.update(connection -> {
        PreparedStatement ps = connection.prepareStatement(
            INSERT_TRANSACTION, Statement.RETURN_GENERATED_KEYS);
        ps.setLong(1, null);
        ps.setLong(2, accountId);
        ps.setBigDecimal(3, totalAmount);
        ps.setString(4, "REFUND");
        ps.setString(5, "COMPLETED");
        ps.setString(6, "Refund for order #" + orderId + ": " + reason);
        ps.setTimestamp(7, Timestamp.valueOf(now));
        ps.setTimestamp(8, Timestamp.valueOf(now));
        return ps;
      }, refundKeyHolder);

      Long refundTransactionId = refundKeyHolder.getKey() != null ?
          refundKeyHolder.getKey().longValue() : null;

      jdbcTemplate.update(UPDATE_ORDER_STATUS, "CANCELLED", orderId);

      log.debug("Order cancelled successfully, refundTransactionId: {}", refundTransactionId);

      return OrderCancellationResult.builder()
          .success(true)
          .orderId(orderId)
          .refundTransactionId(
              refundTransactionId != null ? refundTransactionId.toString() : null)
          .refundAmount(totalAmount)
          .restoredProducts(restoredProducts)
          .accountNewBalance(newBalance)
          .reason(reason)
          .cancelledAt(now)
          .build();

    } catch (Exception e) {
      log.error("Error during atomic order cancellation", e);
      throw new DataAccessException("Failed to cancel order: " + e.getMessage(), e);
    }
  }

  @Override
  public BrokenTransactionResult executeBrokenTransfer(
      Long fromAccountId, Long toAccountId, BigDecimal amount)
      throws DataAccessException {

    log.debug("Executing broken transfer (without transaction)");

    List<String> problems = new ArrayList<>();
    Boolean fromAccountUpdated = false;
    Boolean toAccountUpdated = false;
    Boolean transactionRecorded = false;
    BigDecimal fromAccountBalance = null;
    BigDecimal toAccountBalance = null;

    try {
      Map<String, Object> fromAccount = getAccountInfo(fromAccountId);
      Map<String, Object> toAccount = getAccountInfo(toAccountId);

      BigDecimal initialFromBalance = (BigDecimal) fromAccount.get("balance");
      BigDecimal initialToBalance = (BigDecimal) toAccount.get("balance");

      fromAccountBalance = initialFromBalance;
      toAccountBalance = initialToBalance;

      BigDecimal newFromBalance = initialFromBalance.subtract(amount);
      int fromUpdateResult = jdbcTemplate.update(
          UPDATE_ACCOUNT_BALANCE, newFromBalance, fromAccountId);

      if (fromUpdateResult > 0) {
        fromAccountUpdated = true;
        fromAccountBalance = newFromBalance;
      } else {
        problems.add("Failed to update sender account balance");
      }

      throw new RuntimeException("Simulated database failure between operations");

    } catch (Exception e) {
      problems.add("Exception occurred during transfer: " + e.getMessage());
      problems.add("Database state is inconsistent!");
      problems.add("Sender account may be updated but receiver account not");

      log.warn("Broken transfer completed with problems: {}", problems);

      return BrokenTransactionResult.builder()
          .partiallyCompleted(true)
          .fromAccountBalance(fromAccountBalance)
          .toAccountBalance(toAccountBalance)
          .fromAccountUpdated(fromAccountUpdated)
          .toAccountUpdated(toAccountUpdated)
          .transactionRecorded(transactionRecorded)
          .errorMessage(e.getMessage())
          .problems(problems)
          .build();
    }
  }

  @Override
  public BigDecimal getAccountBalance(Long accountId) throws DataAccessException {
    try {
      Map<String, Object> account = getAccountInfo(accountId);
      return (BigDecimal) account.get("balance");
    } catch (Exception e) {
      throw new DataAccessException("Failed to get account balance: " + e.getMessage(), e);
    }
  }

  @Override
  public List<TransactionHistory> getTransactionHistory(Long accountId, Integer limit)
      throws DataAccessException {

    try {
      List<TransactionHistory> history = jdbcTemplate.query(
          GET_TRANSACTION_HISTORY,
          new Object[]{accountId, accountId, limit},
          (rs, rowNum) -> TransactionHistory.builder()
              .transactionId(rs.getLong("id"))
              .fromAccountId(rs.getObject("from_account_id", Long.class))
              .toAccountId(rs.getObject("to_account_id", Long.class))
              .amount(rs.getBigDecimal("amount"))
              .transactionType(rs.getString("transaction_type"))
              .status(rs.getString("status"))
              .description(rs.getString("description"))
              .createdAt(rs.getTimestamp("created_at") != null ?
                  rs.getTimestamp("created_at").toLocalDateTime() : null)
              .processedAt(rs.getTimestamp("processed_at") != null ?
                  rs.getTimestamp("processed_at").toLocalDateTime() : null)
              .build()
      );

      return history;
    } catch (Exception e) {
      throw new DataAccessException("Failed to get transaction history: " + e.getMessage(), e);
    }
  }

  private Map<String, Object> getAccountInfo(Long accountId) {
    try {
      return jdbcTemplate.queryForMap(GET_ACCOUNT_INFO, accountId);
    } catch (Exception e) {
      throw new DataAccessException("Account not found or not active: " + accountId);
    }
  }

  private Map<String, Object> getProductInfo(Long productId) {
    try {
      return jdbcTemplate.queryForMap(GET_PRODUCT_INFO, productId);
    } catch (Exception e) {
      throw new DataAccessException("Product not found or not active: " + productId);
    }
  }
}