package org.idp.server.tokenintrospection.exception;

public class TokenInvalidException extends RuntimeException {
  public TokenInvalidException(String message) {
    super(message);
  }

  public TokenInvalidException(String message, Throwable throwable) {
    super(message, throwable);
  }
}
