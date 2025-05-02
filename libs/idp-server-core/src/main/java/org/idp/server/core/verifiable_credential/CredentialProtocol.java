package org.idp.server.core.verifiable_credential;

import org.idp.server.core.verifiable_credential.handler.io.*;

public interface CredentialProtocol {

  CredentialResponse request(CredentialRequest request);

  BatchCredentialResponse requestBatch(BatchCredentialRequest request);

  DeferredCredentialResponse requestDeferred(DeferredCredentialRequest request);

  void setDelegate(VerifiableCredentialDelegate delegate);
}
