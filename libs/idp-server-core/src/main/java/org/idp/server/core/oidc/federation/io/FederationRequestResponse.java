/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.federation.io;

import java.util.Map;

public class FederationRequestResponse {

  FederationRequestStatus status;
  Map<String, Object> contents;

  public FederationRequestResponse(FederationRequestStatus status) {
    this.status = status;
  }

  public FederationRequestResponse(FederationRequestStatus status, Map<String, Object> contents) {
    this.status = status;
    this.contents = contents;
  }

  public FederationRequestStatus status() {
    return status;
  }

  public Map<String, Object> contents() {
    return contents;
  }
}
