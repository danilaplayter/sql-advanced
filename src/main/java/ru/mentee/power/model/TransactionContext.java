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
public class TransactionContext {
  private String transactionId;
  private String sessionId;
  private String isolationLevel;
  private Boolean isReadOnly;
  private LocalDateTime startTime;
  private LocalDateTime endTime;
  private String status;

  private Long durationMillis;
}