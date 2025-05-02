package org.idp.server.core.verifiable_credential;

import org.idp.server.basic.type.oauth.Error;
import org.idp.server.basic.type.oauth.ErrorDescription;
import org.idp.server.basic.type.oauth.ErrorResponseCreatable;

public class VerifiableCredentialErrorResponse implements ErrorResponseCreatable {
  Error error;
  ErrorDescription errorDescription;

  public VerifiableCredentialErrorResponse() {}

  public VerifiableCredentialErrorResponse(Error error, ErrorDescription errorDescription) {
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
