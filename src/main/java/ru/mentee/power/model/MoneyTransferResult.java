package ru.mentee.power.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MoneyTransferResult {
  private String status;
  private BigDecimal amount;
  private Long fromAccountId;
  private Long toAccountId;
  private BigDecimal fromAccountBalanceBefore;
  private BigDecimal fromAccountBalanceAfter;
  private BigDecimal toAccountBalanceBefore;
  private BigDecimal toAccountBalanceAfter;
  private String isolationLevel;
  private   LocalDateTime executionTime;
  private Long executionDurationMillis;
}