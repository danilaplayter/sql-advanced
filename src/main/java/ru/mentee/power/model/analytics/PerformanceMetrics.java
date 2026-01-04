package ru.mentee.power.model.analytics;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PerformanceMetrics<T> {
  private T data;
  private Long executionTimeMs;
  private Long planningTimeMs;
  private Long buffersHit;
  private Long buffersRead;
  private String queryType;
  private LocalDateTime executedAt;
  private String performanceGrade;
  private String scanType;
  private Long rowsScanned;
  private Long rowsReturned;
  private Double costEstimate;
  private List<String> indexesUsed;
  private String optimizationRecommendation;

  public static <T> PerformanceMetricsBuilder<T> builder() {
    return new PerformanceMetricsBuilder<T>();
  }
}