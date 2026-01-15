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
public class NonRepeatableReadResult {
  private String sessionId;
  private String isolationLevel;
  private Long accountId;
  private BigDecimal firstReadBalance;
  private BigDecimal secondReadBalance;
  private Boolean nonRepeatableReadDetected;
  private LocalDateTime firstReadTime;
  private LocalDateTime secondReadTime;
  private String concurrentTransactionId;
}