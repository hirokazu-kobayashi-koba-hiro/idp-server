package org.idp.server.core.oauth.exception;

import org.idp.server.core.oauth.OAuthRequestContext;

/** OAuthRedirectableBadRequestException */
public class OAuthRedirectableBadRequestException extends RuntimeException implements OAuthError {

  String error;
  String errorDescription;
  OAuthRequestContext oAuthRequestContext;

  public OAuthRedirectableBadRequestException(String message) {
    super(message);
  }

  public OAuthRedirectableBadRequestException(
      String error, String message, Throwable throwable, OAuthRequestContext oAuthRequestContext) {
    super(message, throwable);
    this.error = error;
    this.errorDescription = message;
    this.oAuthRequestContext = oAuthRequestContext;
  }

  public OAuthRequestContext oAuthRequestContext() {
    return oAuthRequestContext;
  }

  @Override
  public String error() {
    return error;
  }

  @Override
  public String errorDescription() {
    return errorDescription;
  }
}