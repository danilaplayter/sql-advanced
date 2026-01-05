package ru.mentee.power.model.acid;

import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BrokenTransactionResult {
  private Boolean partiallyCompleted;
  private BigDecimal fromAccountBalance;
  private BigDecimal toAccountBalance;
  private Boolean fromAccountUpdated;
  private Boolean toAccountUpdated;
  private Boolean transactionRecorded;
  private String errorMessage;
  private List<String> problems;
}