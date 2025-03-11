package org.idp.server.adapters.springboot.domain.model.base;

public class ConflictException extends RuntimeException {
  public ConflictException(String message) {
    super(message);
  }
}
