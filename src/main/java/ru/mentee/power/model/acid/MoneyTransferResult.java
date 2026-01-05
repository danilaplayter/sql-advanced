package ru.mentee.power.model.acid;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MoneyTransferResult {
  private Boolean success;
  private String transactionId;
  private Long fromAccountId;
  private Long toAccountId;
  private BigDecimal amount;
  private BigDecimal fromAccountNewBalance;
  private BigDecimal toAccountNewBalance;
  private String description;
  private LocalDateTime processedAt;
  private String status;
  private String errorMessage;
  private List<String> validationErrors;
}