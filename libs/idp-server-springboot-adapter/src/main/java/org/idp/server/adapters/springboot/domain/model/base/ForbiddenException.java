package org.idp.server.adapters.springboot.domain.model.base;

public class ForbiddenException extends RuntimeException {
  public ForbiddenException(String message) {
    super(message);
  }
}
