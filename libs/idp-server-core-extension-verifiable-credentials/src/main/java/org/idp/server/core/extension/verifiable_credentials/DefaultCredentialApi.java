/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.extension.verifiable_credentials;

import org.idp.server.core.extension.verifiable_credentials.handler.CredentialHandler;
import org.idp.server.core.extension.verifiable_credentials.handler.CredentialRequestErrorHandler;
import org.idp.server.core.extension.verifiable_credentials.handler.io.*;

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
