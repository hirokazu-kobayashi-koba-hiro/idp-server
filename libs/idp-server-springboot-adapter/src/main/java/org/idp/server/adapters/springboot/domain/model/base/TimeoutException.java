package org.idp.server.adapters.springboot.domain.model.base;

public class TimeoutException extends RuntimeException {
  public TimeoutException(String message) {
    super(message);
  }
}
