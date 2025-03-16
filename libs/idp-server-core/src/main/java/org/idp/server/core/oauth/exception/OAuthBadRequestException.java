package org.idp.server.core.oauth.exception;

import org.idp.server.core.type.oauth.Error;
import org.idp.server.core.type.oauth.ErrorDescription;

/** OAuthBadRequestException */
public class OAuthBadRequestException extends RuntimeException {

  String error;
  String errorDescription;

  public OAuthBadRequestException(String error, String errorDescription) {
    super(errorDescription);
    this.error = error;
    this.errorDescription = errorDescription;
  }

  public OAuthBadRequestException(String error, String errorDescription, Throwable throwable) {
    super(errorDescription, throwable);
    this.error = error;
    this.errorDescription = errorDescription;
  }

  public Error error() {
    return new Error(error);
  }

  public ErrorDescription errorDescription() {
    return new ErrorDescription(errorDescription);
  }
}
