package ru.mentee.power.model.acid;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemResult {
  private Long productId;
  private String productName;
  private String productSku;
  private Integer quantityOrdered;
  private Integer quantityReserved;
  private BigDecimal unitPrice;
  private BigDecimal totalPrice;
  private Integer newStockQuantity;
  private String status;
}
