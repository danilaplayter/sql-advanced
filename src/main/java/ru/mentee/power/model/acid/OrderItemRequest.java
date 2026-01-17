package ru.mentee.power.model.acid;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemRequest {
  private Long productId;
  private Integer quantity;
  private String notes;
}