package ru.mentee.power.exception;

public class IsolationLevelException extends RuntimeException {
  public IsolationLevelException(String message) {
    super(message);
  }

  public IsolationLevelException(String message, Throwable cause) {
    super(message, cause);
  }
}