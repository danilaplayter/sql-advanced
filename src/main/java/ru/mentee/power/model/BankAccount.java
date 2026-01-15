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
public class BankAccount {
  private Long id;
  private Long ownerId;
  private BigDecimal balance;
  private String accountType;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
  private String status;
}