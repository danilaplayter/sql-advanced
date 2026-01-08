package ru.mentee.power.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConcurrentBookingResult {
  private BookingStatus status;
  private Long userId;
  private Long productId;
  private Integer requestedQuantity;
  private Integer actualQuantity;
  private Integer remainingStock;
  private String isolationLevel;
  private LocalDateTime bookingTime;
  private Double executionTimeMs;
  private String concurrencyIssues;
}