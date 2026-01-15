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
public class BankTransaction {
  private Long id;
  private Long accountId;
  private Long relatedAccountId;
  private BigDecimal amount;
  private String transactionType;
  private String status;
  private String description;
  private String purpose;
  private LocalDateTime createdAt;
  private LocalDateTime processedAt;
}
