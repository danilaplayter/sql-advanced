package ru.mentee.power.model.acid;

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
public class TransactionHistory {
  private Long transactionId;
  private Long fromAccountId;
  private Long toAccountId;
  private BigDecimal amount;
  private String transactionType;
  private String status;
  private String description;
  private LocalDateTime createdAt;
  private LocalDateTime processedAt;
}