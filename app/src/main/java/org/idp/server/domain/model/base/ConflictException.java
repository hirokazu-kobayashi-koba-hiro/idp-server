package org.idp.server.domain.model.base;

public class ConflictException extends RuntimeException {
  public ConflictException(String message) {
    super(message);
  }
}
