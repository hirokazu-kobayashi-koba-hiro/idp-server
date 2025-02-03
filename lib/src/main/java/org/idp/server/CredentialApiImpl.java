package org.idp.server;

import org.idp.server.api.CredentialApi;
import org.idp.server.basic.sql.Transactional;
import org.idp.server.handler.credential.CredentialHandler;
import org.idp.server.handler.credential.CredentialRequestErrorHandler;
import org.idp.server.handler.credential.io.*;
import org.idp.server.verifiablecredential.VerifiableCredentialDelegate;

@Transactional
public class CredentialApiImpl implements CredentialApi {

  CredentialHandler credentialHandler;
  CredentialRequestErrorHandler credentialRequestErrorHandler;
  VerifiableCredentialDelegate delegate;

  public CredentialApiImpl(CredentialHandler credentialHandler) {
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
