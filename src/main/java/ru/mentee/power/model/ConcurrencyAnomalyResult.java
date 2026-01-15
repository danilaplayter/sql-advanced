package ru.mentee.power.model;

import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConcurrencyAnomalyResult {
  private String anomalyType;
  private String isolationLevel;
  private Boolean anomalyDetected;
  private String detailedDescription;
  private List<String> executionSteps;
  private String initialValue;
  private String intermediateValue;
  private String finalValue;
  private LocalDateTime executionTime;
  private Long executionDurationMillis;
  private List<String> preventionRecommendations;
}