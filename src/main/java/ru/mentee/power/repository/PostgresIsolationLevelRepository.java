package ru.mentee.power.repository;

import java.util.Map;

public class PostgresIsolationLevelRepository implements IsolationLevelRepository{

  @Override
  public <T> T executeWithIsolationLevel(String isolationLevel, TransactionOperation<T> operation) {
    return null;
  }

  @Override
  public TransactionContext startTransactionWithLevel(String isolationLevel) {
    return null;
  }

  @Override
  public OperationResult performConcurrentOperation(TransactionContext context, String operation,
      Map<String, Object> params) {
    return null;
  }
}
