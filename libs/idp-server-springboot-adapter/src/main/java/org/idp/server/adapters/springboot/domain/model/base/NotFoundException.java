package org.idp.server.adapters.springboot.domain.model.base;

public class NotFoundException extends RuntimeException {
  public NotFoundException(String message) {
    super(message);
  }
}
