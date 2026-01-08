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
public class ConcurrencySimulationResult {
  private String isolationLevel;
  private Integer totalOperations;
  private Integer successfulOperations;
  private Integer failedOperations;
  private Integer deadlocksDetected;
  private Integer serializationFailures;
  private Double averageResponseTimeMs;
  private Double minResponseTimeMs;
  private Double maxResponseTimeMs;
  private LocalDateTime simulationStartTime;
  private LocalDateTime simulationEndTime;
  private Map<String, Object> additionalMetrics;
}