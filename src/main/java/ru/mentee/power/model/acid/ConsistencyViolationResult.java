package ru.mentee.power.model.acid;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsistencyViolationResult {
  private Boolean violationOccurred;
  private String constraintName;
  private String errorMessage;
  private String attemptedAction;
  private String preventedState;
}