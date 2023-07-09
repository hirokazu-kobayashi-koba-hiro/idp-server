package org.idp.server;

import org.idp.server.handler.credential.CredentialHandler;
import org.idp.server.handler.credential.io.CredentialRequest;
import org.idp.server.handler.credential.io.CredentialRequestStatus;
import org.idp.server.handler.credential.io.CredentialResponse;
import org.idp.server.type.oauth.Error;
import org.idp.server.type.oauth.ErrorDescription;
import org.idp.server.verifiablecredential.VerifiableCredentialErrorResponse;

public class CredentialApi {

  CredentialHandler credentialHandler;

  public CredentialApi(CredentialHandler credentialHandler) {
    this.credentialHandler = credentialHandler;
  }

  public CredentialResponse request(CredentialRequest credentialRequest) {
    try {
      return credentialHandler.handle(credentialRequest);
    } catch (Exception exception) {
      return new CredentialResponse(
          CredentialRequestStatus.SERVER_ERROR,
          new VerifiableCredentialErrorResponse(
              new Error("server_error"), new ErrorDescription(exception.getMessage())));
    }
  }
}
