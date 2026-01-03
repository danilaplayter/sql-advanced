package ru.mentee.power.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExplainPlan {
  private Plan plan;

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Plan {
    @JsonProperty("Node Type")
    private String nodeType;

    @JsonProperty("Relation Name")
    private String relationName;

    @JsonProperty("Index Name")
    private String indexName;

    @JsonProperty("Actual Total Time")
    private Double actualTotalTime;

    @JsonProperty("Actual Startup Time")
    private Double actualStartupTime;

    @JsonProperty("Actual Rows")
    private Long actualRows;

    @JsonProperty("Plan Rows")
    private Long planRows;

    @JsonProperty("Shared Hit Blocks")
    private Long sharedHitBlocks;

    @JsonProperty("Shared Read Blocks")
    private Long sharedReadBlocks;

    @JsonProperty("Total Cost")
    private Double totalCost;

    @JsonProperty("Startup Cost")
    private Double startupCost;

    @JsonProperty("Plans")
    private List<Plan> plans;

    @JsonProperty("Filter")
    private String filter;

    @JsonProperty("Index Cond")
    private String indexCond;
  }
}