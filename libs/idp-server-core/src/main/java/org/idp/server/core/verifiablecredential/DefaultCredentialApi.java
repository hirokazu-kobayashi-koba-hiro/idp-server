package org.idp.server.core.verifiablecredential;

import org.idp.server.core.verifiablecredential.handler.CredentialHandler;
import org.idp.server.core.verifiablecredential.handler.CredentialRequestErrorHandler;
import org.idp.server.core.verifiablecredential.handler.io.*;

public class DefaultCredentialApi implements CredentialProtocol {

  CredentialHandler credentialHandler;
  CredentialRequestErrorHandler credentialRequestErrorHandler;
  VerifiableCredentialDelegate delegate;

  public DefaultCredentialApi(CredentialHandler credentialHandler) {
    this.credentialHandler = credentialHandler;
    this.credentialRequestErrorHandler = new CredentialRequestErrorHandler();
  }

  public CredentialResponse request(CredentialRequest request) {
    try {
      return credentialHandler.handleRequest(request, delegate);
    } catch (Exception exception) {
      return credentialRequestErrorHandler.handle(exception);
    }
  }

  public BatchCredentialResponse requestBatch(BatchCredentialRequest request) {
    try {
      return credentialHandler.handleBatchRequest(request, delegate);
    } catch (Exception exception) {
      return credentialRequestErrorHandler.handleBatchRequest(exception);
    }
  }

  public DeferredCredentialResponse requestDeferred(DeferredCredentialRequest request) {
    try {
      return credentialHandler.handleDeferredRequest(request, delegate);
    } catch (Exception exception) {
      return credentialRequestErrorHandler.handleDeferredRequest(exception);
    }
  }

  public void setDelegate(VerifiableCredentialDelegate delegate) {
    this.delegate = delegate;
  }
}
