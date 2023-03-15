package org.idp.server.core.oauth.exception;

/** OAuthRedirectableBadRequestException */
public class OAuthRedirectableBadRequestException extends RuntimeException {
  public OAuthRedirectableBadRequestException(String message) {
    super(message);
  }

  public OAuthRedirectableBadRequestException(String message, Throwable throwable) {
    super(message, throwable);
  }
}
