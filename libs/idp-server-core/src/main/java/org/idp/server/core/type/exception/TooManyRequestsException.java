package org.idp.server.core.type.exception;

public class TooManyRequestsException extends RuntimeException {
  public TooManyRequestsException(String message) {
    super(message);
  }

  public TooManyRequestsException(String message, Throwable cause) {
    super(message, cause);
  }
}
