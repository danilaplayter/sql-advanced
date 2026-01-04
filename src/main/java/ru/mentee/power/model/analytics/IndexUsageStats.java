package ru.mentee.power.model.analytics;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class IndexUsageStats {
  private String indexName;
  private String tableName;
  private Long totalScans;
  private Long tuplesRead;
  private Long tuplesReturned;
  private Double selectivity;
  private Long sizeBytes;
  private String definition;
  private String recommendedUsage;
}