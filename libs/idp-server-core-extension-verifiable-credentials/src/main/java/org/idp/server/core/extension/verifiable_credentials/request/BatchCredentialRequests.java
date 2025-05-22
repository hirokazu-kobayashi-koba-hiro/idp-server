/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.extension.verifiable_credentials.request;

import java.util.Iterator;
import java.util.List;

public class BatchCredentialRequests implements Iterable<VerifiableCredentialRequest> {

  List<VerifiableCredentialRequest> values;

  public BatchCredentialRequests() {
    this.values = List.of();
  }

  public BatchCredentialRequests(List<VerifiableCredentialRequest> values) {
    this.values = values;
  }

  @Override
  public Iterator<VerifiableCredentialRequest> iterator() {
    return values.iterator();
  }
}
