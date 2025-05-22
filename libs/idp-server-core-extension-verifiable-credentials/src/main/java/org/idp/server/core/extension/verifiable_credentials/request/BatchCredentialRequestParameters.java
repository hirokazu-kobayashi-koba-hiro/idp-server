/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.extension.verifiable_credentials.request;

import java.util.Map;
import org.idp.server.core.extension.verifiable_credentials.exception.VerifiableCredentialRequestInvalidException;

public class BatchCredentialRequestParameters implements VerifiableCredentialRequestTransformable {
  Map<String, Object> values;

  public BatchCredentialRequestParameters() {
    this.values = Map.of();
  }

  public BatchCredentialRequestParameters(Map<String, Object> values) {
    this.values = values;
  }

  public boolean isEmpty() {
    return values.isEmpty();
  }

  public Object credentialRequests() {
    return values.get("credential_requests");
  }

  public boolean hasCredentialRequests() {
    return values.containsKey("credential_requests");
  }

  // FIXME
  public BatchCredentialRequests toBatchCredentialRequest() {
    try {
      return transformBatchRequest(credentialRequests());
    } catch (VerifiableCredentialRequestInvalidException e) {
      throw new RuntimeException(e);
    }
  }
}
