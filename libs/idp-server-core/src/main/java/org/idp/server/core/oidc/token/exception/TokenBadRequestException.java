package org.idp.server.core.oidc.token.exception;

import org.idp.server.basic.type.oauth.Error;
import org.idp.server.basic.type.oauth.ErrorDescription;

public class TokenBadRequestException extends RuntimeException {

  String error;
  String errorDescription;

  public TokenBadRequestException(String errorDescription) {
    super(errorDescription);
    this.error = "invalid_request";
    this.errorDescription = errorDescription;
  }

  public TokenBadRequestException(String errorDescription, Throwable throwable) {
    super(errorDescription, throwable);
    this.error = "invalid_request";
    this.errorDescription = errorDescription;
  }

  public TokenBadRequestException(String error, String errorDescription) {
    super(errorDescription);
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
