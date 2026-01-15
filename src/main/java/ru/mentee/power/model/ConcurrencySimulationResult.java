package ru.mentee.power.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConcurrencySimulationResult {
  private Integer totalOperations;
  private Double successRate;
  private Double averageResponseTime;
  private Integer deadlockCount;
  private Integer serializationFailureCount;
}