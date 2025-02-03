package org.idp.server.api;

import org.idp.server.handler.credential.io.*;
import org.idp.server.verifiablecredential.VerifiableCredentialDelegate;

public interface CredentialApi {

  CredentialResponse request(CredentialRequest request);

  BatchCredentialResponse requestBatch(BatchCredentialRequest request);

  DeferredCredentialResponse requestDeferred(DeferredCredentialRequest request);

  void setDelegate(VerifiableCredentialDelegate delegate);
}
