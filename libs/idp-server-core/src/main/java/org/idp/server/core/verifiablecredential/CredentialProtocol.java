package org.idp.server.core.verifiablecredential;

import org.idp.server.core.verifiablecredential.handler.io.*;

public interface CredentialProtocol {

  CredentialResponse request(CredentialRequest request);

  BatchCredentialResponse requestBatch(BatchCredentialRequest request);

  DeferredCredentialResponse requestDeferred(DeferredCredentialRequest request);

  void setDelegate(VerifiableCredentialDelegate delegate);
}
