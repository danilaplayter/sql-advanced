package ru.mentee.power.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PhantomReadResult {
  private Long transactionId;
  private String isolationLevel;
  private List<Integer> recordCounts;
  private Map<String, Integer> phantomRecords;
  private boolean phantomReadDetected;
  private LocalDateTime startTime;
  private LocalDateTime endTime;
}