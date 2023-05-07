package org.idp.server.token;

import org.idp.server.type.oauth.Error;
import org.idp.server.type.oauth.ErrorDescription;
import org.idp.server.type.oauth.ErrorResponseCreatable;

public class TokenErrorResponse implements ErrorResponseCreatable {
  Error error;
  ErrorDescription errorDescription;

  public TokenErrorResponse() {}

  public TokenErrorResponse(Error error, ErrorDescription errorDescription) {
    this.error = error;
    this.errorDescription = errorDescription;
  }

  public String contents() {
    return toErrorResponse(error, errorDescription);
  }
}
