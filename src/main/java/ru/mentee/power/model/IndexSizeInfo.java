package ru.mentee.power.model;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IndexSizeInfo {
  private String indexName;
  private String tableName;
  private String indexType;
  private Long sizeBytes;
  private String sizeHuman;
  private Long tuples;
  private String definition;
  private Boolean isUnique;
}