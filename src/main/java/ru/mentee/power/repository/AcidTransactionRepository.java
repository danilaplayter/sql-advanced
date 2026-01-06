package ru.mentee.power.repository;

import ru.mentee.power.model.acid.*;
import ru.mentee.power.exception.DataAccessException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Repository для демонстрации ACID транзакций на уровне данных.
 */
public interface AcidTransactionRepository {

  /**
   * Выполняет атомарный перевод денег в рамках одной транзакции.
   *
   * @param fromAccountId счет списания
   * @param toAccountId счет зачисления
   * @param amount сумма перевода
   * @param description описание
   * @return результат транзакции
   * @throws DataAccessException при ошибках доступа к данным
   */
  MoneyTransferResult executeAtomicMoneyTransfer(
      Long fromAccountId, Long toAccountId, BigDecimal amount, String description
  ) throws DataAccessException;

  /**
   * Создает заказ, резервирует товары и списывает средства атомарно.
   *
   * @param userId пользователь
   * @param accountId счет оплаты
   * @param orderItems товары заказа
   * @return результат создания заказа
   * @throws DataAccessException при ошибках доступа к данным
   */
  OrderCreationResult createOrderAtomically(
      Long userId, Long accountId, List<OrderItemRequest> orderItems
  ) throws DataAccessException;

  /**
   * Отменяет заказ и возвращает средства атомарно.
   *
   * @param orderId идентификатор заказа
   * @param reason причина отмены
   * @return результат отмены
   * @throws DataAccessException при ошибках доступа к данным
   */
  OrderCancellationResult cancelOrderAtomically(Long orderId, String reason)
      throws DataAccessException;

  /**
   * Демонстрирует сломанную атомарность (БЕЗ транзакции).
   *
   * @param fromAccountId счет списания
   * @param toAccountId счет зачисления
   * @param amount сумма
   * @return результат с проблемами
   * @throws DataAccessException при ошибках доступа к данным
   */
  BrokenTransactionResult executeBrokenTransfer(
      Long fromAccountId, Long toAccountId, BigDecimal amount
  ) throws DataAccessException;

  /**
   * Получает баланс счета.
   *
   * @param accountId идентификатор счета
   * @return текущий баланс
   * @throws DataAccessException при ошибках доступа к данным
   */
  BigDecimal getAccountBalance(Long accountId) throws DataAccessException;

  /**
   * Получает историю транзакций.
   *
   * @param accountId счет
   * @param limit количество записей
   * @return история транзакций
   * @throws DataAccessException при ошибках доступа к данным
   */
  List<TransactionHistory> getTransactionHistory(Long accountId, Integer limit)
      throws DataAccessException;
}