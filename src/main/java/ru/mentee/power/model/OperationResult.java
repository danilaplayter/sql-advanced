package ru.mentee.power.model;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationResult {
  private String operationId;
  private Boolean success;
  private String status;
  private LocalDateTime executionTime;
  private Long durationMillis;
  private String errorMessage;
  private Integer retryCount;
}