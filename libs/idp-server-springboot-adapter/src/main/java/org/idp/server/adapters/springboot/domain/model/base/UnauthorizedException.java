package org.idp.server.adapters.springboot.domain.model.base;

public class UnauthorizedException extends RuntimeException {
  public UnauthorizedException(String message) {
    super(message);
  }
}
