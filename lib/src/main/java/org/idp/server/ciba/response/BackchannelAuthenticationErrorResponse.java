package org.idp.server.ciba.response;

import org.idp.server.type.oauth.Error;
import org.idp.server.type.oauth.ErrorDescription;
import org.idp.server.type.oauth.ErrorResponseCreatable;

public class BackchannelAuthenticationErrorResponse implements ErrorResponseCreatable {
  Error error;
  ErrorDescription errorDescription;

  public BackchannelAuthenticationErrorResponse() {}

  public BackchannelAuthenticationErrorResponse(Error error, ErrorDescription errorDescription) {
    this.error = error;
    this.errorDescription = errorDescription;
  }

  public Error error() {
    return error;
  }

  public ErrorDescription errorDescription() {
    return errorDescription;
  }

  public String contents() {
    return toErrorResponse(error, errorDescription);
  }
}
