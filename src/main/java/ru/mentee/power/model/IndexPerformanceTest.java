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
public class IndexPerformanceTest<T> {
  private T data;
  private Long executionTimeNanos;
  private Long executionTimeMs;
  private String queryPlan;
  private String operationType;
  private Long buffersHit;
  private Long buffersRead;
  private Long rowsScanned;
  private Long rowsReturned;
  private String performanceGrade;
  private LocalDateTime executedAt;
  private String indexUsed;
  private BigDecimal costEstimate;
  private Double actualTotalTime;
  private Double actualStartupTime;

  public double getBuffersHitRatio() {
    if (buffersHit == null || buffersRead == null || (buffersHit + buffersRead) == 0) {
      return 0.0;
    }
    return buffersHit.doubleValue() / (buffersHit + buffersRead);
  }
}