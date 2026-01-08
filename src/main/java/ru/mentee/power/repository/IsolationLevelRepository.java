package ru.mentee.power.repository;

import java.util.Map;

/**
 * Repository для выполнения операций с различными уровнями изоляции транзакций.
 */
public interface IsolationLevelRepository {

  /**
   * Выполняет операцию с заданным уровнем изоляции.
   */
  <T> T executeWithIsolationLevel(String isolationLevel, TransactionOperation<T> operation);

  /**
   * Начинает транзакцию с указанным уровнем изоляции.
   */
  TransactionContext startTransactionWithLevel(String isolationLevel);

  /**
   * Выполняет конкурентную операцию для тестирования race conditions.
   */
  OperationResult performConcurrentOperation(TransactionContext context, String operation, Map<String, Object> params);
}
