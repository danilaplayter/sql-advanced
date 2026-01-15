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
public class PhantomReadResult {
  private String sessionId;
  private String isolationLevel;
  private Integer firstReadCount;
  private Integer secondReadCount;
  private Boolean phantomReadDetected;
  private LocalDateTime firstReadTime;
  private LocalDateTime secondReadTime;
  private Integer newRecordsCount;
  private String query;
}