package ru.mentee.power.exception;

public class InsufficientFundsException extends BusinessException {
  public InsufficientFundsException(String message) {
    super(message);
  }

  public InsufficientFundsException(String message, Throwable cause) {
    super(message, cause);
  }
}