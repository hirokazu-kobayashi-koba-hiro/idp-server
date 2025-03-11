package org.idp.server.domain.model.base;

public class TimeoutException extends RuntimeException {
  public TimeoutException(String message) {
    super(message);
  }
}
