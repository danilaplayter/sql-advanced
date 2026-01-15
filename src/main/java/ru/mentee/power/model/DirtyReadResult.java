package ru.mentee.power.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DirtyReadResult {
  private String sessionId;
  private String isolationLevel;
  private BigDecimal initialBalance;
  private BigDecimal intermediateBalance;
  private BigDecimal finalBalance;
  private Boolean dirtyReadDetected;
  private LocalDateTime operationStartTime;
  private LocalDateTime intermediateReadTime;
  private LocalDateTime operationEndTime;
}