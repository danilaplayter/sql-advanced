package ru.mentee.power.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DirtyReadResult {
  private Long sessionId;
  private String isolationLevel;
  private Double initialBalance;
  private Double intermediateBalance;
  private Double finalBalance;
  private boolean dirtyReadDetected;
  private LocalDateTime operationStartTime;
  private LocalDateTime operationEndTime;
  private String description;
}