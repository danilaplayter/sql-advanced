package ru.mentee.power.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mentee.power.exception.BusinessException;
import ru.mentee.power.exception.DataAccessException;
import ru.mentee.power.exception.InsufficientFundsException;
import ru.mentee.power.exception.ProductNotAvailableException;
import ru.mentee.power.model.acid.BrokenTransactionResult;
import ru.mentee.power.model.acid.ConsistencyViolationResult;
import ru.mentee.power.model.acid.MoneyTransferResult;
import ru.mentee.power.model.acid.OrderCancellationResult;
import ru.mentee.power.model.acid.OrderCreationResult;
import ru.mentee.power.model.acid.OrderItemRequest;
import ru.mentee.power.model.acid.TransactionHistory;
import ru.mentee.power.repository.AcidTransactionRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class AcidTransactionServiceImpl implements AcidTransactionService {

  private final AcidTransactionRepository repository;

  @Override
  @Transactional
  public MoneyTransferResult transferMoney(Long fromAccountId, Long toAccountId,
      BigDecimal amount, String description)
      throws InsufficientFundsException, BusinessException {

    log.info("Transferring money: {} from {} to {}, description: {}",
        amount, fromAccountId, toAccountId, description);

    try {
      return repository.executeAtomicMoneyTransfer(fromAccountId, toAccountId, amount, description);
    } catch (InsufficientFundsException e) {
      log.warn("Insufficient funds for transfer from {} to {} amount {}",
          fromAccountId, toAccountId, amount);
      throw e;
    } catch (DataAccessException e) {
      log.error("Database error during transfer", e);
      throw new BusinessException("Transfer failed", e);
    }
  }

  @Override
  @Transactional
  public OrderCreationResult createOrderWithPayment(Long userId, Long accountId,
      List<OrderItemRequest> orderItems)
      throws InsufficientFundsException, ProductNotAvailableException, BusinessException {

    log.info("Creating order for user {}, account {} with {} items",
        userId, accountId, orderItems.size());

    try {
      validateOrderRequest(userId, accountId, orderItems);

      return repository.createOrderAtomically(userId, accountId, orderItems);
    } catch (DataAccessException e) {
      if (e.getMessage().contains("Insufficient funds")) {
        throw new InsufficientFundsException(e.getMessage());
      }
      if (e.getMessage().contains("insufficient stock")) {
        throw new ProductNotAvailableException(e.getMessage());
      }
      throw new BusinessException("Order creation failed: " + e.getMessage(), e);
    }
  }

  @Override
  @Transactional
  public OrderCancellationResult cancelOrderWithRefund(Long orderId, String reason)
      throws BusinessException {

    log.info("Cancelling order {} with reason: {}", orderId, reason);

    try {
      return repository.cancelOrderAtomically(orderId, reason);
    } catch (DataAccessException e) {
      throw new BusinessException("Order cancellation failed: " + e.getMessage(), e);
    }
  }

  @Override
  public BrokenTransactionResult demonstrateBrokenAtomicity(Long fromAccountId,
      Long toAccountId,
      BigDecimal amount) {

    log.info("Demonstrating broken atomicity for transfer from {} to {} amount {}",
        fromAccountId, toAccountId, amount);

    try {
      return repository.executeBrokenTransfer(fromAccountId, toAccountId, amount);
    } catch (Exception e) {
      log.error("Demonstration of broken atomicity completed with exception", e);

      return BrokenTransactionResult.builder()
          .partiallyCompleted(true)
          .errorMessage(e.getMessage())
          .problems(List.of(
              "This demonstrates what happens WITHOUT transactions",
              "Exception: " + e.getMessage(),
              "Database state may be inconsistent"
          ))
          .build();
    }
  }

  @Override
  public ConsistencyViolationResult demonstrateConsistencyViolation(Long accountId,
      BigDecimal invalidAmount) {

    log.info("Demonstrating consistency violation for account {} with amount {}",
        accountId, invalidAmount);

    try {
      BigDecimal currentBalance = repository.getAccountBalance(accountId);
      BigDecimal newBalance = currentBalance.add(invalidAmount);

      if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
        return ConsistencyViolationResult.builder()
            .violationOccurred(true)
            .constraintName("PositiveBalanceConstraint")
            .errorMessage("Account balance cannot be negative. Attempted to set: " + newBalance)
            .attemptedAction("Update account balance to negative value")
            .preventedState("Negative account balance")
            .build();
      }

      return ConsistencyViolationResult.builder()
          .violationOccurred(false)
          .constraintName("PositiveBalanceConstraint")
          .errorMessage(null)
          .attemptedAction("Update account balance to " + newBalance)
          .preventedState("N/A - no violation")
          .build();

    } catch (DataAccessException e) {
      return ConsistencyViolationResult.builder()
          .violationOccurred(true)
          .constraintName("AccountExistenceConstraint")
          .errorMessage("Account not found or not active: " + e.getMessage())
          .attemptedAction("Access non-existent account")
          .preventedState("Invalid account access")
          .build();
    }
  }

  @Override
  public BigDecimal getAccountBalance(Long accountId) throws BusinessException {
    try {
      return repository.getAccountBalance(accountId);
    } catch (DataAccessException e) {
      throw new BusinessException("Failed to get account balance: " + e.getMessage(), e);
    }
  }

  @Override
  public List<TransactionHistory> getTransactionHistory(Long accountId, Integer limit)
      throws BusinessException {

    try {
      return repository.getTransactionHistory(accountId, limit);
    } catch (DataAccessException e) {
      throw new BusinessException("Failed to get transaction history: " + e.getMessage(), e);
    }
  }

  private void validateOrderRequest(Long userId, Long accountId, List<OrderItemRequest> orderItems) {
    List<String> validationErrors = new ArrayList<>();

    if (userId == null || userId <= 0) {
      validationErrors.add("Invalid user ID");
    }

    if (accountId == null || accountId <= 0) {
      validationErrors.add("Invalid account ID");
    }

    if (orderItems == null || orderItems.isEmpty()) {
      validationErrors.add("Order items cannot be empty");
    } else {
      for (int i = 0; i < orderItems.size(); i++) {
        OrderItemRequest item = orderItems.get(i);
        if (item.getProductId() == null || item.getProductId() <= 0) {
          validationErrors.add("Invalid product ID at position " + i);
        }
        if (item.getQuantity() == null || item.getQuantity() <= 0) {
          validationErrors.add("Invalid quantity at position " + i);
        }
      }
    }

    if (!validationErrors.isEmpty()) {
      throw new BusinessException("Validation errors: " + String.join(", ", validationErrors));
    }
  }
}