package ru.mentee.power.model.acid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductRestoreResult {
  private Long productId;
  private String productName;
  private Integer quantityRestored;
  private Integer newStockQuantity;
  private String status;
}