package ru.mentee.power.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class NonRepeatableReadResult {
  private Long transactionId;
  private String isolationLevel;
  private Map<String, Double> balanceReadings;
  private boolean nonRepeatableReadDetected;
  private LocalDateTime startTime;
  private LocalDateTime endTime;
  private int readCount;
}