package org.idp.server.core.protocol;

import org.idp.server.core.handler.credential.io.*;
import org.idp.server.core.verifiablecredential.VerifiableCredentialDelegate;

public interface CredentialProtocol {

  CredentialResponse request(CredentialRequest request);

  BatchCredentialResponse requestBatch(BatchCredentialRequest request);

  DeferredCredentialResponse requestDeferred(DeferredCredentialRequest request);

  void setDelegate(VerifiableCredentialDelegate delegate);
}
