package org.idp.server.handler.credential.io;

import org.idp.server.verifiablecredential.VerifiableCredentialErrorResponse;
import org.idp.server.verifiablecredential.VerifiableCredentialResponse;

public class CredentialResponse {
  CredentialRequestStatus status;
  VerifiableCredentialResponse response;
  VerifiableCredentialErrorResponse errorResponse;

  public CredentialResponse(CredentialRequestStatus status, VerifiableCredentialResponse response) {
    this.status = status;
    this.response = response;
    this.errorResponse = new VerifiableCredentialErrorResponse();
  }

  public CredentialResponse(
      CredentialRequestStatus status, VerifiableCredentialErrorResponse errorResponse) {
    this.status = status;
    this.response = new VerifiableCredentialResponse();
    this.errorResponse = errorResponse;
  }

  public String response() {
    if (status.isOK()) {
      return "";
    }
    return errorResponse.contents();
  }
}
