package ru.mentee.power.model.acid;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderCancellationResult {
  private Boolean success;
  private Long orderId;
  private String refundTransactionId;
  private BigDecimal refundAmount;
  private List<ProductRestoreResult> restoredProducts;
  private BigDecimal accountNewBalance;
  private String reason;
  private LocalDateTime cancelledAt;
  private String errorMessage;
}