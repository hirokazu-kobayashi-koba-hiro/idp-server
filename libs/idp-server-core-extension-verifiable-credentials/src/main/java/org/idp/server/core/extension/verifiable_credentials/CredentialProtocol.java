/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.extension.verifiable_credentials;

import org.idp.server.core.extension.verifiable_credentials.handler.io.*;

public interface CredentialProtocol {

  CredentialResponse request(CredentialRequest request);

  BatchCredentialResponse requestBatch(BatchCredentialRequest request);

  DeferredCredentialResponse requestDeferred(DeferredCredentialRequest request);

  void setDelegate(VerifiableCredentialDelegate delegate);
}
