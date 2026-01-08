package ru.mentee.power.exception;

import lombok.Getter;

@Getter
public class ConcurrencyException extends RuntimeException {
  private final String isolationLevel;
  private final String operation;

  public ConcurrencyException(String message, String isolationLevel, String operation) {
    super(message);
    this.isolationLevel = isolationLevel;
    this.operation = operation;
  }

  public ConcurrencyException(String message, Throwable cause, String isolationLevel, String operation) {
    super(message, cause);
    this.isolationLevel = isolationLevel;
    this.operation = operation;
  }

}