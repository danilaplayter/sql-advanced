package ru.mentee.power.model;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionOperation<T> {
  private String operationId;
  private String transactionId;
  private String operationType;
  private String sqlQuery;
  private LocalDateTime executionTime;
  private Long durationMillis;
  private Integer rowsAffected;
  private Boolean success;
  private String errorMessage;
  private T result;
}