package org.idp.server.oauth.exception;

import org.idp.server.oauth.OAuthRequestContext;
import org.idp.server.type.oauth.Error;
import org.idp.server.type.oauth.ErrorDescription;

public class OAuthRedirectableBadRequestException extends RuntimeException {

  String error;
  String errorDescription;
  OAuthRequestContext oAuthRequestContext;

  public OAuthRedirectableBadRequestException(String message) {
    super(message);
  }

  public OAuthRedirectableBadRequestException(
      String error, String message, OAuthRequestContext oAuthRequestContext) {
    super(message);
    this.error = error;
    this.errorDescription = message;
    this.oAuthRequestContext = oAuthRequestContext;
  }

  public OAuthRequestContext oAuthRequestContext() {
    return oAuthRequestContext;
  }

  public Error error() {
    return new Error(error);
  }

  public ErrorDescription errorDescription() {
    return new ErrorDescription(errorDescription);
  }
}
