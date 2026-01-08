package ru.mentee.power.model;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public enum IsolationLevel {
  READ_UNCOMMITTED("READ UNCOMMITTED"),
  READ_COMMITTED("READ COMMITTED"),
  REPEATABLE_READ("REPEATABLE READ"),
  SERIALIZABLE("SERIALIZABLE");
  private final String sql;
}
