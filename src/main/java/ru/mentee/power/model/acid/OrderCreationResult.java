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
public class OrderCreationResult {
  private Boolean success;
  private Long orderId;
  private Long userId;
  private BigDecimal totalAmount;
  private List<OrderItemResult> items;
  private String paymentTransactionId;
  private BigDecimal accountNewBalance;
  private String orderStatus;
  private LocalDateTime createdAt;
  private String errorMessage;
  private List<String> validationErrors;
}