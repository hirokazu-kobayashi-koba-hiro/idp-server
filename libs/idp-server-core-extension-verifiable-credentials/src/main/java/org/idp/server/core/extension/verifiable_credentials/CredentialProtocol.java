package org.idp.server.core.extension.verifiable_credentials;

import org.idp.server.core.extension.verifiable_credentials.handler.io.*;

public interface CredentialProtocol {

  CredentialResponse request(CredentialRequest request);

  BatchCredentialResponse requestBatch(BatchCredentialRequest request);

  DeferredCredentialResponse requestDeferred(DeferredCredentialRequest request);

  void setDelegate(VerifiableCredentialDelegate delegate);
}
