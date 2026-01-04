package ru.mentee.power.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Data
@AllArgsConstructor
public class QueryPlanParser {

  private final JsonNode planNode;
  private final ObjectMapper objectMapper;

  private String scanType;
  private Long buffersHit;
  private Long buffersRead;
  private Long rowsScanned;
  private Long rowsReturned;
  private Long planningTimeMs;
  private Double costEstimate;
  private List<String> indexesUsed;

  public QueryPlanParser(JsonNode planNode) {
    this.planNode = planNode;
    this.objectMapper = new ObjectMapper();
    this.buffersHit = 0L;
    this.buffersRead = 0L;
    this.rowsScanned = 0L;
    this.rowsReturned = 0L;
    this.indexesUsed = new ArrayList<>();
    this.planningTimeMs = 0L;
    this.costEstimate = 0.0;
    parsePlan();
  }

  private void parsePlan() {
    try {
      if (planNode == null || !planNode.isArray() || planNode.size() == 0) {
        log.warn("Empty or invalid plan node");
        return;
      }

      JsonNode root = planNode.get(0);

      if (root.has("Planning Time")) {
        planningTimeMs = (long) (root.get("Planning Time").asDouble() * 1000);
      }

      JsonNode plan = root.get("Plan");
      if (plan != null) {
        parseNode(plan);
      }

    } catch (Exception e) {
      log.error("Error parsing query plan", e);
    }
  }

  private void parseNode(JsonNode node) {
    if (node == null) return;

    if (node.has("Node Type")) {
      String nodeType = node.get("Node Type").asText();

      if (scanType == null && nodeType.contains("Scan")) {
        scanType = nodeType;

        if (node.has("Index Name")) {
          String indexName = node.get("Index Name").asText();
          indexesUsed.add(indexName);
        }
      }

      if (node.has("Plan Rows")) {
        rowsScanned += node.get("Plan Rows").asLong();
      }
      if (node.has("Actual Rows")) {
        rowsReturned += node.get("Actual Rows").asLong();
      }

      if (node.has("Total Cost")) {
        double nodeCost = node.get("Total Cost").asDouble();
        if (costEstimate == null || nodeCost > costEstimate) {
          costEstimate = nodeCost;
        }
      }
    }

    if (node.has("Buffers")) {
      JsonNode buffers = node.get("Buffers");
      if (buffers.has("shared")) {
        JsonNode shared = buffers.get("shared");
        if (shared.has("hit")) {
          buffersHit += shared.get("hit").asLong();
        }
        if (shared.has("read")) {
          buffersRead += shared.get("read").asLong();
        }
      }
    }

    if (node.has("Plans")) {
      JsonNode plans = node.get("Plans");
      if (plans.isArray()) {
        for (JsonNode child : plans) {
          parseNode(child);
        }
      }
    }
  }

}