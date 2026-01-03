package ru.mentee.power.model;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Product {
  private Long id;
  private String name;
  private String sku;
  private BigDecimal price;
  private Long categoryId;
  private String categoryName;
  private Integer stockQuantity;
  private String status;
  private LocalDateTime createdAt;
  private String description;
}