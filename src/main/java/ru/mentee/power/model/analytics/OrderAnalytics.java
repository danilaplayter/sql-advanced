package ru.mentee.power.model.analytics;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OrderAnalytics {
  private String region;
  private String status;
  private Long ordersCount;
  private BigDecimal totalRevenue;
  private BigDecimal avgOrderValue;
  private LocalDateTime firstOrder;
  private LocalDateTime lastOrder;
}