package org.idp.server.core;

import org.idp.server.core.api.CredentialApi;
import org.idp.server.core.basic.sql.Transactional;
import org.idp.server.core.handler.credential.CredentialHandler;
import org.idp.server.core.handler.credential.CredentialRequestErrorHandler;
import org.idp.server.core.handler.credential.io.*;
import org.idp.server.core.verifiablecredential.VerifiableCredentialDelegate;

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
