package org.idp.server.core.type.exception;

public class TimeoutException extends RuntimeException {
  public TimeoutException(String message) {
    super(message);
  }
}
