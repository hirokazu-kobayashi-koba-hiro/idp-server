package org.idp.server.core.ciba.exception;

import org.idp.server.basic.type.oauth.Error;
import org.idp.server.basic.type.oauth.ErrorDescription;

public class BackchannelAuthenticationBadRequestException extends RuntimeException {
  String error;
  String errorDescription;

  public BackchannelAuthenticationBadRequestException(String error, String errorDescription) {
    super(errorDescription);
    this.error = error;
    this.errorDescription = errorDescription;
  }

  public BackchannelAuthenticationBadRequestException(String error, String errorDescription, Throwable throwable) {
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
