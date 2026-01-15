package ru.mentee.power.model;


import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConcurrentBookingResult {
  private String bookingStatus;
  private Integer requestedQuantity;
  private Integer actualReservedQuantity;
  private Integer stockAfterOperation;
  private List<String> concurrencyIssues;
}