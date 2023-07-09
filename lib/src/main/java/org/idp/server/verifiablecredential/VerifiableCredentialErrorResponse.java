package org.idp.server.verifiablecredential;

import org.idp.server.type.oauth.Error;
import org.idp.server.type.oauth.ErrorDescription;
import org.idp.server.type.oauth.ErrorResponseCreatable;

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
