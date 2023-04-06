package org.idp.server.core.token.exception;

public class TokenBadRequestException extends RuntimeException {
  public TokenBadRequestException(String message) {
    super(message);
  }

  public TokenBadRequestException(String message, Throwable throwable) {
    super(message, throwable);
  }
}
