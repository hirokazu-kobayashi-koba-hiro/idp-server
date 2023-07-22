package org.idp.server;

import org.idp.server.handler.credential.CredentialHandler;
import org.idp.server.handler.credential.CredentialRequestErrorHandler;
import org.idp.server.handler.credential.io.CredentialRequest;
import org.idp.server.handler.credential.io.CredentialResponse;
import org.idp.server.verifiablecredential.VerifiableCredentialDelegate;

public class CredentialApi {

  CredentialHandler credentialHandler;
  CredentialRequestErrorHandler credentialRequestErrorHandler;
  VerifiableCredentialDelegate delegate;

  public CredentialApi(CredentialHandler credentialHandler) {
    this.credentialHandler = credentialHandler;
    this.credentialRequestErrorHandler = new CredentialRequestErrorHandler();
  }

  public CredentialResponse request(CredentialRequest credentialRequest) {
    try {
      return credentialHandler.handle(credentialRequest, delegate);
    } catch (Exception exception) {
      return credentialRequestErrorHandler.handle(exception);
    }
  }

  public void setDelegate(VerifiableCredentialDelegate delegate) {
    this.delegate = delegate;
  }
}
