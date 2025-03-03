package org.idp.server.domain.model.base;

public class InternalServerErrorException extends RuntimeException {
  public InternalServerErrorException(String message) {
    super(message);
  }

  public InternalServerErrorException(String message, Throwable cause) {
    super(message, cause);
  }
}
