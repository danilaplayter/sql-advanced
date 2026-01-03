package ru.mentee.power.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {
  private Long id;
  private Long userId;
  private BigDecimal totalAmount;
  private String status;
  private String paymentMethod;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
  private String deliveryAddress;
}