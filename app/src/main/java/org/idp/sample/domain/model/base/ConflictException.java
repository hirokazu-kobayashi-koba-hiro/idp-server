package org.idp.sample.domain.model.base;

public class ConflictException extends RuntimeException {
  public ConflictException(String message) {
    super(message);
  }
}
