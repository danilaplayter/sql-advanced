package ru.mentee.power.exception;

public class ProductNotAvailableException extends BusinessException {
  public ProductNotAvailableException(String message) {
    super(message);
  }

  public ProductNotAvailableException(String message, Throwable cause) {
    super(message, cause);
  }
}