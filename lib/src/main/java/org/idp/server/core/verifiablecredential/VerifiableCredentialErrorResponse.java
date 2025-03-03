package org.idp.server.core.verifiablecredential;

import org.idp.server.core.type.oauth.Error;
import org.idp.server.core.type.oauth.ErrorDescription;
import org.idp.server.core.type.oauth.ErrorResponseCreatable;

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
