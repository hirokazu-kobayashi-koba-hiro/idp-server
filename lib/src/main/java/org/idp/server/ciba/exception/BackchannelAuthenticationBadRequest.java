package org.idp.server.ciba.exception;

import org.idp.server.type.oauth.Error;
import org.idp.server.type.oauth.ErrorDescription;

public class BackchannelAuthenticationBadRequest extends RuntimeException {
  String error;
  String errorDescription;

  public BackchannelAuthenticationBadRequest(String error, String errorDescription) {
    super(errorDescription);
    this.error = error;
    this.errorDescription = errorDescription;
  }

  public BackchannelAuthenticationBadRequest(
      String error, String errorDescription, Throwable throwable) {
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
