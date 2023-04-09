package org.idp.server.core.oauth.exception;

import org.idp.server.core.oauth.OAuthRequestContext;
import org.idp.server.core.type.oauth.Error;
import org.idp.server.core.type.oauth.ErrorDescription;

/** OAuthRedirectableBadRequestException */
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
