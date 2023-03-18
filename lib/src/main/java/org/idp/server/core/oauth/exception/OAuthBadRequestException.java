package org.idp.server.core.oauth.exception;

/** OAuthBadRequestException */
public class OAuthBadRequestException extends RuntimeException {
  public OAuthBadRequestException(String message) {
    super(message);
  }

  public OAuthBadRequestException(String message, Throwable throwable) {
    super(message, throwable);
  }
}
