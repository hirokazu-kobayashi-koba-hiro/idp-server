package org.idp.server.basic.exception;

public class UnSupportedException extends RuntimeException {
  public UnSupportedException(String message) {
    super(message);
  }

  public UnSupportedException(String message, Throwable cause) {
    super(message, cause);
  }
}
