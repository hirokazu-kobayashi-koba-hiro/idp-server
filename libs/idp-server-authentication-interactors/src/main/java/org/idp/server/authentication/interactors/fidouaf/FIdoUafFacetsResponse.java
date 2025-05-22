/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.authentication.interactors.fidouaf;

import java.util.Map;

public class FIdoUafFacetsResponse {
  int statusCode;
  Map<String, Object> response;

  public FIdoUafFacetsResponse() {}

  public FIdoUafFacetsResponse(int statusCode, Map<String, Object> response) {
    this.statusCode = statusCode;
    this.response = response;
  }

  public int statusCode() {
    return statusCode;
  }

  public Map<String, Object> response() {
    return response;
  }
}
